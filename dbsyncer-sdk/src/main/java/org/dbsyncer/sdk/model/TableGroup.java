package org.dbsyncer.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.constant.ConfigConstant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TableGroup extends AbstractConfigModel {

    public static final String STATUS_NORMAL = "正常";
    public static final String STATUS_ERROR = "异常";

    public TableGroup() {
        super.setType(ConfigConstant.TABLE_GROUP);
        super.setName(ConfigConstant.TABLE_GROUP);
    }

    @JsonIgnore
    public static final int Version = 8;
    public int currentVersion;
    @JsonIgnore
    public boolean isInit = false;

    private int index;
    private String mappingId;

    // 缓存字段（非持久化，用于避免反复构建 SQL 片段）
    @JsonIgnore
    private transient String cachedFieldListSql;

    @JsonIgnore
    private transient String cachedPrimaryKeys;

    @JsonIgnore
    private Map<String, String> command = new HashMap<>();
    private MetaInfo sourceTable;
    private MetaInfo targetTable;
    private List<FieldMapping> fieldMapping = new ArrayList<>();
    private Object[] cursors;

    // 同步信息
    private volatile long success;
    private volatile long fail;
    private volatile long total;
    private volatile long estimatedTotal;
    private volatile long incrementSuccess;
    private volatile long incrementFail;
    private volatile String status = STATUS_NORMAL;
    private volatile long fullSuccess;
    private volatile long fullFail;
    private volatile long lastSyncTime;
    private volatile double currentSpeed;
    private String errorMessage;
    private boolean fullCompleted;

    @JsonIgnore
    public List<Field> getSyncSourceFields() {
        if (CollectionUtils.isEmpty(fieldMapping)) {
            return Collections.emptyList();
        }
        List<Field> result = new ArrayList<>(fieldMapping.size());
        for (FieldMapping m : fieldMapping) {
            if (sourceTable != null) {
                Field field = sourceTable.findColumnByName(m.getSourceName());
                if (field != null) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    @JsonIgnore
    public List<Field> getSyncTargetFields() {
        if (CollectionUtils.isEmpty(fieldMapping)) {
            return Collections.emptyList();
        }
        List<FieldMapping> fieldMapping = this.getFieldMapping();
        List<Field> result = new ArrayList<>(fieldMapping.size());
        for (FieldMapping m : fieldMapping) {
            if (targetTable != null) {
                Field field = targetTable.findColumnByName(m.getTargetName());
                if (field != null) {
                    result.add(field);
                } else if (StringUtil.isNotBlank(m.getTargetName())) {
                    // 目标表未定义该字段（如 Kafka 等无 Schema 的目标端），
                    // 使用 fieldMapping 的 targetName 创建占位 Field，
                    // 保证 SyncRow 包含所有映射字段，避免非 converter 字段丢失
                    Field placeholder = new Field();
                    placeholder.setName(m.getTargetName());
                    result.add(placeholder);
                }
            }
        }
        return result;
    }

    @JsonIgnore
    public Set<String> getSyncTargetFieldNameSet() {
        if (CollectionUtils.isEmpty(fieldMapping)) {
            return Collections.emptySet();
        }
        Set<String> fieldNameSet = new HashSet<>(fieldMapping.size());
        for (FieldMapping fm : fieldMapping) {
            fieldNameSet.add(fm.getTargetName());
        }
        return fieldNameSet;
    }

    @JsonIgnore
    public boolean hasError() {
        return StringUtil.isNotBlank(errorMessage);
    }

    @JsonIgnore
    public void clear() {
        isInit = false;
        fullCompleted = false;
        cursors = null;
        this.errorMessage = null;
        this.success = 0;
        this.fail = 0;
        this.total = 0;
        this.estimatedTotal = 0;
        this.incrementSuccess = 0;
        this.incrementFail = 0;
        this.status = STATUS_NORMAL;
        this.fullSuccess = 0;
        this.fullFail = 0;
        this.lastSyncTime = 0;
        this.currentSpeed = 0;
    }

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

    @JsonIgnore
    public Map<String, String> getCommand() {
        return command;
    }

    @JsonIgnore
    public void setCommand(Map<String, String> command) {
        this.command = command;
    }

    public MetaInfo getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(MetaInfo sourceTable) {
        this.sourceTable = sourceTable;
    }

    public MetaInfo getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(MetaInfo targetTable) {
        this.targetTable = targetTable;
    }

    public List<FieldMapping> getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(List<FieldMapping> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public Object[] getCursors() {
        return cursors;
    }

    public void setCursors(Object[] cursors) {
        this.cursors = cursors;
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

    public long getIncrementFail() {
        return incrementFail;
    }

    public void setIncrementFail(long incrementFail) {
        this.incrementFail = incrementFail;
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isFullCompleted() {
        return fullCompleted;
    }

    public void setFullCompleted(boolean fullCompleted) {
        this.fullCompleted = fullCompleted;
    }

    @JsonIgnore
    public String getCachedFieldListSql() {
        return cachedFieldListSql;
    }

    @JsonIgnore
    public void setCachedFieldListSql(String cachedFieldListSql) {
        this.cachedFieldListSql = cachedFieldListSql;
    }

    @JsonIgnore
    public String getCachedPrimaryKeys() {
        return cachedPrimaryKeys;
    }

    @JsonIgnore
    public void setCachedPrimaryKeys(String cachedPrimaryKeys) {
        this.cachedPrimaryKeys = cachedPrimaryKeys;
    }

}
