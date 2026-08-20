/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import org.junit.Assert;
import org.junit.Test;

/**
 * BinaryLogRemoteClient.correctMysqlGtidSet() 单元测试。
 * <p>
 * 测试 GTID 集合修正逻辑：保留小于指定 sequence 的部分。
 * 用于处理用户 edit 修改 fileName/position 后，临时 gtidSet 需要用第一条 GTID 事件修正的场景。
 */
public class BinaryLogRemoteClientCorrectGtidSetTest {

    private static final String UUID1 = "3E11FA47-71CA-11E1-9E33-C80AA9429562";
    private static final String UUID2 = "5B2E6F5C-F842-11EC-9B92-1418774D35D0";

    // ========== 正常情况 ==========

    @Test
    public void testCorrectGtidSet_singleInterval_truncated() {
        // 单个 uuid，区间被截断
        String currentGtidSet = UUID1 + ":1-200";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID1 + ":1-101", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequence() {
        // 单个 uuid，单个 sequence（不是区间）
        // 150 >= 101，区间起始值大于等于第一条 GTID 序列号，说明位置已无效
        String currentGtidSet = UUID1 + ":150";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
    }

    @Test
    public void testCorrectGtidSet_multipleUuids_onlyTargetCorrected() {
        // 多个 uuid：只修正目标 uuid，其他保留
        String currentGtidSet = UUID1 + ":1-200," + UUID2 + ":1-500";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID1 + ":1-101," + UUID2 + ":1-500", result);
    }

    @Test
    public void testCorrectGtidSet_multipleIntervals() {
        // 多个区间：250 在第二个区间 200-300 内
        // 第一个区间 1-100 已完全处理（end=100 < 250），保持不变
        // 第二个区间 200-300 截断到 200-250
        String currentGtidSet = UUID1 + ":1-100:200-300";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 250);
        Assert.assertEquals(UUID1 + ":1-100:200-250", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceExceedsAllMultipleIntervals() {
        // sequence 超出所有区间（end=100、end=300 均 < 400），抛异常
        String currentGtidSet = UUID1 + ":1-100:200-300";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 400);
    }

    // ========== 边界情况 ==========

    @Test
    public void testCorrectGtidSet_emptyString() {
        // 空字符串
        String currentGtidSet = "";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCorrectGtidSet_null() {
        // null
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(null, UUID1, 101);
        Assert.assertNull(result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceGreaterThanAllIntervals() {
        // sequence 超出所有区间（end=100 < 200），临时位点不可信，抛异常
        String currentGtidSet = UUID1 + ":1-100";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 200);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceJustAfterInterval() {
        // sequence 刚好超出区间结束值（end=200 < 201），超出所有区间，抛异常
        String currentGtidSet = UUID1 + ":1-200";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 201);
    }

    @Test
    public void testCorrectGtidSet_sequenceInMiddle() {
        // sequence 在区间中间，区间结束值大于第一条 GTID 序列号，范围缩小
        String currentGtidSet = UUID1 + ":1-100";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 50);
        Assert.assertEquals(UUID1 + ":1-50", result);
    }

    @Test
    public void testCorrectGtidSet_sequenceEqualToStart() {
        // sequence 等于区间起始，区间结束值大于第一条 GTID 序列号，范围缩小
        String currentGtidSet = UUID1 + ":100-200";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
        Assert.assertEquals(UUID1 + ":100", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_sequenceJustAfterEnd() {
        // sequence 等于区间结束+1（end=100 < 101），超出所有区间，抛异常
        String currentGtidSet = UUID1 + ":1-100";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
    }

    // ========== 大小写不敏感 ==========

    @Test
    public void testCorrectGtidSet_uuidCaseInsensitive() {
        // uuid 大小写不敏感，区间结束值大于第一条 GTID 序列号，范围缩小
        String currentGtidSet = UUID1.toLowerCase() + ":1-200";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1.toUpperCase(), 101);
        Assert.assertEquals(UUID1.toLowerCase() + ":1-101", result);
    }

    @Test
    public void testCorrectGtidSet_uuidMixedCase() {
        // uuid 混合大小写，区间结束值大于第一条 GTID 序列号，范围缩小
        String currentGtidSet = "3e11fa47-71ca-11e1-9e33-c80aa9429562:1-200";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, "3E11FA47-71CA-11E1-9E33-C80AA9429562", 101);
        Assert.assertEquals("3e11fa47-71ca-11e1-9e33-c80aa9429562:1-101", result);
    }

    // ========== 复杂场景 ==========

    @Test
    public void testCorrectGtidSet_multipleUuids_multipleIntervals() {
        // 多个 uuid，多个区间：250 在第二个区间 200-300 内
        // 第一个区间 1-100 保持不变，第二个区间截断到 200-250，非目标 uuid 保留
        String currentGtidSet = UUID1 + ":1-100:200-300," + UUID2 + ":1-50";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 250);
        Assert.assertEquals(UUID1 + ":1-100:200-250," + UUID2 + ":1-50", result);
    }

    @Test
    public void testCorrectGtidSet_targetUuidNotInSet() {
        // 目标 uuid 不在集合中（应该保留原值）
        String currentGtidSet = UUID1 + ":1-200";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID2, 101);
        Assert.assertEquals(UUID1 + ":1-200", result);
    }

    @Test
    public void testCorrectGtidSet_sequenceEquals1() {
        // sequence 等于 1，区间结束值大于第一条 GTID 序列号，范围缩小
        String currentGtidSet = UUID1 + ":1-100";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 1);
        Assert.assertEquals(UUID1 + ":1", result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequenceLessThanTarget() {
        // 单点区间小于目标（50 < 100），超出所有区间，抛异常
        String currentGtidSet = UUID1 + ":50";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
    }

    @Test(expected = IllegalStateException.class)
    public void testCorrectGtidSet_singleSequenceGreaterThanTarget() {
        // 单个 sequence 大于目标：150 >= 100，区间起始值大于等于第一条 GTID 序列号，位置已无效
        String currentGtidSet = UUID1 + ":150";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 100);
    }

    // ========== 异常输入 ==========

    @Test(expected = NumberFormatException.class)
    public void testCorrectGtidSet_malformedInterval() {
        // 格式错误的区间，应该抛异常，由调用方 correctGtidSetWithFirstGtid 处理
        String currentGtidSet = UUID1 + ":invalid";
        BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
    }

    @Test
    public void testCorrectGtidSet_malformedFormat() {
        // 格式错误的输入（缺少冒号）
        String currentGtidSet = UUID1;
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals("", result);
    }

    @Test
    public void testCorrectGtidSet_emptyInterval() {
        // 空区间（应该跳过）
        String currentGtidSet = UUID1 + ":," + UUID2 + ":1-100";
        String result = BinaryLogRemoteClient.correctMysqlGtidSet(currentGtidSet, UUID1, 101);
        Assert.assertEquals(UUID2 + ":1-100", result);
    }

    // ========== 实际场景 ==========

    @Test
    public void testCorrectGtidSet_realWorldScenario() {
        // 实际场景：用户 edit 修改了 fileName/position，从服务端获取临时 gtidSet
        // 临时 gtidSet = 服务端当前状态（uuid:1-200）
        // 从旧位置开始消费，遇到第一条 GTID 事件（uuid:101）
        // 区间结束值大于第一条 GTID 序列号，范围缩小
        String temporaryGtidSet = UUID1 + ":1-200";
        String firstGtid = UUID1 + ":101";
        
        // 解析 firstGtid
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        
        String correctedGtidSet = BinaryLogRemoteClient.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        
        // 验证修正后的 gtidSet
        Assert.assertEquals(UUID1 + ":1-101", correctedGtidSet);
    }

    @Test
    public void testCorrectGtidSet_realWorldScenario_multipleUuids() {
        // 实际场景：多个 uuid 的情况，目标 uuid 区间结束值大于第一条 GTID 序列号，范围缩小
        String temporaryGtidSet = UUID1 + ":1-200," + UUID2 + ":1-100";
        String firstGtid = UUID1 + ":101";
        
        String[] parts = firstGtid.split(":");
        String uuid = parts[0];
        long sequence = Long.parseLong(parts[1]);
        
        String correctedGtidSet = BinaryLogRemoteClient.correctMysqlGtidSet(temporaryGtidSet, uuid, sequence);
        
        // 验证修正后的 gtidSet：只修正目标 uuid，其他保留
        Assert.assertEquals(UUID1 + ":1-101," + UUID2 + ":1-100", correctedGtidSet);
    }
}
