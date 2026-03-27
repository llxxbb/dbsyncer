package org.dbsyncer.biz.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FieldDiffFixVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tableGroupId;

    private String fixDirection;

    private List<String> sqlStatements = new ArrayList<>();

    private List<FieldDiffFixItem> items = new ArrayList<>();

    private boolean hasSql;

    private String warning;

    private String sourceTableName;

    private String targetTableName;

    public String getTableGroupId() {
        return tableGroupId;
    }

    public void setTableGroupId(String tableGroupId) {
        this.tableGroupId = tableGroupId;
    }

    public String getFixDirection() {
        return fixDirection;
    }

    public void setFixDirection(String fixDirection) {
        this.fixDirection = fixDirection;
    }

    public List<String> getSqlStatements() {
        return sqlStatements;
    }

    public void setSqlStatements(List<String> sqlStatements) {
        this.sqlStatements = sqlStatements;
    }

    public List<FieldDiffFixItem> getItems() {
        return items;
    }

    public void setItems(List<FieldDiffFixItem> items) {
        this.items = items;
    }

    public boolean isHasSql() {
        return hasSql;
    }

    public void setHasSql(boolean hasSql) {
        this.hasSql = hasSql;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public void setSourceTableName(String sourceTableName) {
        this.sourceTableName = sourceTableName;
    }

    public String getTargetTableName() {
        return targetTableName;
    }

    public void setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
    }

    @Override
    public String toString() {
        return "FieldDiffFixVO{" +
                "tableGroupId='" + tableGroupId + '\'' +
                ", fixDirection='" + fixDirection + '\'' +
                ", sqlStatements=" + sqlStatements.size() +
                ", items=" + items.size() +
                ", hasSql=" + hasSql +
                ", warning='" + warning + '\'' +
                '}';
    }
}
