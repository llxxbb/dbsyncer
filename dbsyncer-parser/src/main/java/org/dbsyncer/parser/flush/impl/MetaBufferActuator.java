/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.ExecutorFactory;
import org.dbsyncer.common.config.props.BufferProps;
import org.dbsyncer.common.metric.TimeRegistry;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.scheduled.ScheduledTaskJob;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.common.util.UUIDUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.TableGroupContext;
import org.dbsyncer.parser.ddl.DDLParser;
import org.dbsyncer.parser.flush.BufferActuator;
import org.dbsyncer.parser.flush.BufferRequest;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.parser.pipeline.SyncPipeline;
import org.dbsyncer.parser.pipeline.SyncTask;
import org.dbsyncer.parser.service.TableGroupModelService;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.config.ListenerConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.enums.ChangedEventTypeEnum;
import org.dbsyncer.sdk.enums.DDLOperationEnum;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.model.*;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.sdk.util.RowConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Meta 级别的事件同步执行器（每个 meta 独享一个队列）
 * <p>
 * 数据同步流水线：
 * <pre>
 * enqueue → queue → dequeue → groupByTable → writeDDL()/writeDML() → commitOffset()
 *    ↑                      ↑         ↑            ↑                    ↑
 *  入队                   出队循环   按表分组      按类型写入           提交偏移
 * </pre>
 *
 * @Version 2.0.0
 * @Author AE86
 * @Date 2024-01-XX
 */
public class MetaBufferActuator implements BufferActuator, ScheduledTaskJob {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    // ===== 队列与调度基础设施 =====

    private final ReentrantLock taskLock = new ReentrantLock();
    private volatile BlockingQueue<WriterRequest> queue;
    private String taskKey;

    /**
     * ADR-0031：排空标志。true 表示队列只出不进，enqueue() 阻塞等待。
     */
    private volatile boolean draining = false;

    /**
     * ADR-0031：排空完成条件。reload() 完成后排空后 signalAll 唤醒等待的 enqueue() 线程。
     */
    private final ReentrantLock drainingLock = new ReentrantLock();
    private final Condition drainingDone = drainingLock.newCondition();

    // ===== 业务字段 =====

    private String metaId;
    private ExecutorFactory executorFactory;
    private ConnectorFactory connectorFactory;
    private CounterService counterService;
    private DDLParser ddlParser;
    private TableGroupContext tableGroupContext;
    private TableGroupModelService tableGroupModelService;
    private ErrorQueueCollector errorQueueCollector;
    public MessageService messageService;

    /**
     * 是否有待处理的任务（事件进入队列时设置为 true，队列为空时设置为 false）
     */
    private volatile boolean hasPendingTask = false;

    // ===== 依赖注入（由 BufferActuatorRouter 手动注入，非 Spring Bean） =====

    private ScheduledTaskService scheduledTaskService;
    protected ProfileComponent profileComponent;
    private TimeRegistry timeRegistry;
    protected ConfigRegistry configRegistry;

    // ========================================================================
    // 流水线步骤 1: 入队 (enqueue)
    // ========================================================================

    /**
     * 入队事件。
     * <p>
     * DDL 事件会阻塞等待队列消费完成，保证表结构修改的原子性。
     */
    @Override
    public void enqueue(BufferRequest request) {
        // ADR-0031：排空中阻塞等待，不丢数据
        if (draining) {
            drainingLock.lock();
            try {
                while (draining) {
                    drainingDone.await();
                }
            } catch (InterruptedException e) {
                // ADR-0031 fix：不 return，等 draining 结束后继续走正常入队路径，避免请求丢失
                Thread.currentThread().interrupt();
            } finally {
                drainingLock.unlock();
            }
        }

        // DDL 事件特殊处理：阻塞等待队列消费完成，保证表结构修改的原子性
        if (request instanceof WriterRequest) {
            WriterRequest writerRequest = (WriterRequest) request;
            if (ChangedEventTypeEnum.isDDL(writerRequest.getTypeEnum())) {
                // DDL事件，阻塞等待队列消费完成
                while (isRunning()) {
                    if (queue.isEmpty()) {
                        // 事件进入队列时，设置 pending 状态
                        setPendingTask();
                        doEnqueue(writerRequest);
                        return;
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        logger.warn("DDL 事件等待队列排空时被中断，DDL 将被丢弃: metaId={}, sql={}", metaId, writerRequest.getSql());
                        return;
                    }
                }
                // 任务已停止，DDL 无法入队
                logger.warn("[{}] 任务已停止，DDL 事件无法入队，将被丢弃: metaId={}, sql={}",
                        getClass().getSimpleName(), metaId, writerRequest.getSql());
                return;
            }
        }

        // 普通事件：事件进入队列时，设置 pending 状态
        setPendingTask();
        doEnqueue((WriterRequest) request);
    }

    /**
     * 将请求放入队列。队列满时阻塞等待（带超时重试），保证数据不丢失。
     * <p>
     * 只有当任务已停止（isRunning()=false）且队列仍满时才会放弃入队。
     */
    private void doEnqueue(WriterRequest request) {
        // 快速路径：队列未满直接入队
        if (queue.offer(request)) {
            return;
        }

        // 慢速路径：队列已满，阻塞等待直到入队成功或任务停止
        logger.warn("[{}] 缓存队列已满(metaId={})，阻塞等待消费者排空...", getClass().getSimpleName(), metaId);
        while (isRunning()) {
            try {
                // 带超时重试，避免无限死等
                if (queue.offer(request, 1, TimeUnit.SECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("[{}] 队列满等待时被中断，事件将丢失: metaId={}, tableName={}",
                        getClass().getSimpleName(), metaId, request.getTableName());
                return;
            }
        }

        // 任务已停止，无法入队
        logger.warn("[{}] 任务已停止且队列已满，事件将被丢弃: metaId={}, tableName={}",
                getClass().getSimpleName(), metaId, request.getTableName());
    }

    // ========================================================================
    // 流水线步骤 2: 出队循环 (dequeue)
    // ========================================================================

    /**
     * 定时任务入口：从队列中取出事件，按批次处理。
     */
    @Override
    public void run() {
        boolean locked = false;
        try {
            locked = taskLock.tryLock();
            if (locked) {
                dequeue();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (locked) {
                taskLock.unlock();
            }
        }
    }

    /**
     * 出队循环：从队列中取出事件，按批次分组并执行同步。
     */
    private void dequeue() throws Exception {
        if (queue.isEmpty()) {
            return;
        }

        AtomicLong batchCounter = new AtomicLong();
        AtomicLong batchIndexCounter = new AtomicLong();  // 批次序号计数器，用于同一 position 保序
        Map<String, WriterResponse> tableGroups = new HashMap<>();
        WriterResponse maxOffsetResponse = null;
        int bufferPullCount = getBufferPullCount();

        while (!queue.isEmpty()) {
            // 任务已停止：立即停止出队，残留事件留在队列（快照点不动，重启后由源库重放）
            if (!isRunning()) {
                logger.warn("[{}] 任务已停止，停止出队: metaId={}, 残留事件={}",
                        getClass().getSimpleName(), metaId, queue.size());
                return;
            }
            WriterRequest poll = queue.poll();
            String tableName = poll.getTableName();
            WriterResponse response = tableGroups.compute(tableName, (k, v) -> {
                if (v == null) {
                    WriterResponse newResponse = new WriterResponse();
                    // 为每个新创建的批次分配序号
                    newResponse.setBatchIndex((int) batchIndexCounter.getAndIncrement());
                    return newResponse;
                }
                return v;
            });
            mergeToGroup(poll, response);
            batchCounter.incrementAndGet();

            // 记录最大 offset（queue 是 FIFO，最后一个 response 的 offset 最大）
            maxOffsetResponse = response;

            // 当达到批次大小时，执行当前批次
            if (batchCounter.get() >= bufferPullCount) {
                executeBatch(tableGroups, maxOffsetResponse);
                tableGroups.clear();
                meter(batchCounter.get());
                batchCounter.set(0);
                tableGroups = new HashMap<>();
                maxOffsetResponse = null;
            }

            WriterRequest next = queue.peek();
            if (null != next && shouldBreakBatch(next, response)) {
                break;
            }
        }

        // 执行剩余未达到批次大小的数据
        if (!tableGroups.isEmpty()) {
            executeBatch(tableGroups, maxOffsetResponse);
            tableGroups.clear();
            meter(batchCounter.get());
        }
    }

    /**
     * 将请求合并到对应的表分组中。
     */
    private void mergeToGroup(WriterRequest request, WriterResponse response) {
        if (!CollectionUtils.isEmpty(request.getRow())) {
            response.addData(request.getRow());
            // 每追加一条新行数据，同步追加对应的 beforeRow（UPDATE 场景下为旧值，非 UPDATE 场景为 null），
            // 保证 beforeRows 与 dataList 等长，避免批内多行共享第一条的旧主键导致目标端 WHERE 定位错误
            response.addBeforeRow(request.getBeforeRow());
        }
        if (request.getChangedOffset() != null) {
            response.setChangedOffset(request.getChangedOffset());
        }
        if (!response.isMerged()) {
            response.setTraceId(request.getTraceId());
            response.setTableName(request.getTableName());
            response.setEvent(request.getEvent());
            response.setTypeEnum(request.getTypeEnum());
            response.setSql(request.getSql());
            // 传递列名信息（从第一个请求中获取）
            response.setColumnNames(request.getColumnNames());
            response.setMerged(true);
        } else if (configRegistry.getConfig().getBasic().getEnablePrintTraceInfo()
                && StringUtil.isNotBlank(request.getTraceId())) {
            logger.info("traceId:{} merge into traceId:{}", request.getTraceId(), response.getTraceId());
        }
    }

    /**
     * 判断是否应该中断当前批次。
     * <p>
     * 并发场景下，同一条数据可能连续触发 Insert > Delete > Insert，批处理任务中出现不同事件时，
     * 或遇到 DDL 事件时，应该中断当前批次，保证数据一致性。
     */
    private boolean shouldBreakBatch(WriterRequest nextRequest, WriterResponse currentResponse) {
        return !StringUtil.equals(nextRequest.getEvent(), currentResponse.getEvent())
                || ChangedEventTypeEnum.isDDL(currentResponse.getTypeEnum());
    }

    /**
     * 执行一批同步任务。
     * <p>
     * 仅当全部表组都已处理时才提交偏移量：被跳过的批次（任务已停止）既未写入目标库、
     * 也未进错误队列，提交偏移量会让快照点越过这些数据，重启后永久丢失。
     */
    private void executeBatch(Map<String, WriterResponse> tableGroups, WriterResponse maxOffsetResponse) throws Exception {
        // 直接执行所有表的同步，无需排序
        // 每个表的同步是独立的
        for (Map.Entry<String, WriterResponse> entry : tableGroups.entrySet()) {
            executeSync(entry.getValue());
        }

        // 执行完成后，提交偏移量
        commitOffset(maxOffsetResponse);
    }

    // ========================================================================
    // 流水线步骤 3: 执行同步 (writeDDL / writeDML)
    // ========================================================================

    /**
     * 执行同步：根据事件类型分发到 DDL 或 DML 处理。
     */
    private void executeSync(WriterResponse response) throws Exception {
        long now = Instant.now().toEpochMilli();
        Meta meta = profileComponent.getMeta(response.getChangedOffset().getMetaId());
        final Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        try {
            doSync(response, meta, mapping);
            logger.info("mappingId {}, {}目标表，{}, {}条, {}ms", meta.getMappingId(), response.getTableName(), response.getSuffixName(), response.getTaskSize(), (Instant.now().toEpochMilli() - now));
        } catch (Exception e) {
            errorQueueCollector.closeTask(meta, response.getTableName(), e, response.getTaskSize(),
                    NotifyType.TASK_INTERRUPTED);
            throw e;
        }
    }

    /**
     * 执行同步的核心逻辑：根据事件类型分发到 DDL 或 DML 处理。
     */
    private void doSync(WriterResponse response, Meta meta, Mapping mapping) throws Exception {
        if (!MetaEnum.isRunning(meta.getState())) {
            logger.warn("任务已停止，跳过批次处理: metaId={}, 表名={}", meta.getId(), response.getTableName());
            return;
        }
        // 打印trace信息
        printTraceInfo(response);
        List<TableGroupPicker> pickers = tableGroupContext.getTableGroupPickers(meta.getId(), response.getTableName());
        if (pickers.isEmpty()) {
            String msg = String.format("pickers 不能为空！mappingId: %s, 源表名: %s",
                    mapping.getId(), response.getTableName());
            throw new RuntimeException(msg);
        }
        switch (response.getTypeEnum()) {
            case DDL:
                if (!mapping.getListener().isEnableDDL()) {
                    // DDL 被禁用，直接返回
                    break;
                }
                // DDL 处理：更新 fieldMapping 并强制刷新缓存
                // 1. 先处理 DDL，更新 TableGroup 的 fieldMapping
                List<TableGroup> updatedTableGroups = pickers.stream().map(picker -> {
                    TableGroup tableGroup = picker.getTableGroup();
                    writeDDL(response, meta, mapping, tableGroup);
                    return tableGroup;
                }).collect(Collectors.toList());

                // 2. 强制刷新缓存，确保后续的 DML 事件使用最新的 fieldMapping
                // 注意：这里使用更新后的 TableGroup，确保 fieldMapping 已同步
                tableGroupContext.update(mapping, updatedTableGroups);
                break;
            case SCAN:
                for(TableGroupPicker picker : pickers) {
                    writeDML(response, meta, mapping, picker, picker.getSourceFields(), false);
                }
                break;
            case ROW:
                for(TableGroupPicker picker : pickers) {
                    writeDML(response, meta, mapping, picker, picker.getSourceFields(), true);
                }
                break;
        }
    }

    /**
     * 写入 DDL：解析 DDL → 执行 DDL（如果启用）→ 刷新表结构 → 更新字段映射
     *
     * @param response   WriterResponse，包含DDL SQL和事件信息
     * @param mapping    Mapping配置，包含源和目标连接器ID
     * @param tableGroup TableGroup配置，包含表结构和字段映射
     */
    public void writeDDL(WriterResponse response, Meta meta, Mapping mapping, TableGroup tableGroup) {
        try {
            ListenerConfig listenerConfig = mapping.getListener();

            // 注意：全局 DDL 开关检查已在 Listener 和 doSync() 方法中完成，此处无需重复检查

            // 1. 解析 DDL 获取操作类型
            ConnectorConfig tConnConfig = getConnectorConfig(mapping.getTargetConnectorId());
            String tConnType = tConnConfig.getConnectorType();
            ConnectorService connectorService = connectorFactory.getConnectorService(tConnType);
            // 传递源和目标连接器类型信息给 DDL 解析器
            DDLConfig targetDDLConfig = ddlParser.parse(connectorService, tableGroup, response.getSql());

            // 复制 tableGroup 的 params 到 DDLConfig，这样 writerDDL 可以访问 mapping 配置的 topic
            ddlParser.copyParamsToDDLConfig(tableGroup, targetDDLConfig);

            // 3. 根据操作类型检查细粒度配置
            DDLOperationEnum operation = targetDDLConfig.getDdlOperationEnum();
            if (!isDDLOperationAllowed(listenerConfig, operation)) {
                logger.warn("DDL 操作被配置禁用，跳过执行。操作类型: {}, 表: {}",
                        operation, tableGroup.getTargetTable().getName());
                // 注意：如果 DDL 被禁用，应该提前返回，不执行后续的字段映射更新
                // 这样可以避免字段映射与实际表结构不一致的问题
                // refreshOffset 会在外部统一调用
                return;
            }

            // 4. 创建PluginContext（类似DML处理方式，用于传递源连接器信息）
            AbstractPluginContext context = new AbstractPluginContext();
            ConnectorConfig sConnConfig = getConnectorConfig(mapping.getSourceConnectorId());
            context.setSourceConnectorInstance(connectorFactory.connect(sConnConfig));
            context.setTargetConnectorInstance(connectorFactory.connect(tConnConfig));
            context.setEvent(response.getEvent());
            context.setTraceId(response.getTraceId());
            targetDDLConfig.setSourceTableName(tableGroup.getSourceTable().getName());

            // 5. 生成目标表执行SQL(含异构数据库)
            ConnectorInstance tConnectorInstance = connectorFactory.connect(tConnConfig);

            Result result;
            try {
                result = connectorFactory.writerDDL(tConnectorInstance, targetDDLConfig, context, mapping);
                result.setMappingId(mapping.getId());
                result.setTableGroupId(tableGroup.getId());
                result.setTargetTableGroupName(tableGroup.getTargetTable().getName());
                int successSize = result.getSuccessData().size();
                counterService.incrementSuccess(meta, tableGroup, successSize, response.isErrQueueRetry());
            } catch (Exception e) {
                errorQueueCollector.handleDdlFailData(mapping, meta, tableGroup,
                        tableGroup.getTargetTable().getName(), targetDDLConfig, e, response.isErrQueueRetry());
                return;
            }

            // 7.更新表字段映射关系（含元数据刷新）
            // 注：表字段元数据刷新已收敛至 DDLParserImpl.refreshFiledMappings 内部
            ddlParser.refreshFiledMappings(tableGroup, targetDDLConfig);
            logger.info("字段映射关系已更新: table={}, 映射数量={}", tableGroup.getTargetTable().getName(),
                    tableGroup.getFieldMapping() != null ? tableGroup.getFieldMapping().size() : 0);

            // 9.更新执行命令
            tableGroupModelService.initCommand(tableGroup, mapping);

            // 10.持久化存储 & 更新缓存配置
            profileComponent.editTableGroup(tableGroup);
            logger.info("TableGroup 已持久化: table={}", tableGroup.getTargetTable().getName());

            logger.info("DDL 处理完成: mapping={}, table={}", mapping.getName(), tableGroup.getTargetTable().getName());
        } catch (Exception e) {
            logger.error("DDL 处理异常: mapping={}, table={}, error={}", mapping.getName(),
                    tableGroup.getTargetTable().getName(), e.getMessage(), e);
            // refreshOffset 会在外部统一调用，这里不需要处理
        }
    }

    /**
     * 写入 DML：将数据同步到目标表。
     */
    private void writeDML(WriterResponse response, Meta meta, Mapping mapping,
            TableGroupPicker tableGroupPicker, List<Field> sourceFields, boolean enableFilter) throws Exception {
        if (response.getColumnNames() == null || response.getColumnNames().isEmpty()) {
            return;
        }

        TableGroup tableGroup = tableGroupPicker.getTableGroup();

        Map<String, Integer> fieldIndexMap = buildFieldIndexMap(response.getColumnNames(), sourceFields);

        if (fieldIndexMap.isEmpty()) {
            String message = String.format("字段定位错误！表名: %s, 列名: %s，未找到任何匹配字段",
                    response.getTableName(), response.getColumnNames());
            handleFailData(response, meta, mapping, tableGroup, sourceFields, fieldIndexMap, new RuntimeException(message), false);
            return;
        }

        List<Field> actualSourceFields = sourceFields.stream()
                .filter(f -> fieldIndexMap.containsKey(f.nameIgnoreCase()))
                .collect(Collectors.toList());

        boolean enableSchemaResolver = configRegistry.getConfig().getBasic().getEnableSchemaResolver();
        ConnectorConfig sourceConfig = getConnectorConfig(mapping.getSourceConnectorId());
        ConnectorService sourceConnector = connectorFactory.getConnectorService(sourceConfig.getConnectorType());
        try {
            AbstractPluginContext context = new AbstractPluginContext();
            context.setSourceConnectorInstance(connectorFactory.connect(sourceConfig));
            context.setTargetConnectorInstance(
                    connectorFactory.connect(getConnectorConfig(mapping.getTargetConnectorId())));
            context.setEvent(response.getEvent());
            context.setEnablePrintTraceInfo(StringUtil.isNotBlank(response.getTraceId()));

            SyncTask task = new SyncTask(mapping, tableGroup, meta,
                    tableGroupPicker.getPicker()
                            .setSourceResolver(enableSchemaResolver ? sourceConnector.getSchemaResolver() : null),
                    context, response.getColumnNames(), response.isErrQueueRetry());
            new SyncPipeline(connectorFactory, counterService, errorQueueCollector)
                    .execute(task, null, actualSourceFields, fieldIndexMap, enableFilter,
                            response.getDataList(), response.getBeforeRows());
        } catch (Exception e) {
            handleFailData(response, meta, mapping, tableGroup, sourceFields, fieldIndexMap, e, true);
        }
    }

    // ========================================================================
    // 流水线步骤 4: 提交偏移量 (commitOffset)
    // ========================================================================

    /**
     * 提交偏移量：刷新监听器偏移量，并检查队列是否为空来清除 pending 状态。
     */
    private void commitOffset(WriterResponse lastResponse) {
        if (lastResponse != null && lastResponse.getChangedOffset() != null) {
            ChangedOffset offset = lastResponse.getChangedOffset();

            // 直接获取 listener 并刷新偏移量（快照更新）
            Meta meta = profileComponent.getMeta(metaId);
            if (meta != null) {
                Listener listener = meta.getListener();
                if (listener != null) {
                    try {
                        // 每个 meta 独享一个队列，队列是 FIFO，事件按 binlog 顺序进入
                        // executeBatch() 传入的 offset 是当前批次的最大位置
                        // 队列中的事件位置一定 >= 当前批次的最大位置
                        // 所以更新快照到当前批次的最大位置是安全的，不需要等待队列为空
                        listener.refreshEvent(offset);
                    } catch (Exception e) {
                        logger.error("刷新监听器偏移量失败: metaId={}, error={}", metaId, e.getMessage(), e);
                    }
                }
            }
        }

        // 执行完所有批次后，检查队列是否为空，如果为空则清除 pending 状态
        if (queue != null && queue.isEmpty()) {
            clearPendingTask();
        }
    }

    // ========================================================================
    // 重试入口（不走队列，直接执行）
    // ========================================================================

    /**
     * 直接执行事件（不走队列，用于重试场景）
     *
     * @param event 变更事件
     * @throws Exception 处理异常
     */
    public void executeDirectly(ChangedEvent event) throws Exception {
        WriterRequest request = new WriterRequest(event);
        WriterResponse response = new WriterResponse();

        // 标记为重试操作，防止重试失败时再次写入错误队列
        response.setErrQueueRetry(true);

        // 合并到分组
        mergeToGroup(request, response);

        // 直接执行同步，异常自然抛出
        Meta meta = profileComponent.getMeta(response.getChangedOffset().getMetaId());
        final Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        doSync(response, meta, mapping);
    }

    // ========================================================================
    // 队列维护
    // ========================================================================

    /**
     * 初始化：注入队列/调度所需依赖，创建队列，启动调度器。
     * 内部保证依赖注入顺序，调用方无需关心。
     */
    public void initialize(ConfigRegistry configRegistry, ScheduledTaskService scheduledTaskService, TimeRegistry timeRegistry) {
        this.configRegistry = configRegistry;
        this.scheduledTaskService = scheduledTaskService;
        this.timeRegistry = timeRegistry;

        Assert.notNull(configRegistry, "configRegistry 不能为 null");
        this.queue = new LinkedBlockingQueue<>(getBufferQueueCapacity());
        if (taskKey == null) {
            taskKey = UUIDUtil.getUUID();
        }
        scheduledTaskService.start(taskKey, getBufferPeriodMillisecond(), this);
        logger.info("[{}] 初始化完成: queueCapacity={}, period={}ms",
                getClass().getSimpleName(), getBufferQueueCapacity(), getBufferPeriodMillisecond());
    }

    @Override
    public Queue getQueue() {
        return queue;
    }

    @Override
    public int getQueueCapacity() {
        return getBufferQueueCapacity();
    }

    @Override
    public Executor getExecutor() {
        // ADR-0031 fix：动态获取，避免 ExecutorFactory.reload() 后持有已关闭的旧线程池
        return executorFactory.getGeneralExecutor();
    }

    /**
     * 停止执行器
     */
    public void stop() {
        if (taskKey != null && scheduledTaskService != null) {
            scheduledTaskService.stop(taskKey);
        }
        hasPendingTask = false; // 停止时清除 pending 状态
    }

    /**
     * ADR-0031：热重载（排空-切换）。
     * <p>
     * ① 标记 draining=true → enqueue() 阻塞<br>
     * ② 主动触发一次消费（不等调度周期）<br>
     * ③ 等待队列排空（直到完成）<br>
     * ④ 替换队列<br>
     * ⑤ 清除 draining 标志 → 唤醒 enqueue() 恢复<br>
     * ⑥ 重注册定时调度（周期可能也变了）
     */
    @Override
    public void reload() {
        // 防御性检查：队列未初始化则跳过热重载（下次 enqueue 前会由 initialize() 完成初始化）
        if (queue == null) {
            logger.info("[{}] 尚未初始化，跳过热重载", getClass().getSimpleName());
            return;
        }

        // ① 标记只出不进
        draining = true;

        try {
            // ② 主动触发一次消费，不等调度周期
            run();

            // ③ 等待队列排空
            while (!queue.isEmpty()) {
                // 消费者已停止（如 unbind 并发执行），无人消费队列，跳出避免死等
                if (!isRunning()) {
                    logger.info("[{}] 任务已停止，跳过热重载，任务启动时会使用新配置创建队列", getClass().getSimpleName());
                    return;  // finally 会清理 draining，reschedule 不会执行
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 不 break，继续等待直到排空
                }
            }

            // ④ 队列已空，替换
            int newCapacity = getBufferQueueCapacity();
            this.queue = new LinkedBlockingQueue<>(newCapacity);
            logger.info("[{}] 队列已替换: capacity={}", getClass().getSimpleName(), newCapacity);
        } finally {
            // ⑤ 恢复，唤醒等待的 enqueue() 线程
            draining = false;
            drainingLock.lock();
            try {
                drainingDone.signalAll();
            } finally {
                drainingLock.unlock();
            }
        }

        // ⑥ 重注册调度（周期可能也变了）
        reschedule();
    }

    /**
     * ADR-0031：重新注册定时调度（用新周期），队列不动。
     */
    @Override
    public void reschedule() {
        if (taskKey != null) {
            scheduledTaskService.stop(taskKey);
        }
        if (taskKey == null) {
            taskKey = UUIDUtil.getUUID();
        }
        scheduledTaskService.start(taskKey, getBufferPeriodMillisecond(), this);
        logger.info("[{}] 调度已重新注册: period={}ms",
                getClass().getSimpleName(), getBufferPeriodMillisecond());
    }

    // ========================================================================
    // 状态查询
    // ========================================================================

    /**
     * 消费者是否仍在运行（meta 状态为 Running）。
     * reload() 等待排空时据此判断：任务已停止则跳出等待，避免死等。
     */
    protected boolean isRunning() {
        Meta meta = profileComponent != null ? profileComponent.getMeta(metaId) : null;
        return meta != null && MetaEnum.isRunning(meta.getState());
    }

    /**
     * 检查是否有待处理的任务
     */
    public boolean hasPendingTask() {
        return hasPendingTask;
    }

    /**
     * 设置 pending 状态（事件进入队列时调用）
     */
    public void setPendingTask() {
        hasPendingTask = true;
    }

    /**
     * 清除 pending 状态（队列为空时调用）
     */
    public void clearPendingTask() {
        hasPendingTask = false;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    protected BufferProps getBufferProps() {
        BufferProps buffers = configRegistry.getConfig().getBuffers();
        return buffers != null ? buffers : new BufferProps();
    }

    private void meter(long count) {
        // 统计执行器同步效率TPS
        timeRegistry.meter(TimeRegistry.GENERAL_BUFFER_ACTUATOR_TPS).add(count);
    }

    private int getBufferPullCount() {
        return getBufferProps().getBufferPullCount();
    }

    private int getBufferQueueCapacity() {
        return getBufferProps().getBufferQueueCapacity();
    }

    private int getBufferPeriodMillisecond() {
        return getBufferProps().getBufferPeriodMillisecond();
    }

    /**
     * 检查 DDL 操作是否被允许
     *
     * @param config    监听器配置
     * @param operation DDL 操作类型
     * @return 是否允许执行
     */
    private boolean isDDLOperationAllowed(ListenerConfig config, DDLOperationEnum operation) {
        switch (operation) {
            case ALTER_ADD:
                return config.isAllowAddColumn();
            case ALTER_DROP:
                return config.isAllowDropColumn();
            case ALTER_MODIFY:
            case ALTER_CHANGE:
                return config.isAllowModifyColumn();
            default:
                logger.warn("未知的 DDL 操作类型: {}", operation);
                return false;
        }
    }

    /**
     * 构建字段名到行数据索引的映射
     * <p>
     * 解决 CT 模式下用户删除字段后数据错位的问题：
     * - CT 返回的 columnNames 是数据库表的完整列
     * - 用户配置的 tableColumns 可能只包含部分列
     * - 通过索引映射，确保每个字段获取正确的值
     *
     * @param columnNames  CT 返回的列名列表（按行数据顺序）
     * @param tableColumns TableGroup 中配置的字段列表
     * @return Map<字段名, 行数据索引>，只包含在 columnNames 中存在且有对应字段的映射
     */
    private Map<String, Integer> buildFieldIndexMap(List<String> columnNames, List<Field> tableColumns) {
        if (CollectionUtils.isEmpty(columnNames) || CollectionUtils.isEmpty(tableColumns)) {
            return new LinkedHashMap<>();
        }

        // 不区分大小写构建字段 Map
        Map<String, Field> fieldMap = tableColumns.stream()
                .collect(Collectors.toMap(Field::nameIgnoreCase, field -> field, (k1, k2) -> k1));

        Map<String, Integer> fieldIndexMap = new LinkedHashMap<>();
        Set<String> addedFieldNames = new LinkedHashSet<>();

        int rowIndex = 0;
        for (String columnName : columnNames) {
            String columnNameKey = columnName.toLowerCase();
            Field field = fieldMap.get(columnNameKey);
            if (field != null && addedFieldNames.add(columnNameKey)) {
                fieldIndexMap.put(columnNameKey, rowIndex);
            }
            rowIndex++;
        }

        return fieldIndexMap;
    }

    /**
     * 获取连接器配置
     */
    private ConnectorConfig getConnectorConfig(String connectorId) {
        Assert.hasText(connectorId, "Connector id can not be empty.");
        Connector conn = profileComponent.getConnector(connectorId);
        Assert.notNull(conn, "Connector can not be null.");
        return conn.getConfig();
    }

    private void printTraceInfo(WriterResponse response) {
        if (configRegistry.getConfig().getBasic().getEnablePrintTraceInfo()
                && StringUtil.isNotBlank(response.getTraceId())) {
            logger.info("traceId:{}, tableName:{}, event:{}, offset:{}, row:{}", response.getTraceId(),
                    response.getTableName(), response.getEvent(), JsonUtil.objToJson(response.getChangedOffset()),
                    response.getDataList());
        }
    }

    private void handleFailData(WriterResponse response, Meta meta, Mapping mapping,
            TableGroup tableGroup, List<Field> sourceFields, Map<String, Integer> fieldIndexMap,
            Exception e, boolean throwOnRetry) throws Exception {
        List<Map> failData = new ArrayList<>(
                RowConverter.toListOfMaps(sourceFields, fieldIndexMap, response.getDataList()));
        errorQueueCollector.handleFailData(mapping, meta, tableGroup, tableGroup.getTargetTable().getName(),
                response.getEvent(), failData, null, e, response.isErrQueueRetry());
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public String getMetaId() {
        return metaId;
    }

    public void setMetaId(String metaId) {
        this.metaId = metaId;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public void setExecutorFactory(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public void setConnectorFactory(ConnectorFactory connectorFactory) {
        this.connectorFactory = connectorFactory;
    }

    public void setCounterService(CounterService counterService) {
        this.counterService = counterService;
    }

    public void setTableGroupModelService(TableGroupModelService tableGroupModelService) {
        this.tableGroupModelService = tableGroupModelService;
    }

    public void setDdlParser(DDLParser ddlParser) {
        this.ddlParser = ddlParser;
    }

    public void setTableGroupContext(TableGroupContext tableGroupContext) {
        this.tableGroupContext = tableGroupContext;
    }

    public void setErrorQueueCollector(ErrorQueueCollector errorQueueCollector) {
        this.errorQueueCollector = errorQueueCollector;
    }

    public void setScheduledTaskService(ScheduledTaskService scheduledTaskService) {
        // 由 initialize() 统一设置，保留此方法仅用于测试场景
        this.scheduledTaskService = scheduledTaskService;
    }

    public void setProfileComponent(ProfileComponent profileComponent) {
        this.profileComponent = profileComponent;
    }

    public void setTimeRegistry(TimeRegistry timeRegistry) {
        // 由 initialize() 统一设置，保留此方法仅用于测试场景
        this.timeRegistry = timeRegistry;
    }

    public void setConfigRegistry(ConfigRegistry configRegistry) {
        // 由 initialize() 统一设置，保留此方法仅用于测试场景
        this.configRegistry = configRegistry;
    }

}
