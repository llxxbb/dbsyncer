package org.dbsyncer.sdk.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = FieldMapping.FieldMappingSerializer.class)
@JsonDeserialize(using = FieldMapping.FieldMappingDeserializer.class)
public class FieldMapping {

    private String sourceName;
    private String targetName;

    public FieldMapping() {
    }

    public FieldMapping(Field source, Field target) {
        this.sourceName = source != null ? source.getName() : null;
        this.targetName = target != null ? target.getName() : null;
    }

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

    public String getSource() {
        return sourceName;
    }

    public void setSource(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getTarget() {
        return targetName;
    }

    public void setTarget(String targetName) {
        this.targetName = targetName;
    }

    public boolean matchesSource(String name) {
        return org.dbsyncer.common.util.StringUtil.equalsIgnoreCase(this.sourceName, name);
    }

    public boolean matchesTarget(String name) {
        return org.dbsyncer.common.util.StringUtil.equalsIgnoreCase(this.targetName, name);
    }

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

    static class FieldMappingDeserializer extends JsonDeserializer<FieldMapping> {
        @Override
        public FieldMapping deserialize(JsonParser p, DeserializationContext ctxt) throws java.io.IOException {
            FieldMapping fm = new FieldMapping();
            JsonNode node = p.getCodec().readTree(p);

            JsonNode sourceNode = node.get("source");
            if (sourceNode != null) {
                if (sourceNode.isObject()) {
                    JsonNode nameNode = sourceNode.get("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        fm.sourceName = nameNode.asText();
                    }
                } else if (sourceNode.isTextual()) {
                    fm.sourceName = sourceNode.asText();
                }
            }

            JsonNode targetNode = node.get("target");
            if (targetNode != null) {
                if (targetNode.isObject()) {
                    JsonNode nameNode = targetNode.get("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        fm.targetName = nameNode.asText();
                    }
                } else if (targetNode.isTextual()) {
                    fm.targetName = targetNode.asText();
                }
            }

            return fm;
        }
    }
}
