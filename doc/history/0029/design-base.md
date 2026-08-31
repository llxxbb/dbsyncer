# ADR-0029 设计稿 — 运行时配置 UI 化

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | base（v1.6） |
| 作者 | @dbsyncer-team |
| 日期 | 2026-09-01 |
| 关联 ADR | [ADR-0029](../../adr/0029-运行时配置UI化.md) |

> **v1.6 新增**：配置迁移与版本管理（`schemaVersion`）、启动期配置对照表、配置变更安全机制（前置校验）、
> B 类自重启 + 发消息通知、SystemConfig 结构重构（A 类子配置类，根节点不留具体配置项）。

## 1. 设计目标

将 `application.properties` 中 B+C 类配置 UI 化，按 Tab 组织为 `SystemConfig` 的嵌套子对象，每个 Tab 独立保存。C 类运行时立即生效，B 类重启应用后生效，A 类只读脱敏展示。

**v1.6 新增目标**：
- 配置迁移与版本管理（`schemaVersion`）
- 启动期配置对照表
- 配置变更安全机制（前置校验）
- B 类自重启 + 发消息通知
- SystemConfig 结构重构（A 类子配置类，根节点不留具体配置项）

## 2. 架构设计

### 2.0 SystemConfig 结构重构（v1.6 新增）

**v1.6 重大变更**：SystemConfig 根节点不留具体配置项，所有配置都组织成子配置类。

```java
public class SystemConfig extends ConfigModel {
    private int schemaVersion;  // 配置版本号，默认 0

    // === A 类（@JsonIgnore：不落库，每次读页面时由 web 层从 Environment 现取） ===
    private ServerProps server;                    // server.port, server.ssl.*, server.http.port
    private StorageConnectionProps storageConn;    // dbsyncer.storage.type, mysql.*
    private LoggingProps logging;                  // logging.level.root
    private HelpdocProps helpdoc;                  // helpdoc.path
    private WorkerProps worker;                    // dbsyncer.web.worker.id, data-center-id
    private LicenseProps license;                  // dbsyncer.web.license.server.address

    // === B 类（需重启） ===
    private ExecutorProps executors;               // Tab 2，null=未覆盖
    private BufferProps   buffers;                 // Tab 3，null=未覆盖

    // === C 类（立即生效） ===
    private RetryProps retry;                      // Tab 4，null=未覆盖
    private NotifyProps notify;                    // Tab 5，null=未覆盖
    private ErrorProps error;                      // Tab 6，null=未覆盖

    // === 基础设置（原扁平字段组织成子对象） ===
    private BasicProps basic;                      // expireDataDays, expireLogDays, ...
}
```

**好处**：
1. **统一管理**：所有配置都在 SystemConfig 里，不管是 A/B/C 类
2. **迁移一致**：首次运行时，所有配置都从 bean 迁移到 DB
3. **结构清晰**：SystemConfig 变成纯容器，所有具体配置都在子对象里

> **A 类子对象为什么 `@JsonIgnore` 不落库**：A 类的唯一真相源是 `application.properties` + JVM 启动参数，
> `SystemConfig` 里那份只是**显示用的视图**。若持久化，运维手工改 properties 并重启后 DB 仍是旧副本，
> 页面显示与实际监听端口/存储类型不一致——这正是本 ADR 要根除的「UI 显示值 ≠ 实际值」。
> 因此 A 类子对象只在 `GET /system/tabs`、`GET /system/serverInfo` 的响应里由 `environment.getProperty(...)`
> 现取填充（A 类回显只能由 web 层做，biz 看不到 `WebAppConfig`，见 §2.1.1 模块方向）；
> 持久化 JSON 里只有 `basic` + B 类 2 个 + C 类 3 个子对象。
4. **UI 一致**：A 类配置在同一个 `SystemConfig` 结构里以子对象形式只读展示（脱敏）

**新增 A 类子配置类**：

```java
/** A 类：服务器配置（只读） */
public class ServerProps {
    private int port;                          // server.port
    private Boolean sslEnabled;                // server.ssl.enabled
    private String sslKeyStore;                // server.ssl.key-store
    private String sslKeyStorePassword;        // server.ssl.key-store-password
    private String sslKeyStoreType;            // server.ssl.key-store-type
    private String sslKeyAlias;                // server.ssl.key-alias
    private Integer httpPort;                  // server.http.port
    private Boolean httpRedirect;              // server.http.redirect
    private String contextPath;                // server.servlet.context-path
    private Integer sessionTimeout;            // server.servlet.session.timeout
}

/** A 类：存储连接配置（只读） */
public class StorageConnectionProps {
    private String type;                       // dbsyncer.storage.type
    private String mysqlUrl;                   // dbsyncer.storage.mysql.url
    private String mysqlUsername;              // dbsyncer.storage.mysql.username
    private String mysqlPassword;              // dbsyncer.storage.mysql.password
    private String mysqlDriverClassName;       // dbsyncer.storage.mysql.driver-class-name
}

/** A 类：日志配置（只读） */
public class LoggingProps {
    private String levelRoot;                  // logging.level.root
}

/** A 类：帮助文档配置（只读） */
public class HelpdocProps {
    private String path;                       // helpdoc.path
}

/** A 类：Worker 配置（只读） */
public class WorkerProps {
    private Long id;                           // dbsyncer.web.worker.id
    private Long dataCenterId;                 // dbsyncer.web.worker.data-center-id
}

/** A 类：License 配置（只读，脱敏） */
public class LicenseProps {
    private String serverAddress;              // dbsyncer.web.license.server.address
}

/** 基础设置（原扁平字段组织成子对象） */
/** S1：BasicProps 也用包装类型。迁移时从旧 SystemConfig 平铺字段拷入实际值 */
public class BasicProps {
    private Integer expireDataDays;
    private Integer expireLogDays;
    private Integer refreshIntervalSeconds;
    private Boolean enableStorageWriteFail;
    private Integer maxStorageErrorLength;
    private Boolean enableCDN;
    private Boolean enableWatermark;
    private String watermark;
    private Boolean enableSchemaResolver;
    private Boolean enablePrintTraceInfo;
}
```

### 2.0.1 配置迁移与版本管理（v1.6 新增）

**SystemConfig 增加 `schemaVersion` 字段**：
- 类型：`int`，默认 `0`
- 用途：
  - **迁移判定**：`schemaVersion=0` 表示未迁移，迁移完成后置为 1，后续结构变更再递增
  - **防回退**：避免旧版 JSON 反序列化后静默丢失新字段

**首次运行迁移逻辑**：
```
PreloadTemplate.onApplicationEvent
  ↓
检测 schemaVersion == 0
  ↓ 是
从 @ConfigurationProperties bean 读运行时值
  ↓
写入 SystemConfig 的 sub-object JSON
  ↓
置 schemaVersion = 1
  ↓
打印"配置迁移完成"日志
```

**迁移源**：bean 运行时值（Spring relaxed binding 已归一化，不用自己解析 key 命名）。

**迁移范围**：
- A 类：**不迁移**（不落库，读时从 Environment 现取，见 §2.0）
- B+C 类：从 @ConfigurationProperties bean 读取（`bean.getXxx()`）
- BasicProps：从原 SystemConfig 的 10 个平铺字段迁移

**迁移完成后**：
- `schemaVersion = 1`
- 所有配置都在 DB 里，application.properties 只作为回退
- 后续版本如需改结构，递增 `schemaVersion` 并写对应迁移逻辑

**迁移代码示例**：
```java
@Component
public class ConfigMigrator {
    @Resource private ProfileComponent profileComponent;
    @Resource private Environment environment;
    @Resource private GeneralBufferConfig generalBufferConfig;
    @Resource private StorageConfig storageConfig;
    // ... 其他 bean

    public void migrateIfNeeded() {
        // L2 修订：直接查存储层，不调 getSystemConfig()（避免触发默认配置创建）
        List<ConfigModel> list = operationTemplate.queryAll(SystemConfig.class);
        SystemConfig config = /* 取最早/最小 id，同 ProfileComponentImpl:118-119 */;
        if (config == null) return;  // 无记录则不迁移（由后续 getSystemConfig 创建默认记录）
        if (config.getSchemaVersion() > 0) {
            return;  // 已迁移
        }

        // M5 修订：A 类不迁移、不落库（@JsonIgnore，读时从 Environment 现取）
        // —— 不写任何 A 类迁移代码

        // 迁移 B 类（S1：包装类型，直接赋 int 自动装箱）
        ExecutorProps executors = new ExecutorProps();
        PoolProps general = new PoolProps();
        general.setThreadCoreSize(generalBufferConfig.getThreadCoreSize());  // int → Integer
        general.setMaxThreadSize(generalBufferConfig.getMaxThreadSize());
        general.setThreadQueueCapacity(generalBufferConfig.getThreadQueueCapacity());
        executors.setGeneral(general);
        // ... 其他 B 类字段
        config.setExecutors(executors);

        // 迁移 C 类
        // ...

        // 迁移 BasicProps（原平铺字段 → 子对象）
        BasicProps basic = new BasicProps();
        basic.setExpireDataDays(config.getExpireDataDays());
        basic.setExpireLogDays(config.getExpireLogDays());
        // ... 其他字段
        config.setBasic(basic);

        // 置版本号
        config.setSchemaVersion(1);

        // 保存
        profileComponent.editConfigModel(config);

        logger.info("配置迁移完成，schemaVersion=1");
    }
}
```

### 2.0.2 启动期配置对照表（v1.6 新增）

启动时打印表格，便于排查配置来源和最终值：

```
╔═══════════════════════════════════════════════════════════╗
║ 配置对照（schemaVersion=1）                               ║
╠══════════════════════════════════╦═════════╦═════════╦═════╣
║ 配置项                           ║ props   ║ DB      ║ 最终║
╠══════════════════════════════════╬═════════╬═════════╬═════╣
║ parser.general.thread-core-size  ║ 8       ║ 16      ║ 16  ║
║ parser.general.buffer-queue-...  ║ 30000   ║ -       ║30000║
║ retry.global.max-attempts        ║ 3       ║ 5       ║ 5   ║
╚══════════════════════════════════╩═════════╩═════════╩═════╝
```

**打印时机**：`PreloadTemplate.onApplicationEvent` 完成后，`openStartupGates()` 之前。

**打印内容**：
- 配置项：properties key
- props：application.properties 的值（无则 `-`）
- DB：SystemConfig 的值（无则 `-`）
- 最终：实际生效值

**打印代码示例**：
```java
@Component
public class ConfigPrinter {
    @Resource private Environment environment;
    @Resource private ProfileComponent profileComponent;

    public void printConfigSummary() {
        SystemConfig config = profileComponent.getSystemConfig();
        if (config.getSchemaVersion() == 0) {
            return;  // 未迁移，不打印
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═══════════════════════════════════════════════════════════╗\n");
        sb.append("║ 配置对照（schemaVersion=").append(config.getSchemaVersion()).append("）                               ║\n");
        sb.append("╠══════════════════════════════════╦═════════╦═════════╦═════╣\n");
        sb.append("║ 配置项                           ║ props   ║ DB      ║ 最终║\n");
        sb.append("╠══════════════════════════════════╬═════════╬═════════╬═════╣\n");

        // 打印 B 类配置
        ExecutorProps executors = config.getExecutors();
        if (executors != null && executors.getGeneral() != null) {
            String key = "dbsyncer.parser.general.thread-core-size";
            String propsVal = environment.getProperty(key, "-");
            String dbVal = String.valueOf(executors.getGeneral().getThreadCoreSize());
            String finalVal = dbVal;  // DB 优先
            sb.append(String.format("║ %-32s ║ %-7s ║ %-7s ║ %-3s ║\n", key, propsVal, dbVal, finalVal));
        }

        // ... 其他配置

        sb.append("╚══════════════════════════════════╩═════════╩═════════╩═════╝\n");
        logger.info(sb.toString());
    }
}
```

### 2.0.3 配置变更安全机制（v1.6 新增）

**前置校验（替代事后回滚）**

| 时机 | 校验内容 | 失败处理 |
|---|---|---|
| **保存前**（POST /system/editTab） | B 类字段范围检查（线程数>0、队列容量合理范围等） | 返回 400 + 字段错误，拒绝保存 |
| **启动前**（RuntimeConfigApplier.applyAll） | 全量校验 | 跳过该字段 + warn 日志 + 用默认值 + UI 横幅 |

**校验器示例**（约 60 行）：
```java
public class RuntimeConfigValidator {
    public static List<String> validate(ExecutorProps exec) {
        List<String> errors = new ArrayList<>();
        if (exec.getGeneral().getThreadCoreSize() < 1) {
            errors.add("执行器核心线程数必须 > 0");
        }
        if (exec.getGeneral().getThreadCoreSize() > exec.getGeneral().getMaxThreadSize()) {
            errors.add("核心线程数不能大于最大线程数");
        }
        if (exec.getGeneral().getThreadQueueCapacity() < 1) {
            errors.add("执行器队列容量必须 > 0");
        }
        // ... 其他字段
        return errors;
    }

    public static List<String> validate(BufferProps buffers) {
        List<String> errors = new ArrayList<>();
        if (buffers.getGeneral().getBufferQueueCapacity() < 1) {
            errors.add("缓冲区队列容量必须 > 0");
        }
        if (buffers.getGeneral().getBufferPeriodMillisecond() < 1) {
            errors.add("缓冲区消费间隔必须 > 0");
        }
        // ... 其他字段
        return errors;
    }
}
```

**放弃 C 方案（同字段双槽）**：前置校验已经足够，不需要自动回滚。

**L4 修订：校验器 null 安全**。所有 Props 字段为包装类型（S1），校验器必须先判 null 再比较：
```java
public static List<String> validate(ExecutorProps exec) {
    List<String> errors = new ArrayList<>();
    if (exec == null) return errors;
    if (exec.getGeneral() != null) {
        PoolProps g = exec.getGeneral();
        if (g.getThreadCoreSize() != null && g.getThreadCoreSize() < 1)
            errors.add("执行器核心线程数必须 > 0");
        if (g.getThreadCoreSize() != null && g.getMaxThreadSize() != null
            && g.getThreadCoreSize() > g.getMaxThreadSize())
            errors.add("核心线程数不能大于最大线程数");
    }
    // dispatch、storage 同上
    return errors;
}
```

**UI 变更**：
- 保存前校验失败 → 表单字段标红 + 错误提示
- 启动后校验失败 → 页面顶部横幅"部分配置无效，请检查系统日志"

### 2.0.4 B 类自重启流程（v1.6 新增）

**保存后 → 自重启**

| 配置组 | 重启方式 | 实现 |
|---|---|---|
| `parser.general.buffer-*` | 驱动重启 | stop 该驱动（drain buffer）→ start |
| `storage.buffer-*` | 应用重启 | 优雅停机 → 重启 JVM |
| 执行器线程池 | 应用重启 | 同上 |

**自重启流程**：
```
保存成功 → 返回 hasRestartRequired=true
  ↓
UI 弹窗"配置已保存，需要重启生效。是否立即重启？"
  ↓ 用户确认
后端调用 System.exit(0) 或 Spring Boot graceful shutdown
  ↓
外部监控脚本（如 systemd）自动拉起
  ↓
启动时 → 前置校验 → 通过 → 应用新值
```

**重启前发消息通知**：
```java
// 保存成功后，需要重启
if (hasRestartRequired) {
    // 1. 发送通知
    String message = String.format(
        "系统参数已更新，应用即将重启。\n" +
        "操作人：%s\n" +
        "变更配置：%s\n" +
        "重启时间：%s",
        currentUser,
        changedConfigKeys,
        LocalDateTime.now()
    );
    notifyService.sendSystemNotification(message);  // 邮件/钉钉
    
    // 2. 延迟 3 秒后重启（给通知发送时间）
    Thread.sleep(3000);
    
    // 3. 优雅停机
    System.exit(0);  // 或 SpringApplication.exit()
}
```

**通知内容**：
- 操作人
- 变更的配置项列表
- 重启时间
- 重启原因（B 类配置变更）

**自重启失败处理**：
- 前置校验失败 → 用默认值 + warn 日志 + UI 横幅"部分配置无效"
- 应用启动失败 → 人工介入（看日志）

### 2.1 关键前提：三个实测约束

| # | 约束 | 出处 | 设计影响 |
|---|------|------|---------|
| C1 | `JsonUtil` 使用 `Include.NON_DEFAULT` | `commons/.../util/JsonUtil.java:22` | 等于 Java 字段初始值的属性不落库；子对象为 `null` 时整个不进 JSON → **天然支持"未配置"语义 + 存量记录兼容** |
| C2 | `BeanUtil.mapToBean` 只支持扁平字段名 + 6 种标量，未知 key 直接 NPE | `commons/.../util/BeanUtil.java:12-25` | **嵌套子对象无法走现有 form 通道** → Tab 2-6 必须走 JSON 接口 |
| C3 | `@Bean generalExecutor()` 在 bean 创建期执行，早于 `ContextRefreshedEvent` | `GeneralBufferConfig.java` / `PreloadTemplate.java` | 启动后回写 `@ConfigurationProperties` 字段**无法影响已建好的线程池** → B 类必须在 refresh 前注入 |
| C4 | `ProfileComponent.getSystemConfig()` 返回缓存对象**引用** | `ProfileComponentImpl.java:106` | 读-改-写天然成立，未提交的 Tab 自动保留旧值 |
| C5 | `NotifyConfig.MailConfig.shouldNotify()` 内 `notifyTypeSet` 懒加载后**永不失效**，`setNotifyTypes()` 是裸 setter | `NotifyConfig.java:96-118`（DingTalk 同构） | Tab 5 的通知类型**无法热生效**，需先补 2 行缓存失效（见 §4.5） |

### 2.1.1 模块依赖（决定新代码放哪，实测）

```
dbsyncer-common  ←  dbsyncer-parser  ←  dbsyncer-manager  ←  dbsyncer-biz  ←  dbsyncer-web
```

| 目标 Bean | 所在模块 | 包 |
|-----------|---------|-----|
| `GeneralBufferConfig` / `StorageConfig` / `DispatchTaskConfig` / `SchedulerConfig` / `RetryConfig` / `BufferActuatorConfig` | dbsyncer-common | `org.dbsyncer.common.config` |
| `RetryPolicy` / `TerminationMode` / `MatchMode` | dbsyncer-common | `org.dbsyncer.common.retry` / `.util` |
| `SystemConfig` / `ErrorQueueConfig` / `NotifyConfig` | **dbsyncer-parser** | `org.dbsyncer.parser.model` / `.error` / `.notify` |

**推论**：`RuntimeConfigApplier` 必须放在 **dbsyncer-parser**，不能放 commons——它要同时引用 `SystemConfig`、`ErrorQueueConfig`、`NotifyConfig`（均在 parser），而 commons 反向依赖 parser 会成环。Props 类同样落 parser。

### 2.2 启动时序

```
Spring Boot 启动
  ↓
EnvironmentPostProcessor（RuntimeConfigPropertySource）
  ├─ 读 {app.home}/config/dbsyncer-runtime.properties（sidecar，保存时全量重写）
  ├─ 文件缺失/解析失败 → 交空属性源，行为与今天完全一致（fail-soft，L2）
  ├─ 把 sidecar 里的键原样摊平为
  │    dbsyncer.parser.general.thread-core-size=... 等 key
  └─ addFirst 到 Environment（优先级高于 application.properties）
     ⚠ 为什么读文件不读 DB：EPP 跑在 Spring 之前，此时 StorageService/驱动都还没建；
     且 disk 模式的配置存在 Lucene 索引里（F13），EPP 阶段读不动。DB 仍是唯一真相源
     （UI 展示、审计、导入导出都用 DB），sidecar 只是可删除的启动缓存。见 L10。
  ↓
@ConfigurationProperties 绑定 —— 自动取 Environment 中的 DB 值
  ↓
@Bean 方法执行：线程池/调度器按 DB 值创建（无需任何重建代码）
  ↓
ContextRefreshedEvent → PreloadTemplate.onApplicationEvent()
  ├─ 加载配置到缓存（现有逻辑）
  ├─ 【新增钩子 1】ConfigMigrationService.migrateIfNeeded()
  │     schemaVersion==0 → 从 bean 读 B+C 类生效值写入子对象 → 置 1 → 落库（幂等，见 §2.0.1）
  ├─ 【新增钩子 2】RuntimeConfigValidator.validateOnStartup(cfg)
  │     非法字段 → 跳过 + 回退 properties 值 + warn + 记入 UI 横幅（见 §2.0.3）
  ├─ 【新增钩子 3】ConfigComparator.printStartupTable()
  │     打印 props / DB / 最终值 + 来源标记，落在端口开放前（见 §2.0.2）
  ├─ loadConnectorInstance() / launch()
  └─ openStartupGates() → 端口开放
     ⚠ 启动期不调 applyCClass()，见下
```

因此 `applyCClass()` **只在 UI 保存时调用**。但 `PreloadTemplate` **v1.6 起仍需改动**：迁移、启动校验、对照表三个钩子必须跑在 `openStartupGates()` **之前**——此时端口未开、无任务在跑，是唯一的无窗口期插入点，与 ADR-0028 的启动门控顺序是同一约束（见上图【新增钩子 1/2/3】）。

> **L2 硬约束：三个钩子绝不触碰 `getSystemConfig()`**。迁移钩子用 `operationTemplate.queryAll(SystemConfig.class)`
> 直接查存储层；对照表/校验钩子同样走 `queryAll` 取引用。`getSystemConfig()` 在记录不存在时会触发
> `checkAddConfigModel(new HashMap<>())` 创建默认配置并落库，复活 ADR-0028 的重复创建问题。
> 单测锁定：启动期三个钩子不调用 `getSystemConfig()`。

> Spring Boot 的 relaxed binding 对**任意** `PropertySource` 生效，自定义 `MapPropertySource` 里写 `thread-core-size` 照样能绑到 `threadCoreSize`，无需手动转换命名。

**降级预案（C3 无法满足时）**：把 4 个 `@Configuration` 类拆成「纯 `@ConfigurationProperties` 载体」+「`ExecutorBeanFactory`」，工厂方法签名 `@Bean generalExecutor(GeneralBufferConfig c, RuntimeConfigBootstrap b)`，靠参数依赖保证 `b` 的 @PostConstruct 先回写再建池。注意不能对原 `@Configuration` 类加 `@DependsOn("runtimeConfigBootstrap")`——bootstrap 需注入该 Bean，会成环。

### 2.3 运行时保存流程

```
UI 点某个 Tab 的「保存」
  ↓
POST /system/editTab   { "tab": "buffers", "payload": { ... } }
  ↓
SystemTabEnum.of("buffers") → BufferProps.class
  ↓
JsonUtil.jsonToObj(payload, BufferProps.class)        // 整块替换，非字段 merge
  ↓
SystemConfigTabChecker.check(tab, props)              // 结构/必填校验
  ↓
RuntimeConfigValidator.validate(tab, props)             // 【v1.6】取值范围校验，失败 → 400 + 字段错误
  ↓                                                     // 校验在任何写动作之前，失败时零写入
  ↓
SystemConfig cfg = profileComponent.getSystemConfig() // 缓存引用
cfg.setBuffers(props)
  ↓
profileComponent.editConfigModel(cfg)                 // ① DB 落库（真相源，先提交）
  ↓
sidecarWriter.rewrite(cfg)                              // ② 全量重写 sidecar（下次启动的输入）
  ↓                                                     // ①② 顺序固定；② 失败则回滚 ①（L11）
  ↓
tab.isRuntimeApply()  ? runtimeConfigService.applyCClass(cfg)   // C 类：原地 setter 回写，立即生效
                        : selfRestart.schedule(tab)               // B 类：发消息 → 延迟 3s → 优雅停机（§2.0.4）
  ↓
响应 { success:true, restartRequired: tab.isRestartRequired() }
```

B 类 Tab 保存后 `restartRequired=true`，**v1.6 起由后端自重启**（发消息通知 + 延迟 3 秒 + 优雅停机），
不再要求运维手工去命令行重启；但**自重启前置要求有进程守护**（systemd `Restart=always` / docker `--restart=always`），
无守护时降级为"仅提示需人工重启"——否则 `System.exit(0)` 等于把系统关掉，比原来更危险。见 L13。
`executors` Tab 的 4 个线程池与 `storage.buffer-*` 走应用自重启；
`parser.general.buffer-queue-capacity` / `buffer-period-millisecond` 同属 B 类（M4 已决：统一重启应用）。

> **M1 修订：editTab 并发锁**。`SystemConfigServiceImpl.editTab()` 加 `synchronized`，
> 与现有 `getSystemConfig()` 的双检锁同一把实例锁。两个 Tab 并发保存时串行化，
> 避免读-改-写丢失更新。锁粒度为实例级（单例 Service，足够）。

> **M3 修订：废除 `/system/edit` + `mapToBean` 通道**。Tab 1 迁到 `BasicProps` 后
> `mapToBean` 无法处理嵌套路径（C2），继续保留只会造成两套保存通路并存的混乱。
> `schemaVersion>=1` 后前端不再调用 `/system/edit`，后端该端点标记 `@Deprecated`
> 并返回 410 Gone + 提示前端升级。存量自动化脚本需迁移到 `/system/editTab` + `{tab:"basic"}`。

> **sidecar 路径定义（L1）**：`{app.home}/config/dbsyncer-runtime.properties`，
> 其中 `app.home` = `System.getProperty("user.dir")` = 启动脚本中的 `$DBS_HOME`
> （见 `startup.sh:46`、`startup.bat:22`、`startup-docker.sh:45` 的 `-Duser.dir=$DBS_HOME`）。
> 该目录已用于 `data/`（DiskStorageService:55）、`conf/`（SdkSupportConfiguration:36）、
> `helpdocs/`（HelpDocController:52），sidecar 放 `config/` 子目录与之一致。

### 2.4 核心组件

| 组件 | 模块 | 职责 |
|------|------|------|
| `SystemConfig` | dbsyncer-parser | 仅加子对象：`basic` + B 类 2 + C 类 3 + A 类 6（`@JsonIgnore`）+ `schemaVersion`；**根节点不再留平铺字段** |
| `*Props` × 12 | **dbsyncer-common** `common.config.props` | 6 个 A 类 + 5 个 B/C 类 + `BasicProps`；字段名与目标 Bean **逐字对齐**。放 common 是因为 `SystemConfig` 在 parser、目标 Bean 在 common，common 是唯一两侧可见的位置（F19） |
| `SystemTabEnum` | dbsyncer-common `common.config` | Tab → 子对象类型 / 存取函数 / 是否重启生效 / 是否落库 |
| `RuntimeConfigValidator` | dbsyncer-biz | 【v1.6】保存前 + 启动前两处取值校验，唯一校验规则来源 |
| `ConfigMigrationService` | dbsyncer-biz | 【v1.6】`schemaVersion` 判定 + 首次从 bean 摊平到子对象，幂等 |
| `ConfigComparator` | dbsyncer-biz | 【v1.6】启动期打印 props / DB / 最终值对照表 |
| `SidecarPropertyWriter` | dbsyncer-biz | 【v1.6】保存后全量重写 `{app.home}/config/dbsyncer-runtime.properties` |
| `SelfRestartService` | dbsyncer-web | 【v1.6】发消息 → 延迟 3s → 优雅停机；需检测进程守护存在 |
| `RuntimeConfigPropertySource` | dbsyncer-web | `EnvironmentPostProcessor`，启动前注入 DB 值（B 类） |
| `RuntimeConfigService` | **dbsyncer-biz** `biz.impl` | **仅 UI 保存时**原地 setter 回写 C 类（依赖方向见 §2.1.1）。**不能**看到 `WebAppConfig`（在 dbsyncer-web），故 A 类回显一律由 web 层直读 `Environment` |
| `SystemConfigController` | dbsyncer-web | 新增 `/system/editTab`、`/system/tabs`、`/system/serverInfo` |
| `system.html` + `index.js` | dbsyncer-web | 6 Tab 布局，每 Tab 独立保存 |

## 3. 数据模型

### 3.1 SystemConfig（v1.6 重构）

**v1.6 重大变更**：SystemConfig 根节点不留具体配置项，全部组织成子配置类。

```java
public class SystemConfig extends ConfigModel {
    private int schemaVersion;  // 配置版本号，默认 0

    // === A 类（@JsonIgnore：不落库，每次读页面时由 web 层从 Environment 现取） ===
    private ServerProps server;                    // server.port, server.ssl.*, server.http.port
    private StorageConnectionProps storageConn;    // dbsyncer.storage.type, mysql.*
    private LoggingProps logging;                  // logging.level.root
    private HelpdocProps helpdoc;                  // helpdoc.path
    private WorkerProps worker;                    // dbsyncer.web.worker.id, data-center-id
    private LicenseProps license;                  // dbsyncer.web.license.server.address

    // === B 类（需重启） ===
    private ExecutorProps executors;               // Tab 2，null=未覆盖
    private BufferProps   buffers;                 // Tab 3，null=未覆盖

    // === C 类（立即生效） ===
    private RetryProps retry;                      // Tab 4，null=未覆盖
    private NotifyProps notify;                    // Tab 5，null=未覆盖
    private ErrorProps error;                      // Tab 6，null=未覆盖

    // === 基础设置（原扁平字段组织成子对象） ===
    private BasicProps basic;                      // expireDataDays, expireLogDays, ...
}
```

**为什么重构为子配置类**：
1. **统一管理**：所有配置都在 SystemConfig 里，不管是 A/B/C 类
2. **迁移一致**：首次运行时，所有配置都从 bean 迁移到 DB
3. **结构清晰**：SystemConfig 变成纯容器，所有具体配置都在子对象里
4. **UI 一致**：A 类配置在同一个 `SystemConfig` 结构里以子对象形式只读展示（脱敏）

**存量兼容**：
- 旧版本 DB 记录（无新字段）→ 反序列化为 `null` → 行为与今天完全一致
- 首次启动时，配置迁移逻辑将原 10 个平铺字段迁移到 `BasicProps` 子对象

**Tab 1 为什么用 BasicProps 子对象**：
- 原 10 个平铺字段迁移到 `BasicProps` 子对象，与其他 Tab 保持一致的结构
- 存量 DB 记录通过配置迁移逻辑自动转换
- UI 提交时走 JSON 接口（`POST /system/editTab`），不再走 `mapToBean`

### 3.2 Props 类定义

> 包路径：`org.dbsyncer.parser.model.props`。字段名与目标 `@ConfigurationProperties` Bean **逐字一致**，使 `BeanUtils.copyProperties` 可直接工作。初始值一律 `null`（不设业务默认值）——"未配置"由子对象 `null` 表达，字段无需 sentinel。
>
> **S1 修订：所有字段使用包装类型**（`Integer`/`Boolean`/`Double`/`Long`），不用基本类型。
> 原因：`JsonUtil` 使用 `NON_DEFAULT` 策略（F6），基本类型 `int` 的 Java 默认值 `0` 与用户显式设置的 `0`
> 不可区分——`threshold=0`（不熔断）、`maxDurationMs=0`（不限时长）等合法业务值会被 NON_DEFAULT 省略，
> 反序列化后回退到 properties 值而非保持 `0`。包装类型 Java 默认 = `null`，`null` 被省略 →
> 反序列化 `null` → 回退 properties ✓；显式设 `0` → `Integer(0)` ≠ null → 序列化 ✓。

```java
/** 线程池三元组，被多处复用。S1：全部包装类型 */
public class PoolProps {
    private Integer threadCoreSize;
    private Integer maxThreadSize;
    private Integer threadQueueCapacity;
}

/** Tab 2 执行器 */
public class ExecutorProps {
    private PoolProps general;      // → GeneralBufferConfig
    private PoolProps storage;      // → StorageConfig
    private PoolProps dispatch;     // → DispatchTaskConfig
    private Integer   schedulerPoolSize;  // → SchedulerConfig.poolSize
}

/** 单个执行器的缓冲区参数。S1：全部包装类型 */
public class BufferActuatorProps {
    private Integer bufferWriterCount;
    private Integer bufferPullCount;
    private Integer bufferQueueCapacity;
    private Integer bufferPeriodMillisecond;
}

/** Tab 3 缓冲区 */
public class BufferProps {
    private BufferActuatorProps general;   // → GeneralBufferConfig
    private BufferActuatorProps storage;   // → StorageConfig
}

/** Tab 4 重试策略（字段对齐 RetryPolicy）。S1：全部包装类型 */
public class RetryProps {
    private Boolean         disable;
    private Integer         maxAttempts;
    private Long            initialIntervalMs;
    private Long            maxIntervalMs;
    private Double          multiplier;
    private Long            maxDurationMs;
    private TerminationMode terminationMode;
    private Boolean         useKeyword;
    private MatchMode       matchMode;
    private List<String>    keywords;
    private List<String>    excludeKeywords;
}

/** Tab 5 通知通道 */
public class NotifyProps {
    private List<String>   channels;
    private MailProps      mail;
    private DingTalkProps  dingtalk;
}
public class MailProps {
    private String       username;
    private String       password;
    private List<String> notifyTypes;
}
public class DingTalkProps {
    private String       webhookUrl;
    private String       secret;          // L3：保留，暴露并脱敏展示
    private List<String> notifyTypes;
}

/** Tab 6 错误处理（字段对齐 ErrorQueueConfig）。S1：全部包装类型 */
public class ErrorProps {
    private Integer   threshold;
    private MatchMode fatalMatchMode;
    private List<String> fatalKeywords;
}
```

### 3.3 为什么"全部新建 Props"仍要防漂移

Props 与现有类是平行关系，默认值不会自动同步。缓解手段两条，**必须落地**：

1. **UI 预填取运行时实际生效值，不取 Props 初始值**（`GET /system/tabs`，见 §5.2）。Props 初始值只是 Java 层的"空壳"，用户永远看不到。
2. **单测锁定字段名对齐**：断言 `copyProperties(props, bean)` 后每个字段值符合预期，防止后续有人重命名 Bean 字段而 Props 静默失配。

### 3.4 序列化行为验证（C1 推论 + S1 包装类型修订）

| 场景 | 落库 JSON | 重载结果 |
|------|----------|---------| 
| 从未保存 Tab 4 | 无 `retry` key | `retry == null` → 用 properties 值 ✓ |
| **S1 修订后**：保存 Tab 4，`maxAttempts` 设为 `Integer(3)` | `maxAttempts: 3` 落库（`Integer(3)` ≠ `null`） | 重载后 `Integer(3)` ✓ |
| **S1 修订后**：保存 Tab 4，`maxDurationMs = Long(0)`（业务含义"不限制"） | `maxDurationMs: 0` 落库（`Long(0)` ≠ `null`） | 重载后 `Long(0)` ✓ |
| **S1 修订后**：保存 Tab 4，`threshold = Integer(0)`（业务含义"不熔断"） | `threshold: 0` 落库 | 重载后 `Integer(0)` ✓ |
| 旧版本 DB 记录（无任何新字段） | — | 全部 `null` → 完全等价于未配置 ✓ |
| `RetryPolicy` 的 `matcher` 缓存 | 包级私有 getter，Jackson 不序列化 ✓ | — |

> **S1 修订结论**：包装类型下，`null` 被 NON_DEFAULT 省略 → 反序列化 `null` → 回退 properties ✓；
> 显式设 `0` → `Integer(0)` ≠ `null` → 序列化 ✓。**`0` 是合法业务值且能正确往返**。
> 注意：如果用户「未填」某字段（前端不提交），该字段为 `null` → 回退 properties，
> 而非「保持旧值」。这是整块替换语义的一部分（§5.3）。
>
> ⚠️ **NON_DEFAULT 对包装类型的行为**：`Integer(0)` 在 NON_DEFAULT 下**不被省略**，
> 因为 `Integer` 字段的 Java 默认值是 `null`（不是 `0`），`Integer(0)` ≠ `null` → 保留 ✓。
> **实施首日必须写单测锁定此行为**，防止 Jackson 版本升级改变语义。

## 4. 组件设计

### 4.1 SystemTabEnum — Tab 元数据单一来源

```java
public enum SystemTabEnum {
    EXECUTORS("executors", ExecutorProps.class, true),   // restartRequired
    BUFFERS  ("buffers",   BufferProps.class,   true),
    RETRY    ("retry",     RetryProps.class,    false),
    NOTIFY   ("notify",    NotifyProps.class,   false),
    ERROR    ("error",     ErrorProps.class,    false);

    private final String key;
    private final Class<?> propsType;
    private final boolean restartRequired;

    /** 从 SystemConfig 取/放子对象 */
    public Object get(SystemConfig cfg) { ... }
    public void   set(SystemConfig cfg, Object props) { ... }
}
```

新增 Tab 只改这一处，controller / checker / applier 均无需改动。

### 4.2 RuntimeConfigApplier — 仅 C 类

```java
@Component
public class RuntimeConfigApplier {
    @Resource private RetryConfig retryConfig;           // dbsyncer-common
    @Resource private ErrorQueueConfig errorQueueConfig; // dbsyncer-parser
    @Resource private NotifyConfig notifyConfig;         // dbsyncer-parser

    /** 启动期 + 每次 C 类保存后调用 */
    public void applyCClass(SystemConfig config) {
        RetryProps retry = config.getRetry();
        if (retry != null) {
            // 目标 setter 内部会把 matcher/excludeMatcher 置 null，缓存自动失效
            BeanUtils.copyProperties(retry, retryConfig.getGlobal());
        }

        ErrorProps error = config.getError();
        if (error != null) {
            BeanUtils.copyProperties(error, errorQueueConfig);   // setFatalMatchMode 内置 fatalMatcher=null
        }

        NotifyProps notify = config.getNotify();
        if (notify != null) {
            notifyConfig.setChannels(orEmpty(notify.getChannels()));
            copyInto(notify.getMail(),     notifyConfig.getMail());
            copyInto(notify.getDingtalk(), notifyConfig.getDingtalk());
        }

        if (config.getSessionTimeout() > 0) { /* Tomcat session timeout */ }
        if (config.getSqlserverCtPollIntervalMs() > 0) { /* ... */ }
    }
}
```

**为什么 `copyProperties` 在这里是安全的**：Props 字段名与目标 Bean 逐字对齐（§3.2）；目标为 null 的子字段由调用方先判空；`BeanUtils` 按名字匹配、跳过无 public setter 的属性，因此 `RetryPolicy.matcher`（包级私有 getter、无 setter）不会被触碰。

> **S1 修订：包装类型对 `copyProperties` 的影响**。Props 字段为 `Integer`/`Boolean`/`Double`，
> 目标 Bean 字段为 `int`/`boolean`/`double`。`BeanUtils.copyProperties` 支持自动拆箱，
> 但 Props 字段为 `null` 时会把目标 Bean 字段设为 `0`/`false`（拆箱 NPE 不会发生，但会覆盖为默认值）。
> 因此 `copyProperties` 前必须先判 null：只拷贝非 null 字段，或改用手动逐字段拷贝。
> 推荐方案：写一个 `copyNonNullProperties(src, dest)` 工具方法，跳过 null 字段。

**为什么用 `copyProperties(props, retryConfig.getGlobal())` 而不是 `setGlobal(newPolicy)`**：`getPolicy(taskId)` 在无任务级覆盖时**直接返回 `global` 引用**，调用方可能短期持有它。原地改字段保证所有引用方看到同一实例；`setGlobal()` 换新实例会让已持有旧引用的代码静默读到旧策略。同理 `errorQueueConfig` 也必原地改——它的 `fatalMatcher` 缓存挂在实例上。

**关键字列表不再需要手动 `split("\n")`**：Props 直接用 `List<String>`，Jackson 原生往返。UI 侧 textarea 与数组的转换在前端完成（`value.split("\n")` / `arr.join("\n")`）。

### 4.3 RuntimeConfigPropertySource — B 类启动注入

```java
public class RuntimeConfigPropertySource implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        try {
            SystemConfig cfg = SystemConfigReader.read(env);   // 见下
            if (cfg == null) return;
            Map<String, Object> props = new LinkedHashMap<>();

            ExecutorProps e = cfg.getExecutors();
            if (e != null) {
                putPool(props, "dbsyncer.parser.general", e.getGeneral());
                putPool(props, "dbsyncer.storage",        e.getStorage());
                putPool(props, "dbsyncer.common.dispatch", e.getDispatch());
                putIfPositive(props, "dbsyncer.web.scheduler.pool-size", e.getSchedulerPoolSize());
            }
            BufferProps b = cfg.getBuffers();
            if (b != null) {
                putBuffer(props, "dbsyncer.parser.general", b.getGeneral());
                putBuffer(props, "dbsyncer.storage",        b.getStorage());
            }
            if (!props.isEmpty()) {
                env.getPropertySources().addFirst(new MapPropertySource("dbsyncerRuntimeConfig", props));
            }
        } catch (Exception ex) {
            // L2: 绝不能因配置读取失败阻塞启动
            System.err.println("[dbsyncer] 运行时配置预读失败，回退 application.properties: " + ex);
        }
    }

    @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
```

`SystemConfigReader` 需按 `dbsyncer.storage.type` 分叉：
- `mysql` → 用 `DriverManager` 裸连 `dbsyncer.storage.mysql.*`，查 `dbsyncer_config` 表 `TYPE='system'`。
- `disk` → 读 `dbsyncer.storage.disk.path` 下的配置文件。

注册于 `META-INF/spring.factories`（Spring Boot 2.5）的 `org.springframework.context.EnvironmentPostProcessor`。

**两条必须遵守的实现约束（实测自 `StorageSupportConfiguration.java:44-90`）**：

1. **PropertySource 名称不得包含 `application`**。`storageService()` 会遍历所有名字含 `application` 的属性源，把 `dbsyncer.storage.` 前缀的 key 捞出来直接传给 `storageService.init(properties)`。我们的源叫 `dbsyncerRuntimeConfig` → 不会被捣入 ✓（避开存储层语义污染）。
2. **不得复用 `OriginTrackedMapPropertySource`以外的类型却取名为含 `application`**——那里将 `propertySource.getSource()` 无检查强转为 `Map<String, OriginTrackedValue>`，命名不当直接 `ClassCastException`。

**EPP 自身时序**：必须晚于 `ConfigDataEnvironmentPostProcessor`（order = `HIGHEST_PRECEDENCE + 10`）才能读到 `application.properties` 里的 `dbsyncer.storage.mysql.*`；本文用 `Ordered.LOWEST_PRECEDENCE` 保证。**注意**：EPP 注入的 `dbsyncer.storage.thread-core-size` 等 B 类 key 只影响 `StorageConfig`（走完整 Environment 绑定），不影响存储层连接参数。

**风险提示（L1）**：这是本设计唯一未被验证的环节。实施首日应先做 spike 确认「EPP 阶段能否可靠读到 DB 且不影响正常启动」；失败则切 §2.2 降级预案。

**C 类也可一并注入**：`sessionTimeout`、`sqlserverCtPollIntervalMs` 同样在 EPP 阶段摊平为 `server.servlet.session.timeout`、`sqlserver.ct.poll.interval.ms`，这样它们无需 `applyCClass` 即生效；`applyCClass` 只负责真正需要即时生效的 retry / notify / error 三块。

### 4.5 前置修复：notifyTypes 缓存失效（C5）

现状（`NotifyConfig.java:96-118`，`DingTalkConfig` 同构）：

```java
private Set<NotifyType> notifyTypeSet;   // 懒加载后永不置空

public boolean shouldNotify(NotifyType type) {
    if (notifyTypeSet == null) { ... }   // 只构建一次
}
public void setNotifyTypes(List<String> notifyTypes) { this.notifyTypes = notifyTypes; }
```

→ UI 改了 `notifyTypes` 并热应用后，`shouldNotify()` 仍用旧集合，**变更静默失效**。

修复（各 1 行，Mail + DingTalk）：

```java
public void setNotifyTypes(List<String> notifyTypes) {
    this.notifyTypes = notifyTypes;
    this.notifyTypeSet = null;   // ← 新增：失效懒加载缓存
}
```

`notifyTypeSet` 是 private 且无 public getter → 不会被 Jackson 序列化，不影响落库格式。此修复独立于本 ADR 也有价值（当前改 properties 不重启同样失效），可单独提 PR。

## 5. API 设计

### 5.1 接口总览

| 方法 | 路径 | 用途 | 绑定 |
|------|------|------|------|
| GET | `/system` | 渲染页面（Thymeleaf 模型） | — |
| POST | `/system/edit` | **M3：已废弃**。`schemaVersion>=1` 后返回 410 Gone + 提示升级。存量脚本迁移到 `/system/editTab` + `{tab:"basic"}` | ~~form → `mapToBean`~~ |
| POST | `/system/editTab` | **新增**，Tab 1-6 子对象整块替换（含 `basic`） | JSON |
| GET | `/system/tabs` | **新增**，各 Tab 当前生效值（预填用） | — |
| POST | `/system/resetTab` | **新增**，子对象置 null，恢复 properties 默认 | form |
| GET | `/system/serverInfo` | **新增**，A 类只读（脱敏）。直接读 `Environment`，与 `@JsonIgnore` 的 A 类子对象同源 | — |

### 5.2 `GET /system/tabs`

子对象为 `null` 时，UI 需要知道"当前实际生效值"才能预填。返回值同时给出生效值与来源：

```json
{
  "success": true,
  "result": {
    "buffers": {
      "configured": false,
      "values": {
        "general": {
          "bufferWriterCount":       { "value": 1000, "source": "properties" },
          "bufferQueueCapacity":     { "value": 50000, "source": "properties" },
          "bufferPeriodMillisecond": { "value": 300,  "source": "default" }
        }
      }
    }
  }
}
```

`source` ∈ `db` / `properties` / `default`，UI 据此把"默认(N)"渲染成灰色占位，让用户清楚哪些还没落库。

### 5.3 `POST /system/editTab`

```json
{ "tab": "buffers",
  "payload": { "general": { "bufferWriterCount": 1000, "bufferPullCount": 20000,
                            "bufferQueueCapacity": 50000, "bufferPeriodMillisecond": 300 } } }
```

响应：

```json
{ "success": true, "restartRequired": true,
  "result": "配置已保存。缓冲区参数需在重启应用后生效。" }
```

> ⚠️ 整块替换语义：`payload` 中**缺失**的字段会被写成 Props 初始值（`null`，S1 包装类型），不是"保留旧值"。因此前端必须提交该 Tab 的**完整**字段集（表单全集，非 dirty 集合）。这条约束需在 checker 中防御：`payload` 缺关键字段时报错而非静默归 null。
>
> **S1 修订后的 0 值语义**：包装类型下，用户显式设 `0` → `Integer(0)` ≠ null → 序列化落库 ✓；
> 未填（前端不提交）→ `null` → NON_DEFAULT 省略 → 反序列化 `null` → 回退 properties ✓。
> `threshold=0`（不熔断）、`maxDurationMs=0`（不限时长）等合法业务值可正确往返。

### 5.4 `GET /system/serverInfo`（A 类只读）

```json
{ "success": true,
  "result": { "serverPort": 18686, "sslEnabled": false, "httpPort": 8080,
              "httpRedirect": false, "contextPath": "/",
              "storageType": "mysql",
              "databaseUrl": "jdbc:mysql://***.***.***.***:6008/dbsyncer",
              "databaseUsername": "test_****",
              "workerId": 1, "helpdocPath": "./helpdocs", "logLevel": "info" } }
```

**脱敏规则**：

| 字段 | 脱敏方式 |
|------|---------|
| 数据库地址 | IP 段用 `***` 替换 |
| 数据库用户名 | 保留前 4 字符 + `****` |
| 数据库密码 | 完全不展示 |
| 邮件授权码 | 只显示 `********` |
| ~~钉钉签名密钥~~ | **不展示**：`DingTalkConfig.getSecret()` 全仓 0 调用者，`DingTalkNotifyServiceProvider` 从不签名，属死字段（见 ledger §2） |

> 邮件/钉钉密钥出现在 Tab 5 编辑区时同样需脱敏：读取接口返回掩码，保存时若字段值仍为掩码则保留原值（避免掩码回写覆盖真实密钥）。

## 6. UI 布局

### 6.1 整体结构

```
┌──────────────────────────────────────────────────────────────────────┐
│  系统参数                                                             │
├──────┬────────┬────────┬──────┬──────┬──────┬────────────────────────┤
│ 基础 │ 执行器⚠️│ 缓冲区⚠️│ 重试 │ 通知 │ 错误 │                        │
│ 设置 │        │        │ 策略 │ 通道 │ 处理 │                        │
└──────┴────────┴────────┴──────┴──────┴──────┴────────────────────────┘
   每个 Tab 右上角：[保存] [恢复系统默认]        ⚠️ = 修改后需重启应用生效
```

**字段路径约定**：Tab 2-6 的 `name` 属性使用嵌套路径（如 `executors.general.threadCoreSize`），前端 `serializeJson()` 后组装成 payload 子对象提交；不使用点号直传后端（C2 不支持）。

### 6.2 Tab 1: 基础设置

**业务参数**（v1.6 起归入 `basic` 子对象，`POST /system/editTab` + `{tab:"basic"}`；字段路径 `basic.xxx`）：

| 字段 | 控件 | 默认值 | 提示文案（问号悬浮） |
|------|------|--------|---------------------|
| `basic.expireDataDays` | number | 7 | 同步数据过期时间（天）。超过此天数的历史同步记录将被自动清理。设置过小可能导致需要回查的数据丢失，设置过大占用更多存储空间。 |
| `basic.expireLogDays` | number | 30 | 系统日志过期时间（天）。超过此天数的操作日志将被自动清理。设置过小可能导致问题排查时缺少历史日志。 |
| `basic.refreshIntervalSeconds` | number | 5 | 监控页面自动刷新的间隔（秒）。值越小刷新越频繁，数据越实时，但会增加服务器负载。 |
| `basic.enableStorageWriteFail` | switch | true | 是否记录同步失败的数据详情。开启后可在错误队列中查看失败数据并支持手动重试，但会增加存储开销。 |
| `basic.maxStorageErrorLength` | number | 2048 | 每条失败日志的最大长度（字符数）。值越大记录的错误信息越完整，但占用更多存储空间。仅当"记录同步失败数据"开启时生效。 |
| `basic.enableCDN` | switch | false | 是否使用 CDN 加速静态资源加载。开启后页面加载速度更快，但需要服务器能访问公网。开启后需刷新页面生效。 |
| `basic.enableSchemaResolver` | switch | true | 是否启用标准数据解析器。开启后可支持更多异构字段类型转换（如不同数据库间的类型差异），主要用于兼容异构数据库同步。关闭后可能减少解析开销，但部分异构字段转换将不支持。 |
| `basic.enableWatermark` | switch | false | 是否在页面上显示水印。开启后页面显示水印内容，可用于追踪截图来源，防止敏感信息泄露。 |
| `basic.watermark` | text | "" | 水印的文字内容，最多 64 个字符。将显示在页面背景上，用于标识当前操作者。仅当"水印"开启时生效。 |
| `basic.enablePrintTraceInfo` | switch | false | 是否打印 trace 级别的调试信息。开启后会输出大量详细的调试日志，仅用于排查问题。生产环境建议关闭，否则会严重影响性能并产生大量日志文件。 |

> 上一版误把 `helpdocPath` 放进可编辑区，同时又在 A 类只读区列了一次，自相矛盾。现统一归 A 类（`@Value` 一次性注入，热改无效），仅保留在下方只读区。

> **v1.4 撤销的 3 个字段**（曾出现在本表，均已从可编辑区移除）：
> `sessionTimeout` → A 类，由 Spring Boot 建容器时消费，改 properties + 重启才变；
> `resetPwd` → `WebAppConfig` 在 dbsyncer-web，biz 侧写不到，且属「动作」不是「配置」，应做二次确认按钮（L7）；
> `sqlserverCtPollIntervalMs` → **死键**，`SqlServerCTListener` 走 `System.getProperty`，Spring 注入无效（F17b）。
> 三者只在下方 A 类只读区按现状展示（或按 L4/L7 结论不展示）。

**服务器信息**（A 类只读，折叠区，脱敏）：

| 字段 | 提示文案 | 脱敏 |
|------|---------|------|
| 服务端口 | 应用监听的 HTTP 端口。修改需编辑 application.properties 并重启应用。 | 原样 |
| SSL | 是否启用 HTTPS 加密访问。修改需编辑 application.properties 并重启应用。 | 开启/关闭 |
| HTTP 端口 | 额外的 HTTP 端口（用于 HTTP→HTTPS 重定向）。0 表示关闭。修改需重启应用。 | 原样 |
| 上下文路径 | 应用的部署路径前缀。修改需重启应用，且前端资源引用路径会随之变化。 | 原样 |
| 存储类型 | 配置数据的存储方式：disk（磁盘文件）或 mysql（数据库，推荐生产环境）。修改需重启应用并迁移数据。 | 原样 |
| 数据库地址 | 配置数据库的 JDBC 连接地址。修改需编辑 application.properties 并重启应用。 | IP 段 `***` |
| 数据库用户 | 配置数据库的连接用户名。修改需编辑 application.properties 并重启应用。 | 前 4 字符 + `****` |
| Worker ID | 当前实例的雪花算法 Worker ID。单机部署填默认值 1；集群部署时每个实例必须唯一，否则可能生成重复 ID。修改需重启应用。 | 原样 |
| 帮助文档路径 | 帮助文档文件的存储路径（对应 `helpdoc.path`）。系统从此路径读取帮助文档内容并在页面上展示。修改需编辑 application.properties 并重启应用。 | 原样 |
| 日志级别 | 系统日志的输出级别（trace/debug/info/warn/error/fatal/off）。修改需编辑 application.properties 并重启应用。 | 原样 |

### 6.3 Tab 2: 执行器（⚠️ 重启生效）

> **页面顶部警告**：⚠️ 以下配置修改后需**重启应用**才能生效。线程池在应用启动时创建，运行中不做在线重建（避免正在执行的任务被中断、引用替换不可靠）。
>
> **右上角「恢复系统默认」**：清空该 Tab 的 DB 配置，重启后回退到 `application.properties` 的值。

| 路径 | 字段 | 生效值 | 提示文案 |
|------|------|--------|---------|
| 通用执行器 | | | 用于通用异步任务（元数据刷新、缓冲区消费调度等） |
| `executors.general.threadCoreSize` | 核心线程数 | 默认(CPU\*2=16) | 线程池维持的最小工作线程数，即使空闲也不回收。决定同时处理通用任务的并发能力。值越大并发越强，但消耗更多 CPU 与内存；重启后生效。 |
| `executors.general.maxThreadSize` | 最大线程数 | 默认(CPU\*2=16) | 队列满且线程数未达此上限时会新建线程。峰值扩展上限，不建议过大以免系统过载；重启后生效。 |
| `executors.general.threadQueueCapacity` | 任务队列容量 | 默认(1000) | 核心线程全忙时新任务的等待队列。队列满后尝试建线程（至最大线程数），仍无法处理则拒绝任务；重启后生效。 |
| 存储执行器 | | | 控制配置/日志数据持久化到存储层的并发 |
| `executors.storage.threadCoreSize` | 核心线程数 | 默认(CPU=8) | 数据持久化的基础并发能力。值越大写入吞吐越高，但受数据库连接池与磁盘 IO 限制；重启后生效。 |
| `executors.storage.maxThreadSize` | 最大线程数 | 默认(CPU=8) | 存储操作峰值并发上限。**建议不超过数据库连接池大小**，否则多出的线程会因等待连接而空转；重启后生效。 |
| `executors.storage.threadQueueCapacity` | 任务队列容量 | 默认(500) | 存储任务的等待队列。队列满后会新建线程或拒绝任务；重启后生效。 |
| 调度线程池 | | | 驱动系统内所有定时任务 |
| `executors.schedulerPoolSize` | 调度线程数 | 默认(CPU=8) | `taskScheduler` 的线程数，决定同时运行的定时任务数量，包括：缓冲区消费（默认每 300ms）、指标上报、监控采集、增量拉取调度。值过小会导致定时任务排队互相拖延，影响同步实时性；值过大增加空闲开销。**此参数涉及全部已注册定时任务，运行中绝不热改**；重启后生效。 |
| 调度任务执行器 | | | 用于任务分发与状态检查 |
| `executors.dispatch.threadCoreSize` | 核心线程数 | 默认(CPU\*2=16) | 任务分发（调度触发、状态检查）的基础并发能力；重启后生效。 |
| `executors.dispatch.maxThreadSize` | 最大线程数 | 默认(CPU\*2=16) | 任务分发的峰值并发上限；重启后生效。 |
| `executors.dispatch.threadQueueCapacity` | 任务队列容量 | 默认(64) | 分发任务的等待队列。此值偏小时突发任务易被拒绝；重启后生效。 |

> **未纳入**：`dbsyncer.parser.table.group.*`（表执行器）。仓库中不存在 `TableGroupBufferConfig` 与 `TableGroupBufferActuator`，这 7 项 properties **无 Java 消费方**，属预留空配置。暴露到 UI 会让用户以为可调，实际无效。待表执行器落地后再纳入。

### 6.4 Tab 3: 缓冲区（⚠️ 重启生效）

> **页面顶部警告**：⚠️ 以下配置修改后需**重启应用**才能生效。缓冲区队列（`LinkedBlockingQueue`）在启动时一次性创建，运行中无法安全重建——`queueCapacity` 不支持原地缩容，替换队列引用会**丢失队列中正在等待处理的数据**，直接造成数据不一致。

| 路径 | 字段 | 生效值 | 提示文案 |
|------|------|--------|---------|
| 通用执行器缓冲 | | | 通用同步任务的内存缓冲队列 |
| `buffers.general.bufferWriterCount` | 任务中断上报失败数 | 默认(1000) | L5 改名：原名「单次写入任务数」严重误导，实测唯一消费点是 `MetaBufferActuator.java:124` 作为 `failCount` 传给 `ErrorQueueCollectorImpl.closeTask()`，只进告警文案，不影响并发/批次。值越大告警文案中的失败计数越大；重启后生效。 |
| `buffers.general.bufferPullCount` | 每次消费任务数 | 默认(20000) | 每次定时消费从缓冲队列取出的任务条数。值越大消费越快，但单次处理时间更长，积压时的感知延迟也更大；重启后生效。 |
| `buffers.general.bufferQueueCapacity` | 缓冲队列容量 | 默认(50000) | 内存缓冲的最大任务条数。值越大越能吸收突发写入流量、避免上游阻塞，但占用更多内存（存在 OOM 风险）；值过小生产端会阻塞等待消费。**队列容量在启动时创建，运行中不可修改**；重启后生效。 |
| `buffers.general.bufferPeriodMillisecond` | 消费间隔（毫秒） | 默认(300) | 定时消费缓冲队列的频率。值越小延迟越低、任务更快被处理，但轮询越频繁增加 CPU 开销；值越大吞吐越平稳但延迟上升；重启后生效。 |
| 存储执行器缓冲 | | | 存储写入操作的内存缓冲队列 |
| `buffers.storage.bufferWriterCount` | ~~单次写入任务数~~ | — | ❗ **不暴露**：存储侧 `buffer-writer-count` 全仓零调用（死键），见 ADR 配置清单。 |
| `buffers.storage.bufferPullCount` | 每次消费任务数 | 默认(20000) | 存储侧每次消费的条数。值越大消费越快，但会给目标数据库带来瞬时写压力；重启后生效。 |
| `buffers.storage.bufferQueueCapacity` | 缓冲队列容量 | 默认(50000) | 存储侧缓冲队列上限，用于吸收写入峰值、避免上游阻塞。过大占用内存。**队列容量在启动时创建，运行中不可修改**；重启后生效。 |
| `buffers.storage.bufferPeriodMillisecond` | 消费间隔（毫秒） | 默认(300) | 存储侧消费频率。值越小写入越及时，但增加数据库查询/提交频率；重启后生效。 |

### 6.5 Tab 4: 重试策略（立即生效）

> **⚠ 任务级覆盖优先**：`RetryConfig` 除 `global` 外还有 `task` 映射（`dbsyncer.retry.task.<taskId>.*`），`getPolicy(taskId)` 对**已配置任务级策略的任务直接返回任务级策略，完全忽略本 Tab**。因此本页改动不会对那些任务生效。本期不将任务级覆盖纳入 UI（见 ADR 遗留问题 L7），提示文案必须声明这一例外。

| 路径 | 控件 | 生效值 | 提示文案 |
|------|------|--------|---------|
| `retry.disable` | switch | false | 是否禁用全局重试机制。开启后同步任务失败不再重试，直接标记失败并记日志。适用于源端/目标端明确不可用时临时关闭，避免无效重试浪费资源。对已配置任务级重试策略的任务无效。立即生效。 |
| `retry.maxAttempts` | number | 默认(3) | 最大执行次数（含首次）。例如设 3 = 首次执行 + 2 次重试。值越大容错越强，但失败任务恢复更慢，且可能掩盖持续性问题。已配置任务级重试策略的任务**不受此值影响**。立即生效。 |
| `retry.initialIntervalMs` | number | 默认(1000) | 首次重试前的等待（毫秒）。值越小恢复越快，但在目标端不可用时会造成频繁重试压力（如数据库连接失败场景）。立即生效。 |
| `retry.maxIntervalMs` | number | 默认(60000) | 指数退避的等待上限（毫秒）。无论重试到第几次，两次重试间隔不超过此值，防止退避过长导致恢复过慢。立即生效。 |
| `retry.multiplier` | number | 默认(2.0) | 指数退避增长因子。每次重试后等待时间的倍数，例如 2.0 表示 1s→2s→4s→8s。值越大给系统越多恢复时间，但重试总耗时更长。立即生效。 |
| `retry.maxDurationMs` | number | 默认(20000) | 从首次失败起的重试总时长上限（毫秒）。**0 表示不限制**，仅由最大次数决定终点。防止任务长期占用资源反复重试。立即生效。 |
| `retry.terminationMode` | select | 默认(MAX_ATTEMPTS) | 重试终止判定模式：`MAX_ATTEMPTS`=按次数、`MAX_DURATION`=按耗时、`WHICHEVER_FIRST`=两者先到为准。网络抖动场景建议按次数，资源受限场景建议按耗时。立即生效。 |
| `retry.useKeyword` | switch | false | 是否仅重试匹配关键字的异常。开启后只有异常消息命中关键字（如 deadlock、timeout）才重试，其余异常直接失败；关闭则无条件重试。建议生产环境开启，避免对不可恢复错误（如数据格式错误）做无效重试。立即生效。 |
| `retry.matchMode` | select | 默认(CONTAINS_IGNORE_CASE) | 关键字匹配模式，4 选 1：`EXACT`=完全相等、`CONTAINS`=包含、`CASE_INSENSITIVE`=相等且忽略大小写、`CONTAINS_IGNORE_CASE`=包含且忽略大小写。推荐 `CONTAINS_IGNORE_CASE`，覆盖面最广。立即生效。 |
| `retry.keywords` | textarea | 默认(deadlock) | 重试关键字列表，**每行一个**。仅当"关键字匹配"开启时生效。常见关键字：`deadlock`（死锁）、`Lock wait timeout`（锁等待超时）、`Communications link failure`（连接断开）。立即生效。 |
| `retry.excludeKeywords` | textarea | (空) | 重试**排除**关键字列表，每行一个。命中排除关键字的异常即使命中重试关键字也不重试，用于在宽泛匹配中挖掉明确的不可恢复错误。立即生效。 |

### 6.6 Tab 5: 通知通道（立即生效）

| 路径 | 控件 | 生效值 | 提示文案 |
|------|------|--------|---------|
| `notify.channels` | checkbox | (空) | 启用的通知通道，可多选：`mail`（SMTP 邮件）、`dingtalk`（钉钉群机器人）。**未勾选的通道不会发送任何通知**，即使下方凭证已填写。至少勾选一个才能收到通知。立即生效。 |
| `notify.mail.username` | text | (空) | 发送通知邮件的账号（如 836391306@qq.com），即收件人看到的发件人地址。需在邮箱设置中开启 SMTP 服务。立即生效。 |
| `notify.mail.password` | password | (空) | 邮件账号的**授权码**（注意：不是邮箱登录密码），用于 SMTP 认证。例如 QQ 邮箱需在「设置 → 账户 → POP3/SMTP 服务」开启并获取。页面展示时脱敏为 `********`。立即生效。 |
| `notify.mail.notifyTypes` | checkbox | (空=全部) | 邮件通知的事件类型。不勾选=通知全部类型；勾选后只通知选中项。可选：任务中断、数据错误、手动操作、错误队列熔断、致命关键字命中、DDL 操作、目标异常、数据源异常。立即生效。 |
| `notify.dingtalk.webhookUrl` | text | (空) | 钉钉群机器人的 Webhook 地址。在钉钉群「设置 → 智能群助手 → 添加机器人 → 自定义」中获取。通知消息将发送到该群。立即生效。 |
| `notify.dingtalk.secret` | password | (空) | L3：虽然当前 `getSecret()` 全仓零调用、钉钉签名未启用，但保留暴露并脱敏展示（前 3 字符 + `****`）。理由：钉钉加签是安全最佳实践，未来可能启用；保留字段可引导用户配置。文案标注「当前未启用签名校验，配置后重启生效」。 |
| `notify.dingtalk.notifyTypes` | checkbox | (空=全部) | 钉钉通知的事件类型，作用同邮件通知类型，不勾选则通知全部。立即生效。 |

> **不暴露** `dbsyncer.plugin.notify.mail.enabled`：`NotifyConfig.MailConfig` 类中**不存在** `enabled` 字段，通道启用完全由 `notify.channels` 控制。properties 里这一项是死配置（见 ADR 遗留问题 L3）。若 UI 提供该开关，用户勾选后不会有任何效果。

### 6.7 Tab 6: 错误处理（立即生效）

| 路径 | 控件 | 生效值 | 提示文案 |
|------|------|--------|---------|
| `error.threshold` | number | 默认(1) | 错误队列熔断阈值。某任务的连续错误数达到此值时触发熔断、暂停任务，防止错误持续扩散。0 表示**不启用熔断**（错误队列将无限增长）。建议生产环境设合理值（如 100），避免错误数据堆积撑满存储。立即生效。 |
| `error.fatalMatchMode` | select | 默认(CONTAINS_IGNORE_CASE) | 致命关键字的匹配模式，用于判定异常是否不可恢复。4 个取值语义同重试关键字匹配模式。立即生效。 |
| `error.fatalKeywords` | textarea | (空) | 致命关键字列表，**每行一个**。命中时任务**直接终止**——不写入错误队列、不重试、不熔断。适用于明确不可恢复的错误（如 `Duplicate entry` 主键冲突、连接彻底断开），避免无效处理浪费资源。立即生效。 |

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `EnvironmentPostProcessor` 阶段读 DB 不可行（存储层 SPI 尚未初始化） | B 类无法启动生效，整个 ADR 阻塞 | 实施首日先做 spike；失败切 §2.2 降级预案（拆 `@Configuration` + `ExecutorBeanFactory`） |
| EPP 读 DB 抛异常导致启动失败 | 应用无法启动，比配置不生效更严重 | `try/catch` 兜底 + 仅告警；EPP 内不抛任何异常（L2） |
| Props 与目标 Bean 字段名漂移 | `copyProperties` 静默失配，配置"看起来保存了但没生效" | ① UI 预填取运行时生效值（`/system/tabs`），不取 Props 初始值；② 单测断言每个 Props 字段在目标 Bean 上有同名可写属性 |
| Tab 整块替换语义导致未提交字段被归零 | 用户改一个字段、其余字段被清成 0 | 前端提交该 Tab **完整表单**；checker 对关键字段缺失报错；文档在 UI 明示"每 Tab 独立整块保存" |
| 用户保存 B 类后未重启（v1.6 已改为自重启，无守护时仍可能） | 配置不生效，误以为已生效 | 自重启 + 发消息；启动期对照表打印「DB 有值但未生效」行；页面顶部常驻「存在 N 项待重启生效」横幅 |
| 任务级重试策略覆盖全局（实测） | 用户改了全局但部分任务无变化，误判为 bug | Tab 4 顶部固定说明该例外；提示文案逐个标注；另评任务级覆盖是否入 UI（L7） |
| 邮件/钉钉密钥读取时返回掩码、保存时回写掩码 | 真实密钥被 `********` 覆盖，通知静默失效 | 保存时若 incoming 值等于展示掩码，则从已存配置里取回原值再写入（仅在 `editTab` 的 notify 分支处理） |
| `mapToBean` 对未知 key NPE、对空串 `parseInt` 抛错 | Tab 1 保存 500 | 前端只提交已知字段；checker 预处理把空串归一化为 `0`（沿用现有 `params.put` 模式） |
| EPP PropertySource 命名含 `application` | 被 `StorageSupportConfiguration` 按名前过滤捣进存储层 `init(properties)`，或因 `getSource()` 强转 `Map<String, OriginTrackedValue>` 抛 CCE | 固定用 `dbsyncerRuntimeConfig`；单测断言名字不含 `application`（§9.11） |
| `notifyTypeSet` 懒加载缓存永不失效（C5，实测缺陷） | 修改通知类型后热应用**静默无效**，直到重启 | 先落 §4.5 的 2 行修复，再上 Tab 5；未修复前 Tab 5 的 notifyTypes 应标为「重启生效」 |
| `enableCDN` 开启但公网不可达 | 页面白屏 | 现有 `NetworkUtil.isInternetAvailable()` 校验保留 |
| **自重启时无进程守护**（`java -jar` 直跑、无 systemd/docker 重启策略） | `System.exit(0)` 后系统直接下线，**比不重启更严重**（从「配置没生效」升级为「全站不可用」） | 重启前探测守护是否存在（读 `PID1`/环境变量/配置开关 `dbsyncer.runtime.self-restart.enabled`），探测不到就降级为「仅提示需人工重启」，绝不裸 `System.exit`（L13） |
| **重启风暴**：某个值能通过范围校验但实际让启动失败 | 每次重启后又读到同一个坏值 → 无限重启循环 | ① 启动期校验失败**不回写 DB**（坏值留在 DB，但以 fallback 运行 + 横幅），避免自我修正写入；② sidecar 里带 `dbsyncer.runtime.restart-guard=本次启动序号`，同一配置摘要在 N 分钟内重复重启即停止自重启并告警 |
| **迁移把计算型默认值固化**（F25：多数线程池默认 = `availableProcessors()*2`） | 迁移后换到核数不同的机器，DB 里那份写死的值会覆盖掉本该随 CPU 重算的默认，**降配机器不会缩池、升配机器不会扩池** | 迁移时只写「与 properties 显式配置一致」的键，纯默认（properties 未出现）的键保持子对象 `null`；对照表的 `source` 列可核对（L14） |
| **A 类子对象被误持久化** | 运维手改 properties 后，页面显示旧副本 → 显示值 ≠ 实际值 | A 类 6 个子对象 `@JsonIgnore`，只由 web 层 `environment.getProperty(...)` 现取（§2.0）；单测断言持久化 JSON 不含 `server`/`storageConn`/`logging`/`helpdoc`/`worker`/`license` |
| **`basic` 迁子对象后 `mapToBean` 打不进嵌套路径** | Tab 1 保存静默丢失全部字段 | Tab 1 一并改走 `/system/editTab`；旧 `/system/edit` 保留但只服务未迁移的存量表单，`schemaVersion>=1` 后前端不再调用 |
| **`SystemConfigChecker:56-67` 无条件写 5 个布尔**（F8） | 部分字段提交时其余 checkbox 被强制 `false` | 切 `editTab` 后按 Tab 白名单裁剪该段；单测锁定（L8） |

## 8. 改动清单

| 模块 | 文件 | 改动 | 说明 |
|------|------|------|------|
| dbsyncer-parser | `model/SystemConfig.java` | 修改 | 删平铺字段→并入 `basic`；+`schemaVersion`；+B/C 类 5 子对象；+A 类 6 子对象（`@JsonIgnore`） |
| **dbsyncer-common** | `common/config/props/*.java` | 新增 | 12 个 Props 类（6 A + 5 B/C + BasicProps）。放 common 的理由见 §2.1.1 / F19 |
| dbsyncer-common | `common/config/SystemTabEnum.java` | 新增 | Tab → 类型 / 存取 / 是否重启 / 是否落库，单一来源 |
| dbsyncer-biz | `impl/RuntimeConfigService.java` | 新增 | C 类运行期原地 setter 回写 + 子对象/Bean 合并读；biz→manager→parser 链可见目标 Bean，但**看不到** `WebAppConfig`（A 类回显交 web） |
| dbsyncer-parser | `parser/notify/NotifyConfig.java` | 修改 | `setNotifyTypes()` 补 `notifyTypeSet = null`（Mail + DingTalk 各 1 行，§4.5） |
| dbsyncer-manager | `command/PreloadTemplate.java` | 修改 | 【v1.6】`openStartupGates()` **之前**插三个钩子：迁移 → 启动校验 → 对照表。顺序要求与 ADR-0028 同源（端口未开、无任务在跑） |
| dbsyncer-web | `config/RuntimeConfigPropertySource.java` | 新增 | `EnvironmentPostProcessor`，B 类启动注入 |
| dbsyncer-web | `config/RuntimeConfigSidecar.java` | 新增 | EPP 侧 sidecar 读取 + 摊平 + fail-soft（不碰 DB） |
| dbsyncer-web | `resources/META-INF/spring.factories` | 新增/修改 | 注册 EPP |
| dbsyncer-biz | `impl/SystemConfigServiceImpl.java` | 修改 | 新增 `editTab` / `getTabs` / `resetTab` / `getServerInfo` |
| dbsyncer-biz | `checker/impl/system/SystemConfigTabChecker.java` | 新增 | 按 Tab 校验 payload |
| dbsyncer-biz | `vo/SystemConfigVo.java` | 修改 | 暴露 5 个子对象给页面渲染 |
| dbsyncer-web | `controller/system/SystemController.java` | 修改 | 4 个新接口 |
| dbsyncer-web | `resources/public/system/system.html` | 修改 | 6 Tab 布局 + 问号提示 |
| dbsyncer-web | `resources/static/js/system/index.js` | 修改 | Tab 切换、per-tab 保存、嵌套路径组装、重启提示 |

> **明确不做**：线程池 setter 注入、`volatile` 改造、`rebuildExecutor()`、缓冲区队列 drain-回填。相对 v1.0 删掉约 300 行重建逻辑。
| dbsyncer-biz | `service/RuntimeConfigValidator.java` + `ConfigMigrationService.java` + `ConfigComparator.java` | 新增 | v1.6：两处共用的取值校验、首次迁移、启动对照表 |
| dbsyncer-biz | `impl/SystemConfigServiceImpl.java` | 追加 | 保存链路末尾接 `SidecarPropertyWriter` 与 `SelfRestartService`（顺序：DB → sidecar → 通知 → 重启） |
| dbsyncer-web | `resources/config/`（运行时目录） | 新增文件 | `dbsyncer-runtime.properties`（sidecar，可删，删了退回 properties 行为） |

## 9. 验证清单

### 9.1 EPP 启动注入（B+C 类核心路径）

1. `application.properties` 设 `dbsyncer.parser.general.thread-core-size=8`、`dbsyncer.retry.global.max-attempts=3`。
2. UI 保存执行器 Tab `general.threadCoreSize=16`、重试 Tab `maxAttempts=5`，重启应用。
3. 验证：
   - 日志出现 `dbsyncerRuntimeConfig` PropertySource 生效记录。
   - `generalBufferConfig.getThreadCoreSize() == 16`。
   - `generalExecutor` 线程池 `corePoolSize == 16`（JMX 或反射确认）——证明**建池时就用到了 DB 值**，无需重建。
   - `LinkedBlockingQueue` 剩余容量与 DB 的 `bufferQueueCapacity` 一致。
   - `retryConfig.getGlobal().getMaxAttempts() == 5`（C 类同样走 Environment，不依赖 `applyCClass`）。

### 9.2 EPP fail-soft

1. 停掉配置库（或把 `dbsyncer.storage.mysql.url` 指向不存在的地址）。
2. 启动应用。
3. 验证：应用**正常启动**，仅有告警日志，所有参数回退 `application.properties` 值。

### 9.3 Tab 独立保存 + 整块替换语义

1. 依次只保存 Tab 4、Tab 6。
2. 验证：
   - DB JSON 中 `retry`、`error` 存在，`executors`/`buffers`/`notify` 缺失（NON_DEFAULT 省略 null）。
   - 保存 Tab 6 不影响 Tab 4 已存的值（读-改-写引用语义）。
   - 保存 Tab 4 时故意漏传 `maxAttempts`，接口报错而非静默存 0。

### 9.4 0 值语义（回归上一版缺陷）

1. 保存错误处理 Tab，`threshold=0`；重启。
2. 验证：
   - 熔断确实**未启用**（连续失败不触发熔断）。
   - `error` 子对象仍落库（其他字段非默认），`threshold` 重载后为 0 而非回退 properties 的 1。
3. 同样验证 `retry.maxDurationMs=0` 表示"不限制时长"。

### 9.5 存量兼容

1. 用**旧版本**导出的配置 JSON（不含任何新字段）导入。
2. 启动并访问系统参数页。
3. 验证：`schemaVersion` 从 0 迁移为 1；A 类 6 子对象**不在**持久化 JSON 里；未配过的 Tab 子对象为 `null` 且各 Tab 显示 `source=default/properties`；同步功能无异常。

### 9.6 C 类立即生效

1. 保存重试 Tab，`maxAttempts=5`、`keywords` 三行。
2. 不重启，触发一个必然失败的同步任务。
3. 验证：实际重试 5 次；`RetryConfig.getGlobal().getKeywords()` 返回 3 个元素；`matcher` 缓存已失效重建（旧关键字不再命中）。
4. 响应 `restartRequired=false`。

### 9.7 B 类保存不改变运行时

1. 保存执行器 Tab，`general.threadCoreSize=16`（当前生效 8）。
2. 验证：
   - 响应 `restartRequired=true`；确认后走自重启（§2.0.4），重启前先发消息；页面顶部出现「待重启生效」横幅。
   - **运行时线程池 corePoolSize 仍为 8**（未被误改）。
   - 重启后变为 16。

### 9.8 脱敏与密钥保护

1. 展开 Tab 1"服务器信息"，验证 DB 地址/用户/密码按规则脱敏。
2. 读取 Tab 5，邮件授权码为 `********`；不改动该字段直接保存。
3. 验证真实授权码未被掩码覆盖（触发一次通知能成功发送）。

### 9.9 字段对齐单测

1. 运行 `PropsFieldAlignmentTest`。
2. 验证每个 Props 字段在目标 Bean 上均存在同名 public setter；缺失即测试失败。

### 9.10 提示文案完整性

1. 逐 Tab 检查每个输入项右侧均有问号图标。
2. 悬浮显示"作用 + 影响 + 生效时机"三段式说明。
3. B 类 Tab 每项提示末尾带"重启后生效"。

### 9.11 EPP 属性源命名隔离

1. 启动后枚举 `environment.getPropertySources()`，确认存在名为 `dbsyncerRuntimeConfig` 的源且已 `addFirst`。
2. 验证 `StorageSupportConfiguration.storageService()` 正常返回 `MySQLStorageService`（而非抛 CCE 或误读 `dbsyncer.storage.type=null` 回退磁盘存储）。
3. 在 UI 保存 `dbsyncer.storage.thread-core-size=2`（DB + sidecar 同时落），重启后验证：`StorageConfig.getThreadCoreSize() == 2`，而存储层连接参数（url/账号）仍按 `application.properties` 生效——确认 A/B 类同前缀不串扰。


### 9.12 首次配置迁移（v1.6）

1. 用一份 `schemaVersion` 缺失（反序列化为 0）的存量 DB 记录启动。
2. 验证日志出现「配置迁移完成，schemaVersion 0 → 1」，且迁移后各 Tab 的 `source` 由 `properties/default` 变为 `db`。
3. **幂等性**：再次重启，验证不发生第二次迁移、值不漂移（`revision` 语义由 `schemaVersion` 单点承载）。
4. **不越权迁移 A 类**：验证持久化 JSON 里不含 `server`/`storageConn`/`logging`/`helpdoc`/`worker`/`license`。
5. **计算型默认不被固化**（F25）：properties 未出现的线程池键，迁移后子对象内该字段仍为 `null`，换到不同 CPU 核数的机器重启，生效值随 `availableProcessors()` 重算。

### 9.13 启动期对照表（v1.6）

1. properties 设 `thread-core-size=8`，UI 存 `16`，另一项不配置。
2. 启动，验证表格在 `openStartupGates()` **之前**打印（端口未开时已可见），三列分别为 props/DB/最终，且 `source` 列依次为 `DB`、`props`、`default`。
3. 验证「DB 有值但运行时不等」的行被标为 ⚠（这类行是 sidecar 与 DB 漂移的直接信号，L10）。

### 9.14 前置校验替代事后回滚（v1.6）

1. 保存非法值：`threadCoreSize=0`、`threadCoreSize>maxThreadSize`、`queueCapacity<1`、`maxAttempts` 越界、`page 大小白名单外`。
2. 验证：`POST /system/editTab` 返回 400 + 字段级错误，**DB 与 sidecar 零写入**（对比提交前的 JSON 全等）。
3. 绕过 UI 直改 DB 写入非法值后启动，验证：该字段回退 properties 值 + warn 日志 + UI 顶部横幅，**其余字段照常生效**（不是整体拒绝启动，也不是把坏值写回 DB）。
4. 验证校验器只有 `RuntimeConfigValidator` 一处：同一非法值在保存与启动两条路径上报错文案一致。

### 9.15 自重启与守护检测（v1.6）

1. 有守护（systemd `Restart=always`）环境保存 B 类并确认，验证：收到含操作人/变更键/重启时间的通知 → 延迟 3 秒 → 优雅停机 → 守护拉起 → 新值生效。
2. **无守护**环境（`java -jar` 直跑）重复，验证：**不执行** `System.exit`，只返回「需人工重启」提示（L13）。
3. 注入一个能通过校验但会让启动失败的组合，验证重启守卫在 N 分钟内拦截第二次自重启并告警，不进入重启循环。
4. 验证优雅停机是在 DB 提交 + sidecar 重写**都成功之后**才发起（L11 顺序）。