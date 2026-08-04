/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.strategy.impl;

import org.dbsyncer.common.model.Result;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.parser.CacheService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.flush.BufferActuator;
import org.dbsyncer.parser.service.CounterService;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.parser.model.StorageRequest;
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.parser.strategy.FlushStrategy;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.storage.enums.StorageDataStatusEnum;
import org.dbsyncer.storage.impl.SnowflakeIdWorker;
import org.dbsyncer.storage.util.BinlogMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Version 1.0.0
 * @Author AE86
 * @Date 2021-11-18 22:22
 */
@Component
public final class FlushStrategyImpl implements FlushStrategy {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    @Resource
    private CacheService cacheService;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private BufferActuator storageBufferActuator;

    @Resource
    private CounterService counterService;

    @Override
    public void flushFullData(String metaId, Result result, String event) {
        Meta meta = cacheService.get(metaId, Meta.class);
        if (meta == null) {
            return;
        }
        TableGroup tableGroup = getTableGroup(result.getTableGroupId());

        int successSize = result.getSuccessData().size();
        if (successSize > 0 && tableGroup != null) {
            counterService.incrementSuccess(meta, tableGroup, successSize);
            updateTableGroupSpeed(tableGroup, successSize);
            counterService.flush(meta, tableGroup);
        }

        logWriteResult(result);
    }

    @Override
    public void flushIncrementData(String metaId, Result result, String event) {
        Meta meta = cacheService.get(metaId, Meta.class);
        if (meta == null) {
            return;
        }
        TableGroup tableGroup = getTableGroup(result.getTableGroupId());

        int successSize = result.getSuccessData().size();
        if (successSize > 0 && tableGroup != null) {
            counterService.incrementSuccess(meta, tableGroup, successSize);
            meta.setTotal(meta.getTotal() + successSize);
            updateTableGroupSpeed(tableGroup, successSize);
            counterService.flush(meta, tableGroup);
        }

        logWriteResult(result);
    }

    private void logWriteResult(Result result) {
        if (result != null) {
            int successCount = result.getSuccessData().size();
            String targetTableName = result.getTargetTableGroupName();
            logger.info("tableGroupName: {} 写入成功 {} 条", targetTableName, successCount);
        }
    }

    private void updateTableGroupSpeed(TableGroup tableGroup, int successSize) {
        long currentTime = System.currentTimeMillis();
        long lastSyncTime = tableGroup.getLastSyncTime();
        long batchCount = successSize;
        if (lastSyncTime > 0 && batchCount > 0) {
            long timeDiffMs = currentTime - lastSyncTime;
            if (timeDiffMs > 0) {
                double speed = (double) batchCount / (timeDiffMs / 1000.0);
                tableGroup.setCurrentSpeed(speed);
            }
        }
        tableGroup.setLastSyncTime(currentTime);
        tableGroup.setLastBatchCount(batchCount);
    }

    private void asyncWrite(String metaId, String tableGroupId, String targetTableGroupName, String event, boolean success, List<Map> data, String error) {
        long now = Instant.now().toEpochMilli();
        data.forEach(r -> {
            Map<String, Object> row = new HashMap();
            row.put(ConfigConstant.CONFIG_MODEL_ID, String.valueOf(snowflakeIdWorker.nextId()));
            row.put(ConfigConstant.DATA_SUCCESS, success ? StorageDataStatusEnum.SUCCESS.getValue() : StorageDataStatusEnum.FAIL.getValue());
            row.put(ConfigConstant.DATA_TABLE_GROUP_ID, tableGroupId);
            row.put(ConfigConstant.DATA_TARGET_TABLE_NAME, targetTableGroupName);
            row.put(ConfigConstant.DATA_EVENT, event);
            row.put(ConfigConstant.DATA_ERROR, error);
            row.put(ConfigConstant.CONFIG_MODEL_CREATE_TIME, now);
            try {
                byte[] bytes = BinlogMessageUtil.toBinlogMap(r).toByteArray();
                row.put(ConfigConstant.BINLOG_DATA, bytes);
            } catch (Exception e) {
                logger.warn("可能存在 Blob 或 inputStream 大文件类型，无法序列化:{}", r);
            }
            storageBufferActuator.offer(new StorageRequest(metaId, row));
        });
    }

    private TableGroup getTableGroup(String tableGroupId) {
        if (tableGroupId == null) {
            return null;
        }
        try {
            return profileComponent.getTableGroup(tableGroupId);
        } catch (Exception e) {
            logger.error("获取 TableGroup 失败", e);
            return null;
        }
    }
}
