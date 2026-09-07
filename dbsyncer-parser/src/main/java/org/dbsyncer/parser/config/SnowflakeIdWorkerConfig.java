package org.dbsyncer.parser.config;

import org.dbsyncer.common.config.props.WorkerProps;
import org.dbsyncer.parser.impl.ConfigRegistry;
import org.dbsyncer.storage.impl.SnowflakeIdWorker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-0032：注册 SnowflakeIdWorker Bean。
 * <p>
 * 替代原 {@code @ConfigurationProperties(prefix = "dbsyncer.web.worker")}，
 * 配置统一从 ConfigRegistry 读取（WorkerProps，A 类）。
 */
@Configuration
public class SnowflakeIdWorkerConfig {

    @Bean
    public SnowflakeIdWorker snowflakeIdWorker(ConfigRegistry configRegistry) {
        WorkerProps worker = configRegistry.getConfig().getWorker();
        long id = worker != null && worker.getId() != null ? worker.getId() : 1L;
        long dataCenterId = worker != null && worker.getDataCenterId() != null ? worker.getDataCenterId() : 1L;
        return new SnowflakeIdWorker(id, dataCenterId);
    }
}
