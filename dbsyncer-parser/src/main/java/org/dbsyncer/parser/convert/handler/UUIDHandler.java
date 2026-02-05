package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.UUIDUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * UUID
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/8 23:05
 */
public class UUIDHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        // converts 参数未使用
        // row 参数未使用
        // args 参数未使用
        // value 参数未使用
        return UUIDUtil.getUUID();
    }
}