package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * 后面追加
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/8 23:04
 */
public class AppendHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        // converts 参数未使用
        // row 参数未使用
        if (null == value) {
            return args;
        }
        return new StringBuilder().append(value).append(args).toString();
    }
}