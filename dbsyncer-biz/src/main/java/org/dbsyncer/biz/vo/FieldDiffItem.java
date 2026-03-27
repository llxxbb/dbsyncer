package org.dbsyncer.biz.vo;

import java.io.Serializable;

public class FieldDiffItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String fieldName;

    private String sourceType;

    private String targetType;

    private Long sourceLength;

    private Long targetLength;

    private String diffType;

    private String description;

    public FieldDiffItem() {
    }

    public FieldDiffItem(String fieldName, String diffType, String description) {
        this.fieldName = fieldName;
        this.diffType = diffType;
        this.description = description;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getSourceLength() {
        return sourceLength;
    }

    public void setSourceLength(Long sourceLength) {
        this.sourceLength = sourceLength;
    }

    public Long getTargetLength() {
        return targetLength;
    }

    public void setTargetLength(Long targetLength) {
        this.targetLength = targetLength;
    }

    public String getDiffType() {
        return diffType;
    }

    public void setDiffType(String diffType) {
        this.diffType = diffType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "FieldDiffItem{" +
                "fieldName='" + fieldName + '\'' +
                ", sourceType='" + sourceType + '\'' +
                ", targetType='" + targetType + '\'' +
                ", sourceLength=" + sourceLength +
                ", targetLength=" + targetLength +
                ", diffType='" + diffType + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}
