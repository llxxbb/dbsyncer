package org.dbsyncer.biz.vo;

public class FieldDiffFixItem extends FieldDiffItem {

    private static final long serialVersionUID = 1L;

    private String id;

    private String operation;

    private String sql;

    private boolean selected = true;

    public FieldDiffFixItem() {
    }

    public FieldDiffFixItem(String fieldName, String diffType, String operation, String description) {
        super();
        setFieldName(fieldName);
        setDiffType(diffType);
        setDescription(description);
        this.id = fieldName + "_" + diffType;
        this.operation = operation;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public String toString() {
        return "FieldDiffFixItem{" +
                "fieldName='" + getFieldName() + '\'' +
                ", diffType='" + getDiffType() + '\'' +
                ", operation='" + operation + '\'' +
                ", sql='" + sql + '\'' +
                ", description='" + getDescription() + '\'' +
                '}';
    }
}
