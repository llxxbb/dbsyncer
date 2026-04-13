package org.dbsyncer.biz.vo;

import java.io.Serializable;
import java.util.List;

public class PrimaryKeyDifferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean hasDifference;
    private List<String> configuredPKs;
    private List<String> actualPKs;
    private List<String> addedPKs;
    private List<String> removedPKs;
    private String tableName;

    public boolean isHasDifference() {
        return hasDifference;
    }

    public void setHasDifference(boolean hasDifference) {
        this.hasDifference = hasDifference;
    }

    public List<String> getConfiguredPKs() {
        return configuredPKs;
    }

    public void setConfiguredPKs(List<String> configuredPKs) {
        this.configuredPKs = configuredPKs;
    }

    public List<String> getActualPKs() {
        return actualPKs;
    }

    public void setActualPKs(List<String> actualPKs) {
        this.actualPKs = actualPKs;
    }

    public List<String> getAddedPKs() {
        return addedPKs;
    }

    public void setAddedPKs(List<String> addedPKs) {
        this.addedPKs = addedPKs;
    }

    public List<String> getRemovedPKs() {
        return removedPKs;
    }

    public void setRemovedPKs(List<String> removedPKs) {
        this.removedPKs = removedPKs;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
