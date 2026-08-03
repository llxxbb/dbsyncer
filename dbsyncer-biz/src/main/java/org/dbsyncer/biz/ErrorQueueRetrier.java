/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz;

import org.dbsyncer.biz.vo.MessageVo;

import java.util.Map;

public interface ErrorQueueRetrier {

    /**
     * 获取错误队列消息详情（供重试页面展示）
     *
     * @param metaId    元数据ID
     * @param messageId 消息ID
     * @return 消息详情
     */
    MessageVo getMessageVo(String metaId, String messageId) throws Exception;

    /**
     * 获取Binlog数据
     *
     * @param row         数据行
     * @param prettyBytes 是否格式化字节数组
     * @return Binlog数据
     */
    Map getBinlogData(Map row, boolean prettyBytes) throws Exception;

    /**
     * 单条重试
     *
     * @param params 重试参数
     * @return 成功的 messageId
     */
    String sync(Map<String, String> params) throws Exception;

    /**
     * 批量重试所有失败数据
     *
     * @param metaId 元数据ID
     * @return 包含总数、成功数、失败数的Map
     */
    Map<String, Object> retryAll(String metaId) throws Exception;

}
