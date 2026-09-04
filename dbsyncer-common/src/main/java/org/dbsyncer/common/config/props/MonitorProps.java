package org.dbsyncer.common.config.props;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 监控定时任务配置（C 类，立即生效）。
 * <p>
 * 控制所有监控相关定时任务的执行间隔，统一从 ConfigRegistry 管理。
 */
public class MonitorProps implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 调度线程池大小（B 类语义，但在此统一管理） */
    private Integer schedulerPoolSize;

    /** CPU/内存指标采集间隔（毫秒），默认 5000 */
    private Integer cpuMemoryIntervalMs;

    /** 连接器健康检查间隔（毫秒），默认 10000 */
    private Integer healthCheckIntervalMs;

    /** 过期数据清理间隔（毫秒），默认 30000 */
    private Integer cleanupIntervalMs;

    /** 指标上报间隔（毫秒），默认 5000 */
    private Integer metricReportIntervalMs;

    /** 预警 cron 表达式，默认 "0 *&#47;10 * * * ?" */
    private String alertCron;

    public Integer getSchedulerPoolSize() {
        return schedulerPoolSize;
    }

    public void setSchedulerPoolSize(Integer schedulerPoolSize) {
        this.schedulerPoolSize = schedulerPoolSize;
    }

    public Integer getCpuMemoryIntervalMs() {
        return cpuMemoryIntervalMs;
    }

    public void setCpuMemoryIntervalMs(Integer cpuMemoryIntervalMs) {
        this.cpuMemoryIntervalMs = cpuMemoryIntervalMs;
    }

    public Integer getHealthCheckIntervalMs() {
        return healthCheckIntervalMs;
    }

    public void setHealthCheckIntervalMs(Integer healthCheckIntervalMs) {
        this.healthCheckIntervalMs = healthCheckIntervalMs;
    }

    public Integer getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(Integer cleanupIntervalMs) {
        this.cleanupIntervalMs = cleanupIntervalMs;
    }

    public Integer getMetricReportIntervalMs() {
        return metricReportIntervalMs;
    }

    public void setMetricReportIntervalMs(Integer metricReportIntervalMs) {
        this.metricReportIntervalMs = metricReportIntervalMs;
    }

    public String getAlertCron() {
        return alertCron;
    }

    public void setAlertCron(String alertCron) {
        this.alertCron = alertCron;
    }

    /**
     * 校验监控配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (schedulerPoolSize != null && schedulerPoolSize < 1) {
            errors.add("调度线程数必须 > 0");
        }
        if (cpuMemoryIntervalMs != null && cpuMemoryIntervalMs < 1000) {
            errors.add("CPU/内存采集间隔必须 >= 1000 毫秒");
        }
        if (healthCheckIntervalMs != null && healthCheckIntervalMs < 1000) {
            errors.add("健康检查间隔必须 >= 1000 毫秒");
        }
        if (cleanupIntervalMs != null && cleanupIntervalMs < 1000) {
            errors.add("数据清理间隔必须 >= 1000 毫秒");
        }
        if (metricReportIntervalMs != null && metricReportIntervalMs < 1000) {
            errors.add("指标上报间隔必须 >= 1000 毫秒");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry("dbsyncer.monitor.scheduler-pool-size", schedulerPoolSize));
        entries.add(new ConfigEntry("dbsyncer.monitor.cpu-memory-interval-ms", cpuMemoryIntervalMs));
        entries.add(new ConfigEntry("dbsyncer.monitor.health-check-interval-ms", healthCheckIntervalMs));
        entries.add(new ConfigEntry("dbsyncer.monitor.cleanup-interval-ms", cleanupIntervalMs));
        entries.add(new ConfigEntry("dbsyncer.monitor.metric-report-interval-ms", metricReportIntervalMs));
        entries.add(new ConfigEntry("dbsyncer.monitor.alert-cron", alertCron));
        return entries;
    }
}
