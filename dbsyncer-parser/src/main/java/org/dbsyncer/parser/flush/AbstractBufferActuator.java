/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.flush;

import org.dbsyncer.common.config.props.BufferProps;
import org.dbsyncer.common.metric.TimeRegistry;
import org.dbsyncer.common.scheduled.ScheduledTaskJob;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.UUIDUtil;
import org.dbsyncer.common.ParserException;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.parser.model.WriterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 任务缓存执行器
 * <p>1. 任务优先进入缓存队列
 * <p>2. 将任务分区合并，批量执行
 * <p>
 * ADR-0030：不再依赖 BufferActuatorConfig，改为从 ConfigRegistry 动态读取缓冲区参数。
 * 队列和调度器延迟到首次 offer() 时初始化，确保 ConfigRegistry 已从 DB 加载配置。
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/3/27 17:36
 */
public abstract class AbstractBufferActuator<Request extends BufferRequest, Response extends BufferResponse> implements BufferActuator, ScheduledTaskJob {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private Class<Response> responseClazz;
    private Lock taskLock;
    private volatile BlockingQueue<Request> queue;
    private String taskKey;

    protected ConfigRegistry configRegistry;

    /**
     * 延迟初始化标志：队列和调度器是否已初始化
     */
    private volatile boolean initialized = false;

    /**
     * ADR-0031：排空标志。true 表示队列只出不进，offer() 阻塞等待。
     */
    private volatile boolean draining = false;

    /**
     * ADR-0031：排空完成条件。reload() 完成后排空后 signalAll 唤醒等待的 offer() 线程。
     */
    private final ReentrantLock drainingLock = new ReentrantLock();
    private final Condition drainingDone = drainingLock.newCondition();

    @Resource
    private ScheduledTaskService scheduledTaskService;

    @Resource
    protected ProfileComponent profileComponent;

    @Resource
    private TimeRegistry timeRegistry;


    public AbstractBufferActuator() {
        int level = 5;
        Class<?> aClass = getClass();
        while (level > 0) {
            if (aClass.getSuperclass() == AbstractBufferActuator.class) {
                responseClazz = (Class<Response>) ((ParameterizedType) aClass.getGenericSuperclass()).getActualTypeArguments()[1];
                break;
            }
            aClass = aClass.getSuperclass();
            level--;
        }
        Assert.notNull(responseClazz, String.format("%s的父类%s泛型参数Response为空.", getClass().getName(), AbstractBufferActuator.class.getName()));
    }



    /**
     * 延迟初始化：创建队列 + 启动调度器。
     * 首次 offer() 时调用，确保此时 ConfigRegistry 已就绪。
     */
    protected void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    Assert.notNull(configRegistry, "请先设置 configRegistry");
                    buildQueueConfig();
                    if (taskKey == null) {
                        taskKey = UUIDUtil.getUUID();
                    }
                    scheduledTaskService.start(taskKey, getBufferPeriodMillisecond(), this);
                    initialized = true;
                    logger.info("[{}] 延迟初始化完成: queueCapacity={}, period={}ms",
                            getClass().getSimpleName(), getBufferQueueCapacity(), getBufferPeriodMillisecond());
                }
            }
        }
    }

    /**
     * 初始化缓存队列配置
     */
    protected void buildQueueConfig() {
        taskLock = new ReentrantLock();
        this.queue = new LinkedBlockingQueue<>(getBufferQueueCapacity());
    }

    /**
     * 生成分区key
     *
     * @param request
     * @return
     */
    protected abstract String getPartitionKey(Request request);

    /**
     * 分区
     *
     * @param request
     * @param response
     */
    protected abstract void partition(Request request, Response response);

    /**
     * 消费者是否仍在运行。
     * - offer() 队列满时据此判断是否抛异常
     * - reload() 等待排空时据此判断是否继续等待
     * 默认返回 true。子类可重写（如 MetaBufferActuator 检查 meta 状态）。
     */
    protected boolean isRunning() {
        return true;
    }

    /**
     * 是否跳过分区处理
     *
     * @param nextRequest
     * @param response
     * @return
     */
    protected boolean skipPartition(Request nextRequest, Response response) {
        return false;
    }

    /**
     * 批处理
     *
     * @param response
     * @throws Exception 
     */
    public abstract void pull(Response response) throws Exception;

    /**
     * 批量处理分区数据
     *
     * @param map
     * @param maxOffsetResponse 上游提供的最大偏移量响应
     * @throws Exception 
     */
    protected void process(Map<String, Response> map, WriterResponse maxOffsetResponse) throws Exception {
        // 直接处理所有响应，无需排序
        // pull() 的执行顺序不影响正确性，因为每个表的 pull() 是独立的
        for (Map.Entry<String, Response> entry : map.entrySet()) {
            pull(entry.getValue());
        }

        // 处理完成后，调用钩子方法（子类可以重写来处理偏移量刷新、pending 状态等）
        afterProcess(maxOffsetResponse);
    }

    /**
     * 处理完成后的钩子方法（子类可以重写）
     * 
     * @param lastResponse 最后一个响应（可能为 null），包含最大偏移量信息
     */
    protected void afterProcess(WriterResponse lastResponse) {
        // 默认实现为空，子类可以重写
    }


    /**
     * 提交任务失败
     *
     * @param queue
     * @param request
     */
    protected abstract void offerFailed(BlockingQueue<Request> queue, Request request);

    /**
     * 统计消费 TPS
     *
     * @param timeRegistry
     * @param count
     */
    protected void meter(TimeRegistry timeRegistry, long count) {

    }

    @Override
    public void offer(BufferRequest request) {
        ensureInitialized();
        // ADR-0031：排空中阻塞等待，不丢数据
        if (draining) {
            drainingLock.lock();
            try {
                while (draining) {
                    drainingDone.await();
                }
            } catch (InterruptedException e) {
                // ADR-0031 fix：不 return，等 draining 结束后继续走正常 offer 路径，避免请求丢失
                Thread.currentThread().interrupt();
            } finally {
                drainingLock.unlock();
            }
        }
        if (queue.offer((Request) request)) {
            return;
        }
        if (isRunning()) {
            offerFailed(queue, (Request) request);
        }
    }

    @Override
    public void run() {
        boolean locked = false;
        try {
            locked = taskLock.tryLock();
            if (locked) {
                submit();
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (locked) {
                taskLock.unlock();
            }
        }
    }

    @Override
    public Queue getQueue() {
        return queue;
    }

    @Override
    public int getQueueCapacity() {
        return getBufferQueueCapacity();
    }

    private void submit() throws Exception {
        if (queue.isEmpty()) {
            return;
        }

        AtomicLong batchCounter = new AtomicLong();
        AtomicLong batchIndexCounter = new AtomicLong();  // 批次序号计数器，用于同一 position 保序
        Map<String, Response> map = new HashMap<>();
        WriterResponse maxOffsetResponse = null;
        int bufferPullCount = getBufferPullCount();
        while (!queue.isEmpty()) {
            Request poll = queue.poll();
            String key = getPartitionKey(poll);
            Response response = map.compute(key, (k, v) -> {
                if (v == null) {
                    try {
                        Response newResponse = responseClazz.newInstance();
                        // 为每个新创建的批次分配序号 (需要强制类型转换)
                        if (newResponse instanceof WriterResponse) {
                            ((WriterResponse) newResponse).setBatchIndex((int) batchIndexCounter.getAndIncrement());
                        }
                        return newResponse;
                    } catch (Exception e) {
                        throw new ParserException(e);
                    }
                }
                return v;
            });
            partition(poll, response);
            batchCounter.incrementAndGet();

            // 记录最大 offset（queue 是 FIFO，最后一个 response 的 offset 最大）
            if (response instanceof WriterResponse) {
                maxOffsetResponse = (WriterResponse) response;
            }

            // 当达到批次大小时，处理当前批次
            if (batchCounter.get() >= bufferPullCount) {
                process(map, maxOffsetResponse);
                map.clear();
                meter(timeRegistry, batchCounter.get());
                batchCounter.set(0);
                map = new HashMap<>();
                maxOffsetResponse = null;
            }

            Request next = queue.peek();
            if (null != next && skipPartition(next, response)) {
                break;
            }
        }

        // 处理剩余未达到批次大小的数据
        if (!map.isEmpty()) {
            process(map, maxOffsetResponse);
            map.clear();
            meter(timeRegistry, batchCounter.get());
        }

        map = null;
        batchCounter = null;
    }

    // ===== 从 ConfigRegistry 动态读取缓冲区参数 =====

    /**
     * 获取当前生效的缓冲区参数。
     * 子类实现此方法，指定读取 general 还是 storage 缓冲区配置。
     */
    protected abstract BufferProps getBufferProps();

    protected int getBufferPullCount() {
        return getBufferProps().getBufferPullCount();
    }

    protected int getBufferQueueCapacity() {
        return getBufferProps().getBufferQueueCapacity();
    }

    protected int getBufferPeriodMillisecond() {
        return getBufferProps().getBufferPeriodMillisecond();
    }

    /**
     * 获取定时任务的 key
     */
    public String getTaskKey() {
        return taskKey;
    }

    /**
     * 设置定时任务的 key（用于手动管理定时任务）
     */
    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    /**
     * 停止执行器（停止定时任务）
     */
    public void stop() {
        if (taskKey != null && scheduledTaskService != null) {
            scheduledTaskService.stop(taskKey);
        }
    }

    /**
     * ADR-0031：热重载（排空-切换）。
     * <p>
     * ① 标记 draining=true → offer() 阻塞<br>
     * ② 主动触发一次消费（不等调度周期）<br>
     * ③ 等待队列排空（直到完成）<br>
     * ④ 替换队列<br>
     * ⑤ 清除 draining 标志 → 唤醒 offer() 恢复<br>
     * ⑥ 重注册定时调度（周期可能也变了）
     */
    public void reload() {
        // 未初始化过则无需 reload，下次 offer() 会用新配置初始化
        if (!initialized) {
            logger.info("[{}] 尚未初始化，跳过热重载", getClass().getSimpleName());
            return;
        }

        // ① 标记只出不进
        draining = true;

        try {
            // ② 主动触发一次消费，不等调度周期
            run();

            // ③ 等待队列排空
            while (!queue.isEmpty()) {
                // 消费者已停止（如 unbind 并发执行），无人消费队列，跳出避免死等
                if (!isRunning()) {
                    logger.info("[{}] 任务已停止，跳过热重载，任务启动时会使用新配置创建队列", getClass().getSimpleName());
                    return;  // finally 会清理 draining，reschedule 不会执行
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 不 break，继续等待直到排空
                }
            }

            // ④ 队列已空，替换
            int newCapacity = getBufferQueueCapacity();
            this.queue = new LinkedBlockingQueue<>(newCapacity);
            logger.info("[{}] 队列已替换: capacity={}", getClass().getSimpleName(), newCapacity);
        } finally {
            // ⑤ 恢复，唤醒等待的 offer() 线程
            draining = false;
            drainingLock.lock();
            try {
                drainingDone.signalAll();
            } finally {
                drainingLock.unlock();
            }
        }

        // ⑥ 重注册调度（周期可能也变了）
        reschedule();
    }

    /**
     * ADR-0031：重新注册定时调度（用新周期），队列不动。
     */
    public void reschedule() {
        if (taskKey != null) {
            scheduledTaskService.stop(taskKey);
        }
        if (taskKey == null) {
            taskKey = UUIDUtil.getUUID();
        }
        scheduledTaskService.start(taskKey, getBufferPeriodMillisecond(), this);
        logger.info("[{}] 调度已重新注册: period={}ms",
                getClass().getSimpleName(), getBufferPeriodMillisecond());
    }

    /**
     * 设置定时任务服务（用于手动注入依赖）
     */
    public void setScheduledTaskService(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    /**
     * 设置 ProfileComponent（用于手动注入依赖）
     */
    public void setProfileComponent(ProfileComponent profileComponent) {
        this.profileComponent = profileComponent;
    }

    /**
     * 设置 TimeRegistry（用于手动注入依赖）
     */
    public void setTimeRegistry(TimeRegistry timeRegistry) {
        this.timeRegistry = timeRegistry;
    }

    /**
     * 设置 ConfigRegistry（用于手动注入依赖）
     */
    public void setConfigRegistry(ConfigRegistry configRegistry) {
        this.configRegistry = configRegistry;
    }
}
