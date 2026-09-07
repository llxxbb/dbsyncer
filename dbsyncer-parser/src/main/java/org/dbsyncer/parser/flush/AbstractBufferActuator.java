/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.parser.flush;

import org.dbsyncer.common.config.props.BufferActuatorProps;
import org.dbsyncer.common.metric.TimeRegistry;
import org.dbsyncer.common.scheduled.ScheduledTaskJob;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.util.UUIDUtil;
import org.dbsyncer.common.ParserException;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.model.Meta;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 任务缓存执行器
 * <p>1. 任务优先进入缓存队列
 * <p>2. 将任务分区合并，批量执行
 * <p>
 * ADR-0031：不再依赖 BufferActuatorConfig，改为从 ConfigRegistry 动态读取缓冲区参数。
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
     * 驱动是否运行中
     *
     * @param request
     * @return
     */
    public boolean isRunning(BufferRequest request) {
        Meta meta = profileComponent.getMeta(request.getMetaId());
        return meta != null && MetaEnum.isRunning(meta.getState());
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
        if (queue.offer((Request) request)) {
            return;
        }
        if (isRunning(request)) {
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
    protected abstract BufferActuatorProps getBufferProps();

    protected int getBufferWriterCount() {
        return getBufferProps().getBufferWriterCount();
    }

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
