package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.DateFormatUtil;
import org.dbsyncer.parser.convert.Handler;

import java.sql.Timestamp;
import java.util.Map;

/**
 * Timestamp转中国标准时间
 *
 * @author AE86
 * @version 1.0.0
 * @date 2021/12/20 23:04
 */
public class TimestampToChineseStandardTimeHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, java.util.List<org.dbsyncer.parser.model.Convert> converts) {
        // converts 参数未使用
        // sourceRow 参数未使用
        // context 参数未使用
        // args 参数未使用
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp) {
            Timestamp t = (Timestamp) value;
            return DateFormatUtil.timestampToString(t);
        }
        return value;
    }
}