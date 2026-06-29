package org.dbsyncer.sdk.plugin;

import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.TableGroup;

import java.util.List;
import java.util.Map;

/**
 * 同步数据条目 ADR 18
 * 包含原始数据、配置模型及运行时上下文
 */
public class SyncRow {
    private Map<String, Object> data;
    private PluginContext context;
    private Mapping mapping;
    private TableGroup tableGroup;

    public SyncRow() {
    }

    public SyncRow(Map<String, Object> data, PluginContext context) {
        this.data = data;
        this.context = context;
    }

    public SyncRow(Map<String, Object> data, PluginContext context, Mapping mapping, TableGroup tableGroup) {
        this.data = data;
        this.context = context;
        this.mapping = mapping;
        this.tableGroup = tableGroup;
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

    public Mapping getMapping() {
        return mapping;
    }

    public void setMapping(Mapping mapping) {
        this.mapping = mapping;
    }

    public TableGroup getTableGroup() {
        return tableGroup;
    }

    public void setTableGroup(TableGroup tableGroup) {
        this.tableGroup = tableGroup;
    }

    public List<Field> getTargetFields() {
        return tableGroup != null ? tableGroup.getSyncTargetFields() : null;
    }

    public List<String> getPrimaryKeys() {
        return tableGroup != null ? tableGroup.getTargetTable().getPrimaryKeys() : null;
    }
}
