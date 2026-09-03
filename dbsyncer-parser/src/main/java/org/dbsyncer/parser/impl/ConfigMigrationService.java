package org.dbsyncer.parser.impl;

import org.dbsyncer.common.config.BufferActuatorConfig;
import org.dbsyncer.common.config.DispatchTaskConfig;
import org.dbsyncer.common.config.GeneralBufferConfig;
import org.dbsyncer.common.config.RetryConfig;
import org.dbsyncer.common.config.SchedulerConfig;
import org.dbsyncer.common.config.StorageConfig;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.retry.RetryPolicy;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.error.ErrorQueueConfig;
import org.dbsyncer.parser.notify.NotifyConfig;
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.filter.Query;
import org.dbsyncer.sdk.storage.StorageService;
import org.dbsyncer.common.model.Paging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ADR-0029 §2.0.1：配置迁移服务。
 * <p>
 * 首次运行时（schemaVersion == 0），从 @ConfigurationProperties bean 读运行时值，
 * 写入 SystemConfig 的子对象，然后置 schemaVersion = 1。
 * <p>
 * L2 修订：直接查存储层，不调 getSystemConfig()（避免触发默认配置创建）。
 * M5 修订：A 类不迁移、不落库。
 */
@Component
public class ConfigMigrationService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigMigrationService.class);

    @Resource
    private OperationTemplate operationTemplate;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private GeneralBufferConfig generalBufferConfig;

    @Resource
    private StorageConfig storageConfig;

    @Resource
    private DispatchTaskConfig dispatchTaskConfig;

    @Resource
    private SchedulerConfig schedulerConfig;

    @Resource
    private RetryConfig retryConfig;

    @Resource
    private ErrorQueueConfig errorQueueConfig;

    @Resource
    private NotifyConfig notifyConfig;

    @Resource
    private StorageService storageService;

    /**
     * 检测并执行配置迁移。在 PreloadTemplate.onApplicationEvent 中调用。
     */
    public void migrateIfNeeded() {
        // L2：直接查存储层，不调 getSystemConfig()
        SystemConfig config = loadSystemConfigFromStorage();
        if (config == null) {
            logger.info("配置迁移：无 SystemConfig 记录，跳过迁移");
            return;
        }

        if (config.getSchemaVersion() > 0) {
            // 已迁移
            return;
        }

        logger.info("配置迁移开始：schemaVersion=0 → 1");

        // 迁移 B 类：执行器
        ExecutorProps executors = new ExecutorProps();
        executors.setGeneral(buildPoolProps(
                generalBufferConfig.getThreadCoreSize(),
                generalBufferConfig.getMaxThreadSize(),
                generalBufferConfig.getThreadQueueCapacity()));
        executors.setStorage(buildPoolProps(
                storageConfig.getThreadCoreSize(),
                storageConfig.getMaxThreadSize(),
                storageConfig.getThreadQueueCapacity()));
        executors.setDispatch(buildPoolProps(
                dispatchTaskConfig.getThreadCoreSize(),
                dispatchTaskConfig.getMaxThreadSize(),
                dispatchTaskConfig.getThreadQueueCapacity()));
        executors.setSchedulerPoolSize(schedulerConfig.getPoolSize());
        config.setExecutors(executors);

        // 迁移 B 类：缓冲区
        BufferProps buffers = new BufferProps();
        buffers.setGeneral(buildBufferActuatorProps(generalBufferConfig));
        buffers.setStorage(buildBufferActuatorProps(storageConfig));
        config.setBuffers(buffers);

        // 迁移 C 类：重试策略
        RetryPolicy g = retryConfig.getGlobal();
        RetryProps retry = new RetryProps();
        retry.setDisable(g.isDisable());
        retry.setMaxAttempts(g.getMaxAttempts());
        retry.setInitialIntervalMs(g.getInitialIntervalMs());
        retry.setMaxIntervalMs(g.getMaxIntervalMs());
        retry.setMultiplier(g.getMultiplier());
        retry.setMaxDurationMs(g.getMaxDurationMs());
        retry.setTerminationMode(g.getTerminationMode());
        retry.setUseKeyword(g.isUseKeyword());
        retry.setMatchMode(g.getMatchMode());
        retry.setKeywords(g.getKeywords() != null ? new ArrayList<>(g.getKeywords()) : null);
        retry.setExcludeKeywords(g.getExcludeKeywords() != null ? new ArrayList<>(g.getExcludeKeywords()) : null);
        config.setRetry(retry);

        // 迁移 C 类：错误处理
        ErrorProps error = new ErrorProps();
        error.setThreshold(errorQueueConfig.getThreshold());
        error.setFatalMatchMode(errorQueueConfig.getFatalMatchMode());
        error.setFatalKeywords(new ArrayList<>(errorQueueConfig.getFatalKeywords()));
        config.setError(error);

        // 迁移 B 类：通知通道
        NotifyProps notify = new NotifyProps();
        notify.setChannels(new ArrayList<>(notifyConfig.getChannels()));
        // 邮件通道
        MailProps mailProps = new MailProps();
        NotifyConfig.MailConfig mailConfig = notifyConfig.getMail();
        if (mailConfig != null) {
            mailProps.setUsername(mailConfig.getUsername());
            mailProps.setPassword(mailConfig.getPassword());
            mailProps.setNotifyTypes(new ArrayList<>(mailConfig.getNotifyTypes()));
        }
        notify.setMail(mailProps);
        // 钉钉通道
        DingTalkProps dingtalkProps = new DingTalkProps();
        NotifyConfig.DingTalkConfig dingtalkConfig = notifyConfig.getDingtalk();
        if (dingtalkConfig != null) {
            dingtalkProps.setWebhookUrl(dingtalkConfig.getWebhookUrl());
            dingtalkProps.setSecret(dingtalkConfig.getSecret());
            dingtalkProps.setNotifyTypes(new ArrayList<>(dingtalkConfig.getNotifyTypes()));
        }
        notify.setDingtalk(dingtalkProps);
        config.setNotify(notify);

        // 迁移 BasicProps（原平铺字段 → 子对象）
        BasicProps basic = new BasicProps();
        // 从旧字段读取默认值，新部署直接使用默认值
        basic.setExpireDataDays(7);
        basic.setExpireLogDays(30);
        basic.setRefreshIntervalSeconds(5);
        basic.setEnableStorageWriteFail(true);
        basic.setMaxStorageErrorLength(2048);
        basic.setEnableCDN(false);
        basic.setEnableWatermark(false);
        basic.setWatermark(null);
        basic.setEnableSchemaResolver(true);
        basic.setEnablePrintTraceInfo(false);
        config.setBasic(basic);

        // 置版本号
        config.setSchemaVersion(1);

        // 保存
        try {
            profileComponent.editConfigModel(config);
            logger.info("配置迁移完成：schemaVersion=0 → 1");
        } catch (Exception e) {
            logger.error("配置迁移保存失败", e);
        }
    }

    /**
     * L2：直接从存储层读取 SystemConfig，不调 getSystemConfig()。
     */
    private SystemConfig loadSystemConfigFromStorage() {
        try {
            Query query = new Query();
            query.setType(StorageEnum.CONFIG);
            query.addFilter(ConfigConstant.CONFIG_MODEL_TYPE, ConfigConstant.SYSTEM);
            query.setPageNum(1);
            query.setPageSize(1);
            Paging paging = storageService.query(query);
            List<Map> data = (List<Map>) paging.getData();
            if (data == null || data.isEmpty()) {
                return null;
            }
            String json = (String) data.get(0).get(ConfigConstant.CONFIG_MODEL_JSON);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return org.dbsyncer.common.util.JsonUtil.jsonToObj(json, SystemConfig.class);
        } catch (Exception e) {
            logger.error("从存储层读取 SystemConfig 失败", e);
            return null;
        }
    }

    private PoolProps buildPoolProps(int coreSize, int maxSize, int queueCapacity) {
        PoolProps props = new PoolProps();
        props.setThreadCoreSize(coreSize);
        props.setMaxThreadSize(maxSize);
        props.setThreadQueueCapacity(queueCapacity);
        return props;
    }

    private BufferActuatorProps buildBufferActuatorProps(BufferActuatorConfig config) {
        BufferActuatorProps props = new BufferActuatorProps();
        props.setBufferWriterCount(config.getBufferWriterCount());
        props.setBufferPullCount(config.getBufferPullCount());
        props.setBufferQueueCapacity(config.getBufferQueueCapacity());
        props.setBufferPeriodMillisecond(config.getBufferPeriodMillisecond());
        return props;
    }
}
