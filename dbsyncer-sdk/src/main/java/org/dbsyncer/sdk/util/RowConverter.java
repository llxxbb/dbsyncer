package org.dbsyncer.sdk.util;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.sdk.model.Field;

import java.util.*;

/**
 * 将 CDC 原始行数据（按列序排列的 List&lt;List&lt;Object&gt;&gt;）转换为业务语义格式（List&lt;Map&gt;）。
 *
 * <p>CDC 驱动返回的原始数据是位置格式：每行是一个 List，值按数据库列的物理顺序排列。
 * 下游消费者（Picker、asyncWrite 等）统一使用字段名 → 值的 Map 格式。
 * 本类负责两者之间的转换。
 *
 * <p>设计原则：
 * <ul>
 *   <li>单一职责：只做格式转换，不涉及字段映射、过滤等业务逻辑</li>
 *   <li>无状态：纯静态方法，可全局复用</li>
 * </ul>
 */
public final class RowConverter {

    private RowConverter() {
    }

    /**
     * 将 CDC 原始行数据转换为 Map 列表。
     *
     * <p>转换逻辑与 Picker.pickTargetData 内部的源数据转换完全一致，
     * 保证错误路径和正常路径的数据格式统一。
     *
     * @param sourceFields  需要提取的字段列表，用于确定输出 Map 的 key（保留原始大小写）
     * @param fieldIndexMap 字段名(不区分大小写) → 行数据中列索引的映射
     * @param rows          原始行数据（每行是一个 List&lt;Object&gt;，按列顺序存储值）
     * @return 转换后的 List&lt;Map&gt;，只包含 sourceFields 中声明的字段
     */
    public static List<Map<String, Object>> toListOfMaps(List<Field> sourceFields,
                                                          Map<String, Integer> fieldIndexMap,
                                                          List<List<Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> mapList = new ArrayList<>(rows.size());
        for (List<Object> row : rows) {
            Map<String, Object> map = rowToMap(sourceFields, fieldIndexMap, row);
            mapList.add(map);
        }
        return mapList;
    }

    /**
     * 单行转换：与 Picker.pickTargetData 内部的转换逻辑一致。
     */
    private static Map<String, Object> rowToMap(List<Field> sourceFields,
                                                 Map<String, Integer> fieldIndexMap,
                                                 List<Object> row) {
        Map<String, Object> map = new HashMap<>(sourceFields.size());
        for (Field field : sourceFields) {
            Integer index = fieldIndexMap.get(field.nameIgnoreCase());
            if (index != null && index < row.size()) {
                map.put(field.getName(), row.get(index));
            }
        }
        return map;
    }
}
