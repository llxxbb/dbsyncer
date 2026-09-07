package org.dbsyncer.common.config;

import org.dbsyncer.common.CommonException;

import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * 雪花 ID 生成器（支持延迟初始化）。
 * <p>
 * 两种构造方式：
 * <ul>
 *   <li>{@code new SnowflakeIdWorker(id, dataCenterId)} — 直接指定，测试用</li>
 *   <li>{@code new SnowflakeIdWorker(idSupplier, dataCenterIdSupplier)} — 延迟从配置读取</li>
 * </ul>
 * <p>
 * 延迟模式下，首次调用 {@link #nextId()} 时才从 supplier 读取配置，
 * 确保 ConfigRegistry 已加载完毕。
 */
public class SnowflakeIdWorker {

    private LongSupplier idSupplier;
    private LongSupplier dataCenterIdSupplier;

    private volatile boolean initialized;
    private long id;
    private long dataCenterId;

    /** 默认构造（测试用），id=1, dataCenterId=1 */
    public SnowflakeIdWorker() {
        this(1L, 1L);
    }

    /** 直接指定 id 和 dataCenterId */
    public SnowflakeIdWorker(long id, long dataCenterId) {
        this.id = id;
        this.dataCenterId = dataCenterId;
        this.initialized = true;
    }

    /** 延迟初始化，首次 nextId() 时从 supplier 读取配置 */
    public SnowflakeIdWorker(LongSupplier idSupplier, LongSupplier dataCenterIdSupplier) {
        this.idSupplier = idSupplier;
        this.dataCenterIdSupplier = dataCenterIdSupplier;
    }

    // ===== 雪花算法核心 =====

    private long sequence = 0L;
    private long lastTimestamp = -1L;
    private final long twepoch = 1420041600000L;
    private final long workerIdBits = 5L;
    private final long dataCenterIdBits = 5L;
    private final long sequenceBits = 12L;
    private final long workerIdShift = sequenceBits;
    private final long dataCenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + dataCenterIdBits;
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    public SnowflakeId revert(Long id) {
        long workerId = id >> workerIdShift & ~(-1L << workerIdBits);
        long dataCenterId = id >> dataCenterIdShift & ~(-1L << dataCenterIdBits);
        long timestamp = (id >> timestampLeftShift) + twepoch;
        return new SnowflakeId(workerId, dataCenterId, timestamp);
    }

    /**
     * 获得下一个ID (该方法是线程安全的)
     */
    public synchronized long nextId() {
        ensureInitialized();

        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new CommonException(String.format("Clock moved backwards.  Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - twepoch) << timestampLeftShift)
                | (dataCenterId << dataCenterIdShift)
                | (id << workerIdShift)
                | sequence;
    }

    /** DCL 延迟初始化 */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    this.id = idSupplier.getAsLong();
                    this.dataCenterId = dataCenterIdSupplier.getAsLong();
                    this.initialized = true;
                }
            }
        }
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return Instant.now().toEpochMilli();
    }

    public long getId() {
        ensureInitialized();
        return id;
    }

    public long getDataCenterId() {
        ensureInitialized();
        return dataCenterId;
    }

    static class SnowflakeId {
        private final long workerId;
        private final long dataCenterId;
        private final long timestamp;

        public SnowflakeId(long workerId, long dataCenterId, long timestamp) {
            this.workerId = workerId;
            this.dataCenterId = dataCenterId;
            this.timestamp = timestamp;
        }

        public long getWorkerId() { return workerId; }
        public long getDataCenterId() { return dataCenterId; }
        public long getTimestamp() { return timestamp; }
    }
}
