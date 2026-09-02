package org.dbsyncer.web.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * ADR-0029：运行时配置 EnvironmentPostProcessor。
 * <p>
 * 在 Spring refresh 之前读取 sidecar 文件（{app.home}/config/dbsyncer-runtime.properties），
 * 将其作为最高优先级 PropertySource 注入 Environment。
 * <p>
 * 约束：
 * 1. PropertySource 名称固定为 "dbsyncerRuntimeConfig"（不含 "application"，避免被 StorageSupportConfiguration 捞取）
 * 2. 文件缺失/解析失败 → fail-soft，不阻塞启动
 * 3. 优先级：晚于 ConfigDataEnvironmentPostProcessor（HIGHEST_PRECEDENCE + 10），用 LOWEST_PRECEDENCE 保证
 */
public class RuntimeConfigPropertySource implements EnvironmentPostProcessor, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeConfigPropertySource.class);

    private static final String PROPERTY_SOURCE_NAME = "dbsyncerRuntimeConfig";
    private static final String SIDECAR_FILENAME = "dbsyncer-runtime.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            String appHome = System.getProperty("user.dir");
            File sidecarFile = new File(new File(appHome, "config"), SIDECAR_FILENAME);

            if (!sidecarFile.exists()) {
                logger.info("[dbsyncer] Sidecar 文件不存在: {}，使用 application.properties 默认值", sidecarFile.getAbsolutePath());
                return;
            }

            Properties props = new Properties();
            try (InputStream is = new FileInputStream(sidecarFile)) {
                props.load(is);
            }

            if (props.isEmpty()) {
                logger.info("[dbsyncer] Sidecar 文件为空: {}", sidecarFile.getAbsolutePath());
                return;
            }

            // 摊平为 Map<String, Object>
            Map<String, Object> map = new LinkedHashMap<>();
            for (String key : props.stringPropertyNames()) {
                map.put(key, props.getProperty(key));
            }

            // addFirst：最高优先级，覆盖 application.properties
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
            logger.info("[dbsyncer] 已加载 Sidecar 配置: {} ({} 项)", sidecarFile.getAbsolutePath(), map.size());

        } catch (Exception ex) {
            // L2：绝不能因配置读取失败阻塞启动
            logger.error("[dbsyncer] 运行时配置预读失败，回退 application.properties: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public int getOrder() {
        // 晚于 ConfigDataEnvironmentPostProcessor（HIGHEST_PRECEDENCE + 10）
        return Ordered.LOWEST_PRECEDENCE;
    }
}
