/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.logging.log4j.util.Strings;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.model.Field;
import org.dbsyncer.sdk.model.Filter;
import org.dbsyncer.sdk.model.MetaInfo;
import org.dbsyncer.sdk.model.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/15 23:56
 */
public class TableGroup extends AbstractConfigModel {

    public TableGroup() {
        super.setType(ConfigConstant.TABLE_GROUP);
        super.setName(ConfigConstant.TABLE_GROUP);
    }

    @JsonIgnore
    public static final int Version = 6;
    public int currentVersion;
    @JsonIgnore
    public boolean isInit = false;

    // 排序索引
    private int index;

    // 驱动映射关系ID
    private String mappingId;

    // 数据源表
    private MetaInfo sourceTable;

    // 目标源表
    private MetaInfo targetTable;

    // 字段映射关系
    private List<FieldMapping> fieldMapping = new ArrayList<>();

    /**
     * 目标表主键配置（逗号分隔，按顺序）
     * 用于持久化用户自定义的主键顺序
     */
    private String targetTablePK;

    // 执行命令，例 SQL 等
    @JsonIgnore
    private Map<String, String> command = new HashMap<>();

    // 缓存的字段列表SQL片段（避免重复构建）
    @JsonIgnore
    private String cachedFieldListSql;

    // 缓存的主键列表字符串（避免重复构建）
    @JsonIgnore
    private String cachedPrimaryKeys;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getMappingId() {
        return mappingId;
    }

    public TableGroup setMappingId(String mappingId) {
        this.mappingId = mappingId;
        return this;
    }

    public MetaInfo getSourceTable() {
        return sourceTable;
    }

    public TableGroup setSourceTable(MetaInfo sourceTable) {
        this.sourceTable = sourceTable;
        return this;
    }

    public MetaInfo getTargetTable() {
        return targetTable;
    }

    public TableGroup setTargetTable(MetaInfo targetTable) {
        this.targetTable = targetTable;
        return this;
    }

    public List<FieldMapping> getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(List<FieldMapping> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    /**
     * 按 fieldMapping 顺序获取同步用的源字段列表。
     * 直接从 sourceTable 提取，不克隆。
     */
    @JsonIgnore
    public List<Field> getSyncSourceFields() {
        Table sourceTable = this.getSourceTable();
        if (sourceTable == null || CollectionUtils.isEmpty(this.getFieldMapping())) {
            return Collections.emptyList();
        }
        List<Field> result = new ArrayList<>(this.getFieldMapping().size());
        for (FieldMapping m : this.getFieldMapping()) {
            if (StringUtil.isNotBlank(m.getSourceName())) {
                Field sField = sourceTable.findColumnByName(m.getSourceName());
                if (sField != null) {
                    result.add(sField);
                }
            }
        }
        return result;
    }

    /**
     * 按 fieldMapping 顺序获取同步用的目标字段列表。
     * targetTable 无元数据时（如 Kafka），从源字段克隆，保留 pk 等属性。
     */
    @JsonIgnore
    public List<Field> getSyncTargetFields() {
        Table sourceTable = this.getSourceTable();
        Table targetTable = this.getTargetTable();
        List<FieldMapping> fieldMapping = this.getFieldMapping();
        if (CollectionUtils.isEmpty(fieldMapping)) {
            return Collections.emptyList();
        }
        Map<String, Field> sourceFieldCache = new HashMap<>();
        List<Field> result = new ArrayList<>(fieldMapping.size());
        for (FieldMapping m : fieldMapping) {
            if (StringUtil.isNotBlank(m.getTargetName())) {
                Field tField = null;
                if (targetTable != null) {
                    tField = targetTable.findColumnByName(m.getTargetName());
                }
                if (tField != null) {
                    result.add(tField);
                } else {
                    // 目标表无元数据（如 Kafka），从源字段克隆
                    if (StringUtil.isNotBlank(m.getSourceName()) && sourceTable != null) {
                        Field sField = sourceFieldCache.computeIfAbsent(m.getSourceName(),
                                k -> sourceTable.findColumnByName(k));
                        if (sField != null) {
                            tField = new Field(m.getTargetName(), sField.getTypeName(), sField.getType(), sField.isPk(),
                                    sField.getColumnSize(), sField.getRatio(), sField.getSrid());
                            result.add(tField);
                        }
                    }
                }
            }
        }
        // 处理转换配置中的自定义字段
        Set<String> added = result.stream().map(Field::nameIgnoreCase).collect(Collectors.toSet());
        List<Convert> convert = this.getConvert();
        if (!CollectionUtils.isEmpty(convert)) {
            for (Convert c : convert) {
                Field fm = c.getFieldMetadata();
                if (fm != null && !added.contains(fm.nameIgnoreCase())) {
                    result.add(fm);
                }
            }
        }
        return result;
    }

    public String getTargetTablePK() {
        return targetTablePK;
    }

    public TableGroup setTargetTablePK(String targetTablePK) {
        // 内部自动去重（大小写不敏感），保留首次出现顺序
        if (StringUtil.isBlank(targetTablePK)) {
            this.targetTablePK = null;
            return this;
        }
        String[] pks = StringUtil.split(targetTablePK, StringUtil.COMMA);
        Set<String> seen = new java.util.HashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String pk : pks) {
            String trimmed = pk.trim();
            if (trimmed.isEmpty()) continue;
            if (seen.add(trimmed.toLowerCase())) {
                if (sb.length() > 0) sb.append(',');
                sb.append(trimmed);
            }
        }
        this.targetTablePK = sb.length() > 0 ? sb.toString() : null;
        return this;
    }

    /**
     * 获取目标表主键列表（按 targetTablePK 定义的顺序）
     * 返回不可变列表，保证主键顺序不被意外修改
     */
    @JsonIgnore
    public List<String> getTargetTablePrimaryKeys() {
        if (StringUtil.isBlank(this.targetTablePK)) {
            return Collections.emptyList();
        }
        List<String> pks = new ArrayList<>();
        for (String pk : StringUtil.split(this.targetTablePK, StringUtil.COMMA)) {
            pks.add(pk.trim());
        }
        return Collections.unmodifiableList(pks);
    }

    /**
     * 判断主键是否已包含指定字段（不区分大小写）
     * @param name 字段名
     * @return 是否已包含
     */
    @JsonIgnore
    public boolean containsPrimaryKey(String name) {
        if (StringUtil.isBlank(this.targetTablePK) || name == null) {
            return false;
        }
        for (String pk : StringUtil.split(this.targetTablePK, StringUtil.COMMA)) {
            if (StringUtil.equalsIgnoreCase(pk.trim(), name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按源表主键顺序，从目标字段列表中解析出有序的主键 Field 对象。
     * 用于在 context 初始化阶段一次性计算，避免 update/delete 时每条数据重复查找。
     *
     * @param targetFields 目标字段列表（已按 fieldMapping 组装）
     * @return 有序主键 Field 列表，源表无主键或目标字段中找不到匹配时返回空列表
     */
    @JsonIgnore
    public List<Field> resolveTargetPkFields(List<Field> targetFields) {
        if (sourceTable == null || CollectionUtils.isEmpty(targetFields)) {
            return Collections.emptyList();
        }
        List<String> primaryKeys = sourceTable.getPrimaryKeys();
        if (CollectionUtils.isEmpty(primaryKeys)) {
            return Collections.emptyList();
        }
        Map<String, Field> fieldByName = new HashMap<>(targetFields.size());
        for (Field f : targetFields) {
            if (f != null && f.getName() != null) {
                fieldByName.put(f.getName().toLowerCase(), f);
            }
        }
        List<Field> pkFields = new ArrayList<>(primaryKeys.size());
        for (String pkName : primaryKeys) {
            Field f = fieldByName.get(pkName.toLowerCase());
            if (f != null) {
                pkFields.add(f);
            }
        }
        return pkFields;
    }

    /**
     * 添加主键（内部去重，不区分大小写）
     * @param name 字段名
     */
    @JsonIgnore
    public void addPrimaryKeyIfAbsent(String name) {
        if (name == null) {
            return;
        }
        if (containsPrimaryKey(name)) {
            return;
        }
        String currentPK = StringUtil.isBlank(this.targetTablePK) ? name : (this.targetTablePK + "," + name);
        this.targetTablePK = currentPK;
    }

    /**
     * 比较主键是否发生变化（不区分大小写）
     * @param newTargetTablePK 新的主键字符串（逗号分隔）
     * @return 是否发生变化
     */
    @JsonIgnore
    public boolean primaryKeyChanged(String newTargetTablePK) {
        String oldPK = this.targetTablePK;
        String newPK = StringUtil.isBlank(newTargetTablePK) ? null : newTargetTablePK.trim();
        return StringUtil.equalsIgnoreCase(oldPK, newPK) == false;
    }

    /**
     * 判断字段映射中是否包含某字段（不区分大小写，检查源和目标）
     * @param name 字段名
     * @return 是否包含
     */
    @JsonIgnore
    public boolean containsField(String name) {
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            return false;
        }
        for (FieldMapping fm : fieldMapping) {
            if (fm.matchesSource(name)) {
                return true;
            }
            if (fm.matchesTarget(name)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, String> getCommand() {
        return command;
    }

    public TableGroup setCommand(Map<String, String> command) {
        this.command = command;
        return this;
    }

    public String getCachedFieldListSql() {
        return cachedFieldListSql;
    }

    public TableGroup setCachedFieldListSql(String cachedFieldListSql) {
        this.cachedFieldListSql = cachedFieldListSql;
        return this;
    }

    public String getCachedPrimaryKeys() {
        return cachedPrimaryKeys;
    }

    public TableGroup setCachedPrimaryKeys(String cachedPrimaryKeys) {
        this.cachedPrimaryKeys = cachedPrimaryKeys;
        return this;
    }

    // 新增字段 - 流式处理状态管理
    private Object[] cursors; // 当前TableGroup的cursor
    private boolean fullCompleted; // 流式处理是否完成
    private String errorMessage; // 错误信息

    // 同步进度相关字段
    private long success; // 成功计数（全量+增量总和，用于兼容）
    private long fail; // 失败计数（全量+增量总和，用于兼容）
    private long total; // 总数（全量总数）
    private long estimatedTotal; // 预计总数（用于计算进度）
    private long incrementSuccess; // 增量同步成功数量
    private long incrementFail; // 增量同步失败数量
    private String status; // 同步状态（"正常"/"异常"）
    private long fullSuccess; // 全量同步成功数量
    private long fullFail; // 全量同步失败数量
    private long lastSyncTime; // 上次同步时间戳
    private double currentSpeed; // 当前同步速度（条/秒）
    private long lastBatchCount; // 上次批次处理的记录数

    public Object[] getCursors() {
        return cursors;
    }

    public void setCursors(Object[] cursors) {
        this.cursors = cursors;
    }

    public boolean isFullCompleted() {
        return fullCompleted;
    }

    public void setFullCompleted(boolean fullCompleted) {
        this.fullCompleted = fullCompleted;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getSuccess() {
        return success;
    }

    public void setSuccess(long success) {
        this.success = success;
    }

    public long getFail() {
        return fail;
    }

    public void setFail(long fail) {
        this.fail = fail;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getEstimatedTotal() {
        return estimatedTotal;
    }

    public void setEstimatedTotal(long estimatedTotal) {
        this.estimatedTotal = estimatedTotal;
    }

    public long getIncrementSuccess() {
        return incrementSuccess;
    }

    public void setIncrementSuccess(long incrementSuccess) {
        this.incrementSuccess = incrementSuccess;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getFullSuccess() {
        return fullSuccess;
    }

    public void setFullSuccess(long fullSuccess) {
        this.fullSuccess = fullSuccess;
    }

    public long getFullFail() {
        return fullFail;
    }

    public void setFullFail(long fullFail) {
        this.fullFail = fullFail;
    }

    public long getIncrementFail() {
        return incrementFail;
    }

    public void setIncrementFail(long incrementFail) {
        this.incrementFail = incrementFail;
    }

    public long getLastSyncTime() {
        return lastSyncTime;
    }

    public void setLastSyncTime(long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    public double getCurrentSpeed() {
        return currentSpeed;
    }

    public void setCurrentSpeed(double currentSpeed) {
        this.currentSpeed = currentSpeed;
    }

    public long getLastBatchCount() {
        return lastBatchCount;
    }

    public void setLastBatchCount(long lastBatchCount) {
        this.lastBatchCount = lastBatchCount;
    }

    @JsonIgnore
    public boolean hasError() {
        return Strings.isNotBlank(errorMessage);
    }

    /**
     * 清理错误状态
     */
    @JsonIgnore
    public void clear() {
        isInit = false;
        fullCompleted = false;
        cursors = null;
        this.errorMessage = null;
        // 重置同步进度
        this.success = 0;
        this.fail = 0;
        this.total = 0;
        this.estimatedTotal = 0;
        this.incrementSuccess = 0;
        this.incrementFail = 0;
        this.status = "正常";
        this.fullSuccess = 0;
        this.fullFail = 0;
        this.lastSyncTime = 0;
        this.currentSpeed = 0;
        this.lastBatchCount = 0;
    }

}