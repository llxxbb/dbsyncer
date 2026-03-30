package org.dbsyncer.biz.util;

import org.dbsyncer.biz.vo.FieldDiffItem;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.model.Field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段比较工具类
 * 用于比较源表和目标表的字段差异，包括新增字段、缺失字段、类型不匹配和长度不匹配
 */
public final class FieldComparisonUtil {

    /**
     * 字段比较结果类
     * 封装字段比较后的各类差异信息
     */
    public static final class FieldComparisonResult {
        private List<FieldDiffItem> addedFields = new ArrayList<>();
        private List<FieldDiffItem> missingFields = new ArrayList<>();
        private List<FieldDiffItem> typeMismatched = new ArrayList<>();
        private List<FieldDiffItem> lengthMismatched = new ArrayList<>();

        public List<FieldDiffItem> getAddedFields() {
            return addedFields;
        }

        public List<FieldDiffItem> getMissingFields() {
            return missingFields;
        }

        public List<FieldDiffItem> getTypeMismatched() {
            return typeMismatched;
        }

        public List<FieldDiffItem> getLengthMismatched() {
            return lengthMismatched;
        }

        /**
         * 判断是否存在字段差异
         * @return true-存在差异，false-无差异
         */
        public boolean hasDifference() {
            return !addedFields.isEmpty()
                    || !missingFields.isEmpty()
                    || !typeMismatched.isEmpty()
                    || !lengthMismatched.isEmpty();
        }
    }

    /**
     * 比较源表和目标表的字段差异
     * @param sourceFields 源表字段列表
     * @param targetFields 目标表字段列表
     * @return 字段比较结果，包含新增、缺失、类型不匹配和长度不匹配的字段信息
     */
    public static FieldComparisonResult compareFields(List<Field> sourceFields, List<Field> targetFields) {
        FieldComparisonResult result = new FieldComparisonResult();

        if (CollectionUtils.isEmpty(sourceFields) && CollectionUtils.isEmpty(targetFields)) {
            return result;
        }

        Map<String, Field> sourceFieldMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(sourceFields)) {
            sourceFields.forEach(f -> sourceFieldMap.put(f.getName().toLowerCase(), f));
        }

        Map<String, Field> targetFieldMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(targetFields)) {
            targetFields.forEach(f -> targetFieldMap.put(f.getName().toLowerCase(), f));
        }

        // 检查目标表多出的字段
        for (Field targetField : targetFields) {
            if (targetField == null || targetField.getName() == null) {
                continue;
            }
            String fieldNameLower = targetField.getName().toLowerCase();
            Field sourceField = sourceFieldMap.get(fieldNameLower);

            if (sourceField == null) {
                FieldDiffItem item = new FieldDiffItem();
                item.setFieldName(targetField.getName());
                item.setTargetType(targetField.getTypeName());
                item.setTargetLength(targetField.getColumnSize());
                item.setDiffType("ADDED");
                item.setDescription("目标表存在，源表不存在");
                result.addedFields.add(item);
            } else {
                // 检查类型是否匹配
                if (!isTypeMatch(sourceField, targetField)) {
                    FieldDiffItem item = new FieldDiffItem();
                    item.setFieldName(targetField.getName());
                    item.setSourceType(sourceField.getTypeName());
                    item.setTargetType(targetField.getTypeName());
                    item.setDiffType("TYPE_MISMATCH");
                    item.setDescription("字段类型不同");
                    result.typeMismatched.add(item);
                }

                // 检查VARCHAR长度
                if (isVarcharType(sourceField.getTypeName()) && isVarcharType(targetField.getTypeName())) {
                    Long sourceLength = sourceField.getColumnSize();
                    Long targetLength = targetField.getColumnSize();
                    if (sourceLength != null && targetLength != null && !sourceLength.equals(targetLength)) {
                        FieldDiffItem item = new FieldDiffItem();
                        item.setFieldName(targetField.getName());
                        item.setSourceLength(sourceLength);
                        item.setTargetLength(targetLength);
                        item.setDiffType("LENGTH_MISMATCH");
                        item.setDescription("源长度(" + sourceLength + ") ≠ 目标长度(" + targetLength + ")");
                        result.lengthMismatched.add(item);
                    }
                }
            }
        }

        // 检查目标表缺少的字段
        for (Field sourceField : sourceFields) {
            if (sourceField == null || sourceField.getName() == null) {
                continue;
            }
            String fieldNameLower = sourceField.getName().toLowerCase();
            if (!targetFieldMap.containsKey(fieldNameLower)) {
                FieldDiffItem item = new FieldDiffItem();
                item.setFieldName(sourceField.getName());
                item.setSourceType(sourceField.getTypeName());
                item.setSourceLength(sourceField.getColumnSize());
                item.setDiffType("MISSING");
                item.setDescription("源表存在，目标表不存在");
                result.missingFields.add(item);
            }
        }

        return result;
    }

    /**
     * 判断两个字段的类型是否匹配
     * @param sourceField 源字段
     * @param targetField 目标字段
     * @return true-类型匹配，false-类型不匹配
     */
    private static boolean isTypeMatch(Field sourceField, Field targetField) {
        String sourceType = normalizeType(sourceField.getTypeName());
        String targetType = normalizeType(targetField.getTypeName());
        return sourceType.equals(targetType);
    }

    /**
     * 标准化字段类型名称
     * 处理带精度的类型（如VARCHAR(255)）和带空格的类型（如INT IDENTITY）
     * @param typeName 原始类型名称
     * @return 标准化后的类型名称
     */
    private static String normalizeType(String typeName) {
        if (typeName == null) {
            return "";
        }
        String normalized = typeName.toUpperCase();
        // 处理带精度的类型，如 VARCHAR(255) -> VARCHAR
        int idx = normalized.indexOf('(');
        if (idx > 0) {
            normalized = normalized.substring(0, idx);
        }
        // 处理带空格的，如 INT IDENTITY -> INT
        idx = normalized.indexOf(' ');
        if (idx > 0) {
            normalized = normalized.substring(0, idx);
        }
        return normalized;
    }

    /**
     * 判断是否为字符串类型（VARCHAR或CHAR类型）
     * @param typeName 字段类型名称
     * @return true-是字符串类型，false-不是字符串类型
     */
    private static boolean isVarcharType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upperType = typeName.toUpperCase();
        return upperType.contains("VARCHAR") || upperType.contains("CHAR");
    }
}
