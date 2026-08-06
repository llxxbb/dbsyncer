package org.dbsyncer.parser.pipeline;

import org.dbsyncer.parser.model.Picker;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.plugin.PluginContext;

import java.util.List;

public class SyncTask {
    private final Mapping mapping;
    private final TableGroup tableGroup;
    private final Meta meta;
    private final Picker picker;
    private final PluginContext context;
    private final List<String> columnNames;
    private final boolean isErrQueueRetry;

    public SyncTask(Mapping mapping, TableGroup tableGroup, Meta meta,
                    Picker picker, PluginContext context,
                    List<String> columnNames, boolean isErrQueueRetry) {
        this.mapping = mapping;
        this.tableGroup = tableGroup;
        this.meta = meta;
        this.picker = picker;
        this.context = context;
        this.columnNames = columnNames;
        this.isErrQueueRetry = isErrQueueRetry;
    }

    public Mapping getMapping() {
        return mapping;
    }

    public TableGroup getTableGroup() {
        return tableGroup;
    }

    public Meta getMeta() {
        return meta;
    }

    public Picker getPicker() {
        return picker;
    }

    public PluginContext getContext() {
        return context;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public boolean isErrQueueRetry() {
        return isErrQueueRetry;
    }
}
