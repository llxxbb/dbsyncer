package org.dbsyncer.biz.impl;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.parser.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * ADR-0029 §2.3：Sidecar 文件写入器。
 * <p>
 * 保存配置后全量重写 {app.home}/config/dbsyncer-runtime.properties。
 * sidecar 是下次启动的输入，由 EPP（RuntimeConfigPropertySource）读取。
 * <p>
 * 约束：
 * - 路径：{app.home}/config/dbsyncer-runtime.properties
 * - app.home = System.getProperty("user.dir")
 * - 全量重写（非增量），确保 sidecar 与 DB 一致
 * - 失败不阻塞主流程（仅告警）
 */
@Component
public class SidecarPropertyWriter {

    private static final Logger logger = LoggerFactory.getLogger(SidecarPropertyWriter.class);

    private static final String SIDECAR_DIR = "config";
    private static final String SIDECAR_FILENAME = "dbsyncer-runtime.properties";

    /**
     * 全量重写 sidecar 文件。
     *
     * @param cfg 当前 SystemConfig
     */
    public void rewrite(SystemConfig cfg) {
        try {
            String appHome = System.getProperty("user.dir");
            File sidecarDir = new File(appHome, SIDECAR_DIR);
            if (!sidecarDir.exists()) {
                sidecarDir.mkdirs();
            }
            File sidecarFile = new File(sidecarDir, SIDECAR_FILENAME);

            Properties props = new Properties();

            // B 类：执行器
            ExecutorProps exec = cfg.getExecutors();
            if (exec != null) {
                putPoolProps(props, "dbsyncer.parser.general", exec.getGeneral());
                putPoolProps(props, "dbsyncer.storage", exec.getStorage());
                putPoolProps(props, "dbsyncer.common.dispatch", exec.getDispatch());
                if (exec.getSchedulerPoolSize() != null) {
                    props.setProperty("dbsyncer.web.scheduler.pool-size", String.valueOf(exec.getSchedulerPoolSize()));
                }
            }

            // B 类：缓冲区
            BufferProps buf = cfg.getBuffers();
            if (buf != null) {
                putBufferProps(props, "dbsyncer.parser.general", buf.getGeneral());
                putBufferProps(props, "dbsyncer.storage", buf.getStorage());
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(sidecarFile)) {
                props.store(fos, "DbSyncer Runtime Config (auto-generated, do not edit manually)");
            }

            logger.info("Sidecar 已重建: {}", sidecarFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Sidecar 重建失败", e);
        }
    }

    private void putPoolProps(Properties props, String prefix, PoolProps pool) {
        if (pool == null) {
            return;
        }
        if (pool.getThreadCoreSize() != null) {
            props.setProperty(prefix + ".thread-core-size", String.valueOf(pool.getThreadCoreSize()));
        }
        if (pool.getMaxThreadSize() != null) {
            props.setProperty(prefix + ".max-thread-size", String.valueOf(pool.getMaxThreadSize()));
        }
        if (pool.getThreadQueueCapacity() != null) {
            props.setProperty(prefix + ".thread-queue-capacity", String.valueOf(pool.getThreadQueueCapacity()));
        }
    }

    private void putBufferProps(Properties props, String prefix, BufferActuatorProps buf) {
        if (buf == null) {
            return;
        }
        if (buf.getBufferWriterCount() != null) {
            props.setProperty(prefix + ".buffer-writer-count", String.valueOf(buf.getBufferWriterCount()));
        }
        if (buf.getBufferPullCount() != null) {
            props.setProperty(prefix + ".buffer-pull-count", String.valueOf(buf.getBufferPullCount()));
        }
        if (buf.getBufferQueueCapacity() != null) {
            props.setProperty(prefix + ".buffer-queue-capacity", String.valueOf(buf.getBufferQueueCapacity()));
        }
        if (buf.getBufferPeriodMillisecond() != null) {
            props.setProperty(prefix + ".buffer-period-millisecond", String.valueOf(buf.getBufferPeriodMillisecond()));
        }
    }
}
