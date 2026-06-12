package org.dbsyncer.sdk.spi;

/**
 * 日志服务接口
 * 
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-21 23:18
 */
public interface LogService {

    /**
     * 记录日志
     * 
     * @param model 模型对象 (ConfigModel 或 null)
     * @param logType 日志类型 (INSERT/UPDATE/DELETE 等)
     * @param logLevel 日志级别 (INFO/WARN/ERROR)
     * @param msg 日志内容
     */
    void log(Object model, LogType logType, LogLevel logLevel, String msg);

    /**
     * 记录日志 (自动推导 LOG_LEVEL 为 INFO)
     */
    default void log(Object model, LogType logType, String msg) {
        log(model, logType, LogLevel.INFO, msg);
    }
}
