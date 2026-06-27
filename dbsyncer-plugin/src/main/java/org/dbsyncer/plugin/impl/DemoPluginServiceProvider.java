/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.plugin.impl;

import org.dbsyncer.common.config.AppConfig;
import org.dbsyncer.sdk.plugin.PluginContext;
import org.dbsyncer.sdk.spi.PluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public final class DemoPluginServiceProvider implements PluginService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private AppConfig appConfig;

    @Override
    public void postProcessBefore(PluginContext context) {
        logger.info("插件正在处理同步，事件:{}", context.getEvent());
    }

    @Override
    public void convert(PluginContext context) {
        logger.info("插件正在处理{}，事件:{}", context.getModelEnum().getName(), context.getEvent());
    }

    @Override
    public void postProcessAfter(PluginContext context) {
        logger.info("插件后置处理，事件:{}", context.getEvent());
    }

    @Override
    public String getVersion() {
        return appConfig.getVersion();
    }

    public String getName() {
        return "Demo";
    }
}