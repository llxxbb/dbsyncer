package org.dbsyncer.common.config;

import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.model.SystemConfig;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tab 元数据单一来源。
 * <p>
 * 每个枚举项对应 {@link SystemConfig} 中的一个子对象 Tab，
 * 包含分类、标题、Props 类型、getter/setter 函数引用。
 * <p>
 * 驱动场景：
 * <ul>
 *   <li>UI 渲染（getTabs）</li>
 *   <li>配置保存（editTab → setTabProps）</li>
 *   <li>配置校验（validateTab）</li>
 *   <li>启动期对照表（ConfigBootstrap）</li>
 *   <li>三源合并（ConfigRegistry.merge）</li>
 * </ul>
 */
public enum SystemTabEnum {

    // ===== A 类：环境只读（不落库，不可通过 UI 修改） =====

    SERVER(ConfigCategory.A, "A类：服务器配置",
            SystemConfig::getServer, (c, p) -> c.setServer((ServerProps) p), ServerProps.class),

    STORAGE_CONN(ConfigCategory.A, "A类：存储连接配置",
            SystemConfig::getStorageConn, (c, p) -> c.setStorageConn((StorageConnectionProps) p), StorageConnectionProps.class),

    LOGGING(ConfigCategory.A, "A类：日志配置",
            SystemConfig::getLogging, (c, p) -> c.setLogging((LoggingProps) p), LoggingProps.class),

    HELPDOC(ConfigCategory.A, "A类：帮助文档配置",
            SystemConfig::getHelpdoc, (c, p) -> c.setHelpdoc((HelpdocProps) p), HelpdocProps.class),

    WORKER(ConfigCategory.A, "A类：Worker配置",
            SystemConfig::getWorker, (c, p) -> c.setWorker((WorkerProps) p), WorkerProps.class),

    LICENSE(ConfigCategory.A, "A类：许可证配置",
            SystemConfig::getLicense, (c, p) -> c.setLicense((LicenseProps) p), LicenseProps.class),

    /** 加密密钥配置（A 类：环境敏感，只读展示密钥来源） */
    ENCRYPT_KEY(ConfigCategory.A, "A类：加密密钥配置",
            SystemConfig::getEncryptKey, (c, p) -> c.setEncryptKey((EncryptKeyProps) p), EncryptKeyProps.class),

    // ===== 基础设置 =====

    BASIC(ConfigCategory.C, "基础配置",
            SystemConfig::getBasic, (c, p) -> c.setBasic((BasicProps) p), BasicProps.class),

    // ===== B 类：热重载 =====

    EXECUTORS(ConfigCategory.B, "B类：执行器配置",
            SystemConfig::getExecutors, (c, p) -> c.setExecutors((ExecutorProps) p), ExecutorProps.class),

    BUFFERS(ConfigCategory.B, "B类：缓冲区配置",
            SystemConfig::getBuffers, (c, p) -> c.setBuffers((BufferProps) p), BufferProps.class),

    // ===== C 类：立即生效 =====

    MONITOR(ConfigCategory.C, "C类：监控定时任务配置",
            SystemConfig::getMonitor, (c, p) -> c.setMonitor((MonitorProps) p), MonitorProps.class),

    RETRY(ConfigCategory.C, "C类：重试策略配置",
            SystemConfig::getRetry, (c, p) -> c.setRetry((RetryProps) p), RetryProps.class),

    NOTIFY(ConfigCategory.C, "C类：通知通道配置",
            SystemConfig::getNotify, (c, p) -> c.setNotify((NotifyProps) p), NotifyProps.class),

    ERROR(ConfigCategory.C, "C类：错误处理配置",
            SystemConfig::getError, (c, p) -> c.setError((ErrorProps) p), ErrorProps.class),

    RUNTIME(ConfigCategory.C, "C类：运行时控制配置",
            SystemConfig::getRuntime, (c, p) -> c.setRuntime((RuntimeProps) p), RuntimeProps.class),

    TEMPLATE(ConfigCategory.C, "C类：模板引擎配置",
            SystemConfig::getTemplate, (c, p) -> c.setTemplate((TemplateProps) p), TemplateProps.class);

    // ===== 字段 =====

    private final ConfigCategory category;
    private final String title;
    private final Class<?> propsType;
    private final Function<SystemConfig, EnvironmentLoadable> getter;
    private final BiConsumer<SystemConfig, Object> setter;

    SystemTabEnum(ConfigCategory category, String title,
                  Function<SystemConfig, EnvironmentLoadable> getter,
                  BiConsumer<SystemConfig, Object> setter,
                  Class<?> propsType) {
        this.category = category;
        this.title = title;
        this.propsType = propsType;
        this.getter = getter;
        this.setter = setter;
    }

    // ===== 访问器 =====

    public ConfigCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getKey() {
        return name().toLowerCase();
    }

    public Class<?> getPropsType() {
        return propsType;
    }

    /** 获取子对象（类型安全） */
    public EnvironmentLoadable getProps(SystemConfig config) {
        return getter.apply(config);
    }

    /**
     * 设置子对象（带类型检查）。
     * <p>
     * 如果 props 非 null 且类型不匹配当前 Tab 的 PropsType，抛出 ClassCastException。
     */
    public void setProps(SystemConfig config, Object props) {
        if (props != null && !propsType.isInstance(props)) {
            throw new ClassCastException(
                String.format("Tab %s 期望 %s，实际为 %s",
                    name(), propsType.getName(), props.getClass().getName()));
        }
        setter.accept(config, props);
    }

    /** 是否需要重启应用才能生效 */
    public boolean isRestartRequired() {
        return category.isRestartRequired();
    }

    /** 是否在保存后需要运行期回写（C 类立即生效） */
    public boolean isRuntimeApply() {
        return category.isRuntimeApply();
    }

    /** 是否环境只读（A 类，不落库） */
    public boolean isEnvironmentOnly() {
        return category.isEnvironmentOnly();
    }

    // ===== 查询方法 =====

    /**
     * 从 key 查找枚举，找不到返回 null
     */
    public static SystemTabEnum of(String key) {
        if (key == null) {
            return null;
        }
        for (SystemTabEnum tab : values()) {
            if (tab.getKey().equals(key)) {
                return tab;
            }
        }
        return null;
    }

    /**
     * 获取指定分类的所有 Tab
     */
    public static List<SystemTabEnum> getByCategory(ConfigCategory category) {
        return Arrays.stream(values())
                .filter(t -> t.category == category)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有 UI 可编辑的 Tab（排除 A 类环境只读）
     */
    public static List<SystemTabEnum> getEditableTabs() {
        return Arrays.stream(values())
                .filter(t -> !t.category.isEnvironmentOnly())
                .collect(Collectors.toList());
    }
}
