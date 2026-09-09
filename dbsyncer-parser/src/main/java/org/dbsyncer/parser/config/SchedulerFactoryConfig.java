package org.dbsyncer.parser.config;

import org.dbsyncer.common.config.SchedulerFactory;
import org.dbsyncer.common.config.ConfigRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ADR-0032：注册 SchedulerFactory，替代原 SchedulerConfig。
 */
@Configuration
public class SchedulerFactoryConfig {

    @Bean
    public SchedulerFactory schedulerFactory(ConfigRegistry configRegistry) {
        return new SchedulerFactory(() -> configRegistry.getConfig().getMonitor().getSchedulerPoolSize());
    }
}
