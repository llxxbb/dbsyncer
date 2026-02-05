package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.parser.convert.Handler;

import java.sql.Timestamp;
import java.util.Map;

/**
 * Timestamp转Long
 *
 * @author AE86
 * @version 1.0.0
 * @date 2021/9/2 23:04
 */
public class TimestampToLongHandler implements Handler {

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
            return t.getTime();
        }
        return value;
    }
}