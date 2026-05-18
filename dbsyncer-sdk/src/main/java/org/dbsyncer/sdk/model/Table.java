package org.dbsyncer.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.enums.TableTypeEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/15 23:58
 */
public class Table {

    /**
     * 表名
     */
    private String name;

    /**
     * 表类型[TABLE、VIEW、MATERIALIZED VIEW]
     * {@link TableTypeEnum}
     */
    private String type;

    /**
     * 属性字段
     * 格式：[{"name":"ID","typeName":"INT","type":"4"},{"name":"NAME","typeName":"VARCHAR","type":"12"}]
     */
    private List<Field> column;

    /**
     * sql
     */
    private String sql;

    // 总数
    private long count;

    /**
     * 索引类型（ES）
     */
    private String indexType;

    /**
     * 不区分大小写的字段名 → Field 的 Map 缓存
     * 延迟构建，column 变更时失效
     */
    @JsonIgnore
    private transient Map<String, Field> columnMap;

    public Table() {
    }

    public Table(String name) {
        this(name, TableTypeEnum.TABLE.getCode());
    }

    public Table(String name, String type) {
        this(name, type, null, null, null);
    }

    public Table(String name, String type, List<Field> column, String sql, String indexType) {
        this.name = name;
        this.type = type;
        this.column = column;
        this.sql = sql;
        this.indexType = indexType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<Field> getColumn() {
        return column;
    }

    public Table setColumn(List<Field> column) {
        this.column = column;
        this.columnMap = null; // column 变更，缓存失效
        return this;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    /**
     * 获取不区分大小写的表名（用于匹配/查找）
     * @return 小写表名，null 时返回 null
     */
    @JsonIgnore
    public String nameIgnoreCase() {
        return name == null ? null : name.toLowerCase();
    }

    /**
     * 判断表名是否匹配（不区分大小写）
     * @param other 目标表名
     * @return 是否匹配
     */
    @JsonIgnore
    public boolean matchesName(String other) {
        return StringUtil.equalsIgnoreCase(this.name, other);
    }

    /**
     * 按字段名查找字段（不区分大小写）
     * 基于内部缓存 Map 进行 O(1) 查找
     *
     * @param name 字段名
     * @return 匹配的字段，未找到返回 null
     */
    @JsonIgnore
    public Field findColumnByName(String name) {
        return name == null ? null : getColumnMap().get(name.toLowerCase());
    }

    /**
     * 获取不区分大小写的字段名 → Field 的 Map
     * 懒加载缓存，column 变更时自动重建
     *
     * @return 小写字段名为 key 的 Map
     */
    @JsonIgnore
    public Map<String, Field> getColumnMap() {
        if (columnMap == null) {
            if (column == null || column.isEmpty()) {
                columnMap = new HashMap<>();
            } else {
                columnMap = new HashMap<>(column.size());
                for (Field f : column) {
                    columnMap.put(f.nameIgnoreCase(), f);
                }
            }
        }
        return columnMap;
    }

    @Override
    public Table clone() {
        List<Field> clonedColumns = null;
        if (column != null) {
            clonedColumns = new ArrayList<>(column);
        }
        Table cloned = new Table(name, type, clonedColumns, sql, indexType);
        cloned.columnMap = null; // 新对象独立构建缓存
        return cloned;
    }
}