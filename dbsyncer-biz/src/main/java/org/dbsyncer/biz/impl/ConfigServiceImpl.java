/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.apache.commons.io.FileUtils;
import org.dbsyncer.biz.ConfigService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.vo.SystemConfigVo;
import org.dbsyncer.biz.vo.VersionVo;
import org.dbsyncer.common.config.AppConfig;
import org.dbsyncer.common.config.ConfigEncryptor;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.dbsyncer.common.config.SystemTabEnum;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.NetworkUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.impl.ConfigMigrationService;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.common.model.ConfigModel;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.manager.impl.PreloadTemplate;
import org.dbsyncer.common.enums.FileSuffixEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/10/17 23:20
 */
@Service
public class ConfigServiceImpl implements ConfigService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigServiceImpl.class);

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ConfigRegistry configRegistry;

    @Resource
    private ConfigMigrationService configMigrationService;

    @Resource
    private PreloadTemplate preloadTemplate;

    @Resource
    private LogService logService;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private SelfRestartService selfRestartService;

    @Resource
    private List<ConfigChangeListener> configChangeListeners;

    @Override
    @Deprecated
    public String edit(Map<String, String> params) throws Exception {
        SystemConfig cfg = getSystemConfig();
        BeanUtils.copyProperties(params, cfg);
        profileComponent.editConfigModel(cfg);
        return "修改成功.";
    }

    @Override
    public SystemConfigVo getSystemConfigVo() throws Exception {
        SystemConfigVo systemConfigVo = new SystemConfigVo();
        BeanUtils.copyProperties(getSystemConfig(), systemConfigVo);
        systemConfigVo.getBasic().setWatermark(getWatermark(systemConfigVo));
        return systemConfigVo;
    }

    @Override
    public SystemConfig getSystemConfig() throws Exception {
        return configRegistry.getConfig();
    }

    @Override
    public List<ConfigModel> getConfigModelAll() throws Exception {
        List<ConfigModel> list = new ArrayList<>();
        list.add(getSystemConfig());
        list.add(userConfigService.getUserConfig());
        profileComponent.getConnectorAll().forEach(config -> list.add(config));
        profileComponent.getMappingAll().forEach(config -> list.add(config));
        profileComponent.getMetaAll().forEach(config -> list.add(config));
        return list;
    }

    @Override
    public void checkFileSuffix(String filename) {
        Assert.hasText(filename, "the config filename is null.");
        String suffix = filename.substring(filename.lastIndexOf(".") + 1, filename.length());
        FileSuffixEnum fileSuffix = FileSuffixEnum.getFileSuffix(suffix);
        Assert.notNull(fileSuffix, "Illegal file suffix");
        Assert.isTrue(FileSuffixEnum.JSON == fileSuffix, String.format("不正确的文件扩展名 \"%s\"，只支持 \"%s\" 的文件扩展名。", filename, FileSuffixEnum.JSON.getName()));
    }

    @Override
    public void refreshConfig(File file) {
        Assert.notNull(file, "the config file is null.");
        try {
            List<String> lines = FileUtils.readLines(file, Charset.defaultCharset());
            if (!CollectionUtils.isEmpty(lines)) {
                StringBuilder json = new StringBuilder();
                lines.forEach(json::append);
                preloadTemplate.reload(json.toString());
            }
        } catch (IOException e) {
            logService.log(null, LogType.IMPORT, LogLevel.INFO, "系统配置导入失败：" + e.getMessage());
        } finally {
            FileUtils.deleteQuietly(file);
        }
    }

    @Override
    public String getWatermark(SystemConfig systemConfig) {
        return StringUtil.isNotBlank(systemConfig.getBasic().getWatermark()) ? systemConfig.getBasic().getWatermark() : appConfig.getName() + "-${username}<br />" + appConfig.getCompany();
    }

    // ===== ADR-0029 Tab 接口 =====

    /**
     * M1 修订：editTab 加 synchronized 锁，防止并发保存丢失更新。
     * <p>
     * 返回成功消息字符串，校验失败抛 IllegalArgumentException。
     * Controller 负责包装为 RestResult。
     */
    @Override
    public synchronized String editTab(String tab, String payload) throws Exception {
        SystemTabEnum tabEnum = SystemTabEnum.of(tab);
        Assert.notNull(tabEnum, "未知的 Tab: " + tab);

        // 反序列化 payload 为对应 Props 类型
        Object props = JsonUtil.jsonToObj(payload, tabEnum.getPropsType());
        Assert.notNull(props, "Tab payload 解析失败");

        // 数据范围校验（委托给 Props 自身）
        List<String> errors = validateTab(tabEnum, props);

        // CDN 特殊校验（涉及外部状态，保留在 Service 层）
        if (tabEnum == SystemTabEnum.BASIC) {
            BasicProps basic = (BasicProps) props;
            if (Boolean.TRUE.equals(basic.getEnableCDN()) && !NetworkUtil.isInternetAvailable()) {
                errors.add("无法访问互联网，不支持开启[CDN静态资源]");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("校验失败: " + String.join(", ", errors));
        }

        // 深拷贝 mergedConfig，避免直接修改 live 引用（C1 修复）
        SystemConfig original = getSystemConfig();
        SystemConfig cfg = JsonUtil.jsonToObj(JsonUtil.objToJson(original), SystemConfig.class);

        // 敏感字段保护：前端传回掩码值/空值时从 original 恢复（仅当前 Tab）
        if (props instanceof EnvironmentLoadable) {
            cfg.restoreMaskedSensitiveFields(tabEnum, (EnvironmentLoadable) props, original);
        }

        // 设置子对象到 SystemConfig
        setTabProps(cfg, tabEnum, props);

        // ① DB 落库（加密存储）
        ConfigEncryptor encryptor = ConfigEncryptor.getInstance();
        if (encryptor != null && encryptor.isAvailable()) {
            // 再深拷贝一份用于加密存储，避免加密污染 cfg
            SystemConfig toStore = JsonUtil.jsonToObj(JsonUtil.objToJson(cfg), SystemConfig.class);
            toStore.encryptSensitiveFields(encryptor);
            profileComponent.editConfigModel(toStore);
        } else {
            profileComponent.editConfigModel(cfg);
        }

        // ② 通知 Registry 重新合并（cfg 此时是干净的待存副本，不是 live 引用）
        configRegistry.onDbConfigChanged(cfg);

        // ④ 通知所有监听器更新 Bean 状态
        notifyListeners();

        // ⑤ B 类热重载（ADR-0031）
        boolean reloadRequired = tabEnum.isRestartRequired();

        if (reloadRequired) {
            java.util.List<String> changedKeys = new ArrayList<>();
            changedKeys.add(tabEnum.getKey());
            selfRestartService.scheduleRestart(changedKeys, "admin");
        }

        return reloadRequired ? "配置已保存，正在热重载..." : "配置已保存并立即生效。";
    }

    @Override
    public Map<String, Object> getTabs() throws Exception {
        SystemConfig config = configRegistry.getConfig();
        Map<String, Object> result = new LinkedHashMap<>();
        for (SystemTabEnum tab : SystemTabEnum.getEditableTabs()) {
            Object props = tab.getProps(config);
            // 敏感字段掩码：委托 Props 自身的 forDisplay()
            if (props instanceof EnvironmentLoadable) {
                props = ((EnvironmentLoadable) props).forDisplay();
            }
            result.put(tab.getKey(), props);
        }
        return result;
    }

    @Override
    public String resetTab(String tab) throws Exception {
        SystemTabEnum tabEnum = SystemTabEnum.of(tab);
        Assert.notNull(tabEnum, "未知的 Tab: " + tab);

        SystemConfig cfg = getSystemConfig();
        setTabProps(cfg, tabEnum, null);
        profileComponent.editConfigModel(cfg);
        configRegistry.onDbConfigChanged(cfg);
        notifyListeners();

        return "已恢复系统默认。重启后生效。";
    }

    @Override
    public Map<String, Object> getServerInfo() throws Exception {
        SystemConfig config = configRegistry.getConfig();
        Map<String, Object> info = new LinkedHashMap<>();

        // A 类配置只来自 Environment（不落库），默认值已由 Props 字段初值担保
        ServerProps server = config.getServer();
        if (server != null) {
            info.put("serverPort", server.getPort());
            info.put("sslEnabled", server.getSslEnabled());
            info.put("httpPort", server.getHttpPort());
            info.put("httpRedirect", server.getHttpRedirect());
            info.put("contextPath", server.getContextPath() != null ? server.getContextPath() : "/");
            info.put("sessionTimeout", server.getSessionTimeout());
        }

        StorageConnectionProps storageConn = config.getStorageConn();
        if (storageConn != null) {
            info.put("storageType", storageConn.getType() != null ? storageConn.getType() : "disk");
            // 数据库信息脱敏
            String dbUrl = storageConn.getMysqlUrl() != null ? storageConn.getMysqlUrl() : "";
            info.put("databaseUrl", maskIp(dbUrl));
            String dbUser = storageConn.getMysqlUsername() != null ? storageConn.getMysqlUsername() : "";
            info.put("databaseUsername", maskUsername(dbUser));
            // 密码不展示
        }

        WorkerProps worker = config.getWorker();
        if (worker != null) {
            info.put("workerId", worker.getId());
        }

        HelpdocProps helpdoc = config.getHelpdoc();
        if (helpdoc != null) {
            info.put("helpdocPath", helpdoc.getPath() != null ? helpdoc.getPath() : "./helpdocs");
        }

        LoggingProps logging = config.getLogging();
        if (logging != null) {
            info.put("logLevel", logging.getLevelRoot() != null ? logging.getLevelRoot() : "info");
        }

        return info;
    }

    @Override
    public VersionVo getVersionInfo(String username) throws Exception {
        VersionVo versionVo = new VersionVo(appConfig.getName(), appConfig.getCopyright());
        // 是否启用水印
        SystemConfig systemConfig = configRegistry.getConfig();
        if (systemConfig.getBasic().getEnableWatermark()) {
            String watermark = getWatermark(systemConfig);
            watermark = StringUtil.replace(watermark, "${username}", username);
            versionVo.setWatermark(watermark);
        }
        return versionVo;
    }

    // ===== 内部方法 =====

    private List<String> validateTab(SystemTabEnum tabEnum, Object props) {
        if (props instanceof EnvironmentLoadable) {
            return ((EnvironmentLoadable) props).validate();
        }
        return Collections.emptyList();
    }

    private void setTabProps(SystemConfig cfg, SystemTabEnum tabEnum, Object props) {
        tabEnum.setProps(cfg, props);
    }

    /**
     * 通知所有配置变更监听器。
     */
    private void notifyListeners() {
        if (configChangeListeners == null) return;
        SystemConfig config = configRegistry.getConfig();
        if (config == null) return;
        for (ConfigChangeListener listener : configChangeListeners) {
            try {
                listener.onConfigChanged(config);
            } catch (Exception e) {
                logger.error("配置变更通知失败: {}", listener.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * IP 段脱敏
     */
    private String maskIp(String url) {
        if (StringUtil.isBlank(url)) {
            return url;
        }
        // 简单脱：将 jdbc:mysql://xxx:port 中的 IP 段替换为 ***
        return url.replaceAll("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})", "***.***.***.***");
    }

    /**
     * 用户名脱敏
     */
    private String maskUsername(String username) {
        if (StringUtil.isBlank(username) || username.length() <= 4) {
            return "****";
        }
        return username.substring(0, 4) + "****";
    }
}
