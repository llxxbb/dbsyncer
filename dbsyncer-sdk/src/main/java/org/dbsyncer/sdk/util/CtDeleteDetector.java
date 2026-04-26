package org.dbsyncer.sdk.util;

import org.dbsyncer.sdk.model.Field;

import java.util.List;
import java.util.Map;

/**
 * CT（Change Tracking）删除检测工具类
 * 
 * 用于判断 SQL Server CT 场景中，数据是否因物理删除导致整行 null。
 * 
 * 核心判定：null 字段数量 == 非主键字段数量
 * 
 * @author lazyman
 * @version 1.0.0
 * @date 2026-04-24
 */
public class CtDeleteDetector {

    /**
     * 判断是否为 CT 删除（整行 null）
     * 
     * CT 删除判定条件：所有非主键字段均为 null
     * null 数量 == 非主键字段数量
     * 
     * @param data 数据 Map
     * @param fields 字段列表
     * @return true 表示 CT 删除，false 表示其他情况
     */
    public static boolean isDeletedFromCT(Map<String, Object> data, List<Field> fields) {
        if (data == null || fields == null || fields.isEmpty()) {
            return false;
        }

        int nonPkCount = 0;
        int nullCount = 0;

        for (Field field : fields) {
            if (!field.isPk()) {
                nonPkCount++;
                if (data.get(field.getName()) == null) {
                    nullCount++;
                }
            }
        }

        return nullCount == nonPkCount;
    }
}
