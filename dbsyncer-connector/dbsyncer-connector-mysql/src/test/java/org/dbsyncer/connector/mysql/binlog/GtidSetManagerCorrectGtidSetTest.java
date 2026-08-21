/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import org.junit.Assert;
import org.junit.Test;

/**
 * GtidSetManager.correctMysqlGtidSet() 单元测试。
 * <p>
 * 测试 GTID 集合修正逻辑：保留小于指定 sequence 的部分。
 * 用于处理用户 edit 修改 fileName/position 后，临时 gtidSet 需要用第一条 GTID 事件修正的场景。
 */
public class GtidSetManagerCorrectGtidSetTest {

    private static final String UUID1 = "3E11FA47-71CA-11E1-9E33-C80AA9429562";
    private static final String UUID2 = "5B2E6F5C-F842-11EC-9B92-1418774D35D0";

    @Test
    public void testCorrectGtidSet_singleInterval_truncated() {
        String currentGtidSet = UUID1 + ":1-200";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID1 + ":1-101", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequence() {
        String currentGtidSet = UUID1 + ":150";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
    }

    @Test
    public void testCorrectGtidSet_multipleUuids_onlyTargetCorrected() {
        String currentGtidSet = UUID1 + ":1-200," + UUID2 + ":1-500";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID1 + ":1-101," + UUID2 + ":1-500", result);
    }

    @Test
    public void testCorrectGtidSet_multipleIntervals() {
        String currentGtidSet = UUID1 + ":1-100:200-300";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 250);
        Assert.assertEquals(UUID1 + ":1-100:200-250", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceExceedsAllMultipleIntervals() {
        String currentGtidSet = UUID1 + ":1-100:200-300";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 400);
    }

    @Test
    public void testCorrectGtidSet_emptyString() {
        String result = GtidSetManager.correctMysqlGtidSet("", UUID1, 101);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCorrectGtidSet_null() {
        String result = GtidSetManager.correctMysqlGtidSet(null, UUID1, 101);
        Assert.assertNull(result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceGreaterThanAllIntervals() {
        String currentGtidSet = UUID1 + ":1-100";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceJustAfterInterval() {
        String currentGtidSet = UUID1 + ":1-200";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 201);
    }

    @Test
    public void testCorrectGtidSet_sequenceInMiddle() {
        String currentGtidSet = UUID1 + ":1-100";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 50);
        Assert.assertEquals(UUID1 + ":1-50", result);
    }

    @Test
    public void testCorrectGtidSet_sequenceEqualToStart() {
        String currentGtidSet = UUID1 + ":100-200";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
        Assert.assertEquals(UUID1 + ":100", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceJustAfterEnd() {
        String currentGtidSet = UUID1 + ":1-100";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
    }

    @Test
    public void testCorrectGtidSet_uuidCaseInsensitive() {
        String currentGtidSet = UUID1.toLowerCase() + ":1-200";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1.toUpperCase(), 101);
        Assert.assertEquals(UUID1.toLowerCase() + ":1-101", result);
    }

    @Test
    public void testCorrectGtidSet_uuidMixedCase() {
        String currentGtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-200";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, "3E11FA47-71CA-11E1-9E33-C80AA9429562", 101);
        Assert.assertEquals("3e11fa47-71ca-11e1-9e33-c80aa9429562:1-101", result);
    }

    @Test
    public void testCorrectGtidSet_multipleUuids_multipleIntervals() {
        String currentGtidSet = UUID1 + ":1-100:200-300," + UUID2 + ":1-50";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 250);
        Assert.assertEquals(UUID1 + ":1-100:200-250," + UUID2 + ":1-50", result);
    }

    @Test
    public void testCorrectGtidSet_targetUuidNotInSet() {
        String currentGtidSet = UUID1 + ":1-200";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID2, 101);
        Assert.assertEquals(UUID1 + ":1-200", result);
    }

    @Test
    public void testCorrectGtidSet_sequenceEquals1() {
        String currentGtidSet = UUID1 + ":1-100";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 1);
        Assert.assertEquals(UUID1 + ":1", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequenceLessThanTarget() {
        String currentGtidSet = UUID1 + ":50";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequenceGreaterThanTarget() {
        String currentGtidSet = UUID1 + ":150";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
    }

    @Test(expected = NumberFormatException.class)
    public void testCorrectGtidSet_malformedInterval() {
        String currentGtidSet = UUID1 + ":invalid";
        GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
    }

    @Test
    public void testCorrectGtidSet_malformedFormat() {
        String currentGtidSet = UUID1;
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCorrectGtidSet_emptyInterval() {
        String currentGtidSet = UUID1 + ":," + UUID2 + ":1-100";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID2 + ":1-100", result);
    }

    @Test
    public void testCorrectGtidSet_realWorldScenario() {
        String temporaryGtidSet = UUID1 + ":1-200";
        String firstGtid = UUID1 + ":101";
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        String correctedGtidSet = GtidSetManager.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        Assert.assertEquals(UUID1 + ":1-101", correctedGtidSet);
    }

    @Test
    public void testCorrectGtidSet_realWorldScenario_multipleUuids() {
        String temporaryGtidSet = UUID1 + ":1-200," + UUID2 + ":1-100";
        String firstGtid = UUID1 + ":101";
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        String correctedGtidSet = GtidSetManager.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        Assert.assertEquals(UUID1 + ":1-101," + UUID2 + ":1-100", correctedGtidSet);
    }
}
