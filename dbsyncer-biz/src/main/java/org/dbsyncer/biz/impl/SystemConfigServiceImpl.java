/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.apache.commons.io.FileUtils;
import org.dbsyncer.biz.SystemConfigService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.checker.Checker;
import org.dbsyncer.biz.checker.impl.system.SystemConfigTabChecker;
import org.dbsyncer.biz.vo.SystemConfigVo;
import org.dbsyncer.common.config.AppConfig;
import org.dbsyncer.common.config.SystemTabEnum;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.model.ConfigModel;
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.manager.impl.PreloadTemplate;
import org.dbsyncer.common.enums.FileSuffixEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.core.env.Environment;
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
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigServiceImpl.class);

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private PreloadTemplate preloadTemplate;

    @Resource
    private Checker systemConfigChecker;

    @Resource
    private SystemConfigTabChecker systemConfigTabChecker;

    @Resource
    private LogService logService;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private RuntimeConfigService runtimeConfigService;

    @Resource
    private RuntimeConfigValidator runtimeConfigValidator;

    @Resource
    private SelfRestartService selfRestartService;

    @Resource
    private SidecarPropertyWriter sidecarPropertyWriter;

    @Resource
    private Environment environment;

    @Override
    public String edit(Map<String, String> params) throws Exception {
        ConfigModel model = systemConfigChecker.checkEditConfigModel(params);
        profileComponent.editConfigModel(model);
        return "修改成功.";
    }

    @Override
    public SystemConfigVo getSystemConfigVo() throws Exception {
        SystemConfigVo systemConfigVo = new SystemConfigVo();
        BeanUtils.copyProperties(getSystemConfig(), systemConfigVo);
        systemConfigVo.setWatermark(getWatermark(systemConfigVo));
        return systemConfigVo;
    }

    @Override
    public SystemConfig getSystemConfig() throws Exception {
        SystemConfig config = profileComponent.getSystemConfig();
        if (null != config) {
            return config;
        }

        synchronized (this) {
            config = profileComponent.getSystemConfig();
            if (null == config) {
                config = (SystemConfig) systemConfigChecker.checkAddConfigModel(new HashMap<>());
            }
            return config;
        }
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
        return StringUtil.isNotBlank(systemConfig.getWatermark()) ? systemConfig.getWatermark() : appConfig.getName() + "-${username}<br />" + appConfig.getCompany();
    }

    // ===== ADR-0029 Tab 接口 =====

    /**
     * M1 修订：editTab 加 synchronized 锁，防止并发保存丢失更新。
     */
    @Override
    public synchronized Map<String, Object> editTab(String tab, String payload) throws Exception {
        SystemTabEnum tabEnum = SystemTabEnum.of(tab);
        Assert.notNull(tabEnum, "未知的 Tab: " + tab);

        // 反序列化 payload 为对应 Props 类型
        Object props = JsonUtil.jsonToObj(payload, tabEnum.getPropsType());
        Assert.notNull(props, "Tab payload 解析失败");

        // 结构/必填校验
        systemConfigTabChecker.check(tabEnum, props);

        // 前置校验（L4：null 安全）
        List<String> errors = validateTab(tabEnum, props);
        if (!errors.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("errors", errors);
            result.put("restartRequired", false);
            return result;
        }

        // 读-改-写（引用语义，C4）
        SystemConfig cfg = getSystemConfig();

        // 密钥脱敏保护：notify Tab 保存时，若密码字段仍为掩码则保留原值
        if (tabEnum == SystemTabEnum.NOTIFY) {
            protectSecrets(cfg, (NotifyProps) props);
        }

        // 设置子对象到 SystemConfig
        setTabProps(cfg, tabEnum, props);

        // ① DB 落库
        profileComponent.editConfigModel(cfg);

        // ② 重建 sidecar（下次启动的输入）
        sidecarPropertyWriter.rewrite(cfg);

        // ③ C 类运行期回写 / B 类自重启
        boolean restartRequired = tabEnum.isRestartRequired();
        if (tabEnum.isRuntimeApply() && !restartRequired) {
            runtimeConfigService.applyCClass(cfg);
        }

        // B 类：自重启（如果有进程守护）
        if (restartRequired && selfRestartService.isSelfRestartPossible()) {
            java.util.List<String> changedKeys = new ArrayList<>();
            changedKeys.add(tabEnum.getKey());
            selfRestartService.scheduleRestart(changedKeys, "admin");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("restartRequired", restartRequired);
        result.put("selfRestart", restartRequired && selfRestartService.isSelfRestartPossible());
        result.put("result", restartRequired
                ? (selfRestartService.isSelfRestartPossible()
                        ? "配置已保存，应用即将自动重启。"
                        : "配置已保存。修改后需重启应用才能生效。")
                : "配置已保存并立即生效。");
        return result;
    }

    @Override
    public Map<String, Object> getTabs() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("basic", runtimeConfigService.getEffectiveBasic());
        result.put("executors", runtimeConfigService.getEffectiveExecutor());
        result.put("buffers", runtimeConfigService.getEffectiveBuffers());
        result.put("retry", runtimeConfigService.getEffectiveRetry());
        result.put("notify", runtimeConfigService.getEffectiveNotify());
        result.put("error", runtimeConfigService.getEffectiveError());
        return result;
    }

    @Override
    public String resetTab(String tab) throws Exception {
        SystemTabEnum tabEnum = SystemTabEnum.of(tab);
        Assert.notNull(tabEnum, "未知的 Tab: " + tab);

        SystemConfig cfg = getSystemConfig();
        setTabProps(cfg, tabEnum, null);
        profileComponent.editConfigModel(cfg);
        sidecarPropertyWriter.rewrite(cfg);

        return "已恢复系统默认。重启后生效。";
    }

    @Override
    public Map<String, Object> getServerInfo() throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        // A 类配置从 Environment 现取（不读 Bean，不读 DB）
        info.put("serverPort", environment.getProperty("server.port", "18686"));
        info.put("sslEnabled", environment.getProperty("server.ssl.enabled", "false"));
        info.put("httpPort", environment.getProperty("server.http.port", "0"));
        info.put("httpRedirect", environment.getProperty("server.http.redirect", "false"));
        info.put("contextPath", environment.getProperty("server.servlet.context-path", "/"));
        info.put("sessionTimeout", environment.getProperty("server.servlet.session.timeout", "1800"));
        info.put("storageType", environment.getProperty("dbsyncer.storage.type", "disk"));

        // 数据库信息脱敏
        String dbUrl = environment.getProperty("dbsyncer.storage.mysql.url", "");
        info.put("databaseUrl", maskIp(dbUrl));
        String dbUser = environment.getProperty("dbsyncer.storage.mysql.username", "");
        info.put("databaseUsername", maskUsername(dbUser));
        // 密码不展示

        info.put("workerId", environment.getProperty("dbsyncer.web.worker.id", "1"));
        info.put("helpdocPath", environment.getProperty("helpdoc.path", "./helpdocs"));
        info.put("logLevel", environment.getProperty("logging.level.root", "info"));
        return info;
    }

    // ===== 内部方法 =====

    private List<String> validateTab(SystemTabEnum tabEnum, Object props) {
        switch (tabEnum) {
            case BASIC:
                return runtimeConfigValidator.validate((BasicProps) props);
            case EXECUTORS:
                return runtimeConfigValidator.validate((ExecutorProps) props);
            case BUFFERS:
                return runtimeConfigValidator.validate((BufferProps) props);
            case RETRY:
                return runtimeConfigValidator.validate((RetryProps) props);
            case NOTIFY:
                return runtimeConfigValidator.validate((NotifyProps) props);
            case ERROR:
                return runtimeConfigValidator.validate((ErrorProps) props);
            default:
                return Collections.emptyList();
        }
    }

    private void setTabProps(SystemConfig cfg, SystemTabEnum tabEnum, Object props) {
        switch (tabEnum) {
            case BASIC:
                cfg.setBasic((BasicProps) props);
                break;
            case EXECUTORS:
                cfg.setExecutors((ExecutorProps) props);
                break;
            case BUFFERS:
                cfg.setBuffers((BufferProps) props);
                break;
            case RETRY:
                cfg.setRetry((RetryProps) props);
                break;
            case NOTIFY:
                cfg.setNotify((NotifyProps) props);
                break;
            case ERROR:
                cfg.setError((ErrorProps) props);
                break;
            default:
                break;
        }
    }

    /**
     * 密钥脱敏保护：保存时若密码字段仍为掩码，则保留原值。
     */
    private void protectSecrets(SystemConfig cfg, NotifyProps incoming) {
        NotifyProps existing = cfg.getNotify();
        if (existing == null) {
            return;
        }
        // 邮件密码
        if (incoming.getMail() != null && existing.getMail() != null) {
            String pwd = incoming.getMail().getPassword();
            if ("********".equals(pwd)) {
                incoming.getMail().setPassword(existing.getMail().getPassword());
            }
        }
        // 钉钉密钥
        if (incoming.getDingtalk() != null && existing.getDingtalk() != null) {
            String secret = incoming.getDingtalk().getSecret();
            if (secret != null && secret.endsWith("****") && secret.length() > 4) {
                incoming.getDingtalk().setSecret(existing.getDingtalk().getSecret());
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
