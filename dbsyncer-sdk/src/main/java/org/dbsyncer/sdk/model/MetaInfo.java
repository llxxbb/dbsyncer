package org.dbsyncer.sdk.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 连接器详细信息（包含主键）
 *
 * @author AE86
 * @ClassName: MetaInfo
 * @Description: 继承 Table，扩展主键列表信息
 * @date: 2017年7月20日 下午3:37:59
 */
public class MetaInfo extends Table {

    /**
     * 主键列表（按数据库定义的顺序）
     * 用于保持复合主键的正确顺序
     */
    private List<String> primaryKeys;

    public MetaInfo() {
        super();
    }

    public MetaInfo(String name, String type, List<Field> column, String sql, String indexType, List<String> primaryKeys) {
        super(name, type, column, sql, indexType);
        this.primaryKeys = primaryKeys;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public MetaInfo setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
        return this;
    }

    @Override
    public MetaInfo setColumn(List<Field> column) {
        super.setColumn(column);
        return this;
    }

    @Override
    public MetaInfo setIndexType(String indexType) {
        super.setIndexType(indexType);
        return this;
    }

    @Override
    public MetaInfo setSql(String sql) {
        super.setSql(sql);
        return this;
    }

    @Override
    public MetaInfo clone() {
        List<Field> clonedColumns = null;
        if (getColumn() != null) {
            clonedColumns = new ArrayList<>(getColumn());
        }
        MetaInfo cloned = new MetaInfo(
            getName(),
            getType(),
            clonedColumns,
            getSql(),
            getIndexType(),
            primaryKeys != null ? new ArrayList<>(primaryKeys) : null
        );
        cloned.setCount(getCount());
        return cloned;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("MetaInfo{")
            .append("name=").append(getName())
            .append(", type=").append(getType())
            .append(", primaryKeys=").append(primaryKeys)
            .append(", column=").append(getColumn())
            .append('}').toString();
    }
}
