package org.dbsyncer.sdk.spi;

/**
 * 日志级别枚举
 * 
 * @author AE86
 * @version 2026.06.24
 */
public enum LogLevel {
    INFO("信息"),
    WARN("警告"),
    ERROR("错误");

    private final String levelName;

    LogLevel(String levelName) {
        this.levelName = levelName;
    }

    public String getLevelName() {
        return levelName;
    }
}
