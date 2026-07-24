package org.dbsyncer.parser;

import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.TableGroup;

import java.util.Map;

/**
 * 实现全量同步任务
 */
public interface FullSyncExecutor {

    /**
     * TableGroup流式处理
     *
     * @param metaId
     * @param tableGroup
     * @param mapping
     */
    void executeTableGroup(String metaId, TableGroup tableGroup, Mapping mapping) throws Exception;

}