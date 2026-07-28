/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.sdk.connector.database.sql.impl;

import org.dbsyncer.sdk.connector.database.sql.SqlTemplate;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.schema.SyncConverter;

/**
 * 抽象SQL模板实现
 * 提供SchemaResolver支持的基础实现
 */
public abstract class AbstractSqlTemplate implements SqlTemplate {

    protected SyncConverter schemaResolver;

    public AbstractSqlTemplate(SyncConverter schemaResolver) {
        this.schemaResolver = schemaResolver;
    }

    public String convertToDatabaseType(Field column) {
        return schemaResolver.toTargetType(column).getTypeName();
    }
}