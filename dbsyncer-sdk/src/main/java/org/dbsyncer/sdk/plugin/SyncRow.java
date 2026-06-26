package org.dbsyncer.sdk.plugin;

import java.util.Map;

/**
 * 同步数据条目 ADR 18
 * 包含原始数据及其相关的元数据上下文
 */
public class SyncRow {
    private Map<String, Object> data;
    private PluginContext context;

    public SyncRow() {
    }

    public SyncRow(Map<String, Object> data, PluginContext context) {
        this.data = data;
        this.context = context;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public PluginContext getContext() {
        return context;
    }

    public void setContext(PluginContext context) {
        this.context = context;
    }
}
