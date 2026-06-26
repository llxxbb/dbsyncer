/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.sdk.util;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.SdkException;
import org.dbsyncer.sdk.model.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Types;
import java.util.*;

public abstract class PrimaryKeyUtil {

    private static final Logger logger = LoggerFactory.getLogger(PrimaryKeyUtil.class);

    /**
     * 游标主键必须为数字类型，否则会导致分页失效
     *
     * @param fields
     * @return
     */
    public static boolean isSupportedCursor(List<Field> fields) {
        if (CollectionUtils.isEmpty(fields)) {
            return false;
        }

        Map<String, Integer> typeAliases = new HashMap<>();
        fields.forEach(field -> {
            if (field.isPk()) {
                typeAliases.put(field.getName(), field.getType());
            }
        });

        for (Integer pkType : typeAliases.values()) {
            if (!isSupportedCursorType(pkType)) {
                return false;
            }
        }
        return !CollectionUtils.isEmpty(typeAliases);
    }

    /**
     * 是否支持游标类型 (数字)
     *
     * @param type
     * @return
     */
    private static boolean isSupportedCursorType(Integer type) {
        return type == Types.NUMERIC || type == Types.BIGINT || type == Types.INTEGER || type == Types.TINYINT || type == Types.SMALLINT;
    }

    /**
     * 获取最新游标值
     *
     * @param data
     * @param primaryKeys
     * @return
     */
    public static Object[] getLastCursors(List<Map> data, List<String> primaryKeys) {
        if (CollectionUtils.isEmpty(data)) {
            return null;
        }
        Map last = data.get(data.size() - 1);
        Object[] cursors = new Object[primaryKeys.size()];
        Iterator<String> iterator = primaryKeys.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            String pk = iterator.next();
            cursors[i] = last.get(pk);
            i++;
        }
        return cursors;
    }

    public static Object[] getLastCursors(String str) {
        Object[] cursors = null;
        if (StringUtil.isNotBlank(str)) {
            String[] split = StringUtil.split(str, StringUtil.COMMA);
            int length = split.length;
            cursors = new Object[length];
            for (int i = 0; i < length; i++) {
                String val = split[i];
                cursors[i] = NumberUtil.isCreatable(val) ? NumberUtil.toLong(val) : val;
            }
        }
        return cursors;
    }

}
