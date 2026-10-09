package org.dbsyncer.parser.error;

import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.config.props.ErrorProps;
import org.dbsyncer.common.model.Paging;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.retry.KeywordMatcher;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.MatchMode;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.common.constant.ConfigConstant;
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
import org.dbsyncer.common.config.SnowflakeIdWorker;
import org.dbsyncer.storage.util.BinlogMessageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ErrorQueueCollectorImpl implements ErrorQueueCollector, ConfigChangeListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private CounterService counterService;

    @Resource
    private ConfigRegistry configRegistry;

    @Resource
    private MessageService messageService;

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private LogService logService;

    @Resource
    private StorageService storageService;

    /** 致命关键字匹配器（volatile + DCL，配置变更时置 null 失效） */
    private volatile KeywordMatcher fatalMatcher;

    // ===== ConfigChangeListener =====

    @Override
    public void onConfigChanged(SystemConfig newConfig) {
        this.fatalMatcher = null;
    }

    // ===== 致命错误检测 =====

    private boolean isFatalError(String message) {
        ErrorProps error = configRegistry.getConfig().getError();
        List<String> keywords = error.getFatalKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return false;
        }
        if (fatalMatcher == null) {
            synchronized (this) {
                if (fatalMatcher == null) {
                    MatchMode mode = error.getFatalMatchMode();
                    if (mode == null) mode = MatchMode.CONTAINS_IGNORE_CASE;
                    List<String> valid = keywords.stream()
                            .filter(k -> k != null && !k.isEmpty())
                            .collect(Collectors.toList());
                    fatalMatcher = new KeywordMatcher(valid, mode);
                    logger.info("致命关键字匹配器已初始化: matchMode={}, keywords={}", mode, valid);
                }
            }
        }
        String matched = fatalMatcher.findMatch(message);
        if (matched != null) {
            logger.warn("检测到致命错误消息: keyword={}, message={}", matched, message);
        }
        return matched != null;
    }

    @Override
    public void handleFailData(Mapping mapping, Meta meta, TableGroup tableGroup, String targetTableName,
            String event, List<Map> failData, List<String> failReasons, Exception e, boolean isErrQueueRetry) throws Exception {
        if (failData == null || failData.isEmpty()) {
            return;
        }
        logger.error("mappingId={}: tableName={}, error={}", mapping.getId(), targetTableName, e);
        logService.log(mapping, LogType.SYNC, LogLevel.ERROR, "数量：" + failData.size() + "，错误：" + e.getMessage());
        notifyError(meta, targetTableName, e.getMessage(), failData.size(), NotifyType.DATA_ERROR_QUEUE);
        if (isErrQueueRetry) {
            throw e;
        }
        if (isFatalError(e.getMessage())) {
            closeTask(meta, targetTableName, e, failData.size(), NotifyType.FATAL_MATCHED);
            throw e;
        }
        String metaId = meta.getId();
        String tableGroupId = tableGroup != null ? tableGroup.getId() : null;
        try {
            persistError(metaId, tableGroupId, targetTableName, event, failData, failReasons, e.getMessage());
        } catch (Exception e2) {
            closeTask(meta, targetTableName, e2, failData.size(), NotifyType.DATA_ERROR_QUEUE);
            return;
        }
        counterService.incrementFail(meta, tableGroup, failData.size());
        checkCircuitBreaker(meta, targetTableName);
    }

    public void handleDdlFailData(Mapping mapping, Meta meta, TableGroup tableGroup, String targetTableName,
            DDLConfig ddlConfig, Exception e, boolean isErrQueueRetry) throws Exception {
        String metaId = meta.getId();
        String tableGroupId = tableGroup != null ? tableGroup.getId() : null;
        logger.error("mappingId={}: tableName={}, error={}", mapping.getId(), targetTableName, e);
        logService.log(mapping, LogType.SYNC, LogLevel.ERROR, e.getMessage());
        notifyError(meta, targetTableName, e.getMessage(), 1, NotifyType.DATA_ERROR_QUEUE);
        if (isErrQueueRetry) {
            throw e;
        }
        if (isFatalError(e.getMessage())) {
            closeTask(meta, targetTableName, e, 1, NotifyType.FATAL_MATCHED);
            throw e;
        }
        Map<String, Object> row = new HashMap<>();
        row.put(ConfigConstant.BINLOG_DATA, JsonUtil.objToJson(ddlConfig));
        persistErrorRow(metaId, tableGroupId, targetTableName, ConnectorConstant.OPERTION_ALTER, row, e.getMessage());
        counterService.incrementFail(meta, tableGroup, 1);
        checkCircuitBreaker(meta, targetTableName);
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
                tg.setTotal(Math.max(tg.getTotal(), tg.getSuccess() + tg.getFail()));
                totalFail += actualCount;
                profileComponent.editConfigModel(tg);
            }
        } catch (Exception e) {
            throw new RuntimeException("计数对齐失败: " + e.getMessage(), e);
        }
        long oldMetaFail = meta.getFail();
        meta.setFail(totalFail);
        meta.setTotal(Math.max(meta.getTotal(), meta.getSuccess() + meta.getFail()));
        meta.setUpdateTime(Instant.now().toEpochMilli());
        profileComponent.editConfigModel(meta);
        logger.info("计数对齐完成: metaId={}, Meta.fail 旧值={}, 新值={}", metaId, oldMetaFail, totalFail);
    }

    private void persistError(String metaId, String tableGroupId, String targetTableName,
            String event, List<Map> failData, List<String> failReasons, String defaultErrorMsg) throws Exception {
        List<Map> dataList = new java.util.ArrayList<>();
        for (int i = 0; i < failData.size(); i++) {
            Map r = failData.get(i);
            String rowReason = (failReasons != null && i < failReasons.size() && failReasons.get(i) != null)
                    ? failReasons.get(i) : defaultErrorMsg;
            Map<String, Object> row = buildErrorRow(metaId, tableGroupId, targetTableName, event, rowReason);
            byte[] bytes = BinlogMessageUtil.toBinlogMap(r).toByteArray();
            row.put(ConfigConstant.BINLOG_DATA, bytes);
            dataList.add(row);
        }
        storageService.addBatch(StorageEnum.DATA, metaId, dataList);
    }

    private void persistErrorRow(String metaId, String tableGroupId, String targetTableName,
            String event, Map<String, Object> data, String errorMsg) throws Exception {
        Map<String, Object> row = buildErrorRow(metaId, tableGroupId, targetTableName, event, errorMsg);
        row.put(ConfigConstant.BINLOG_DATA, data.get(ConfigConstant.BINLOG_DATA));
        storageService.addBatch(StorageEnum.DATA, metaId, java.util.Collections.singletonList(row));
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
        Mapping mapping = profileComponent.getMapping(mappingId);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("目标表", targetTableName);
        details.put("失败数量", failCount + " 条");
        details.put("错误原因", errorMsg);
        messageService.sendNotifyMessage(mapping, "数据同步异常", notifyType, details);
    }

    @Override
    public void closeTask(Meta meta, String targetTableName, Exception e,
            int failCount, NotifyType notifyType) {
        String errorMsg = e.getMessage();
        logger.error("[FATAL] 任务关闭: mappingId={}, targetTable={}", meta.getMappingId(), targetTableName,  e);
        logService.log(meta, LogType.SYNC, LogLevel.FATAL,
                String.format("目标表:%s, 原因:%s", targetTableName, errorMsg));
        notifyError(meta, targetTableName, errorMsg, failCount, notifyType);
        // 关闭任务
        meta.saveState(MetaEnum.ERROR, errorMsg);
        try {
            profileComponent.editConfigModel(meta);
        } catch (Exception ex) {
            // 不能再抛异常，否则可能无法关闭任务
            logger.error("关闭任务失败: metaId={}", meta.getId(), ex);
        }
        applicationEventPublisher.publishEvent(new CircuitBreakEvent(this, meta.getMappingId()));
    }

    private void checkCircuitBreaker(Meta meta, String tableName) {
        int threshold = configRegistry.getConfig().getError().getThreshold();
        if (threshold <= 0) {
            return;
        }
        if (meta.getFail() < threshold) {
            return;
        }
        String reason = String.format("错误计数(%d)达到熔断阈值(%d)", meta.getFail(), threshold);
        logger.warn("熔断触发: metaId={}, {}", meta.getId(), reason);
        closeTask(meta, tableName, new RuntimeException(reason), threshold, NotifyType.DATA_ERROR_CIRCUIT_BREAKER);
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
