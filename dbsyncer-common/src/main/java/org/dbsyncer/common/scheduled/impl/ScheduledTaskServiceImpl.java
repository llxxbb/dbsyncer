package org.dbsyncer.common.scheduled.impl;

import org.dbsyncer.common.CommonException;
import org.dbsyncer.common.config.SchedulerFactory;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-24 22:06
 */
@Component
public class ScheduledTaskServiceImpl implements ScheduledTaskService, DisposableBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 调度线程池工厂（ADR-0032：延迟创建，配置完全加载后使用）
     */
    @Resource
    private SchedulerFactory schedulerFactory;

    private final Map<String, ScheduledFuture> map = new ConcurrentHashMap<>();

    @Override
    public void start(String key, String cron, ScheduledTaskJob job) {
        logger.info("[{}], Started task [{}]", cron, job.getClass().getSimpleName());
        ThreadPoolTaskScheduler scheduler = schedulerFactory.getTaskScheduler();
        apply(key, () -> scheduler.schedule(job, (trigger) -> new CronTrigger(cron).nextExecutionTime(trigger)));
    }

    @Override
    public void start(String key, long period, ScheduledTaskJob job) {
        logger.info("[period={}], Started task [{}]", period, job.getClass().getSimpleName());
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
    public void stop(String key) {
        ScheduledFuture job = map.get(key);
        if (null != job) {
            job.cancel(true);
            map.remove(key);
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

    private interface ScheduledFutureMapper {
        /**
         * 返回定时任务
         *
         * @return
         */
        ScheduledFuture apply();
    }

}