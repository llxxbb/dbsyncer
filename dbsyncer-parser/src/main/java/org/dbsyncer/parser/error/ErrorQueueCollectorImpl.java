package org.dbsyncer.parser.error;

import org.dbsyncer.common.model.Paging;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.flush.BufferActuator;
import org.dbsyncer.parser.model.StorageRequest;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.constant.ConnectorConstant;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.filter.Query;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.storage.StorageService;
import org.dbsyncer.storage.enums.StorageDataStatusEnum;
import org.dbsyncer.storage.impl.SnowflakeIdWorker;
import org.dbsyncer.storage.util.BinlogMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ErrorQueueCollectorImpl implements ErrorQueueCollector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private CounterService counterService;

    @Resource
    private ErrorQueueConfig errorQueueConfig;

    @Resource
    private MessageService messageService;

    @Resource
    private BufferActuator storageBufferActuator;

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private LogService logService;

    @Resource
    private StorageService storageService;

    @Override
    public void handleFailData(Mapping mapping, Meta meta, TableGroup tableGroup, String targetTableName,
            String event, List<Map> failData, String errorMsg) {
        if (failData == null || failData.isEmpty()) {
            return;
        }
        String metaId = meta.getId();
        String tableGroupId = tableGroup != null ? tableGroup.getId() : null;
        logService.log(mapping, LogType.SYNC, errorMsg);
        try {
            persistError(metaId, tableGroupId, targetTableName, event, failData, errorMsg);
        } catch (Exception e) {
            closeTask(meta, targetTableName, "无法写入错误队列：" + e.getMessage(),
                    failData.size(), NotifyType.DATA_ERROR_QUEUE);
            return;
        }
        counterService.incrementFail(meta, tableGroup, failData.size());
        notifyError(meta, targetTableName, errorMsg, failData.size(), NotifyType.DATA_ERROR_QUEUE);
        checkCircuitBreaker(meta, meta.getMappingId());
    }

    public void handleDdlFailData(Mapping mapping, Meta meta, TableGroup tableGroup, String targetTableName,
            DDLConfig ddlConfig, String errorMsg) {
        String metaId = meta.getId();
        String tableGroupId = tableGroup != null ? tableGroup.getId() : null;
        logService.log(mapping, LogType.SYNC, errorMsg);
        Map<String, Object> row = new HashMap<>();
        row.put(ConfigConstant.BINLOG_DATA, JsonUtil.objToJson(ddlConfig));
        persistErrorRow(metaId, tableGroupId, targetTableName, ConnectorConstant.OPERTION_ALTER, row, errorMsg);
        counterService.incrementFail(meta, tableGroup, 1);
        notifyError(meta, targetTableName, errorMsg, 1, NotifyType.DATA_ERROR_QUEUE);
        checkCircuitBreaker(meta, meta.getMappingId());
    }

    public void alignCount(Meta meta) throws Exception {
        String metaId = meta.getId();
        String mappingId = meta.getMappingId();
        long totalFail = 0;
        try {
            List<TableGroup> tableGroups = profileComponent.getTableGroupAll(mappingId);
            for (TableGroup tg : tableGroups) {
                long actualCount = queryFailCount(metaId, tg.getId());
                long oldFail = tg.getFail();
                if (oldFail != actualCount) {
                    logger.info("计数对齐: tableGroupId={}, 旧值={}, 新值={}", tg.getId(), oldFail, actualCount);
                }
                tg.setFail(actualCount);
                tg.setTotal(tg.getSuccess() + tg.getFail());
                totalFail += actualCount;
                profileComponent.editConfigModel(tg);
            }
        } catch (Exception e) {
            throw new RuntimeException("计数对齐失败: " + e.getMessage(), e);
        }
        long oldMetaFail = meta.getFail();
        meta.setFail(totalFail);
        meta.setTotal(meta.getSuccess() + meta.getFail());
        meta.setUpdateTime(Instant.now().toEpochMilli());
        profileComponent.editConfigModel(meta);
        logger.info("计数对齐完成: metaId={}, Meta.fail 旧值={}, 新值={}", metaId, oldMetaFail, totalFail);
    }

    private void persistError(String metaId, String tableGroupId, String targetTableName,
            String event, List<Map> failData, String errorMsg) throws Exception {
        List<Map> dataList = new java.util.ArrayList<>();
        for (Map r : failData) {
            Map<String, Object> row = buildErrorRow(metaId, tableGroupId, targetTableName, event, errorMsg);
            byte[] bytes = BinlogMessageUtil.toBinlogMap(r).toByteArray();
            row.put(ConfigConstant.BINLOG_DATA, bytes);
            dataList.add(row);
        }
        storageService.addBatch(StorageEnum.DATA, metaId, dataList);
    }

    private void persistErrorRow(String metaId, String tableGroupId, String targetTableName,
            String event, Map<String, Object> data, String errorMsg) {
        Map<String, Object> row = buildErrorRow(metaId, tableGroupId, targetTableName, event, errorMsg);
        row.put(ConfigConstant.BINLOG_DATA, data.get(ConfigConstant.BINLOG_DATA));
        storageBufferActuator.offer(new StorageRequest(metaId, row));
    }

    private Map<String, Object> buildErrorRow(String metaId, String tableGroupId, String targetTableName,
            String event, String errorMsg) {
        Map<String, Object> row = new HashMap<>();
        row.put(ConfigConstant.CONFIG_MODEL_ID, String.valueOf(snowflakeIdWorker.nextId()));
        row.put(ConfigConstant.DATA_SUCCESS, StorageDataStatusEnum.FAIL.getValue());
        row.put(ConfigConstant.DATA_TABLE_GROUP_ID, tableGroupId);
        row.put(ConfigConstant.DATA_TARGET_TABLE_NAME, targetTableName);
        row.put(ConfigConstant.DATA_EVENT, event);
        row.put(ConfigConstant.DATA_ERROR, errorMsg != null ? errorMsg : "");
        row.put(ConfigConstant.CONFIG_MODEL_CREATE_TIME, Instant.now().toEpochMilli());
        return row;
    }

    private void notifyError(Meta meta, String targetTableName, String errorMsg, int failCount, NotifyType notifyType) {
        String mappingId = meta.getMappingId();
        StringBuilder msg = new StringBuilder();
        msg.append("### DBSyncer 数据同步异常\n\n");
        msg.append("**错误类型**: ").append(notifyType.getName()).append("\n\n");
        msg.append("**Mapping ID**: ").append(mappingId).append("\n\n");
        msg.append("**目标表**: ").append(targetTableName).append("\n\n");
        msg.append("**失败数量**: ").append(failCount).append(" 条\n\n");
        msg.append("**错误原因**: ").append(errorMsg);
        messageService.sendMessage("dbsyncer", msg.toString(), notifyType);
    }

    @Override
    public void closeTask(Meta meta, String targetTableName, String errorMsg,
            int failCount, NotifyType notifyType) {

        notifyError(meta, targetTableName, errorMsg, failCount, notifyType);
        // 关闭任务
        meta.saveState(MetaEnum.ERROR, errorMsg);
        try {
            profileComponent.editConfigModel(meta);
        } catch (Exception e) {
            // 不能再抛异常，否则可能无法关闭任务
            logger.error("关闭任务失败: metaId={}", meta.getId(), e);
        }
        applicationEventPublisher.publishEvent(new CircuitBreakEvent(this, meta.getMappingId()));
    }

    private void checkCircuitBreaker(Meta meta, String mappingId) {
        int threshold = errorQueueConfig.getThreshold();
        if (threshold <= 0) {
            return;
        }
        if (meta.getFail() < threshold) {
            return;
        }
        String reason = String.format("错误计数(%d)达到熔断阈值(%d)", meta.getFail(), threshold);
        logger.warn("熔断触发: metaId={}, {}", meta.getId(), reason);
        closeTask(meta, mappingId, reason, threshold, NotifyType.DATA_ERROR_CIRCUIT_BREAKER);
    }

    private long queryFailCount(String metaId, String tableGroupId) {
        Query query = new Query(1, 1);
        query.setQueryTotal(true);
        query.setType(StorageEnum.DATA);
        query.setMetaId(metaId);
        query.addFilter(ConfigConstant.DATA_SUCCESS, StorageDataStatusEnum.FAIL.getValue());
        query.addFilter(ConfigConstant.DATA_TABLE_GROUP_ID, tableGroupId);
        Paging paging = storageService.query(query);
        return paging.getTotal();
    }

}
