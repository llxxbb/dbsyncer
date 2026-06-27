package org.dbsyncer.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.enums.MetaEnum;
import org.dbsyncer.parser.enums.SyncPhaseEnum;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.ModelEnum;
import org.dbsyncer.sdk.listener.Listener;
import org.dbsyncer.storage.impl.SnowflakeIdWorker;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * 驱动同步元信息
 * </p>
 *
 * <pre>
 *     全量同步: 存放分页数
 *     增量同步:定时>时间戳; 日志>binlogFileName/binlogPosition/主从节点信息等
 * </pre>
 *
 * <p>ADR-0019: Model 贫血化重构
 * <ul>
 *     <li>Meta 类只负责维护状态，不包含持久化逻辑</li>
 *     <li>持久化由 MetaService 统一处理</li>
 *     <li>调用方使用 MetaService 来操作 Meta 的状态并持久化</li>
 * </ul>
 *
 * @author AE86
 * @version 1.0.0
 * @date 2020/04/21 16:19
 */
public class Meta extends ConfigModel {

    private String mappingId;
    /**
     * {@link MetaEnum}
     */
    private int state;
    private AtomicLong total;
    private AtomicLong success;
    private AtomicLong fail;
    private Map<String, String> snapshot;   // 仅保存增量同步的 cursor 信息，因为增量是库级别的
    private long beginTime;
    private long endTime;
    @JsonIgnore
    private transient Listener listener;

    // 驱动异常信息
    private String errorMessage = "";

    // 混合同步阶段
    private SyncPhaseEnum syncPhase = SyncPhaseEnum.FULL;


    // 回调函数支持
    @JsonIgnore
    private transient Runnable phaseHandler;

    // 增量同步时发生计数变化的 TableGroup ID 集合（线程安全）
    @JsonIgnore
    private transient Set<String> changedTableGroupIds = ConcurrentHashMap.newKeySet();

    // ADR-0019: 保留 ProfileComponent 引用，但不再直接使用
    // 调用方应通过 MetaService 来操作和持久化
    @JsonIgnore
    private transient List<TableGroup> _cachedTableGroups;

    public Meta() {
        super.setType(ConfigConstant.META);
        init();
    }

    /**
     * ADR-0019: 移除 ProfileComponent 依赖后保留的兼容方法
     * <p>
     * 调用方应通过 MetaService 操作 Meta，而不是直接调用此方法。
     * 
     * @param cachedTableGroups 缓存的 TableGroup 列表（临时使用）
     * @deprecated 使用 MetaService 代替
     */
    @Deprecated
    public void setCachedTableGroups(List<TableGroup> cachedTableGroups) {
        this._cachedTableGroups = cachedTableGroups;
    }

    /**
     * 还原状态
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。调用方应通过 MetaService 调用。
     * <p>
     * 使用示例：
     * <pre>
     *     MetaService metaService = SpringContextUtil.getBean(MetaService.class);
     *     metaService.clear(meta);
     * </pre>
     */
    public void clear() throws Exception {
        init();
    }

    public void clear(String model) throws Exception {
        init();
        // 为计数设置阶段
        if (model.equals(ModelEnum.INCREMENT.getCode())) {
            this.setSyncPhase(SyncPhaseEnum.INCREMENTAL);
        }
    }

    /**
     * 部分重置状态（用于重新同步部分表映射关系）
     * 
     * <p>从所有 TableGroup 重新计算 success 和 fail
     * 注意：被重置的 TableGroup 的 success 和 fail 已经在调用此方法前被清零
     * 所以统计所有 TableGroup 就等于保留了未被重置的 TableGroup 的统计数据
     * 将 syncPhase 重置为 FULL 以便正确统计总数
     * 保留 snapshot（增量起始点）
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。
     * <p>
     * 使用示例：
     * <pre>
     *     MetaService metaService = SpringContextUtil.getBean(MetaService.class);
     *     List&lt;Object&gt; tableGroups = persistenceService.getAllTableGroups(meta.getMappingId());
     *     meta.partialClear(resetTableGroupIds, DefaultModelPersistenceService.castToListTableGroup(tableGroups));
     *     metaService.saveState(meta, MetaEnum.READY, "");
     * </pre>
     * 
     * @param resetTableGroupIds 需要重置的 TableGroup ID 集合（用于日志记录，实际统计所有 TableGroup）
     * @param allTableGroups     所有关联的 TableGroup（用于统计）
     * @throws Exception 保存异常
     */
    public void partialClear(Set<String> resetTableGroupIds, List<TableGroup> allTableGroups) throws Exception {
        long now = Instant.now().toEpochMilli();
        this.state = MetaEnum.READY.getCode();
        
        // 从所有 TableGroup 重新计算 success 和 fail
        // 被重置的 TableGroup 的 success 和 fail 已经在调用此方法前被清零
        // 所以这里统计所有 TableGroup，实际上就是保留了未被重置的 TableGroup 的统计数据
        long totalSuccess = 0;
        long totalFail = 0;
        
        if (!CollectionUtils.isEmpty(allTableGroups)) {
            for (TableGroup g : allTableGroups) {
                totalSuccess += g.getSuccess();
                totalFail += g.getFail();
            }
        }
        
        this.success = new AtomicLong(totalSuccess);
        this.fail = new AtomicLong(totalFail);
        
        this.total = new AtomicLong(0);
        this.beginTime = now;
        this.setUpdateTime(now);
        this.endTime = now;
        this.errorMessage = "";
        // 将 syncPhase 重置为 FULL，以便 updateFullTotal() 能正确统计总数
        this.syncPhase = SyncPhaseEnum.FULL;
        // ADR-0019: 不直接持久化，由 MetaService 统一处理
    }


    private void init() {
        long now = Instant.now().toEpochMilli();
        this.state = MetaEnum.READY.getCode();
        this.total = new AtomicLong(0);
        this.success = new AtomicLong(0);
        this.fail = new AtomicLong(0);
        this.snapshot = new HashMap<>();
        this.beginTime = now;
        this.setUpdateTime(now);
        this.endTime = now;
        // 初始化异常信息
        this.errorMessage = "";
        // 初始化混合同步阶段
        this.syncPhase = SyncPhaseEnum.FULL;
    }

    /**
     * ADR-0019: 移除 ProfileComponent 依赖，此方法不再使用
     * <p>
     * 保留该方法以避免破坏调用方代码，调用方应使用 MetaService 代替。
     * 
     * @deprecated 使用 MetaService 代替
     */
    @Deprecated
    public void setProfileComponent(Object profileComponent) {
        // 空实现，不再使用 ProfileComponent
    }

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public AtomicLong getTotal() {
        return total;
    }

    public void setTotal(AtomicLong total) {
        this.total = total;
    }

    /**
     * 更新全量同步总条数
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。
     * 调用方应通过 MetaService 调用：
     * <pre>
     *     MetaService metaService = SpringContextUtil.getBean(MetaService.class);
     *     List&lt;Object&gt; tableGroups = persistenceService.getAllTableGroups(meta.getMappingId());
     *     meta.updateFullTotal(DefaultModelPersistenceService.castToListTableGroup(tableGroups));
     *     metaService.saveState(meta, MetaEnum.READY, "");
     * </pre>
     * 
     * @param allTableGroups 所有关联的 TableGroup（用于统计）
     * @throws Exception 状态更新异常
     */
    public void updateFullTotal(List<TableGroup> allTableGroups) throws Exception {
        // 全量同步
        if (SyncPhaseEnum.FULL != getSyncPhase()) {
            return;
        }
        // 统计 tableGroup 总条数
        AtomicLong count = new AtomicLong(0);
        if (!CollectionUtils.isEmpty(allTableGroups)) {
            for (TableGroup g : allTableGroups) {
                count.getAndAdd(g.getSourceTable().getCount());
            }
        }
        total.set(count.get());
        setUpdateTime(Instant.now().toEpochMilli());
        // ADR-0019: 不直接持久化，由 MetaService 统一处理
    }


    public AtomicLong getSuccess() {
        return success;
    }

    public void setSuccess(AtomicLong success) {
        this.success = success;
    }

    public AtomicLong getFail() {
        return fail;
    }

    public void setFail(AtomicLong fail) {
        this.fail = fail;
    }

    public Map<String, String> getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Map<String, String> snapshot) {
        // 创建副本，避免直接赋值引用
        // 防止外部修改 snapshot 时影响 Meta 对象的 snapshot
        this.snapshot = snapshot != null ? new HashMap<>(snapshot) : new HashMap<>();
    }

    /**
     * 更新快照（增量同步 cursor 信息）
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。调用方应通过 MetaService 调用。
     * 
     * @param metaSnapshot 快照信息 JSON 字符串
     * @throws Exception 状态更新异常
     */
    public void updateSnapshot(String metaSnapshot) throws Exception {
        if (StringUtil.isNotBlank(metaSnapshot)) {
            @SuppressWarnings("unchecked")
            Map<String, String> snapshot = (Map<String, String>) (Map<?, ?>) JsonUtil.parseMap(metaSnapshot);
            if (!CollectionUtils.isEmpty(snapshot)) {
                // 创建副本，避免直接赋值引用
                this.snapshot = new HashMap<>(snapshot);
                setUpdateTime(Instant.now().toEpochMilli());
                // ADR-0019: 不直接持久化，由 MetaService 统一处理
            }
        }
    }


    public long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // 新增的实例方法
    public SyncPhaseEnum getSyncPhase() {
        return syncPhase;
    }

    public void setSyncPhase(SyncPhaseEnum syncPhase) {
        this.syncPhase = syncPhase;
    }

    /**
     * 更新同步阶段（全量/增量/混合）
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。调用方应通过 MetaService 调用。
     * 
     * @param phase 新的同步阶段
     * @throws Exception 状态更新异常
     */
    public void updateSyncPhase(SyncPhaseEnum phase) throws Exception {
        this.syncPhase = phase;
        // ADR-0019: 不直接持久化，由 MetaService 统一处理
    }

    // 回调函数支持
    public void setPhaseHandler(Runnable handler) {
        this.phaseHandler = handler;
    }

    public Runnable getPhaseHandler() {
        return this.phaseHandler;
    }

    // 检查是否已记录增量起始点
    @JsonIgnore
    public boolean isIncrementStartPointRecorded() {
        return !this.snapshot.isEmpty();
    }

    /**
     * 记录增量起始点（直接保存到 snapshot，不需要受保护字段）
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。调用方应通过 MetaService 调用。
     * 
     * @param position cursor 信息（合并到 snapshot）
     * @throws Exception 状态更新异常
     */
    public void recordIncrementStartPoint(Map<String, String> position) throws Exception {
        // 直接合并到 snapshot，增量同步启动时可以直接读取
        this.snapshot.putAll(position);
        // ADR-0019: 不直接持久化，由 MetaService 统一处理
    }

    /**
     * 保存状态到持久化存储
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。调用方应通过 MetaService 调用。
     *
     * @param state        新的状态
     * @param errorMessage 异常信息（可选）
     * @throws Exception 状态更新异常
     */
    public void saveState(MetaEnum state, String errorMessage) throws Exception {
        this.state = state.getCode();
        this.errorMessage = errorMessage;
        this.setUpdateTime(Instant.now().toEpochMilli());
        // ADR-0019: 不直接持久化，由 MetaService 统一处理
    }

    /**
     * 保存状态到持久化存储（仅状态）
     * 
     * <p>ADR-0019: 此方法只修改状态，不负责持久化。
     *
     * @param state 新的状态
     * @throws Exception 状态更新异常
     */
    public void saveState(MetaEnum state) throws Exception {
        saveState(state, "");
    }

    /**
     * 重置状态到初始状态（不重置计数数据）
     */
    public void resetState() throws Exception {
        saveState(MetaEnum.READY, "");
    }

    /**
     * 检查是否处于异常状态
     *
     * @return 如果处于异常状态返回true，否则返回false
     */
    @JsonIgnore
    public boolean isError() {
        return this.state == MetaEnum.ERROR.getCode();
    }

    /**
     * 检查是否处于运行状态
     *
     * @return 如果处于运行状态返回true，否则返回false
     */
    @JsonIgnore
    public boolean isRunning() {
        return MetaEnum.isRunning(this.state);
    }

    /**
     * 断言当前 Meta 未处于运行状态
     *
     * @throws ParserException 如果当前 Meta 正在运行，抛出异常
     */
    @JsonIgnore
    public void assertNotRunning() {
        if (this.isRunning()) {
            throw new ParserException("驱动正在运行, 请先停止.");
        }
    }

    /**
     * 标记 TableGroup 计数发生变化（增量场景）
     *
     * @param tableGroupId 表组ID
     */
    public void markTableGroupChanged(String tableGroupId) {
        changedTableGroupIds.add(tableGroupId);
    }

    /**
     * 获取发生计数变化的 TableGroup ID 集合
     *
     * @return TableGroup ID 集合
     */
    public Set<String> getChangedTableGroupIds() {
        return changedTableGroupIds;
    }

    /**
     * 清空发生计数变化的 TableGroup ID 集合
     */
    public void clearChangedTableGroupIds() {
        changedTableGroupIds.clear();
    }

    /**
     * 创建 Meta 对象
     * 
     * <p>ADR-0019: 此方法只创建 Meta 对象并设置基本属性，不负责持久化。
     * <p>调用方应通过 MetaService 完成持久化：
     * <pre>
     *     Meta meta = Meta.create(mapping, snowflakeIdWorker);
     *     metaService.createMeta(meta);
     * </pre>
     * 
     * @param mapping         关联的 Mapping 对象
     * @param snowflakeIdWorker 雪花算法 ID 生成器
     * @return 新创建的 Meta 对象
     */
    @JsonIgnore
    public static Meta create(Mapping mapping, SnowflakeIdWorker snowflakeIdWorker) throws Exception {
        Meta meta = new Meta();
        String newId = String.valueOf(snowflakeIdWorker.nextId());
        meta.setId(newId);
        meta.setCreateTime(Instant.now().toEpochMilli());
        meta.setMappingId(mapping.getId());
        meta.setName(mapping.getId());
        mapping.setMetaId(newId);
        // ADR-0019: 不直接持久化，由调用方通过 MetaService 完成
        return meta;
    }
}