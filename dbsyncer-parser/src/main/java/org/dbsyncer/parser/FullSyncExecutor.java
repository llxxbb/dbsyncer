package org.dbsyncer.parser;

import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.model.MetaInfo;

import java.util.Map;

/**
 * 实现全量同步任务
 */
public interface FullSyncExecutor {

    /**
     * 获取表元信息
     *
     * @param connectorId
     * @param tableName
     * @return
     */
    MetaInfo getMetaInfo(String connectorId, String tableName) throws Exception;

    /**
     * 获取总数
     *
     * @param connectorId
     * @param command
     * @return
     */
    long getCount(String connectorId, Map<String, String> command) throws Exception;

    /**
     * TableGroup流式处理
     *
     * @param metaId
     * @param tableGroup
     * @param mapping
     */
    void executeTableGroup(String metaId, TableGroup tableGroup, Mapping mapping) throws Exception;

}