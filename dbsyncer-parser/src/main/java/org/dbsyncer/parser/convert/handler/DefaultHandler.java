package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * 默认值
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/8 23:02
 */
public class DefaultHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        // converts 参数未使用
        // row 参数未使用
        return null == value || StringUtil.isBlank(String.valueOf(value)) ? args : value;
    }
}