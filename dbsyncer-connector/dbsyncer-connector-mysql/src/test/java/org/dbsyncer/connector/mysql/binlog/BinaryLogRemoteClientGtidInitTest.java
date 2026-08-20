/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import com.github.shyiko.mysql.binlog.GtidSet;
import com.github.shyiko.mysql.binlog.MariadbGtidSet;
import com.github.shyiko.mysql.binlog.network.ServerException;
import org.junit.Assert;
import org.junit.Test;

/**
 * BinaryLogRemoteClient GTID 初始化与 1236 错误检测单元测试。
 * <p>验证 ADR-0026 相关逻辑：
 * <ul>
 *     <li>{@code initGtidSetIfEmpty}：空集初始化、非空集保护、fail-open</li>
 *     <li>{@code isGtidPurgedError} / {@code isBogusDataError}：1236 子类型检测</li>
 * </ul>
 */
public class BinaryLogRemoteClientGtidInitTest {

    // ========== initGtidSetIfEmpty ==========

    @Test
    public void testInitFromEmptySet_mysql() {
        GtidSet empty = new GtidSet("");
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(empty, executed, false);
        Assert.assertNotNull(result);
        Assert.assertEquals(executed, result.toString());
    }

    @Test
    public void testInitFromEmptySet_mysqlMultiInterval() {
        GtidSet empty = new GtidSet("");
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100:200-300";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(empty, executed, false);
        Assert.assertNotNull(result);
        Assert.assertEquals(executed, result.toString());
    }

    @Test
    public void testInitFromEmptySet_mysqlMultiUuid() {
        GtidSet empty = new GtidSet("");
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100,aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:1-50";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(empty, executed, false);
        Assert.assertNotNull(result);
        Assert.assertEquals(executed, result.toString());
    }

    @Test
    public void testInitFromEmptySet_mariadb() {
        GtidSet empty = new MariadbGtidSet("");
        String executed = "0-1-1234";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(empty, executed, true);
        Assert.assertNotNull(result);
        Assert.assertTrue(result instanceof MariadbGtidSet);
        Assert.assertEquals(executed, result.toString());
    }

    @Test
    public void testInitFromEmptySet_mariadbMultiInterval() {
        GtidSet empty = new MariadbGtidSet("");
        String executed = "0-1-1234,2-1-5678";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(empty, executed, true);
        Assert.assertNotNull(result);
        Assert.assertEquals(executed, result.toString());
    }

    @Test
    public void testInitFromNullSet_mysql() {
        // null 集合（首次连接尚未初始化）也应被初始化
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(null, executed, false);
        Assert.assertNotNull(result);
        Assert.assertEquals(executed, result.toString());
    }

    @Test
    public void testInitPreservesNonEmptySet_snapshot() {
        // 非空集合（snapshot 检查点）绝不被覆盖
        GtidSet checkpoint = new GtidSet("5b2e6f5c-f842-11ec-9b92-1418774d35d0:1103174135-1103175276");
        String executed = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:1-999999999";
        GtidSet result = BinaryLogRemoteClient.initGtidSetIfEmpty(checkpoint, executed, false);
        Assert.assertSame(checkpoint, result);
        Assert.assertEquals("5b2e6f5c-f842-11ec-9b92-1418774d35d0:1103174135-1103175276", result.toString());
    }

    @Test
    public void testInitWithBlankExecuted_returnsOriginal() {
        GtidSet empty = new GtidSet("");
        Assert.assertSame(empty, BinaryLogRemoteClient.initGtidSetIfEmpty(empty, "", false));
        Assert.assertSame(empty, BinaryLogRemoteClient.initGtidSetIfEmpty(empty, "   ", false));
        Assert.assertNull(BinaryLogRemoteClient.initGtidSetIfEmpty(null, null, false));
    }

    @Test
    public void testInitWithMalformedExecuted_failOpen() {
        // 畸形输入：fail-open，返回原集合（不抛异常）
        GtidSet empty = new GtidSet("");
        Assert.assertSame(empty, BinaryLogRemoteClient.initGtidSetIfEmpty(empty, "not-a-valid-gtid", false));
        Assert.assertSame(empty, BinaryLogRemoteClient.initGtidSetIfEmpty(empty, "::invalid::", false));
        // null 集合 + 畸形输入 → 返回 null（fail-open）
        Assert.assertNull(BinaryLogRemoteClient.initGtidSetIfEmpty(null, "not-a-valid-gtid", false));
    }

    // ========== isGtidPurgedError / isBogusDataError ==========

    @Test
    public void testIsGtidPurgedError_true() {
        ServerException e = new ServerException(
                "The slave is connecting using CHANGE MASTER TO MASTER_AUTO_POSITION = 1, but the master has purged binary logs containing GTIDs that the slave requires.",
                1236, "HY000");
        Assert.assertTrue(BinaryLogRemoteClient.isGtidPurgedError(e));
    }

    @Test
    public void testIsGtidPurgedError_false_other1236() {
        ServerException e = new ServerException("Could not find first log file", 1236, "HY000");
        Assert.assertFalse(BinaryLogRemoteClient.isGtidPurgedError(e));
    }

    @Test
    public void testIsGtidPurgedError_false_non1236() {
        ServerException e = new ServerException("purged binary logs", 1234, "HY000");
        Assert.assertFalse(BinaryLogRemoteClient.isGtidPurgedError(e));
    }

    @Test
    public void testIsBogusDataError_true() {
        ServerException e = new ServerException("Got fatal error during reading: bogus data", 1236, "HY000");
        Assert.assertTrue(BinaryLogRemoteClient.isBogusDataError(e));
    }

    @Test
    public void testIsBogusDataError_false_other1236() {
        ServerException e = new ServerException("Could not find first log file", 1236, "HY000");
        Assert.assertFalse(BinaryLogRemoteClient.isBogusDataError(e));
    }

    @Test
    public void testIsBogusDataError_false_non1236() {
        ServerException e = new ServerException("bogus data", 1234, "HY000");
        Assert.assertFalse(BinaryLogRemoteClient.isBogusDataError(e));
    }

    @Test
    public void testErrorDetection_nullMessage() {
        // null message 不应 NPE
        ServerException e = new ServerException(null, 1236, "HY000");
        Assert.assertFalse(BinaryLogRemoteClient.isGtidPurgedError(e));
        Assert.assertFalse(BinaryLogRemoteClient.isBogusDataError(e));
    }
}
