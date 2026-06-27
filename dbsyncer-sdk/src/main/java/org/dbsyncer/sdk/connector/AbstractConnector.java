/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.plugin.SyncRow;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class AbstractConnector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 写入前类型转换（通过 SyncRow 直接访问数据，无需再经 context 绕一圈）
     */
    public void convertProcessBeforeWriter(List<SyncRow> syncRows, SchemaResolver targetResolver) {
        if (CollectionUtils.isEmpty(syncRows)) {
            return;
        }
        List<Field> targetFields = syncRows.get(0).getTargetFields();
        if (CollectionUtils.isEmpty(targetFields)) {
            return;
        }
        String targetTableName = syncRows.get(0).getTableGroup() != null
                ? syncRows.get(0).getTableGroup().getTargetTable().getName() : "";

        for (SyncRow row : syncRows) {
            for (Field f : targetFields) {
                if (null == f) {
                    continue;
                }
                try {
                    row.getData().computeIfPresent(f.getName(), (k, v) -> targetResolver.convert(v, f));
                } catch (Exception e) {
                    logger.error(String.format("convert value error: (%s, %s, %s)",
                            targetTableName, f.getName(), row.getData().get(f.getName())), e);
                    throw new SdkException(e);
                }
            }
        }
    }
}
