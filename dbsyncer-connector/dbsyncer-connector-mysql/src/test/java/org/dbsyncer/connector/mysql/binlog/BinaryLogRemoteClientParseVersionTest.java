/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * BinaryLogRemoteClient#parseVersion 单元测试。
 * 版本编码规则：去掉后缀（-/+ 或空格之后）与点号，右补 0 至 10 位，
 * 与阈值 MYSQL_VERSION_8_4（8400000000，即 8.4.0）比较决定使用
 * SHOW BINARY LOG STATUS 还是 SHOW MASTER STATUS。
 */
public class BinaryLogRemoteClientParseVersionTest {

    private static final long MYSQL_VERSION_8_4 = 8400000000L;

    @Test
    public void testStandardVersions() throws IOException {
        Assert.assertEquals(8032000000L, BinaryLogRemoteClient.parseVersion("8.0.32"));
        Assert.assertEquals(8400000000L, BinaryLogRemoteClient.parseVersion("8.4.0"));
        Assert.assertEquals(5744000000L, BinaryLogRemoteClient.parseVersion("5.7.44"));
        Assert.assertEquals(8410000000L, BinaryLogRemoteClient.parseVersion("8.4.1"));
    }

    @Test
    public void testVersionsWithSuffix() throws IOException {
        Assert.assertEquals(5744000000L, BinaryLogRemoteClient.parseVersion("5.7.44-log"));
        Assert.assertEquals(8032000000L, BinaryLogRemoteClient.parseVersion("8.0.32-commercial"));
        Assert.assertEquals(8032000000L, BinaryLogRemoteClient.parseVersion("8.0.32+dfsg.1"));
        Assert.assertEquals(8032000000L, BinaryLogRemoteClient.parseVersion(" 8.0.32 "));
        Assert.assertEquals(1011600000L, BinaryLogRemoteClient.parseVersion("10.11.6-MariaDB-1:10.11.6+maria~ubu2204"));
    }

    @Test
    public void testVersionThresholdForShowBinaryLogStatus() throws IOException {
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("8.4.0") >= MYSQL_VERSION_8_4);
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("8.4.2") >= MYSQL_VERSION_8_4);
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("9.0.0") >= MYSQL_VERSION_8_4);
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("8.3.9") < MYSQL_VERSION_8_4);
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("8.0.32") < MYSQL_VERSION_8_4);
        // MariaDB 10.x 必须低于阈值（不支持 SHOW BINARY LOG STATUS）
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("10.11.6-MariaDB") < MYSQL_VERSION_8_4);
        Assert.assertTrue(BinaryLogRemoteClient.parseVersion("11.4.2-MariaDB") < MYSQL_VERSION_8_4);
    }

    @Test(expected = IOException.class)
    public void testInvalidVersion() throws IOException {
        BinaryLogRemoteClient.parseVersion("abc");
    }

    @Test(expected = IOException.class)
    public void testBlankVersion() throws IOException {
        BinaryLogRemoteClient.parseVersion("   ");
    }

}
