package org.dbsyncer.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.sdk.model.Field;

/**
 * 字段转换
 */
public class Convert {

    /**
     * 转换器实例 ID（前端自动生成）
     * 格式：纯数字，如：0, 1, 2
     */
    private String id;

    /**
     * 字段名称
     */
    private String name;

    /**
     * 转换名称
     * @see ConvertEnum
     */
    private String convertName;

    /**
     * 转换方式
     *
     * @see ConvertEnum
     */
    private String convertCode;

    /**
     * 转换参数
     *
     * @see ConvertEnum
     */
    private String args;

    /**
     * 是否根转换器（新增的转换器为 true）
     */
    private boolean isRoot;

    /**
     * 字段元数据（自定义字段时填充）
     * 包含字段类型、长度、精度、是否允许为空、注释等信息
     */
    private Field fieldMetadata;

    /**
     * 模板解析结果缓存（仅根转换器使用）
     * <p>
     * 注意：此缓存仅在通过 TemplateHandler 调用时生效
     * transient: 不参与序列化，配置变更后自动失效
     */
    @JsonIgnore
    private transient ParseResult parseResultCache;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConvertName() {
        return convertName;
    }

    public void setConvertName(String convertName) {
        this.convertName = convertName;
    }

    public String getConvertCode() {
        return convertCode;
    }

    public void setConvertCode(String convertCode) {
        this.convertCode = convertCode;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public void setRoot(boolean isRoot) {
        this.isRoot = isRoot;
    }

    public Field getFieldMetadata() {
        return fieldMetadata;
    }

    public void setFieldMetadata(Field fieldMetadata) {
        this.fieldMetadata = fieldMetadata;
    }

    public ParseResult getParseResultCache() {
        return parseResultCache;
    }

    public void setParseResultCache(ParseResult parseResultCache) {
        this.parseResultCache = parseResultCache;
    }
}
