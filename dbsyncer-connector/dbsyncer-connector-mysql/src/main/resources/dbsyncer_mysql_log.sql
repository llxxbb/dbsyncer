-- ADR-0016: 日志表结构更新
-- v2 方案：使用 type + model_id 双字段
CREATE TABLE `dbsyncer_log`  (
  `ID` varchar(64) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '唯一 ID',
  `TYPE` varchar(24) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT 'system、user、connector、mapping、tableGroup、meta、projectGroup',
  `MODEL_ID` varchar(64) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '实体 ID',
  `LOG_TYPE` varchar(32) CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '日志类型：INSERT, UPDATE, DELETE, CONNECT 等',
  `LOG_LEVEL` varchar(10) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '日志级别：INFO, WARN, ERROR',
  `CREATE_TIME` bigint(0) NOT NULL COMMENT '创建时间',
  `JSON` mediumtext CHARACTER SET utf8 COLLATE utf8_bin NOT NULL COMMENT '日志内容',
  PRIMARY KEY (`ID`) USING BTREE,
  INDEX `IDX_MODEL_ID`(`MODEL_ID`) USING BTREE,
  INDEX `IDX_CREATE_TIME`(`CREATE_TIME`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_bin COMMENT = '操作日志表' ROW_FORMAT = Dynamic;

-- 数据清理
-- TRUNCATE TABLE `dbsyncer_log`;


