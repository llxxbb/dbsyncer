package org.dbsyncer.parser.config;

import org.dbsyncer.common.config.SchedulerFactory;
import org.dbsyncer.parser.impl.ConfigRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * ADR-0032：注册 SchedulerFactory，替代原 SchedulerConfig。
 */
@Configuration
public class SchedulerFactoryConfig {

    @Resource
    private ConfigRegistry configRegistry;

    @Bean
    public SchedulerFactory schedulerFactory() {
        return new SchedulerFactory(() -> {
            Integer poolSize = configRegistry.getConfig().getMonitor().getSchedulerPoolSize();
            return poolSize != null ? poolSize : Runtime.getRuntime().availableProcessors();
        });
    }
}
