package org.dbsyncer.parser.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.dbsyncer.sdk.model.Field;

/**
 * 字段映射关系 — ADR-0011 简化版
 * <p>
 * 只存储字段名字符串，不再持有完整 Field 对象。
 * 字段元数据在运行时通过 Table.findColumnByName() 动态获取。
 *
 * @author AE86
 * @version 2.0.0
 * @date 2020/01/16 15:20
 */
@JsonSerialize(using = FieldMapping.FieldMappingSerializer.class)
@JsonDeserialize(using = FieldMapping.FieldMappingDeserializer.class)
public class FieldMapping {

    // ========== 新结构：只存字段名 ==========
    private String sourceName;
    private String targetName;

    public FieldMapping() {
    }

    /**
     * 旧构造函数保留（内部调用），从 Field 对象提取字段名
     */
    public FieldMapping(Field source, Field target) {
        this.sourceName = source != null ? source.getName() : null;
        this.targetName = target != null ? target.getName() : null;
    }

    /**
     * 新构造函数：直接存字段名
     */
    public FieldMapping(String sourceName, String targetName) {
        this.sourceName = sourceName;
        this.targetName = targetName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    // ========== 兼容方法：保持 getFieldMapping().getSource() 等调用可用 ==========

    /**
     * 兼容旧调用：返回源字段名（等价于 getSourceName）
     */
    public String getSource() {
        return sourceName;
    }

    public void setSource(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * 兼容旧调用：返回目标字段名（等价于 getTargetName）
     */
    public String getTarget() {
        return targetName;
    }

    public void setTarget(String targetName) {
        this.targetName = targetName;
    }

    // ========== 匹配方法：统一字段名比较入口 ==========

    /**
     * 判断此映射的源字段名是否匹配给定名称（不区分大小写）
     */
    public boolean matchesSource(String name) {
        return org.dbsyncer.common.util.StringUtil.equalsIgnoreCase(this.sourceName, name);
    }

    /**
     * 判断此映射的目标字段名是否匹配给定名称（不区分大小写）
     */
    public boolean matchesTarget(String name) {
        return org.dbsyncer.common.util.StringUtil.equalsIgnoreCase(this.targetName, name);
    }

    // ========== 序列化/反序列化 ==========

    /**
     * 自定义序列化器：输出 {"source":"name","target":"name"}
     */
    static class FieldMappingSerializer extends com.fasterxml.jackson.databind.JsonSerializer<FieldMapping> {
        @Override
        public void serialize(FieldMapping value, com.fasterxml.jackson.core.JsonGenerator gen,
                              com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
            gen.writeStartObject();
            if (value.sourceName != null) {
                gen.writeStringField("source", value.sourceName);
            }
            if (value.targetName != null) {
                gen.writeStringField("target", value.targetName);
            }
            gen.writeEndObject();
        }
    }

    /**
     * 自定义反序列化器：兼容旧格式 {"source":{"name":"id",...}} 和新格式 {"source":"id"}
     */
    static class FieldMappingDeserializer extends JsonDeserializer<FieldMapping> {
        @Override
        public FieldMapping deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
            FieldMapping fm = new FieldMapping();
            JsonNode node = p.getCodec().readTree(p);

            JsonNode sourceNode = node.get("source");
            if (sourceNode != null) {
                if (sourceNode.isObject()) {
                    // 旧格式：{"source":{"name":"id","typeName":"INT",...}}
                    JsonNode nameNode = sourceNode.get("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        fm.sourceName = nameNode.asText();
                    }
                } else if (sourceNode.isTextual()) {
                    // 新格式：{"source":"id"}
                    fm.sourceName = sourceNode.asText();
                }
            }

            JsonNode targetNode = node.get("target");
            if (targetNode != null) {
                if (targetNode.isObject()) {
                    // 旧格式
                    JsonNode nameNode = targetNode.get("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        fm.targetName = nameNode.asText();
                    }
                } else if (targetNode.isTextual()) {
                    // 新格式
                    fm.targetName = targetNode.asText();
                }
            }

            return fm;
        }
    }
}
