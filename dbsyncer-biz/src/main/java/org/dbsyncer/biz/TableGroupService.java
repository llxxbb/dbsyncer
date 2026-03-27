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
     * @param id           TableGroup ID
     * @param fixDirection 修复方向: "TARGET" 或 "SOURCE"
     * @return 修复 SQL 预览信息
     */
    FieldDiffFixVO getFieldDiffFixPreview(String id, String fixDirection) throws Exception;

    /**
     * 执行字段差异修复（支持选择性修复）
     *
     * @param id           TableGroup ID
     * @param fixDirection 修复方向: "TARGET" 或 "SOURCE"
     * @param selectedIds  选中的差异项 ID 列表（为空表示全选）
     * @return 执行结果消息
     */
    String executeFieldDiffFix(String id, String fixDirection, List<String> selectedIds) throws Exception;
}