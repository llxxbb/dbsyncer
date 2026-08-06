package org.dbsyncer.sdk.connector;

import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.notify.NotifyType;

import java.util.List;
import java.util.Map;

public interface ErrorQueueCollector {

    void handleFailData(Meta meta, TableGroup tableGroup, String targetTableName,
                        String event, List<Map> failData, String errorMsg);

    void handleDdlFailData(Meta meta, TableGroup tableGroup, String targetTableName,
                           DDLConfig ddlConfig, String errorMsg);

    void alignCount(Meta meta) throws Exception;

    void closeTask(Meta meta, String targetTableName, String errorMsg,
            int failCount, NotifyType notifyType);

}