package org.dbsyncer.sdk.connector;

import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.notify.NotifyType;

import java.util.List;
import java.util.Map;

public interface ErrorQueueCollector {

    void handleFailData(Mapping mapping, Meta meta, TableGroup tableGroup, String targetTableName,
                        String event, List<Map> failData, List<String> failReasons,
                        Exception e, boolean isErrQueueRetry) throws Exception;

    void handleDdlFailData(Mapping mapping, Meta meta, TableGroup tableGroup, String targetTableName,
                           DDLConfig ddlConfig, Exception e, boolean isErrQueueRetry) throws Exception;

    void alignCount(Meta meta) throws Exception;

    void closeTask(Meta meta, String targetTableName, String errorMsg,
            int failCount, NotifyType notifyType);

}