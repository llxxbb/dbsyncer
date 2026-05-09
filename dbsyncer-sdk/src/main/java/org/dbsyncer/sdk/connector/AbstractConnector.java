/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.schema.SchemaResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public abstract class AbstractConnector {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 转换字段值
     *
     * @param context
     * @param targetResolver
     */
    public void convertProcessBeforeWriter(PluginContext context, SchemaResolver targetResolver) {
        if (CollectionUtils.isEmpty(context.getTargetFields()) || CollectionUtils.isEmpty(context.getTargetList())) {
            return;
        }

        for (Map row : context.getTargetList()) {
            for (Field f : context.getTargetFields()) {
                if (null == f) {
                    continue;
                }
                try {
                    row.computeIfPresent(f.getName(), (k, v) -> targetResolver.convert(v, f));
                } catch (Exception e) {
                    logger.error(String.format("convert value error: (%s, %s, %s)", context.getTargetTableName(), f.getName(), row.get(f.getName())), e);
                    throw new SdkException(e);
                }
            }
        }
    }
}
