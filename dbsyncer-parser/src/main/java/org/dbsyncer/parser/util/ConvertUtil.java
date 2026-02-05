package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.model.Convert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ConvertUtil {

    private ConvertUtil() {
    }

    /**
     * 转换参数（批量处理）
     *
     * @param convert 转换配置列表
     * @param data    数据列表
     */
    public static void convert(List<Convert> convert, List<Map> data) {
        if (!CollectionUtils.isEmpty(convert) && !CollectionUtils.isEmpty(data)) {
            data.forEach(row -> convert(convert, row));
        }
    }

    /**
     * 转换参数
     *
     * @param convert 转换配置列表
     * @param row     数据行（作为 sourceRow 传入）
     */
    public static void convert(List<Convert> convert, Map row) {
        if (CollectionUtils.isEmpty(convert) || row == null) {
            return;
        }

        // 创建转换器上下文，用于存储已计算的转换器值
        Map<String, Object> context = new HashMap<>();

        final int size = convert.size();
        Convert c = null;
        String name = null;
        String code = null;
        String args = null;
        Object value = null;

        for (int i = 0; i < size; i++) {
            c = convert.get(i);
            name = c.getName();

            // 获取转换类型
            code = c.getConvertCode();
            if (StringUtil.isBlank(code)) {
                continue;
            }

            ConvertEnum convertEnum = null;
            try {
                convertEnum = ConvertEnum.valueOf(code);
            } catch (Exception e) {
                // 如果 code 不是有效的枚举值，跳过
                continue;
            }

            // 获取参数
            args = c.getArgs();

            // 获取 Handler
            Handler handler = convertEnum.getHandler();

            // 使用 Handler 处理
            // row 作为 sourceRow，context 用于递归引用
            value = row.get(name);
            value = handler.handle(args, value, row, context, convert);

            // 将处理后的值放入 Map
            row.put(name, value);
        }
    }
}