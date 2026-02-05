package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.DateFormatUtil;
import org.dbsyncer.parser.convert.Handler;

import java.util.Map;

/**
 * 字符串转Timestamp
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/7/12 23:04
 */
public class StringToTimestampHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, java.util.List<org.dbsyncer.parser.model.Convert> converts) {
        // converts 参数未使用
        // sourceRow 参数未使用
        // context 参数未使用
        // args 参数未使用
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String s = (String) value;
            return DateFormatUtil.stringToTimestamp(s);
        }
        return value;
    }
}
