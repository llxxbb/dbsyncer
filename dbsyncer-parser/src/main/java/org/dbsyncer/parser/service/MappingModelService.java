/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.common.ParserException;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.common.config.SnowflakeIdWorker;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.List;

/**
 * Mapping 模型服务
 * 
 * <p>
 * 封装 Mapping 的持久化和查询逻辑，替代原来 Mapping 类中对 ProfileComponent 的直接依赖。
 * 
 * <p>
 * ADR-0019: Model 层贫血化重构的一部分，将 Mapping 的持久化逻辑从 Model 层转移到 Service 层。
 * 
 * @author opencode
 * @version 1.0.0
 * @date 2026-06-27
 */
@Service
public class MappingModelService {

    @Resource
    private ProfileComponent profileComponent;

    /**
     * 根据 Mapping 获取关联的 Meta 对象
     * 
     * @param mapping Mapping 对象
     * @return Meta 对象，如果不存在返回 null
     */
    @JsonIgnore
    public Meta getMeta(Mapping mapping) {
        if (mapping == null) {
            return null;
        }
        Object metaObj = profileComponent.getMeta(mapping.getMetaId());
        if (metaObj instanceof Meta) {
            return (Meta) metaObj;
        }
        return null;
    }

    /**
     * 检查是否禁止编辑
     * 
     * @param mapping Mapping 对象
     * @throws ParserException 如果任务处于运行中，抛出异常
     */
    public void assertDisableEdit(Mapping mapping) {
        Meta meta = getMeta(mapping);
        if (meta != null && meta.isRunning()) {
            throw new ParserException("任务【运行中】就不允许编辑了。如需编辑请【停止】任务。");
        }
    }

    /**
     * 重置 Meta 状态（不持久化 Meta，只修改状态）
     * 
     * @param mapping Mapping 对象
     * @throws Exception 持久化异常
     */
    public void resetMetaState(Mapping mapping) throws Exception {
        Meta meta = getMeta(mapping);
        if (meta != null) {
            meta.resetState();
            profileComponent.editConfigModel(meta);
        }
    }

    /**
     * 更新 Meta 的快照信息并持久化 Mapping
     * <p>
     * 如果 fileName 或 position 发生变化，会自动清空 gtid，让系统在下次连接时重新初始化。
     * 这是因为 GTID 与 binlog 位置必须匹配，位置变化后旧的 GTID 可能指向已 purge 的事务。
     * 
     * @param mapping      需要更新的 Mapping 对象
     * @param metaSnapshot 快照信息的 JSON 字符串
     * @throws Exception 持久化异常
     */
    public void updateSnapshot(Mapping mapping, String metaSnapshot) throws Exception {
        Meta meta = getMeta(mapping);
        if (meta != null) {
            meta.updateSnapshot(metaSnapshot);
            profileComponent.editConfigModel(meta);
        }
    }

    /**
     * 复制 Mapping
     * 
     * @param mapping           原 Mapping 对象
     * @param snowflakeIdWorker ID 生成器
     * @param newName           新名称
     * @param targetConnectorId 新目标连接器 ID（可选）
     * @return 复制后的新 Mapping 对象
     * @throws Exception 异常
     */
    public Mapping copy(Mapping mapping, SnowflakeIdWorker snowflakeIdWorker, String newName,
            String model, String targetConnectorId) throws Exception {
        String json = JsonUtil.objToJson(mapping);
        Mapping newMapping = JsonUtil.jsonToObj(json, Mapping.class);
        newMapping.setName(newName);
        String newId = String.valueOf(snowflakeIdWorker.nextId());
        newMapping.setId(newId);
        newMapping.setUpdateTime(Instant.now().toEpochMilli());
        newMapping.setDisableEdit(false);
        if (model != null && !model.isEmpty()) {
            newMapping.setModel(model);
        }
        if (targetConnectorId != null && !targetConnectorId.isEmpty()) {
            newMapping.setTargetConnectorId(targetConnectorId);
        }

        // 必须先创建 Meta 并写入新的 metaId，再持久化 newMapping。
        // 否则会沿用源任务的 metaId（Mapping.metaId 不参与 JSON 忽略），导致删除副本时误删源任务的数据与 meta。
        Meta meta = Meta.create(newMapping, String.valueOf(snowflakeIdWorker.nextId()));
        // 复制任务必须复位状态与计数，避免从运行中的任务复制后沿用运行状态/计数
        meta.resetState();
        meta.clear();
        profileComponent.addConfigModel(newMapping);
        profileComponent.addConfigModel(meta);

        List<TableGroup> groupList = profileComponent.getTableGroupAll(mapping.getId());
        if (!CollectionUtils.isEmpty(groupList)) {
            for (TableGroup obj : groupList) {
                TableGroup tg = (TableGroup) obj;
                String tgJson = JsonUtil.objToJson(tg);
                TableGroup newTg = JsonUtil.jsonToObj(tgJson, TableGroup.class);
                // 复位表映射的同步状态、计数、游标等，避免沿用源任务的运行态
                newTg.clear();
                newTg.setId(String.valueOf(snowflakeIdWorker.nextId()));
                newTg.setMappingId(newId);
                newTg.setName(newId + ":" + newTg.getSourceTable().getName());
                // 必须使用 addTableGroup（TABLE 分组策略），否则新表映射无法被 getTableGroupAll 查询到
                profileComponent.addTableGroup(newTg);
            }
        }

        return newMapping;
    }
}
