package org.dbsyncer.parser.pipeline;

import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.util.ConvertUtil;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.connector.ErrorQueueCollector;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SyncPipeline {

    private static final Logger logger = LoggerFactory.getLogger(SyncPipeline.class);


    private final ConnectorFactory connectorFactory;
    private final CounterService counterService;
    private final ErrorQueueCollector errorQueueCollector;

    public SyncPipeline(ConnectorFactory connectorFactory,
            CounterService counterService,
            ErrorQueueCollector errorQueueCollector) {
        this.connectorFactory = connectorFactory;
        this.counterService = counterService;
        this.errorQueueCollector = errorQueueCollector;
    }

    public void execute(SyncTask task, List<Map> source, List<Field> sourceFields,
            Map<String, Integer> fieldIndexMap, boolean enableFilter,
            List<List<Object>> rows, List<List<Object>> beforeRows) {
        List<SyncRow> target = null;
        try {
            if (sourceFields == null) {
                target = task.getPicker().pickTargetData(source, task.getContext());
            } else {
                target = task.getPicker().pickTargetData(sourceFields, fieldIndexMap,
                        enableFilter, rows, beforeRows, task.getContext());
            }
            enrichRows(target, task);
            ConvertUtil.convert(task.getTableGroup().getConvert(), target);
            connectorFactory.writeBatch(target, task.getMeta(), task.getMapping(),
                    task.getTableGroup(), counterService, errorQueueCollector, task.isErrQueueRetry());
        } catch (Exception e) {
            logger.error("同步异常！", e);
            if (target == null) {
                errorQueueCollector.closeTask(task.getMeta(), task.getTableGroup().getTargetTable().getName(),
                        e.getMessage(), 0, NotifyType.TASK_INTERRUPTED);
                return;
            }
            String event = target.get(0).getContext().getEvent();
            List<Map> failData = target.stream().map(SyncRow::getSourceData).collect(Collectors.toList());
            errorQueueCollector.handleFailData(task.getMapping(), task.getMeta(), task.getTableGroup(),
                    task.getTableGroup().getTargetTable().getName(), event, failData, e.getMessage(),
                    task.isErrQueueRetry());
        }
    }

    private void enrichRows(List<SyncRow> rows, SyncTask task) {
        for (SyncRow row : rows) {
            row.setMapping(task.getMapping());
            row.setTableGroup(task.getTableGroup());
            if (task.getColumnNames() != null) {
                row.setColumnNames(task.getColumnNames());
            }
        }
    }
}
