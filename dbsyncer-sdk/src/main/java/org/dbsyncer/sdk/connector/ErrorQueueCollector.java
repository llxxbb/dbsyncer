package org.dbsyncer.sdk.connector;

import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;

import java.util.List;
import java.util.Map;

public interface ErrorQueueCollector {

    void handleFailData(Meta meta, TableGroup tableGroup, String targetTableName,
                        String event, List<Map> failData, String errorMsg);

    void handleDdlFailData(Meta meta, TableGroup tableGroup, String targetTableName,
                           DDLConfig ddlConfig, String errorMsg);

    void onRetrySuccess(Meta meta, TableGroup tableGroup);

    void alignCount(Meta meta);

}