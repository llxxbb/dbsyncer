package org.dbsyncer.parser.flush;

import java.util.Queue;
import java.util.concurrent.Executor;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2022/3/27 17:34
 */
public interface BufferActuator {

    /**
     * 提交任务
     *
     * @param request
     */
    void offer(BufferRequest request);

    /**
     * 获取缓存队列
     *
     * @return
     */
    Queue getQueue();

    /**
     * 获取缓存队列容量
     *
     * @return
     */
    int getQueueCapacity();

    /**
     * 获取线程池
     *
     * @return
     */
    Executor getExecutor();

    /**
     * ADR-0031：热重载（排空-切换）。
     * <p>
     * 标记队列只出不进，等待消费者排空后替换队列，并重新注册定时调度。
     */
    void reload();

    /**
     * ADR-0031：重新注册定时调度（用新周期），队列不动。
     */
    void reschedule();

}