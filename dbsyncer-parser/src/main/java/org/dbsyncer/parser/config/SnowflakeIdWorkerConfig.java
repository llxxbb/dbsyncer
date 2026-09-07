package org.dbsyncer.parser.config;

import org.dbsyncer.common.config.SnowflakeIdWorker;
import org.dbsyncer.common.config.props.WorkerProps;
import org.dbsyncer.parser.impl.ConfigRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-0032：注册 SnowflakeIdWorker Bean。
 * <p>
 * 替代原 {@code @ConfigurationProperties(prefix = "dbsyncer.web.worker")}，
 * 配置统一从 ConfigRegistry 读取（WorkerProps，A 类）。
 * <p>
 * 延迟初始化：Bean 创建时只存 supplier，首次 nextId() 时才读取配置，
 * 确保 ConfigRegistry 已加载完毕。
 */
@Configuration
public class SnowflakeIdWorkerConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(ConfigRegistry configRegistry) {
        return new SnowflakeIdWorker(
            () -> {
                WorkerProps worker = configRegistry.getConfig().getWorker();
                return worker != null && worker.getId() != null ? worker.getId() : 1L;
            },
            () -> {
                WorkerProps worker = configRegistry.getConfig().getWorker();
                return worker != null && worker.getDataCenterId() != null ? worker.getDataCenterId() : 1L;
            }
        );
    }
}
