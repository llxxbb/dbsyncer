/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.service;

import org.dbsyncer.storage.impl.SnowflakeIdWorker;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.enums.SyncPhaseEnum;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.service.ModelPersistenceService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meta 业务服务
 * 
 * <p>封装 Meta 的状态修改和持久化逻辑，替代原来 Meta 类中对 ProfileComponent 的直接依赖。
 * 
 * <p>设计思路：
 * <ul>
 *     <li>Meta 类只负责维护状态，不包含持久化逻辑</li>
 *     <li>MetaService 负责把状态修改和持久化封装在一起</li>
 *     <li>调用方只需要使用 MetaService 的方法，保证 API 简洁</li>
 * </ul>
 * 
 * <p>此服务作为 ADR-0019（Model 层贫血化重构）的一部分。
 * 
 * @author opencode
 * @version 1.0.0
 * @date 2026-06-27
 */
@Service
public class MetaService {

    @Resource
    private ModelPersistenceService persistenceService;

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    /**
     * 还原 Meta 状态
     * 
     * @param meta 需要重置的 Meta 对象
     * @throws Exception 持久化异常
     */
    public void clear(Meta meta) throws Exception {
        meta.clear();
        persistenceService.save(null, meta);
    }

    /**
     * 根据模型类型还原 Meta 状态
     * 
     * @param meta  需要重置的 Meta 对象
     * @param model 模型类型（全量/增量）
     * @throws Exception 持久化异常
     */
    public void clear(Meta meta, String model) throws Exception {
        meta.clear(model);
        persistenceService.save(null, meta);
    }

    /**
     * 部分重置 Meta 状态（用于重新同步部分表映射关系）
     * 
     * @param meta              需要重置的 Meta 对象
     * @param resetTableGroupIds 需要重置的 TableGroup ID 集合（用于日志记录，实际统计所有 TableGroup）
     * @throws Exception 持久化异常
     */
    public void partialClear(Meta meta, Set<String> resetTableGroupIds) throws Exception {
        // 从 ModelPersistenceService 获取 allTableGroups
        List<Object> allTableGroupsRaw = persistenceService.getAllTableGroups(meta.getMappingId());
        List<TableGroup> allTableGroups = DefaultModelPersistenceService.castToListTableGroup(allTableGroupsRaw);
        
        meta.partialClear(resetTableGroupIds, allTableGroups);
        persistenceService.save(null, meta);
    }

    /**
     * 更新 Meta 的全量统计总数
     * 
     * <p>内部逻辑：
     * <ul>
     *     <li>遍历所有关联的 TableGroup</li>
     *     <li>累计所有 TableGroup 的 total 值</li>
     *     <li>更新 Meta 的 total 字段并持久化</li>
     * </ul>
     * 
     * @param meta 需要更新统计的 Meta 对象
     * @throws Exception 持久化异常
     */
    public void updateFullTotal(Meta meta) throws Exception {
        // 从 ModelPersistenceService 获取 allTableGroups
        List<Object> allTableGroupsRaw = persistenceService.getAllTableGroups(meta.getMappingId());
        List<TableGroup> allTableGroups = DefaultModelPersistenceService.castToListTableGroup(allTableGroupsRaw);
        
        meta.updateFullTotal(allTableGroups);
        persistenceService.save(null, meta);
    }

    /**
     * 更新 Meta 的快照（增量同步 cursor 信息）
     * 
     * @param meta    需要更新快照的 Meta 对象
     * @param snapshot 新的快照信息（合并到现有快照）
     * @throws Exception 持久化异常
     */
    public void updateSnapshot(Meta meta, Map<String, String> snapshot) throws Exception {
        // 直接合并到 meta 的 snapshot，不走 Meta.updateSnapshot(String)
        if (snapshot != null && !snapshot.isEmpty()) {
            meta.getSnapshot().putAll(snapshot);
            meta.setUpdateTime(java.time.Instant.now().toEpochMilli());
        }
        persistenceService.save(null, meta);
    }

    /**
     * 更新 Meta 的同步阶段（全量/增量/混合）
     * 
     * @param meta  需要更新的 Meta 对象
     * @param phase 新的同步阶段
     * @throws Exception 持久化异常
     */
    public void updateSyncPhase(Meta meta, SyncPhaseEnum phase) throws Exception {
        meta.updateSyncPhase(phase);
        persistenceService.save(null, meta);
    }

    /**
     * 记录增量同步的起始点（cursor 信息）
     * 
     * @param meta     需要记录的 Meta 对象
     * @param position cursor 信息（合并到 snapshot）
     * @throws Exception 持久化异常
     */
    public void recordIncrementStartPoint(Meta meta, Map<String, String> position) throws Exception {
        meta.recordIncrementStartPoint(position);
        persistenceService.save(null, meta);
    }

    /**
     * 保存 Meta 状态到持久化存储
     * 
     * @param meta         需要保存的 Meta 对象
     * @param state        新的状态
     * @param errorMessage 异常信息（可选）
     * @throws Exception 持久化异常
     */
    public void saveState(Meta meta, MetaEnum state, String errorMessage) throws Exception {
        meta.saveState(state, errorMessage);
        persistenceService.save(null, meta);
    }

    /**
     * 保存 Meta 状态到持久化存储（仅状态）
     * 
     * @param meta  需要保存的 Meta 对象
     * @param state 新的状态
     * @throws Exception 持久化异常
     */
    public void saveState(Meta meta, MetaEnum state) throws Exception {
        saveState(meta, state, "");
    }

    /**
     * 重置 Meta 状态到初始状态（不重置计数数据）
     * 
     * @param meta 需要重置状态的 Meta 对象
     * @throws Exception 持久化异常
     */
    public void resetState(Meta meta) throws Exception {
        meta.resetState();
        persistenceService.save(null, meta);
    }

    /**
     * 创建新的 Meta 对象并持久化
     * 
     * @param mappingId 关联的 Mapping ID
     * @return 创建成功的 Meta 对象
     * @throws Exception 持久化异常
     */
    public Meta createMeta(String mappingId) throws Exception {
        Meta meta = new Meta();
        meta.setMappingId(mappingId);
        return createMeta(meta);
    }

    /**
     * 创建 Meta 对象并持久化
     * 
     * @param meta 需要创建的 Meta 对象（已经设置好 mappingId 等属性）
     * @return 持久化后的 Meta 对象（ID 已填充）
     * @throws Exception 持久化异常
     */
    public Meta createMeta(Meta meta) throws Exception {
        if (org.dbsyncer.common.util.StringUtil.isBlank(meta.getId())) {
            meta.setId(String.valueOf(snowflakeIdWorker.nextId()));
        }
        if (meta.getName() == null) {
            meta.setName(meta.getType() != null ? meta.getType() : ConfigConstant.META);
        }
        if (meta.getCreateTime() == null) {
            meta.setCreateTime(System.currentTimeMillis());
        }
        meta.setUpdateTime(System.currentTimeMillis());
        String id = persistenceService.save(null, meta);
        meta.setId(id);
        return meta;
    }

    /**
     * 获取映射关联的所有 TableGroup 并用于部分重置
     * 
     * <p>这是一个辅助方法，封装了常见的业务调用：
     * <ul>
     *     <li>从 persistenceService 获取所有关联的 TableGroup</li>
     *     <li>调用 Meta 的部分重置方法</li>
     *     <li>持久化更新后的 Meta</li>
     * </ul>
     * 
     * @param meta 需要重置的 Meta 对象
     * @param resetTableGroupIds 需要重置的 TableGroup ID 集合
     * @throws Exception 业务或持久化异常
     */
    public void partialClearWithTableGroups(Meta meta, Set<String> resetTableGroupIds) throws Exception {
        // 内部调用 Meta 的 partialClear 方法，不需要外部再调用 Meta 方法
        partialClear(meta, resetTableGroupIds);
    }

    /**
     * 辅助方法：将 List<Object> 转换为 List<TableGroup>
     * 
     * @param objects 对象列表
     * @return TableGroup 列表
     */
    public List<TableGroup> castToListTableGroup(List<Object> objects) {
        return DefaultModelPersistenceService.castToListTableGroup(objects);
    }
}
