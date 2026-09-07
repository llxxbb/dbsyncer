package org.dbsyncer.parser.config;

import org.dbsyncer.common.config.ExecutorFactory;
import org.dbsyncer.common.config.ConfigRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * ADR-0031：注册 ExecutorFactory，替代原 GeneralBufferConfig / StorageConfig / DispatchTaskConfig。
 */
@Configuration
public class ExecutorFactoryConfig {

    @Resource
    private ConfigRegistry configRegistry;

    @Bean
    public ExecutorFactory executorFactory() {
        return new ExecutorFactory(() -> configRegistry.getConfig().getExecutors());
    }
}
