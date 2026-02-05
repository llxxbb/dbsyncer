package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 系统时间戳
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/8 23:03
 */
public class TimestampHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        // converts 参数未使用
        // sourceRow 参数未使用
        // context 参数未使用
        // args 参数未使用
        return new Timestamp(Instant.now().toEpochMilli());
    }
}