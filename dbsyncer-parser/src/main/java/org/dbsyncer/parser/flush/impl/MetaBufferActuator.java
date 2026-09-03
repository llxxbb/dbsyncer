/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.config.GeneralBufferConfig;
import org.dbsyncer.common.metric.TimeRegistry;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.TableGroupContext;
import org.dbsyncer.parser.ddl.DDLParser;
import org.dbsyncer.parser.flush.AbstractBufferActuator;
import org.dbsyncer.parser.flush.BufferRequest;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.parser.pipeline.SyncPipeline;
import org.dbsyncer.parser.pipeline.SyncTask;
import org.dbsyncer.sdk.model.*;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.parser.service.TableGroupModelService;
import org.dbsyncer.parser.impl.SystemConfigRegistry;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.config.ListenerConfig;
import org.dbsyncer.sdk.connector.ConnectorInstance;
import org.dbsyncer.sdk.enums.ChangedEventTypeEnum;
import org.dbsyncer.sdk.enums.DDLOperationEnum;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.util.RowConverter;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Meta 专用执行器（每个 meta 独享一个队列）
 *
 * @Version 1.0.0
 * @Author AE86
 * @Date 2024-01-XX
 */
public class MetaBufferActuator extends AbstractBufferActuator<WriterRequest, WriterResponse> {

    private String metaId;
    private GeneralBufferConfig generalBufferConfig;
    private Executor generalExecutor;
    private ConnectorFactory connectorFactory;
    private CounterService counterService;
    private DDLParser ddlParser;
    private TableGroupContext tableGroupContext;
    private TableGroupModelService tableGroupModelService;
    private ErrorQueueCollector errorQueueCollector;
    public MessageService messageService;
    private SystemConfigRegistry configRegistry;

    /**
     * 是否有待处理的任务（事件进入队列时设置为 true，队列为空时设置为 false）
     */
    private volatile boolean hasPendingTask = false;

    public void buildConfig() {
        setConfig(generalBufferConfig);
        super.buildConfig();
    }

    @Override
    protected String getPartitionKey(WriterRequest request) {
        return request.getTableName();
    }

    @Override
    protected void partition(WriterRequest request, WriterResponse response) {
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

    @Override
    protected boolean skipPartition(WriterRequest nextRequest, WriterResponse response) {
        // 并发场景，同一条数据可能连续触发Insert > Delete > Insert，批处理任务中出现不同事件时，跳过分区处理
        // 跳过表结构修改事件（保证表结构修改原子性）
        return !StringUtil.equals(nextRequest.getEvent(), response.getEvent())
                || ChangedEventTypeEnum.isDDL(response.getTypeEnum());
    }

    @Override
    public void pull(WriterResponse response) throws Exception {
        long now = Instant.now().toEpochMilli();
        Meta meta = profileComponent.getMeta(response.getChangedOffset().getMetaId());
        final Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        try {
            pullNoCatch(response, meta, mapping);
            logger.info("mappingId {}, {}目标表，{}, {}条, {}ms", meta.getMappingId(), response.getTableName(), response.getSuffixName(), response.getTaskSize(), (Instant.now().toEpochMilli() - now));
        } catch (Exception e) {
            errorQueueCollector.closeTask(meta, response.getTableName(), e, getBufferWriterCount(),
                    NotifyType.TASK_INTERRUPTED);
            throw e;
        }
    }

    private void pullNoCatch(WriterResponse response, Meta meta, Mapping mapping) throws Exception {
        // 所有任务事件处理完成后，由 process 方法统一刷新偏移量
        if (!MetaEnum.isRunning(meta.getState())) {
            logger.warn("任务已停止，跳过批次处理: metaId={}, 分区={}", meta.getId(), response.getTableName());
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
                    parseDDl(response, meta, mapping, tableGroup);
                    return tableGroup;
                }).collect(Collectors.toList());

                // 2. 强制刷新缓存，确保后续的 DML 事件使用最新的 fieldMapping
                // 注意：这里使用更新后的 TableGroup，确保 fieldMapping 已同步
                tableGroupContext.update(mapping, updatedTableGroups);
                break;
            case SCAN:
                for(TableGroupPicker picker : pickers) {
                    distributeTableGroup(response, meta, mapping, picker, picker.getSourceFields(), false);
                }
                break;
            case ROW:
                for(TableGroupPicker picker : pickers) {
                    distributeTableGroup(response, meta, mapping, picker, picker.getSourceFields(), true);
                }
                break;
        }
    }

    @Override
    protected void offerFailed(BlockingQueue<WriterRequest> queue, WriterRequest request) {
        throw new org.dbsyncer.common.QueueOverflowException("缓存队列已满");
    }

    @Override
    protected void meter(TimeRegistry timeRegistry, long count) {
        // 统计执行器同步效率TPS
        timeRegistry.meter(TimeRegistry.GENERAL_BUFFER_ACTUATOR_TPS).add(count);
    }

    @Override
    public Executor getExecutor() {
        return generalExecutor;
    }

    @Override
    public void offer(BufferRequest request) {
        // DDL 事件特殊处理：阻塞等待队列消费完成，保证表结构修改的原子性
        if (request instanceof WriterRequest) {
            WriterRequest writerRequest = (WriterRequest) request;
            if (ChangedEventTypeEnum.isDDL(writerRequest.getTypeEnum())) {
                // DDL事件，阻塞等待队列消费完成
                while (isRunning(request)) {
                    if (getQueue().isEmpty()) {
                        // 事件进入队列时，设置 pending 状态
                        setPendingTask();
                        super.offer(request);
                        return;
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(10);
                    } catch (InterruptedException ex) {
                        logger.error(ex.getMessage(), ex);
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }

        // 普通事件：事件进入队列时，设置 pending 状态
        setPendingTask();
        super.offer(request);
    }

    /**
     * 停止执行器
     */
    @Override
    public void stop() {
        super.stop();
        hasPendingTask = false; // 停止时清除 pending 状态
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

    /**
     * 处理完成后的钩子方法：刷新偏移量，并检查队列是否为空来清除 pending 状态
     */
    @Override
    protected void afterProcess(WriterResponse lastResponse) {
        if (lastResponse != null && lastResponse.getChangedOffset() != null) {
            ChangedOffset offset = lastResponse.getChangedOffset();

            // 直接获取 listener 并刷新偏移量（快照更新）
            Meta meta = profileComponent.getMeta(metaId);
            if (meta != null) {
                Listener listener = meta.getListener();
                if (listener != null) {
                    try {
                        // 每个 meta 独享一个队列，队列是 FIFO，事件按 binlog 顺序进入
                        // process() 传入的 offset 是当前批次的最大位置
                        // 队列中的事件位置一定 >= 当前批次的最大位置
                        // 所以更新快照到当前批次的最大位置是安全的，不需要等待队列为空
                        listener.refreshEvent(offset);
                    } catch (Exception e) {
                        logger.error("刷新监听器偏移量失败: metaId={}, error={}", metaId, e.getMessage(), e);
                    }
                }
            }
        }

        // 处理完所有批次后，检查队列是否为空，如果为空则清除 pending 状态
        if (getQueue().isEmpty()) {
            clearPendingTask();
        }
    }

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

        // 分区处理
        partition(request, response);

        // 直接调用核心处理方法，异常自然抛出
        Meta meta = profileComponent.getMeta(response.getChangedOffset().getMetaId());
        final Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        pullNoCatch(response, meta, mapping);
    }

    private void distributeTableGroup(WriterResponse response, Meta meta, Mapping mapping,
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

    /**
     * 解析DDL
     * 完整的DDL处理流程：解析DDL → 执行DDL（如果启用）→ 刷新表结构 → 更新字段映射
     *
     * @param response   WriterResponse，包含DDL SQL和事件信息
     * @param mapping    Mapping配置，包含源和目标连接器ID
     * @param tableGroup TableGroup配置，包含表结构和字段映射
     */
    public void parseDDl(WriterResponse response, Meta meta, Mapping mapping, TableGroup tableGroup) {
        try {
            ListenerConfig listenerConfig = mapping.getListener();

            // 注意：全局 DDL 开关检查已在 Listener 和 pull() 方法中完成，此处无需重复检查

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

            // 5. 生成目标表执行SQL(支持异构数据库)
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

    // Getters and Setters
    public String getMetaId() {
        return metaId;
    }

    public void setMetaId(String metaId) {
        this.metaId = metaId;
    }

    public void setGeneralBufferConfig(GeneralBufferConfig generalBufferConfig) {
        this.generalBufferConfig = generalBufferConfig;
    }

    public void setGeneralExecutor(Executor generalExecutor) {
        this.generalExecutor = generalExecutor;
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

    public void setConfigRegistry(SystemConfigRegistry configRegistry) {
        this.configRegistry = configRegistry;
    }
}
