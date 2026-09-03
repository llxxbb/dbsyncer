/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.checker.impl.system;

import org.dbsyncer.biz.BizException;
import org.dbsyncer.biz.checker.AbstractChecker;
import org.dbsyncer.common.util.BeanUtil;
import org.dbsyncer.common.util.NetworkUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.impl.SystemConfigRegistry;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.model.ConfigModel;
import org.dbsyncer.parser.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2020/1/8 15:17
 */
@Component
public class SystemConfigChecker extends AbstractChecker {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private SystemConfigRegistry configRegistry;

    @Resource
    private LogService logService;

    @Override
    public ConfigModel checkAddConfigModel(Map<String, String> params) throws Exception {
        SystemConfig systemConfig = new SystemConfig();
        systemConfig.setName("系统配置");

        // 修改基本配置
        this.modifyConfigModel(systemConfig, params);

        profileComponent.addConfigModel(systemConfig);
        return systemConfig;
    }

    @Override
    public ConfigModel checkEditConfigModel(Map<String, String> params) {
        logger.info("params:{}", params);
        Assert.notEmpty(params, "Config check params is null.");
        params.put("enableStorageWriteFail", StringUtil.isNotBlank(params.get("enableStorageWriteFail")) ? "true" : "false");
        params.put("enableWatermark", StringUtil.isNotBlank(params.get("enableWatermark")) ? "true" : "false");
        params.put("enableSchemaResolver", StringUtil.isNotBlank(params.get("enableSchemaResolver")) ? "true" : "false");
        params.put("enablePrintTraceInfo", StringUtil.isNotBlank(params.get("enablePrintTraceInfo")) ? "true" : "false");
        String enableCDN = "false";
        if (StringUtil.isNotBlank(params.get("enableCDN"))) {
            if (!NetworkUtil.isInternetAvailable()) {
                throw new BizException("无法访问互联网，不支持开启[CDN静态资源]");
            }
            enableCDN = "true";
        }
        params.put("enableCDN", enableCDN);
        String watermark = params.get("watermark");
        if (StringUtil.isNotBlank(watermark)) {
            Assert.isTrue(watermark.length() <= 64, "允许水印内容最多输入64个字.");
        }
        params.put("watermark", watermark);

        // 校验端口范围（如果传入了 port）
        String portStr = params.get("server.port");
        if (StringUtil.isNotBlank(portStr)) {
            try {
                int port = Integer.parseInt(portStr);
                Assert.isTrue(port >= 1 && port <= 65535, "端口号必须在 1-65535 之间");
            } catch (NumberFormatException e) {
                throw new BizException("端口号格式错误: " + portStr);
            }
        }

        SystemConfig systemConfig = configRegistry.getConfig();
        Assert.notNull(systemConfig, "配置文件为空.");
        BeanUtil.mapToBean(params, systemConfig);

        // 修改基本配置
        this.modifyConfigModel(systemConfig, params);
        return systemConfig;
    }

}