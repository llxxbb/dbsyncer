/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import com.github.shyiko.mysql.binlog.network.ServerException;
import org.junit.Assert;
import org.junit.Test;

/**
 * GtidSetManager GTID 初始化与 1236 错误检测单元测试。
 * <p>验证 ADR-0026 相关逻辑：
 * <ul>
 *     <li>{@code initIfEmpty}：空集初始化、非空集保护、fail-open</li>
 *     <li>{@code isGtidPurgedError} / {@code isBogusDataError}：1236 子类型检测</li>
 * </ul>
 */
public class GtidSetManagerInitTest {

    // ========== initIfEmpty (MySQL) ==========

    @Test
    public void testInitFromEmptySet_mysql() {
        GtidSetManager manager = new GtidSetManager(false);
        manager.initEmpty();
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertTrue(result);
        Assert.assertEquals(executed, manager.getGtidSet());
    }

    @Test
    public void testInitFromEmptySet_mysqlMultiInterval() {
        GtidSetManager manager = new GtidSetManager(false);
        manager.initEmpty();
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100:200-300";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertTrue(result);
        Assert.assertEquals(executed, manager.getGtidSet());
    }

    @Test
    public void testInitFromEmptySet_mysqlMultiUuid() {
        GtidSetManager manager = new GtidSetManager(false);
        manager.initEmpty();
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100,aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:1-50";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertTrue(result);
        Assert.assertEquals(executed, manager.getGtidSet());
    }

    // ========== initIfEmpty (MariaDB) ==========

    @Test
    public void testInitFromEmptySet_mariadb() {
        GtidSetManager manager = new GtidSetManager(true);
        manager.initEmpty();
        String executed = "0-1-1234";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertTrue(result);
        Assert.assertEquals(executed, manager.getGtidSet());
    }

    @Test
    public void testInitFromEmptySet_mariadbMultiInterval() {
        GtidSetManager manager = new GtidSetManager(true);
        manager.initEmpty();
        String executed = "0-1-1234,2-1-5678";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertTrue(result);
        Assert.assertEquals(executed, manager.getGtidSet());
    }

    // ========== initIfEmpty from null ==========

    @Test
    public void testInitFromNullSet_mysql() {
        GtidSetManager manager = new GtidSetManager(false);
        String executed = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1-100";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertTrue(result);
        Assert.assertEquals(executed, manager.getGtidSet());
    }

    // ========== initIfEmpty preserves non-empty ==========

    @Test
    public void testInitPreservesNonEmptySet_snapshot() {
        GtidSetManager manager = new GtidSetManager(false);
        String checkpoint = "5b2e6f5c-f842-11ec-9b92-1418774d35d0:1103174135-1103175276";
        manager.initFromSnapshot(checkpoint);
        String executed = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:1-999999999";
        boolean result = manager.initIfEmpty(executed);
        Assert.assertFalse(result);
        Assert.assertEquals(checkpoint, manager.getGtidSet());
    }

    // ========== initIfEmpty with blank/malformed ==========

    @Test
    public void testInitWithBlankExecuted_returnsFalse() {
        GtidSetManager manager = new GtidSetManager(false);
        manager.initEmpty();
        Assert.assertFalse(manager.initIfEmpty(""));
        Assert.assertFalse(manager.initIfEmpty("   "));
        Assert.assertFalse(manager.initIfEmpty(null));
    }

    @Test
    public void testInitWithMalformedExecuted_failOpen() {
        GtidSetManager manager = new GtidSetManager(false);
        manager.initEmpty();
        String originalGtid = manager.getGtidSet();
        Assert.assertFalse(manager.initIfEmpty("not-a-valid-gtid"));
        Assert.assertEquals(originalGtid, manager.getGtidSet());
        
        Assert.assertFalse(manager.initIfEmpty("::invalid::"));
        
        GtidSetManager manager2 = new GtidSetManager(false);
        Assert.assertFalse(manager2.initIfEmpty("not-a-valid-gtid"));
        Assert.assertNull(manager2.getGtidSet());
    }

    // ========== isGtidPurgedError / isBogusDataError ==========

    @Test
    public void testIsGtidPurgedError_true() {
        ServerException e = new ServerException(
                "The slave is connecting using CHANGE MASTER TO MASTER_AUTO_POSITION = 1, but the master has purged binary logs containing GTIDs that the slave requires.",
                1236, "HY000");
        Assert.assertTrue(GtidSetManager.isGtidPurgedError(e));
    }

    @Test
    public void testIsGtidPurgedError_false_other1236() {
        ServerException e = new ServerException("Could not find first log file", 1236, "HY000");
        Assert.assertFalse(GtidSetManager.isGtidPurgedError(e));
    }

    @Test
    public void testIsGtidPurgedError_false_non1236() {
        ServerException e = new ServerException("purged binary logs", 1234, "HY000");
        Assert.assertFalse(GtidSetManager.isGtidPurgedError(e));
    }

    @Test
    public void testIsBogusDataError_true() {
        ServerException e = new ServerException("Got fatal error during reading: bogus data", 1236, "HY000");
        Assert.assertTrue(GtidSetManager.isBogusDataError(e));
    }

    @Test
    public void testIsBogusDataError_false_other1236() {
        ServerException e = new ServerException("Could not find first log file", 1236, "HY000");
        Assert.assertFalse(GtidSetManager.isBogusDataError(e));
    }

    @Test
    public void testIsBogusDataError_false_non1236() {
        ServerException e = new ServerException("bogus data", 1234, "HY000");
        Assert.assertFalse(GtidSetManager.isBogusDataError(e));
    }

    @Test
    public void testErrorDetection_nullMessage() {
        ServerException e = new ServerException(null, 1236, "HY000");
        Assert.assertFalse(GtidSetManager.isGtidPurgedError(e));
        Assert.assertFalse(GtidSetManager.isBogusDataError(e));
    }
}
