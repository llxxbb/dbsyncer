package org.dbsyncer.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * 主键列的 Field 对象缓存（懒加载）
     * 由 primaryKeys + column 计算得出，供 Connector update/delete/upsert 使用
     */
    @JsonIgnore
    private transient List<Field> pkFields;

    /**
     * 列名列表缓存（懒加载）
     * 由 column 计算得出，按字段定义顺序
     */
    @JsonIgnore
    private transient volatile List<String> columnNames;

    public MetaInfo() {
        super();
    }

    public MetaInfo(String name, String type, List<Field> column, String sql, String indexType,
            List<String> primaryKeys) {
        super(name, type, column, sql, indexType);
        this.primaryKeys = primaryKeys;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }

    public MetaInfo setPrimaryKeys(List<String> primaryKeys) {
        this.primaryKeys = primaryKeys;
        this.pkFields = null;
        return this;
    }

    @JsonIgnore
    public List<Field> getPkFields() {
        if (pkFields != null) {
            return pkFields;
        }

        if (CollectionUtils.isEmpty(primaryKeys)
                || CollectionUtils.isEmpty(getColumn())) {
            pkFields = Collections.emptyList();
            return pkFields;
        }

        List<Field> orderedPkFields = new ArrayList<>(primaryKeys.size());

        for (String pkName : primaryKeys) {
            Field matched = getColumn().stream()
                    .filter(field -> field != null && field.matchesName(pkName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "主键字段不存在，table=" + getName()
                                    + ", primaryKey=" + pkName
                                    + ", primaryKeys=" + primaryKeys));

            orderedPkFields.add(matched);
        }

        pkFields = Collections.unmodifiableList(orderedPkFields);
        return pkFields;
    }

    @Override
    public MetaInfo setColumn(List<Field> column) {
        super.setColumn(column);
        this.pkFields = null;
        this.columnNames = null;
        return this;
    }

    /**
     * 获取列名列表（按字段定义顺序）
     * 懒加载缓存，column 变更时自动失效
     */
    @JsonIgnore
    public List<String> getColumnNames() {
        if (columnNames == null && getColumn() != null) {
            columnNames = getColumn().stream()
                    .map(Field::getName)
                    .collect(Collectors.toList());
        }
        return columnNames;
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
            clonedColumns = new ArrayList<>(getColumn().size());
            for (Field field : getColumn()) {
                clonedColumns.add(field.clone());
            }
        }
        MetaInfo cloned = new MetaInfo(
                getName(),
                getType(),
                clonedColumns,
                getSql(),
                getIndexType(),
                primaryKeys != null ? new ArrayList<>(primaryKeys) : null);
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
