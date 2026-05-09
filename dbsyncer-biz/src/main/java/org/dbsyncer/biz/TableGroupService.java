/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz;

import org.dbsyncer.biz.vo.FieldDiffFixVO;
import org.dbsyncer.biz.vo.FieldDifferenceVO;
import org.dbsyncer.parser.model.TableGroup;

import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/11/27 23:14
 */
public interface TableGroupService {

    /**
     * 新增表关系
     *
     * @param params
     */
    String add(Map<String, String> params) throws Exception;

    /**
     * 修改表关系
     *
     * @param params
     */
    String edit(Map<String, String> params) throws Exception;

    /**
     * 刷新表字段
     *
     * @param id
     */
    String refreshFields(String id) throws Exception;

    /**
     * 删除表关系
     *
     * @param mappingId
     * @param ids
     */
    boolean remove(String mappingId, String ids) throws Exception;

    /**
     * 获取表关系
     *
     * @param id
     * @return
     */
    TableGroup getTableGroup(String id) throws Exception;

    /**
     * 获取所有表关系
     *
     * @param mappingId
     * @return
     */
    List<TableGroup> getTableGroupAll(String mappingId) throws Exception;

    /**
     * 重置选中的表映射关系
     *
     * @param mappingId      驱动ID
     * @param tableGroupIds  表映射关系ID列表（逗号分隔）
     * @param truncateTarget 是否清空目标源表
     * @return 重置结果消息
     */
    String resetTableGroups(String mappingId, String tableGroupIds, boolean truncateTarget) throws Exception;

    /**
     * 获取字段差异
     *
     * @param id TableGroup ID
     * @return 字段差异信息
     */
    FieldDifferenceVO getFieldDifference(String id) throws Exception;

    /**
     * 获取字段差异修复 SQL 预览
     *
     * @param id TableGroup ID
     * @return 修复 SQL 预览信息
     */
    FieldDiffFixVO getFieldDiffFixPreview(String id) throws Exception;

    /**
     * 执行字段差异修复（支持选择性修复）
     *
     * @param id          TableGroup ID
     * @param selectedIds 选中的差异项 ID 列表（为空表示全选）
     * @return 执行结果消息
     */
    String executeFieldDiffFix(String id, List<String> selectedIds) throws Exception;

    /**
     * 检查单个目标表是否存在。如果目标连接器不支持创建表（如 Kafka），则跳过检查。
     * 内部封装了：supportsCreateTable 判断 + getMetaInfo + 构建 missingTables Map。
     *
     * @param mappingId        Mapping ID
     * @param tableGroup       表映射
     * @param targetTableName  目标表名（可覆盖 tableGroup 中的值）
     * @param targetTablePK    主键配置（可覆盖）
     * @return null 表示表存在或跳过检查；非 null 表示表不存在的详细信息（Map）
     */
    Map<String, String> checkTargetTableExists(String mappingId, TableGroup tableGroup,
                                                String targetTableName, String targetTablePK) throws Exception;
}