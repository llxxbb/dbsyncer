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
        // 250 落在第二个区间 200-300 内 → 截断
        // 第一个区间 1-100 完整保留
        String currentGtidSet = UUID1 + ":1-100:200-300";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 250);
        Assert.assertEquals(UUID1 + ":1-100:200-250", result);
    }

    @Test
    public void testCorrectGtidSet_sequenceExceedsAllMultipleIntervals() {
        // 400 超出所有区间 → 扩大最近的右边界（300）
        // 第一个区间 1-100 完整保留
        String currentGtidSet = UUID1 + ":1-100:200-300";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 400);
        Assert.assertEquals(UUID1 + ":1-100:200-400", result);
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

    @Test
    public void testCorrectGtidSet_sequenceGreaterThanAllIntervals() {
        // sequence 超出所有区间，扩大区间
        String currentGtidSet = UUID1 + ":1-100";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 200);
        Assert.assertEquals(UUID1 + ":1-200", result);
    }

    @Test
    public void testCorrectGtidSet_sequenceJustAfterInterval() {
        // sequence 刚好超出区间结束值，扩大区间
        String currentGtidSet = UUID1 + ":1-200";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 201);
        Assert.assertEquals(UUID1 + ":1-201", result);
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

    @Test
    public void testCorrectGtidSet_sequenceJustAfterEnd() {
        // sequence 等于区间结束+1，扩大区间
        String currentGtidSet = UUID1 + ":1-100";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID1 + ":1-101", result);
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
        // 250 落在第二个区间 200-300 内 → 截断
        // 第一个区间 1-100 完整保留
        // 非目标 uuid 保留
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

    @Test
    public void testCorrectGtidSet_singleSequenceLessThanTarget() {
        // 单点区间小于目标，扩大区间
        String currentGtidSet = UUID1 + ":50";
        String result = GtidSetManager.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
        Assert.assertEquals(UUID1 + ":50-100", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequenceGreaterThanTarget() {
        // 单个 sequence 大于目标：150 >= 100，区间起始值大于第一条 GTID 序列号，位置已无效
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
        // 实际场景：用户 edit 修改了 fileName/position，从服务端获取临时 gtidSet
        // 临时 gtidSet = 服务端当前状态（uuid:1-200）
        // 从旧位置开始消费，遇到第一条 GTID 事件（uuid:101）
        // 区间截断到 101
        String temporaryGtidSet = UUID1 + ":1-200";
        String firstGtid = UUID1 + ":101";
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        String correctedGtidSet = GtidSetManager.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        Assert.assertEquals(UUID1 + ":1-101", correctedGtidSet);
    }

    @Test
    public void testCorrectGtidSet_realWorldScenario_expandRange() {
        // 实际场景：新序列大于右边界，扩大区间
        String temporaryGtidSet = UUID1 + ":1-100";
        String firstGtid = UUID1 + ":200";
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        String correctedGtidSet = GtidSetManager.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        Assert.assertEquals(UUID1 + ":1-200", correctedGtidSet);
    }

    @Test
    public void testCorrectGtidSet_realWorldScenario_multipleUuids() {
        // 实际场景：多个 uuid 的情况，目标 uuid 区间截断
        String temporaryGtidSet = UUID1 + ":1-200," + UUID2 + ":1-100";
        String firstGtid = UUID1 + ":101";
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        String correctedGtidSet = GtidSetManager.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        // 只修正目标 uuid，其他保留
        Assert.assertEquals(UUID1 + ":1-101," + UUID2 + ":1-100", correctedGtidSet);
    }
}
