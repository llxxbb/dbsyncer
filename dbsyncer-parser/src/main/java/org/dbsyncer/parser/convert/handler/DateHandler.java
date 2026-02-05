package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 系统日期
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/8 23:03
 */
public class DateHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        // converts 参数未使用
        // row 参数未使用
        // args 参数未使用
        return Date.valueOf(LocalDate.now());
    }
}