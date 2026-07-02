/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.ddl;

import net.sf.jsqlparser.JSQLParserException;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.config.DDLConfig;
import org.dbsyncer.sdk.spi.ConnectorService;

public interface DDLParser {

    DDLConfig parse(ConnectorService connectorService, TableGroup tableGroup, String sql) throws JSQLParserException;

    void refreshFiledMappings(TableGroup tableGroup, DDLConfig targetDDLConfig);
    
    /**
     * 将 TableGroup 的 params 复制到 DDLConfig 的 params 中
     * 这样 writerDDL 可以访问 mapping 配置的 params（如 topic）
     */
    default void copyParamsToDDLConfig(TableGroup tableGroup, DDLConfig ddlConfig) {
        if (tableGroup.getParams() != null && !tableGroup.getParams().isEmpty()) {
            ddlConfig.setParams(new java.util.LinkedHashMap<>(tableGroup.getParams()));
        }
    }
}
