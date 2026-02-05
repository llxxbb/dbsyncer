package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.parser.convert.Handler;

import java.util.Map;

/**
 * Number转String
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/7/20 23:04
 */
public class NumberToStringHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, java.util.List<org.dbsyncer.parser.model.Convert> converts) {
        // converts 参数未使用
        // sourceRow 参数未使用
        // context 参数未使用
        // args 参数未使用
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

}