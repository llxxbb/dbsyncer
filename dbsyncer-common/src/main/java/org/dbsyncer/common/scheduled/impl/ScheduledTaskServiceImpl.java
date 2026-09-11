package org.dbsyncer.common.scheduled.impl;

import org.dbsyncer.common.CommonException;
import org.dbsyncer.common.config.SchedulerFactory;
import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.scheduled.ScheduledTaskJob;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.UUIDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-24 22:06
 */
@Component
public class ScheduledTaskServiceImpl implements ScheduledTaskService, DisposableBean, ConfigChangeListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 调度线程池工厂（ADR-0032：延迟创建，配置完全加载后使用）
     */
    @Resource
    private SchedulerFactory schedulerFactory;

    private final Map<String, ScheduledFuture> map = new ConcurrentHashMap<>();

    /**
     * 任务注册参数记录，用于 ADR-0033 热重载后重注册。
     */
    private final Map<String, TaskRegistration> registrations = new ConcurrentHashMap<>();

    /** 上次生效的 schedulerPoolSize，用于变更检测 */
    private volatile int lastPoolSize = -1;

    @Override
    public synchronized void start(String key, String cron, ScheduledTaskJob job) {
        logger.info("[{}], Started task [{}]", cron, job.getClass().getSimpleName());
        registrations.put(key, TaskRegistration.cron(cron, job));
        ThreadPoolTaskScheduler scheduler = schedulerFactory.getTaskScheduler();
        apply(key, () -> scheduler.schedule(job, (trigger) -> new CronTrigger(cron).nextExecutionTime(trigger)));
    }

    @Override
    public synchronized void start(String key, long period, ScheduledTaskJob job) {
        logger.info("[period={}], Started task [{}]", period, job.getClass().getSimpleName());
        registrations.put(key, TaskRegistration.period(period, job));
        ThreadPoolTaskScheduler scheduler = schedulerFactory.getTaskScheduler();
        apply(key, () -> scheduler.scheduleAtFixedRate(job, period));
    }

    @Override
    public void start(String cron, ScheduledTaskJob job) {
        start(UUIDUtil.getUUID(), cron, job);
    }

    @Override
    public void start(long period, ScheduledTaskJob job) {
        start(UUIDUtil.getUUID(), period, job);
    }

    @Override
    public synchronized void stop(String key) {
        ScheduledFuture future = map.remove(key);
        registrations.remove(key);
        if (null != future) {
            future.cancel(true);
            logger.info("Stopped task [{}]", key);
        }
    }

    private void apply(String key, ScheduledFutureMapper scheduledFutureMapper) {
        final ScheduledFuture scheduledFuture = map.get(key);
        if (null != scheduledFuture && !scheduledFuture.isCancelled()) {
            String msg = String.format(">>>>>> 任务已启动 %s  >>>>>>", key);
            logger.error(msg);
            throw new CommonException(msg);
        }
        map.compute(key, (k,v) -> {
            if (v == null) {
                return scheduledFutureMapper.apply();
            }
            return v;
        });
    }

    @Override
    public void destroy() {
        map.keySet().forEach(this::stop);
    }

    // ===== ConfigChangeListener（ADR-0033：调度器热重载后重注册定时任务） =====

    @Override
    public synchronized void onConfigChanged(SystemConfig newConfig) {
        if (registrations.isEmpty()) {
            return;
        }
        int newPoolSize = newConfig.getMonitor().getSchedulerPoolSize();
        if (newPoolSize == lastPoolSize) {
            return;
        }
        logger.info("[ADR-0033] 检测到 schedulerPoolSize 变更: {} → {}，开始重建调度器并重注册任务",
                lastPoolSize, newPoolSize);

        // 主动触发 SchedulerFactory 热重载（不依赖 listener 调用顺序）
        schedulerFactory.reload();

        // 取消旧 future 并在新调度器上重新注册
        reRegisterAll();
        lastPoolSize = schedulerFactory.getCurrentPoolSize();
    }

    /**
     * 重新注册所有已记录的定时任务。
     * <p>
     * 当 SchedulerFactory 热重载后，旧的 ScheduledFuture 已失效，
     * 需要用新的 ThreadPoolTaskScheduler 重新注册。
     */
    private void reRegisterAll() {
        List<String> keys = new ArrayList<>(registrations.keySet());
        List<TaskRegistration> snapshot = new ArrayList<>();
        for (String key : keys) {
            snapshot.add(registrations.get(key));
        }

        // 取消旧 future
        for (String key : keys) {
            ScheduledFuture future = map.remove(key);
            if (future != null) {
                future.cancel(true);
            }
        }

        // 在新调度器上重新注册
        ThreadPoolTaskScheduler scheduler = schedulerFactory.getTaskScheduler();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            TaskRegistration reg = snapshot.get(i);
            if (reg == null) {
                continue;
            }
            try {
                ScheduledFuture future;
                if (reg.cron != null) {
                    future = scheduler.schedule(reg.job,
                            (trigger) -> new CronTrigger(reg.cron).nextExecutionTime(trigger));
                } else {
                    future = scheduler.scheduleAtFixedRate(reg.job, reg.period);
                }
                map.put(key, future);
                logger.info("[ADR-0033] 定时任务已重新注册: key={}", key);
            } catch (Exception e) {
                logger.error("[ADR-0033] 定时任务重注册失败: key={}", key, e);
                registrations.remove(key);
            }
        }
    }

    private interface ScheduledFutureMapper {
        /**
         * 返回定时任务
         *
         * @return
         */
        ScheduledFuture apply();
    }

    /**
     * 定时任务注册参数记录。
     */
    private static class TaskRegistration {
        final String cron;
        final long period;
        final ScheduledTaskJob job;

        private TaskRegistration(String cron, long period, ScheduledTaskJob job) {
            this.cron = cron;
            this.period = period;
            this.job = job;
        }

        static TaskRegistration cron(String cron, ScheduledTaskJob job) {
            return new TaskRegistration(cron, 0, job);
        }

        static TaskRegistration period(long period, ScheduledTaskJob job) {
            return new TaskRegistration(null, period, job);
        }
    }
}
