package org.dbsyncer.biz.impl;

import org.dbsyncer.common.config.props.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 运行时配置校验器（ADR-0029 §2.0.3）。
 * <p>
 * 保存前 + 启动前两处共用，唯一校验规则来源。
 * L4 修订：所有子对象先判 null，避免 NPE。
 */
@Component
public class RuntimeConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeConfigValidator.class);

    /**
     * 校验执行器配置
     */
    public List<String> validate(ExecutorProps exec) {
        List<String> errors = new ArrayList<>();
        if (exec == null) {
            return errors;
        }
        validatePoolProps(exec.getGeneral(), "通用执行器", errors);
        validatePoolProps(exec.getStorage(), "存储执行器", errors);
        validatePoolProps(exec.getDispatch(), "调度任务执行器", errors);
        if (exec.getSchedulerPoolSize() != null && exec.getSchedulerPoolSize() < 1) {
            errors.add("调度线程数必须 > 0");
        }
        return errors;
    }

    /**
     * 校验缓冲区配置
     */
    public List<String> validate(BufferProps buffers) {
        List<String> errors = new ArrayList<>();
        if (buffers == null) {
            return errors;
        }
        validateBufferActuatorProps(buffers.getGeneral(), "通用缓冲区", errors);
        validateBufferActuatorProps(buffers.getStorage(), "存储缓冲区", errors);
        return errors;
    }

    /**
     * 校验重试策略配置
     */
    public List<String> validate(RetryProps retry) {
        List<String> errors = new ArrayList<>();
        if (retry == null) {
            return errors;
        }
        if (retry.getMaxAttempts() != null && retry.getMaxAttempts() < 1) {
            errors.add("最大重试次数必须 >= 1");
        }
        if (retry.getInitialIntervalMs() != null && retry.getInitialIntervalMs() < 0) {
            errors.add("初始重试间隔不能 < 0");
        }
        if (retry.getMaxIntervalMs() != null && retry.getMaxIntervalMs() < 0) {
            errors.add("最大重试间隔不能 < 0");
        }
        if (retry.getMultiplier() != null && retry.getMultiplier() < 1.0) {
            errors.add("退避倍数必须 >= 1.0");
        }
        if (retry.getMaxDurationMs() != null && retry.getMaxDurationMs() < 0) {
            errors.add("重试总时长不能 < 0（0 表示不限制）");
        }
        return errors;
    }

    /**
     * 校验通知通道配置
     */
    public List<String> validate(NotifyProps notify) {
        List<String> errors = new ArrayList<>();
        if (notify == null) {
            return errors;
        }
        // 通知通道无特殊范围校验
        return errors;
    }

    /**
     * 校验错误处理配置
     */
    public List<String> validate(ErrorProps error) {
        List<String> errors = new ArrayList<>();
        if (error == null) {
            return errors;
        }
        if (error.getThreshold() != null && error.getThreshold() < 0) {
            errors.add("熔断阈值不能 < 0（0 表示不启用熔断）");
        }
        return errors;
    }

    /**
     * 校验基础设置
     */
    public List<String> validate(BasicProps basic) {
        List<String> errors = new ArrayList<>();
        if (basic == null) {
            return errors;
        }
        if (basic.getExpireDataDays() != null && basic.getExpireDataDays() < 1) {
            errors.add("同步数据过期时间必须 >= 1 天");
        }
        if (basic.getExpireLogDays() != null && basic.getExpireLogDays() < 1) {
            errors.add("系统日志过期时间必须 >= 1 天");
        }
        if (basic.getRefreshIntervalSeconds() != null && basic.getRefreshIntervalSeconds() < 1) {
            errors.add("刷新间隔必须 >= 1 秒");
        }
        if (basic.getMaxStorageErrorLength() != null && basic.getMaxStorageErrorLength() < 100) {
            errors.add("失败日志最大长度必须 >= 100");
        }
        return errors;
    }

    private void validatePoolProps(PoolProps pool, String name, List<String> errors) {
        if (pool == null) {
            return;
        }
        if (pool.getThreadCoreSize() != null && pool.getThreadCoreSize() < 1) {
            errors.add(name + "核心线程数必须 > 0");
        }
        if (pool.getMaxThreadSize() != null && pool.getMaxThreadSize() < 1) {
            errors.add(name + "最大线程数必须 > 0");
        }
        if (pool.getThreadCoreSize() != null && pool.getMaxThreadSize() != null
                && pool.getThreadCoreSize() > pool.getMaxThreadSize()) {
            errors.add(name + "核心线程数不能大于最大线程数");
        }
        if (pool.getThreadQueueCapacity() != null && pool.getThreadQueueCapacity() < 1) {
            errors.add(name + "队列容量必须 > 0");
        }
    }

    private void validateBufferActuatorProps(BufferActuatorProps buf, String name, List<String> errors) {
        if (buf == null) {
            return;
        }
        if (buf.getBufferQueueCapacity() != null && buf.getBufferQueueCapacity() < 1) {
            errors.add(name + "队列容量必须 > 0");
        }
        if (buf.getBufferPeriodMillisecond() != null && buf.getBufferPeriodMillisecond() < 1) {
            errors.add(name + "消费间隔必须 > 0 毫秒");
        }
        if (buf.getBufferPullCount() != null && buf.getBufferPullCount() < 1) {
            errors.add(name + "每次消费任务数必须 > 0");
        }
    }
}
