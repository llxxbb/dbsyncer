/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.common.constant;

/**
 * 配置常量。
 * <p>
 * 从 dbsyncer-sdk 迁移到 dbsyncer-common，使底层模块可直接访问。
 *
 * @Author AE86
 * @Version 1.0.0
 * @Date 2019-11-16 22:14
 */
public class ConfigConstant {

    /**
     * 公共属性
     */
    public static final String CONFIG_MODEL_ID = "id";
    public static final String CONFIG_MODEL_NAME = "name";
    public static final String CONFIG_MODEL_TYPE = "type";
    public static final String CONFIG_MODEL_CREATE_TIME = "createTime";
    public static final String CONFIG_MODEL_UPDATE_TIME = "updateTime";
    public static final String CONFIG_MODEL_JSON = "json";

    /**
     * 日志属性
     */
    public static final String LOG_MODEL_ID = "modelId";
    public static final String LOG_TYPE = "logType";
    public static final String LOG_LEVEL = "logLevel";

    /**
     * 配置类型
     */
    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final String CONNECTOR = "connector";
    public static final String MAPPING = "mapping";
    public static final String TABLE_GROUP = "tableGroup";
    public static final String META = "meta";
    public static final String PROJECT_GROUP = "projectGroup";
    public static final String USER_GROUP = "userGroup";

    /**
     * 数据
     */
    public static final String DATA_SUCCESS = "success";
    public static final String DATA_TABLE_GROUP_ID = "tableGroupId";
    public static final String DATA_TARGET_TABLE_NAME = "targetTableName";
    public static final String DATA_EVENT = "event";
    public static final String DATA_ERROR = "error";

    /**
     * Binlog
     */
    public static final String BINLOG_DATA = "data";

    /**
     * 分组通知配置：未分配分组的 key（ADR-0035）
     */
    public static final String UNASSIGNED_GROUP_KEY = "__unassigned__";

}
