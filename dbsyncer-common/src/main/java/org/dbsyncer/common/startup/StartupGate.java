package org.dbsyncer.common.startup;

/**
 * 启动门闸接口
 * 用于延迟开放端口，确保配置预加载完成后再允许外部访问
 */
public interface StartupGate {
    
    /**
     * 开放端口，允许外部访问
     * 应在配置预加载完成后调用
     */
    void open();
}
