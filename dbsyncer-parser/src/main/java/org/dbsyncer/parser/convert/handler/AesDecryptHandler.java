/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.AESUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * AES解密
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/8 23:04
 */
public class AesDecryptHandler implements Handler {

    @Override
    public Object handle(String args, Object value, Map<String, Object> sourceRow, Map<String, Object> context, List<Convert> converts) {
        // converts 参数未使用
        // sourceRow 参数未使用
        // context 参数未使用
        if (value == null) {
            return null;
        }
        try {
            return AESUtil.decrypt(String.valueOf(value), args);
        } catch (Exception e) {
            throw new ParserException(e.getMessage());
        }
    }

}