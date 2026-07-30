/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.impl;

import org.dbsyncer.common.ProcessEvent;
import org.dbsyncer.common.model.Result;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.FullSyncExecutor;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.sdk.model.*;
import org.dbsyncer.parser.strategy.FlushStrategy;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.parser.util.PickerUtil;

import org.dbsyncer.sdk.connector.database.Database;
import org.dbsyncer.sdk.connector.database.DatabaseConnectorInstance;
import org.dbsyncer.sdk.connector.database.DatabaseTemplate;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.plugin.AbstractPluginContext;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.sdk.util.PrimaryKeyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.stereotype.Component;


import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/29 22:38
 */
@Component
public class FullSyncExecutorImpl implements FullSyncExecutor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private FlushStrategy flushStrategy;

    @Resource
    private ProfileComponent profileComponent;

    // 移除ApplicationContext依赖，添加ProcessEvent依赖
    @Resource
    private ProcessEvent fullProcessEvent;

        // 保存间隔（毫秒）
    private static final long SAVE_INTERVAL_MS = 3000L;

    /**
     * 新的TableGroup流式处理方法
     */
    @Override
    public void executeTableGroup(String metaId, TableGroup tableGroup, Mapping mapping) throws Exception {
        // 状态清理， 无须保存，因为第一个批次处理完后会保存。
        tableGroup.setErrorMessage("");

        // 上下文初始化
        final String sourceConnectorId = mapping.getSourceConnectorId();
        final String targetConnectorId = mapping.getTargetConnectorId();
        ConnectorConfig sConfig = getConnectorConfig(sourceConnectorId);
        ConnectorConfig tConfig = getConnectorConfig(targetConnectorId);
        TableGroup group = PickerUtil.mergeTableGroupConfig(mapping, tableGroup);
        Map<String, String> command = group.getCommand();
        List<String> primaryKeys = group.getSourceTable().getPrimaryKeys();

        final AbstractPluginContext context = new AbstractPluginContext();
        context.setSourceConnectorInstance(connectorFactory.connect(sConfig));
        context.setTargetConnectorInstance(connectorFactory.connect(tConfig));
        context.setSourceTable(group.getSourceTable());
        context.setEvent(ConnectorConstant.OPERTION_INSERT);
        context.setPageSize(mapping.getReadNum());
        context.setEnableSchemaResolver(profileComponent.getSystemConfig().isEnableSchemaResolver());
        context.setCursors(tableGroup.getCursors());

        ConnectorService sourceConnector = connectorFactory
                .getConnectorService(context.getSourceConnectorInstance().getConfig());
        Picker picker = new Picker(group);
        picker.setSourceResolver(context.isEnableSchemaResolver() ? sourceConnector.getSchemaResolver() : null);

        // 根据是否支持cursor选择不同的SQL
        boolean supportedCursor = null != tableGroup.getCursors();
        String querySql;
        if (supportedCursor) {
            querySql = command.get(ConnectorConstant.OPERTION_QUERY_CURSOR);
        } else {
            querySql = command.get(ConnectorConstant.OPERTION_QUERY_STREAM);
        }

        // 流式处理模式
        Database db = (Database) connectorFactory
                .getConnectorService(context.getSourceConnectorInstance().getConfig());

        // 执行流式处理
        ((DatabaseConnectorInstance) context.getSourceConnectorInstance()).execute(databaseTemplate ->
                executeTableGroupWithStreaming(metaId, tableGroup, group, mapping, context, picker, db, primaryKeys, querySql, databaseTemplate));
    }

    private Object executeTableGroupWithStreaming(String metaId, TableGroup tableGroup, TableGroup group, Mapping mapping,
                                                  AbstractPluginContext context,
                                                  Picker picker, Database db, List<String> primaryKeys,
                                                  String querySql, DatabaseTemplate databaseTemplate) {
        // 获取流式处理的fetchSize
        Integer fetchSize = db.getStreamingFetchSize(context);
        databaseTemplate.setFetchSize(fetchSize);

        // 上次保存时间（局域化，仅在当前任务执行期间有效）
        long[] lastSaveTime = {0L};

        try (Stream<Map<String, Object>> stream = databaseTemplate.queryForStream(querySql,
                new ArgumentPreparedStatementSetter(tableGroup.getCursors()),
                new ColumnMapRowMapper())) {
            List<Map> batch = new ArrayList<>();
            Iterator<Map<String, Object>> iterator = stream.iterator();

            while (iterator.hasNext()) {
                // 检查任务是否被停止或线程是否被中断
                // 通过检查Meta状态来判断任务是否应该继续执行
                Meta meta = profileComponent.getMeta(metaId);
                if (!meta.isRunning() || Thread.currentThread().isInterrupted()) {
                    logger.warn("任务被中止:{}", metaId);
                    return null;
                }

                batch.add(iterator.next());

                // 达到批次大小时处理数据
                if (batch.size() >= mapping.getBatchNum()) {
                    processTableGroupDataBatch(metaId, batch, tableGroup, group, mapping, context, picker, primaryKeys, lastSaveTime);
                    batch = new ArrayList<>();
                }
            }
            // 处理最后一批数据
            if (!batch.isEmpty()) {
                processTableGroupDataBatch(metaId, batch, tableGroup, group, mapping, context, picker, primaryKeys, lastSaveTime);
            }
            // 标记流式处理完成，强制保存一次
            tableGroup.setFullCompleted(true);
            profileComponent.editConfigModel(tableGroup);
        } catch (Exception e) {
            throw new RuntimeException("source table: " + tableGroup.getSourceTable().getName() + "target table: " + tableGroup.getTargetTable().getName(), e);
        }
        return null;
    }

    /**
     * TableGroup专用的数据处理逻辑
     */
    private void processTableGroupDataBatch(String metaId, List<Map> source, TableGroup tableGroup, TableGroup group, Mapping mapping,
                                             AbstractPluginContext context, Picker picker, List<String> primaryKeys,
                                             long[] lastSaveTime) throws Exception {
        // 1、映射字段 + 封装为 SyncRow（Picker 返回的就是 SyncRow）
        List<SyncRow> target = picker.pickTargetData(source, context);
        // 设置 Mapping/TableGroup 引用（使用 group 而不是 tableGroup，确保 writer 能获取到合并后的 params）
        for (SyncRow row : target) {
            row.setMapping(mapping);
            row.setTableGroup(group);
        }

        // 2、参数转换（使用 tableGroup 的 convert）
        ConvertUtil.convert(tableGroup.getConvert(), target);

        // 3、写入目标源（直接传递 syncRows）
        Result result = connectorFactory.writeBatch(target);

        // 4、更新 Meta 统计信息
        if (result != null) {
            result.setMappingId(profileComponent.getMeta(metaId).getMappingId());
            result.setTargetTableGroupName(tableGroup.getName());
            result.setTableGroupId(tableGroup.getId());
            flushStrategy.flushFullData(metaId, result, ConnectorConstant.OPERTION_INSERT);
        }

        // 更新 group 的 cursor 并同步到 tableGroup（用于持久化）
        Object[] newCursors = PrimaryKeyUtil.getLastCursors(source, primaryKeys);
        tableGroup.setCursors(newCursors);
        saveTableGroupIfNeeded(tableGroup, lastSaveTime);

        // 同步完成后无需后置处理
    }
    private ConnectorConfig getConnectorConfig(String connectorId) {
        return profileComponent.getConnector(connectorId).getConfig();
    }

    /**
     * 检查并保存 TableGroup（每3秒保存一次，减少频繁IO）
     *
     * @param tableGroup   TableGroup 对象
     * @param lastSaveTime 上次保存时间（使用数组以便修改）
     */
    private void saveTableGroupIfNeeded(TableGroup tableGroup, long[] lastSaveTime) {
        long currentTime = System.currentTimeMillis();
        long lastSave = lastSaveTime[0];

        // 如果距离上次保存超过3秒，则执行保存
        if (currentTime - lastSave >= SAVE_INTERVAL_MS) {
            try {
                profileComponent.editConfigModel(tableGroup);
                logger.info("保存表 {} 全量同步", tableGroup.getName());
                lastSaveTime[0] = currentTime;
            } catch (Exception e) {
                logger.error("保存 TableGroup [{}] 失败: {}", tableGroup.getId(), e.getMessage(), e);
            }
        }
    }
}