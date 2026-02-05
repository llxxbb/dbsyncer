package org.dbsyncer.parser.convert;

import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 2.0.0
 * @date 2019/10/8 22:55
 */
public interface Handler {

    /**
     * 值转换
     *
     * @param args 参数
     * @param value 当前字段值
     * @param sourceRow 源端数据行（用于 F() 字段引用）
     * @param context 转换器上下文（存储已计算的转换器值，用于 C() 递归引用）
     * @param converts 转换器配置列表
     * @return 转换后的值
     */
    Object handle(String args, Object value,
                  Map<String, Object> sourceRow,
                  Map<String, Object> context,
                  List<Convert> converts);
}