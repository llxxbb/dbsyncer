package org.dbsyncer.connector.mysql.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * MySQL 字符集与 Java Charset 转换工具
 */
public final class MySQLCharsetUtil {

    private MySQLCharsetUtil() {
    }

    /** MySQL 字符集名 → Java Charset 名映射表 */
    private static final Map<String, String> MYSQL_CHARSET_MAP;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("utf16", "UTF-16BE");
        map.put("utf32", "UTF-32");
        map.put("utf8mb4", "UTF-8");
        map.put("utf8mb3", "UTF-8");
        map.put("utf8", "UTF-8");
        map.put("gbk", "GBK");
        map.put("gb2312", "GB2312");
        map.put("big5", "Big5");
        map.put("latin1", "ISO-8859-1");
        MYSQL_CHARSET_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * 将 MySQL 字符集名映射为 Java Charset 对象
     *
     * @param mysqlCharset MySQL 字符集名，null 时返回 UTF-8
     * @return Java Charset 对象
     */
    public static Charset resolveCharset(String mysqlCharset) {
        if (mysqlCharset == null) {
            return StandardCharsets.UTF_8;
        }
        String javaCharset = MYSQL_CHARSET_MAP.get(mysqlCharset.toLowerCase());
        if (javaCharset == null) {
            // 未知字符集降级为 UTF-8
            return StandardCharsets.UTF_8;
        }
        return Charset.forName(javaCharset);
    }
}
