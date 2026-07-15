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
        this.pkFields = null;
        return this;
    }

    @JsonIgnore
    public List<Field> getPkFields() {
        if (pkFields == null) {
            if (CollectionUtils.isEmpty(primaryKeys) || CollectionUtils.isEmpty(getColumn())) {
                pkFields = Collections.emptyList();
            } else {
                pkFields = primaryKeys.stream()
                        .map(pkName -> getColumn().stream()
                                .filter(f -> f != null && pkName.equals(f.getName()))
                                .findFirst()
                                .orElse(null))
                        .filter(f -> f != null)
                        .collect(Collectors.toList());
            }
        }
        return pkFields;
    }

    @Override
    public MetaInfo setColumn(List<Field> column) {
        super.setColumn(column);
        this.pkFields = null;
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
