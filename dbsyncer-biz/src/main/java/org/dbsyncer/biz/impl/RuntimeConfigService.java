package org.dbsyncer.biz.impl;

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
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.parser.notify.NotifyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;

/**
 * 运行时配置服务（ADR-0029）。
 * <p>
 * 职责：
 * 1. C 类运行期原地 setter 回写（立即生效）
 * 2. 子对象/Bean 合并读（UI 预填用）
 * 3. 保存链路中的 sidecar 重建触发
 */
@Service
public class RuntimeConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeConfigService.class);

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

    /**
     * C 类运行期原地 setter 回写。
     * 三条硬约束：原地写、走 setter、不换新实例。
     */
    public void applyCClass(SystemConfig config) {
        // Tab 4: 重试策略
        RetryProps retry = config.getRetry();
        if (retry != null) {
            applyRetryProps(retry);
        }

        // Tab 5: 通知通道
        NotifyProps notify = config.getNotify();
        if (notify != null) {
            applyNotifyProps(notify);
        }

        // Tab 6: 错误处理
        ErrorProps error = config.getError();
        if (error != null) {
            applyErrorProps(error);
        }

        logger.info("C 类配置已运行期生效");
    }

    /**
     * 原地回写重试策略到 RetryPolicy。
     * 走 setter 以触发 matcher/excludeMatcher 缓存失效。
     */
    private void applyRetryProps(RetryProps props) {
        RetryPolicy global = retryConfig.getGlobal();
        if (props.getDisable() != null) {
            global.setDisable(props.getDisable());
        }
        if (props.getMaxAttempts() != null) {
            global.setMaxAttempts(props.getMaxAttempts());
        }
        if (props.getInitialIntervalMs() != null) {
            global.setInitialIntervalMs(props.getInitialIntervalMs());
        }
        if (props.getMaxIntervalMs() != null) {
            global.setMaxIntervalMs(props.getMaxIntervalMs());
        }
        if (props.getMultiplier() != null) {
            global.setMultiplier(props.getMultiplier());
        }
        if (props.getMaxDurationMs() != null) {
            global.setMaxDurationMs(props.getMaxDurationMs());
        }
        if (props.getTerminationMode() != null) {
            global.setTerminationMode(props.getTerminationMode());
        }
        if (props.getUseKeyword() != null) {
            global.setUseKeyword(props.getUseKeyword());
        }
        if (props.getMatchMode() != null) {
            global.setMatchMode(props.getMatchMode());
        }
        if (props.getKeywords() != null) {
            global.setKeywords(props.getKeywords());
        }
        if (props.getExcludeKeywords() != null) {
            global.setExcludeKeywords(props.getExcludeKeywords());
        }
    }

    /**
     * 原地回写通知通道配置。
     */
    private void applyNotifyProps(NotifyProps props) {
        if (props.getChannels() != null) {
            notifyConfig.setChannels(props.getChannels());
        }
        // 邮件
        MailProps mail = props.getMail();
        if (mail != null) {
            NotifyConfig.MailConfig targetMail = notifyConfig.getMail();
            if (mail.getUsername() != null) {
                targetMail.setUsername(mail.getUsername());
            }
            if (mail.getPassword() != null) {
                targetMail.setPassword(mail.getPassword());
            }
            if (mail.getNotifyTypes() != null) {
                targetMail.setNotifyTypes(mail.getNotifyTypes());
            }
        }
        // 钉钉
        DingTalkProps dingtalk = props.getDingtalk();
        if (dingtalk != null) {
            NotifyConfig.DingTalkConfig targetDingtalk = notifyConfig.getDingtalk();
            if (dingtalk.getWebhookUrl() != null) {
                targetDingtalk.setWebhookUrl(dingtalk.getWebhookUrl());
            }
            if (dingtalk.getSecret() != null) {
                targetDingtalk.setSecret(dingtalk.getSecret());
            }
            if (dingtalk.getNotifyTypes() != null) {
                targetDingtalk.setNotifyTypes(dingtalk.getNotifyTypes());
            }
        }
    }

    /**
     * 原地回写错误处理配置。
     * 走 setter 以触发 fatalMatcher 缓存失效。
     */
    private void applyErrorProps(ErrorProps props) {
        if (props.getThreshold() != null) {
            errorQueueConfig.setThreshold(props.getThreshold());
        }
        if (props.getFatalMatchMode() != null) {
            errorQueueConfig.setFatalMatchMode(props.getFatalMatchMode());
        }
        if (props.getFatalKeywords() != null) {
            errorQueueConfig.setFatalKeywords(props.getFatalKeywords());
        }
    }

    // ===== 子对象/Bean 合并读（UI 预填用） =====

    /**
     * 获取执行器 Tab 当前生效值。
     * DB 有值用 DB，否则用 Bean 运行时值。
     */
    public ExecutorProps getEffectiveExecutor() {
        SystemConfig cfg = profileComponent.getSystemConfig();
        ExecutorProps db = cfg != null ? cfg.getExecutors() : null;
        if (db == null) {
            // 未配置，从 Bean 读运行时值
            return buildExecutorPropsFromBeans();
        }
        return db;
    }

    /**
     * 获取缓冲区 Tab 当前生效值。
     */
    public BufferProps getEffectiveBuffers() {
        SystemConfig cfg = profileComponent.getSystemConfig();
        BufferProps db = cfg != null ? cfg.getBuffers() : null;
        if (db == null) {
            return buildBufferPropsFromBeans();
        }
        return db;
    }

    /**
     * 获取重试策略 Tab 当前生效值。
     */
    public RetryProps getEffectiveRetry() {
        SystemConfig cfg = profileComponent.getSystemConfig();
        RetryProps db = cfg != null ? cfg.getRetry() : null;
        if (db == null) {
            return buildRetryPropsFromBean();
        }
        return db;
    }

    /**
     * 获取通知通道 Tab 当前生效值。
     */
    public NotifyProps getEffectiveNotify() {
        SystemConfig cfg = profileComponent.getSystemConfig();
        NotifyProps db = cfg != null ? cfg.getNotify() : null;
        if (db == null) {
            return buildNotifyPropsFromBean();
        }
        return db;
    }

    /**
     * 获取错误处理 Tab 当前生效值。
     */
    public ErrorProps getEffectiveError() {
        SystemConfig cfg = profileComponent.getSystemConfig();
        ErrorProps db = cfg != null ? cfg.getError() : null;
        if (db == null) {
            return buildErrorPropsFromBean();
        }
        return db;
    }

    /**
     * 获取基础设置 Tab 当前生效值。
     */
    public BasicProps getEffectiveBasic() {
        SystemConfig cfg = profileComponent.getSystemConfig();
        BasicProps db = cfg != null ? cfg.getBasic() : null;
        if (db == null) {
            return buildBasicPropsFromLegacy(cfg);
        }
        return db;
    }

    // ===== 从 Bean 构建 Props =====

    private ExecutorProps buildExecutorPropsFromBeans() {
        ExecutorProps props = new ExecutorProps();
        props.setGeneral(buildPoolProps(generalBufferConfig.getThreadCoreSize(),
                generalBufferConfig.getMaxThreadSize(), generalBufferConfig.getThreadQueueCapacity()));
        props.setStorage(buildPoolProps(storageConfig.getThreadCoreSize(),
                storageConfig.getMaxThreadSize(), storageConfig.getThreadQueueCapacity()));
        props.setDispatch(buildPoolProps(dispatchTaskConfig.getThreadCoreSize(),
                dispatchTaskConfig.getMaxThreadSize(), dispatchTaskConfig.getThreadQueueCapacity()));
        props.setSchedulerPoolSize(schedulerConfig.getPoolSize());
        return props;
    }

    private BufferProps buildBufferPropsFromBeans() {
        BufferProps props = new BufferProps();
        props.setGeneral(buildBufferActuatorProps(generalBufferConfig));
        props.setStorage(buildBufferActuatorProps(storageConfig));
        return props;
    }

    private RetryProps buildRetryPropsFromBean() {
        RetryPolicy g = retryConfig.getGlobal();
        RetryProps props = new RetryProps();
        props.setDisable(g.isDisable());
        props.setMaxAttempts(g.getMaxAttempts());
        props.setInitialIntervalMs(g.getInitialIntervalMs());
        props.setMaxIntervalMs(g.getMaxIntervalMs());
        props.setMultiplier(g.getMultiplier());
        props.setMaxDurationMs(g.getMaxDurationMs());
        props.setTerminationMode(g.getTerminationMode());
        props.setUseKeyword(g.isUseKeyword());
        props.setMatchMode(g.getMatchMode());
        props.setKeywords(g.getKeywords() != null ? new ArrayList<>(g.getKeywords()) : null);
        props.setExcludeKeywords(g.getExcludeKeywords() != null ? new ArrayList<>(g.getExcludeKeywords()) : null);
        return props;
    }

    private NotifyProps buildNotifyPropsFromBean() {
        NotifyProps props = new NotifyProps();
        props.setChannels(new ArrayList<>(notifyConfig.getChannels()));
        MailProps mail = new MailProps();
        mail.setUsername(notifyConfig.getMail().getUsername());
        // password 脱敏：不暴露
        mail.setNotifyTypes(new ArrayList<>(notifyConfig.getMail().getNotifyTypes()));
        props.setMail(mail);
        DingTalkProps dingtalk = new DingTalkProps();
        dingtalk.setWebhookUrl(notifyConfig.getDingtalk().getWebhookUrl());
        dingtalk.setSecret(notifyConfig.getDingtalk().getSecret());
        dingtalk.setNotifyTypes(new ArrayList<>(notifyConfig.getDingtalk().getNotifyTypes()));
        props.setDingtalk(dingtalk);
        return props;
    }

    private ErrorProps buildErrorPropsFromBean() {
        ErrorProps props = new ErrorProps();
        props.setThreshold(errorQueueConfig.getThreshold());
        props.setFatalMatchMode(errorQueueConfig.getFatalMatchMode());
        props.setFatalKeywords(new ArrayList<>(errorQueueConfig.getFatalKeywords()));
        return props;
    }

    private BasicProps buildBasicPropsFromLegacy(SystemConfig cfg) {
        BasicProps props = new BasicProps();
        if (cfg != null) {
            // 从旧扁平字段迁移
            props.setExpireDataDays(cfg.getBasic().getExpireDataDays());
            props.setExpireLogDays(cfg.getBasic().getExpireLogDays());
            props.setRefreshIntervalSeconds(cfg.getBasic().getRefreshIntervalSeconds());
            props.setEnableStorageWriteFail(cfg.getBasic().getEnableStorageWriteFail());
            props.setMaxStorageErrorLength(cfg.getBasic().getMaxStorageErrorLength());
            props.setEnableCDN(cfg.getBasic().getEnableCDN());
            props.setEnableWatermark(cfg.getBasic().getEnableWatermark());
            props.setWatermark(cfg.getBasic().getWatermark());
            props.setEnableSchemaResolver(cfg.getBasic().getEnableSchemaResolver());
            props.setEnablePrintTraceInfo(cfg.getBasic().getEnablePrintTraceInfo());
        }
        return props;
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
