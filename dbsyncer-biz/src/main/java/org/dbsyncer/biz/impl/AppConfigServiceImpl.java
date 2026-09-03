/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.dbsyncer.biz.AppConfigService;
import org.dbsyncer.biz.SystemConfigService;
import org.dbsyncer.parser.impl.SystemConfigRegistry;
import org.dbsyncer.biz.vo.VersionVo;
import org.dbsyncer.common.config.AppConfig;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2024-05-12 01:08
 */
@Component
public class AppConfigServiceImpl implements AppConfigService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private AppConfig appConfig;

    private SystemConfigService systemConfigService;

    @Resource
    private SystemConfigRegistry configRegistry;

    public static final String WATERMARK_USERNAME = "${username}";

    public AppConfigServiceImpl(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService; 
        // 删除了 getSystemConfig() 调用，避免在 bean 实例化阶段创建重复配置
        // 配置初始化由 SingletonConfigBootstrap 在预加载完成后统一处理
        logger.info("----AppConfigServiceImpl init----");
    }

    @Override
    public VersionVo getVersionInfo(String username) throws Exception {
        VersionVo versionVo = new VersionVo(appConfig.getName(), appConfig.getCopyright());
        // 是否启用水印
        SystemConfig systemConfig = configRegistry.getConfig();
        if (systemConfig.getBasic().getEnableWatermark()) {
            String watermark = systemConfigService.getWatermark(systemConfig);
            watermark = StringUtil.replace(watermark, WATERMARK_USERNAME, username);
            versionVo.setWatermark(watermark);
        }
        return versionVo;
    }
}