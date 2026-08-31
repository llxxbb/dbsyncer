# ADR-0029 事实基线（当前树实测）

## 元信息

| 字段 | 值 |
|------|-----|
| 对应代码 | `lxb-config` @ `58fd164df` |
| 核实方式 | 逐文件 grep/read，全部带 `file:line` |
| 最后更新 | 2026-08-31 |
| 用途 | ADR-0029 与其设计稿的唯一事实来源；任何引用冲突以本文件为准 |

> ⚠️ **本文件的存在理由**：本 ADR 的早期版本引用过一批实际不存在的标识符
> （`RuntimeConfigDefaults`、`QueueRuntimeConfig`、`@DefaultValue`、`PageController`、
> `SystemConfig` 48 字段）。经 `git fsck` 悬空提交、`git log --all -S`、stash、
> 其他 worktree、IDE history 全面核查，确认它们**从未进入 git 历史**。
> 因此下述每一条都必须重新实测，禁止沿用旧稿。

---

## F1 SystemConfig 只有 10 个字段

`dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/SystemConfig.java`

| 字段 | 行 | 默认值 |
|------|----|--------|
| `int expireDataDays` | :26 | 7 |
| `int expireLogDays` | :31 | 30 |
| `int refreshIntervalSeconds` | :36 | 5 |
| `boolean enableStorageWriteFail` | :41 | true |
| `int maxStorageErrorLength` | :46 | 2048 |
| `boolean enableCDN` | :51 | false |
| `boolean enableWatermark` | :56 | false |
| `String watermark` | :61 | null |
| `boolean enableSchemaResolver` | :66 | true |
| `boolean enablePrintTraceInfo` | :72 | false |

- 继承 `ConfigModel`（:17），构造器固定 `type = ConfigConstant.SYSTEM`（:19-21）。
- **没有** `helpdocPath`、**没有** `@DefaultValue`、**没有** 48 字段。

## F2 无 `@DefaultValue` 注解体系

- `grep -r "@DefaultValue" --include=*.java .` → **0 命中**（含 target 外的全部模块）。
- `git log --all --oneline -S "@DefaultValue"` → 空。
- 结论：**元数据基线尚未存在**，必须由 0029 自建，不能声称"复用 ADR-0028 基线"。

## F3 两套配置源互不相通

| | DB 侧 | Spring 侧 |
|---|---|---|
| 载体 | `SystemConfig`（`dbsyncer_config.JSON`） | `@ConfigurationProperties` Bean |
| 写入 | UI `/system/edit` | 仅 `application.properties` |
| 读取 | `OperationTemplate` 内存缓存 | 容器启动时绑定 |
| 生效 | 编辑即生效（见 F5） | 需重启 JVM |

UI 对 Spring 侧配置**完全无写入口**——这才是 0029 的真实问题域。

## F4 存储表结构

`dbsyncer-connector/dbsyncer-connector-mysql/src/main/resources/dbsyncer_mysql_config.sql`

```sql
CREATE TABLE `dbsyncer_config` (
  `ID` varchar(64) NOT NULL, `NAME` varchar(200) NOT NULL,
  `TYPE` varchar(24) NOT NULL, `CREATE_TIME` bigint NOT NULL,
  `UPDATE_TIME` bigint NOT NULL, `JSON` mediumtext NOT NULL, ...)
```

- **单表多类型**：`TYPE` 区分 system/user/connector/mapping/tableGroup/meta/projectGroup。
- 整对象序列化为一个 `JSON` 列 → 嵌套子对象天然可存，无需改表。

## F5 写路径：编辑即更新缓存

- `OperationTemplate.execute()` `dbsyncer-parser/.../parser/impl/OperationTemplate.java:93`
  → `:99 ConfigModelUtil.convertModelToMap(model)` → 落库 → `:106 cache(model, ...)`
  → `:114 cacheService.put(id, model)`
- `ConfigModelUtil.convertModelToMap()` `dbsyncer-parser/.../util/ConfigModelUtil.java:20-29`
  → `:27 params.put(JSON, JsonUtil.objToJson(model))`（整对象一把梭）
- 读：`ProfileComponentImpl.getSystemConfig()` `dbsyncer-parser/.../impl/ProfileComponentImpl.java:106-121`
  → `operationTemplate.queryAll(SystemConfig.class)`，**多条时取 `createTime` 最早、平手取 `id` 最小**（:118-119）
- 预加载：`PreloadTemplate.execute()` `dbsyncer-manager/.../impl/PreloadTemplate.java:159-193`
  → `:185 operationTemplate.cache(...)`

## F6 `JsonUtil` 三次覆盖包含策略，最后一次生效

`dbsyncer-common/src/main/java/org/dbsyncer/common/util/JsonUtil.java:20-22`

```java
:20 objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
:21 objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
:22 objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT); // ← 实际生效
```

后果：
1. 等于 Java 默认值的字段**不落库**（`int x = 0` 且当前值 0 → JSON 里没这个键）。
2. 子对象为 `null` → 整个对象不进 JSON。
3. **字段级 sentinel 不可用**：`0`/`false`/`""` 既可能是"用户主动设置"也可能是"默认"，无法区分。
   → 表达"未覆盖"只能用**整个 Tab 子对象为 null**。

## F7 表单保存通道不支持嵌套

`dbsyncer-common/src/main/java/org/dbsyncer/common/util/BeanUtil.java`

- `mapToBean(Map<String,String>, Object)` :13-30
  - :19 `getField(property, clazz)` 精确字段名匹配，**不认点号路径**
  - :20 `field.getType()` —— `getField` 返回 `null` 时**直接 NPE**（`getField` :66-75 在 `Object` 层返回 null）
  - :22 `convert(value, fType)` 只认 `Long/Float/Double/Integer/Boolean/String`（:44-64），**无枚举、无 List、无嵌套对象**
- 调用点：`SystemConfigChecker.checkEditConfigModel()` `dbsyncer-biz/.../checker/impl/system/SystemConfigChecker.java:76`

→ 前端多传任何未知 key 都会 500；嵌套对象必须走**独立端点 + 显式 JSON 反序列化**。

## F8 checkbox 无条件写回，分 Tab 保存会误关

`SystemConfigChecker.java:56-59`

```java
params.put("enableStorageWriteFail", isNotBlank(params.get("enableStorageWriteFail")) ? "true" : "false");
params.put("enableWatermark",     ...); params.put("enableSchemaResolver", ...); params.put("enablePrintTraceInfo", ...);
```

`:60-67` `enableCDN` 额外做 `NetworkUtil.isInternetAvailable()` 校验后同样无条件 put。

→ 若某次请求只提交部分字段，这 5 个布尔会被**强制写成 false**。分 Tab 保存必须按 Tab 白名单裁剪这段逻辑。

## F9 8 个配置 Bean 的真实前缀与字段

| Bean | 文件 | 注解 | 字段（默认值） |
|------|------|------|----------------|
| `AppConfig` | `common/config/AppConfig.java` | `@Configuration @ConfigurationProperties("info.app")` :17-18 | `name`=DBSyncer :21, `version` :23, `copyright` :25, `company`="" :27 |
| `CacheConfig` | 同目录 | 同上 `dbsyncer.web` :27-28 | `Map<String,CacheProperties> cache` :33；内嵌 `CacheProperties.max`=200 :76；3 个 `@Bean` :35/:47/:52 |
| `GeneralBufferConfig` | 同目录 | 同上 `dbsyncer.parser.general` :16-17 | `threadCoreSize`=CPU×2 :22, `maxThreadSize`=CPU×2 :27, `threadQueueCapacity`=1000 :32 |
| `StorageConfig` | 同目录 | 同上 `dbsyncer.storage` :19-20 | `threadCoreSize`=CPU :26, `maxThreadSize`=CPU :31, `threadQueueCapacity`=500 :36 |
| `DispatchTaskConfig` | 同目录 | 同上 `dbsyncer.common.dispatch` :19-20 | `threadCoreSize`=CPU×2 :25, `maxThreadSize`=CPU×2 :30, `threadQueueCapacity`=64 :35 |
| `SchedulerConfig` | 同目录 | 同上 `dbsyncer.web.scheduler` :16-17 | `poolSize`=CPU :23 |
| `RetryConfig` | 同目录 | 同上 `dbsyncer.retry` :42-43 | `RetryPolicy global` :49, `Map<String,RetryPolicy> task` :52；`getPolicy(taskId)` :61-63 |
| `BufferActuatorConfig` | 同目录 | **无注解的 abstract 类** :10 | `bufferWriterCount`=100 :15, `bufferPullCount`=1000 :20, `bufferQueueCapacity`=30000 :25, `bufferPeriodMillisecond`=300 :30 |

- `GeneralBufferConfig` 与 `StorageConfig` **都 extends `BufferActuatorConfig`**（buffer 4 字段的共享基类）。

## F10 4 个线程池在 bean 创建期建立

| Bean 方法 | 位置 |
|-----------|------|
| `generalExecutor()` | `GeneralBufferConfig.java:34-35` |
| `storageExecutor()` | `StorageConfig.java:38-39` |
| `dispatchTaskExecutor()` | `DispatchTaskConfig.java:37-38` |
| `taskScheduler()` | `SchedulerConfig.java:30-31` |

全部 `@Bean(destroyMethod = "shutdown")`。

**时序**：这些 `@Bean` 在容器 bean 创建期执行；`PreloadTemplate` 挂在
`ContextRefreshedEvent`（`PreloadTemplate.java:49`, `:81`）——**更晚**。
→ "启动时先改 Bean 字段、线程池自然用新值" **不成立**；池子早已按 properties 值建好。
→ 唯一不动池子引用又能生效的路径：在 refresh **之前**把 DB 值注入 `Environment`。

## F11 缓冲区参数的固化点

`dbsyncer-parser/.../flush/AbstractBufferActuator.java`

- :49 `private BufferActuatorConfig config;`（**非 volatile**）
- :87 `scheduledTaskService.start(taskKey, config.getBufferPeriodMillisecond(), this)` —— 周期在 `start()` 固化
- :95 `this.queue = new LinkedBlockingQueue<>(config.getBufferQueueCapacity())` —— 容量在建队时固化
- :224 / :263 / :294 每次读取 `queueCapacity` / `pullCount` / `writerCount`（这三个是热读）
- :289 `public void setConfig(BufferActuatorConfig config)`（已有 setter）

注入方：`MetaBufferActuator.java:72 setConfig(generalBufferConfig)`；
`StorageBufferActuator.java:45 setConfig(storageConfig)`。

→ 改 DB 值后：**pullCount/writerCount 立即可见；queueCapacity 与 period 必须重启任务**（队列已按旧容量 new 出来）。

## F12 存储层按"属性源名字"过滤，EPP 命名有陷阱

`dbsyncer-storage/src/main/java/org/dbsyncer/storage/StorageSupportConfiguration.java`

- :34 `PREFIX_STORAGE = "dbsyncer.storage"`；:36 `STORAGE_TYPE = "dbsyncer.storage.type"`
- :45 `storageService()` 从 `Environment` 手工收集属性
- :57-59 `if (!name.contains("application")) { continue; }` —— **按属性源名称字符串过滤**
- :62 profile 源判定：`name.contains("application-") && !name.contains("application.properties")`
- :71 与 :80 `Map<String, OriginTrackedValue> props = (Map<String, OriginTrackedValue>) propertySource.getSource();`
  → 泛型擦除，强转点不抛；**:73/:82 命中 `dbsyncer.storage` 前缀时在 `v.getValue()` 的隐式 checkcast 上抛 CCE**
- :90 `properties.getProperty(STORAGE_TYPE)` 决定 mysql/disk；:103 兜底 `new DiskStorageService()`

设计含义（两条都是要的）：
1. **自定义属性源名字必须不含 `application`**，否则轻则污染存储层初始化参数，重则 CCE 启动失败。
2. 反过来，这个过滤**恰好保护了自举环**：DB 侧配置不可能决定"配置存在哪"。

## F13 disk 存储是 Lucene 索引，不是明文文件

`dbsyncer-storage/.../impl/DiskStorageService.java`

- :48 `extends AbstractStorageService`
- :55 `PATH = System.getProperty("user.dir") + "/data/"`
- :65 `select(String sharding, Query)`、:200 `BooleanClause.Occur`、:208 `getPrimaryKeyTerm` → 查询走 Lucene

→ 启动期（EPP）想读 `SystemConfig` **必须引入 Lucene 读索引**，代价与收益不匹配。
→ 决策影响：启动期注入改为**读 sidecar 明文文件**，与存储类型解耦。

## F14 启动门闸（ADR-0028 的真实内容）

- `dbsyncer-common/.../startup/StartupGate.java`（接口，唯一方法 `open()`）
- `dbsyncer-web/.../config/TomcatStartupGate.java:21` 同时实现
  `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` + `StartupGate`
- `PreloadTemplate.java:72 List<StartupGate> startupGates`；`:94 openStartupGates()`；
  `:98-108` 逐个 `gate.open()`（异常吞掉只记日志）；`:115 isPreloadCompleted()`
- `SystemConfigServiceImpl.getSystemConfig()` `dbsyncer-biz/.../impl/SystemConfigServiceImpl.java:77-95`
  双检锁 + `:86 checkAddConfigModel(new HashMap<>())` **会创建并落库默认配置**
- `AppConfigServiceImpl.java:37` 注释：*"删除了 getSystemConfig() 调用，避免在 bean 实例化阶段创建重复配置"*

→ 0029 的读取路径**不得**在 bean 构造期触碰 `getSystemConfig()`，否则复活 ADR-0028 修掉的重复创建。

## F15 重试策略字段全集

`dbsyncer-common/.../retry/RetryPolicy.java`

| 字段 | 行 | 默认 |
|------|----|------|
| `boolean disable` | :14 | false |
| `int maxAttempts` | :17 | 10 |
| `long initialIntervalMs` | :20 | 1000 |
| `double multiplier` | :23 | 2.0 |
| `long maxIntervalMs` | :26 | 600000 |
| `long maxDurationMs` | :29 | 3600000 |
| `TerminationMode terminationMode` | :32 | `WHICHEVER_FIRST`（枚举见 `retry/TerminationMode.java` :8 `MAX_ATTEMPTS` / :10 `MAX_DURATION` / :12 `WHICHEVER_FIRST`） |
| `boolean useKeyword` | :35 | false |
| `List<String> keywords` | :38 | — |
| `List<String> excludeKeywords` | :41 | — |
| `MatchMode matchMode` | :44 | `CONTAINS_IGNORE_CASE`（枚举定义在 **`common/util/MatchMode.java:6`**，非内部类） |
| `volatile KeywordMatcher matcher` | :47 | 懒建 |
| `volatile KeywordMatcher excludeMatcher` | :50 | 懒建 |

- **任务级覆盖**：`dbsyncer.retry.task.<taskId>.*`，`RetryConfig.getPolicy(taskId)` `:61-63` 优先返回任务级。
  → UI 只改 `global`；某任务若存在任务级覆盖，UI 值对它无效。**提示文案必须写明**。

## F16 通知配置是内部类，且有脏缓存缺陷

`dbsyncer-parser/.../notify/NotifyConfig.java`（152 行）

- :25-26 `@Component @ConfigurationProperties(prefix = "dbsyncer.plugin.notify")`
- :35 `List<String> channels`（通道总开关：列表包含才启用）
- :40 `MailConfig mail = new MailConfig()`；:45 `DingTalkConfig dingtalk = new DingTalkConfig()`
- :58-63 `isChannelEnabled(String)` → `channels.contains(channel)`（空列表 = 全关）
- :92 `public static class MailConfig`：`username` / `password` / `List<String> notifyTypes` / `Set<NotifyType> notifyTypeSet`
- :124 `public static class DingTalkConfig`：`webhookUrl` / `secret` / `notifyTypes` / `notifyTypeSet`
- **两者都有完整 getter/setter**（:113-119, :145-151）——旧稿"无 setter、是 @Configuration Bean"是错的
- **真实缺陷**：`shouldNotify(NotifyType)` 在 `:98`（Mail）与 `:130`（DingTalk）首次调用时懒建
  `notifyTypeSet`（`:100` / `:132`），而 `setNotifyTypes()` :117-118 **不清理 `notifyTypeSet`**
  → 运行期改 `notifyTypes` 永不生效（`exclude`/`channels` 无此问题，只有 `notifyTypes` 有）
- 无 `WeComConfig`（企业微信不存在于此树）
- 读取时机：`MailNotifyServiceProvider.java:52 notifyConfig.getMail()`、`:94` —— **每次通知热读**（C 类）

## F17 死键（properties 里有、代码无人绑）

**确定无绑定**（全仓 grep 键名只命中 properties）：

| 键 | 位置 |
|----|------|
| `dbsyncer.parser.table.group.*`（**7 个**：thread-*/buffer-*，:61/:63/:65/:67/:69/:71/:73） | `dbsyncer-web/src/main/resources/application.properties:61-73` |
| `dbsyncer.plugin.notify.mail.enabled` | 同上 `:101` |

**疑似无绑定** → **经复查均为真实绑定，不是死键**：

| 键 | 位置 | 实际绑定点 |
|----|------|------------|
| `dbsyncer.web.worker.id` | 同上 `:29` | `dbsyncer-storage/.../impl/SnowflakeIdWorker.java:10 @ConfigurationProperties(prefix = "dbsyncer.web.worker")`，字段 `:16 private long id = 1L` |
| `dbsyncer.web.security.reset-pwd` | 同上 `:33` | `dbsyncer-web/.../config/WebAppConfig.java:47 @ConfigurationProperties(prefix = "dbsyncer.web.security")`，字段 `:76 private boolean resetPwd`，setter `:221`，使用点 `:167`（**登录时热读** → C 类） |

## F17b 真正的死键：`sqlserver.ct.poll.interval.ms`

- `application.properties:136 sqlserver.ct.poll.interval.ms=500`
- 唯一消费方 `dbsyncer-connector-sqlserver/.../ct/SqlServerCTListener.java:84-85`：

```java
private static long POLL_INTERVAL_MILLIS = Long.parseLong(
        System.getProperty("sqlserver.ct.poll.interval.ms", "30000"));
```

→ 读的是 **JVM 系统属性**（`-D`），不是 Spring `Environment`：
1. `application.properties` 里这一行**完全无效**（实际间隔 30000ms）；
2. `EnvironmentPostProcessor` 注入对它**也无效**；
3. `static` 初始化在类加载时求值一次 → 运行期不可变。
→ 归 A 类（只读展示，并提示“需 `-D` 启动参数”），或本期直接不暴露。

## F17c `ErrorQueueConfig` 是真实 Bean（错误处理 Tab 成立）

`dbsyncer-parser/src/main/java/org/dbsyncer/parser/error/ErrorQueueConfig.java`

- `:15 @ConfigurationProperties(prefix = "dbsyncer.error")`
- `:20 int threshold = 0`（properties `:139` 为 1）
- `:22 MatchMode fatalMatchMode = CONTAINS_IGNORE_CASE`（properties `:141` **被注释**）
- `:24 List<String> fatalKeywords = new ArrayList<>()`（properties `:143-144` **被注释**）
- `:26 volatile KeywordMatcher fatalMatcher` 懒建（`:58-69`）
- **缓存失效正确**：`setFatalMatchMode():40-43` 与 `setFatalKeywords():49-52` 均 `fatalMatcher = null`

→ 与 F16 的 `MailConfig.setNotifyTypes()` **形成对比：同一工程里一处做对了、一处漏了**，
  说明 L8 是真实遗漏而非有意设计。

## F17d 仓库里存在 `application copy.properties`

`dbsyncer-web/src/main/resources/application copy.properties`（与正式文件同名内容、部分值不同，
如 `:139 dbsyncer.error.threshold=0` vs 正式文件 `=1`）。
→ 它不会被 Spring 加载，但极易误导运维与自动补全；UI 化时不得误当作数据源。

- `MailConfig` 无 `enabled` 字段（开关实际由 `channels` 决定）。

→ UI **不得**暴露这些键；需另立条目决定"补绑定"或"删键"，否则给了开关也不生效。

## F18 UI 现状

- 渲染：`dbsyncer-web/.../controller/system/SystemController.java:32-36`
  `index(ModelMap)` → `model.put("config", systemConfigService.getSystemConfigVo())` → 视图 `system/system`
- 模板：`dbsyncer-web/src/main/resources/public/system/system.html`（**85 行，单表单，无 Tab**，9 个输入项）
  —— 前缀来自 `Application.java:47 properties.put("spring.thymeleaf.prefix", "classpath:/public/")`
- 保存：`SystemController.java:38-48` `POST /system/edit` → `getParams(request)` → `systemConfigService.edit(params)`
  → `SystemConfigServiceImpl.java:62-66` → checker（F7/F8）→ `editConfigModel`
- 前端：`dbsyncer-web/src/main/resources/static/js/system/index.js:2 doPoster('/system/edit', ...)`、`:41 const data = $form.serializeJson()`
  —— **整表单一次全量提交**
- **不存在** `PageController.java`（旧稿的 `:307/:313` 引用作废）
- `SystemConfigVo` `dbsyncer-biz/.../vo/SystemConfigVo.java:5` **仅 `extends SystemConfig`，不加字段**

## F19 模块依赖（决定新代码放哪）

| 模块 | 依赖的内部模块 |
|------|----------------|
| `dbsyncer-common` | 无（叶子） |
| `dbsyncer-storage` | common 等 |
| `dbsyncer-parser` | `dbsyncer-storage` :17, `connector-mysql` :24, `connector-sqlserver` :31 |
| `dbsyncer-manager` | （parser 可见） |
| `dbsyncer-biz` | `dbsyncer-manager` :17 |
| `dbsyncer-web` | `dbsyncer-biz` :17, `connector-mysql` :94, `connector-sqlserver` :101 |

- 配置 Bean 在 `dbsyncer-common`，`SystemConfig` 在 `dbsyncer-parser` → 新增 Tab 子对象类放 **`dbsyncer-common`** 两侧可见。
- `dbsyncer-web/src/main/resources` **无 `META-INF/spring.factories`**（实测 find 为空）
  → 走 `EnvironmentPostProcessor` 需新建该文件；`Application.java:29-31` 是 `main()` 内直接 `SpringApplication.run`，
  且 `:52 setDefaultProperties(...)`（**最低优先级**，压不过 `application.properties`）。

## F20 关联 ADR 的正确编号

| 编号 | 标题 | 状态 |
|------|------|------|
| 0013 | 同步任务指数退避重试机制 | Accepted |
| 0014 | 钉钉群消息通知机制 | Accepted |
| 0015 | 同步任务重试排除关键字配置 | Accepted |
| 0025 | 致命关键字终止机制 | Accepted |
| 0028 | 系统配置启动期重复创建防护 | Proposed |
| 0029 | 运行时配置 UI 化 | Proposed |

（0026 = mysql-gtid-support，0027 = mysql-cdc-performance-optimization，**与通知/重试无关**。）

---

# 第二部分：Spring 侧配置全集与生效分级（F21–F33）

> 第一部分是「为什么必须分级」的证据；本部分是**可施工的 key 清单**。
> 每个 key 的阶段判定都必须给出消费方 `file:line`，否则不得写入本表。

## F21 `application.properties` 全量 51 个键（实测行号）

`dbsyncer-web/src/main/resources/application.properties`

| 组 | 键（行号） |
|----|-----------|
| 容器/网络 | `server.ip` :2, `server.port` :4, `server.ssl.enabled` :7, `server.ssl.key-store` :9, `server.ssl.key-store-password` :10, `server.ssl.key-store-type` :11, `server.ssl.key-alias` :12, `server.http.enabled` :15, `server.http.port` :17, `server.http.redirect` :19, `server.servlet.session.timeout` :22, `server.servlet.context-path` :24, `helpdoc.path` :148, `logging.level.root` :151 |
| Spring | `spring.profiles.active` :25 |
| 存储自举 | `dbsyncer.storage.type` :78, `dbsyncer.storage.mysql.url` :79, `.username` :80, `.password` :81 |
| 存储线程池/buffer | `dbsyncer.storage.thread-core-size` :83, `.max-thread-size` :85, `.thread-queue-capacity` :87, `.buffer-writer-count` :89, `.buffer-pull-count` :91, `.buffer-queue-capacity` :93, `.buffer-period-millisecond` :95 |
| general 线程池/buffer | `dbsyncer.parser.general.thread-core-size` :46, `.max-thread-size` :48, `.thread-queue-capacity` :50, `.buffer-writer-count` :52, `.buffer-pull-count` :54, `.buffer-queue-capacity` :56, `.buffer-period-millisecond` :58 |
| table.group（**死键**） | `dbsyncer.parser.table.group.*` :61,:63,:65,:67,:69,:71,:73（**7 个**） |
| 调度/派发 | `dbsyncer.web.worker.id` :29, `dbsyncer.web.scheduler.pool-size` :31, `dbsyncer.web.security.reset-pwd` :33, `dbsyncer.common.dispatch.thread-core-size` :37, `.max-thread-size` :39, `.thread-queue-capacity` :41 |
| 通知 | `dbsyncer.plugin.notify.mail.enabled` :101（**死键**）, `dbsyncer.plugin.notify.mail.username` :103, `.password` :105 |
| 重试 | `dbsyncer.retry.global.disable` :113, `.max-attempts` :115, `.initial-interval-ms` :117, `.max-interval-ms` :119, `.multiplier` :121, `.max-duration-ms` :123, `.termination-mode` :125, `.use-keyword` :127, `.match-mode` :129, `.keywords[0]` :131 |
| 错误队列 | `sqlserver.ct.poll.interval.ms` :136（**对 Spring 无效**，见 F17b）, `dbsyncer.error.threshold` :139 |

**未出现在 properties、但 Bean 里有默认值的键**（Spring 可绑、properties 无）：
`dbsyncer.web.cache.*`（F31）、`dbsyncer.plugin.notify.channels`、`dbsyncer.plugin.notify.dingtalk.*`、
`dbsyncer.plugin.notify.mail.notify-types`、`dbsyncer.error.fatal-match-mode`、`dbsyncer.error.fatal-keywords`、
`dbsyncer.retry.task.*`、`info.app.*`。
→ **UI 的 key 全集必须以 Bean 字段为准，不能以 properties 为准**（否则 7 个可配项永远看不到）。

## F22 `DispatchTaskConfig` 不是死键（此前判断作废）

三个 `dbsyncer.common.dispatch.*` 键经 `dispatchTaskExecutor` bean 生效：
`dbsyncer-common/src/main/java/org/dbsyncer/common/config/DispatchTaskConfig.java:37-38#dispatchTaskExecutor`
→ `dbsyncer-common/src/main/java/org/dbsyncer/common/dispatch/impl/DispatchTaskServiceImpl.java:30#dispatchTaskExecutor`。
按类名 grep 会漏判（注入点是 bean 名，不是配置类名）——**死键判定必须追到 `@Bean` 返回值的使用点**。

## F23 生效阶段（三档）与逐项依据

### A 类 —— bean 创建期固化，必须重启

| 键 | 固化证据 |
|----|---------|
| `dbsyncer.parser.general.thread-core-size` / `.max-thread-size` / `.thread-queue-capacity` | `GeneralBufferConfig.java:34-35#generalExecutor` 建池 |
| `dbsyncer.storage.thread-core-size` / `.max-thread-size` / `.thread-queue-capacity` | `StorageConfig.java:38-39#storageExecutor` |
| `dbsyncer.common.dispatch.thread-core-size` / `.max-thread-size` / `.thread-queue-capacity` | `DispatchTaskConfig.java:37-38#dispatchTaskExecutor` |
| `dbsyncer.web.scheduler.pool-size` | `SchedulerConfig.java:30#taskScheduler` |
| `dbsyncer.web.cache.*` | `CacheConfig.java:53#cacheManager` 在 bean 期建 `CaffeineCache` 列表 |
| `server.port` / `server.ssl.*` / `server.http.*` / `server.servlet.*` / `helpdoc.path` | `HttpRedirectConfig.java:37#@Value`（`:40` 同）+ `HelpDocWebConfig.java:11#helpdoc.path`；容器/安全在 refresh 期建立 |
| `dbsyncer.web.worker.id` | `SnowflakeIdWorker.java:10` + 字段 `:16#id`；且改 workerId 会与既有 ID 空间冲突 → **不暴露给 UI** |
| `sqlserver.ct.poll.interval.ms` | 见 F17b：`static` + `System.getProperty`，Spring 注入无效 |
| `logging.level.root` | 日志系统在 EPP 之前初始化 |

### B 类 —— 队列/任务重建时生效（不重启 JVM，但要停建通道）

| 键 | 证据 |
|----|------|
| `*.buffer-queue-capacity` | `AbstractBufferActuator.java:95#LinkedBlockingQueue` 建队时取容量（`:224#getBufferQueueCapacity` 的热读只是水位判断，不改变已建队列的容量） |
| `*.buffer-period-millisecond` | `AbstractBufferActuator.java:87#start` —— 周期在 `scheduledTaskService.start()` 固化 |
| `dbsyncer.plugin.notify.mail.notify-types` / `dingtalk.notify-types` | 见 F16 脏缓存缺陷：`setNotifyTypes` 不清 `notifyTypeSet` → **当前改不动**，必须先修 setter |

### C 类 —— 热更新（每次调用热读）

| 键 | 消费方（热读点） |
|----|-----------------|
| `*.buffer-writer-count` | `AbstractBufferActuator.java:293-294#getBufferWriterCount` |
| `*.buffer-pull-count` | `AbstractBufferActuator.java:263#getBufferPullCount` |
| `dbsyncer.retry.global.*` | `dbsyncer-connector/dbsyncer-connector-base/src/main/java/org/dbsyncer/connector/base/ConnectorFactory.java:249#getGlobal`（每批写入取一次策略） |
| `dbsyncer.error.threshold` | `dbsyncer-parser/src/main/java/org/dbsyncer/parser/error/ErrorQueueCollectorImpl.java:215#getThreshold` |
| `dbsyncer.error.fatal-match-mode` / `.fatal-keywords` | `ErrorQueueCollectorImpl.java:83#isFatalError`（`:109` 同）；失效逻辑正确见 F17c |
| `dbsyncer.plugin.notify.mail.username` / `.password` / `channels` | `dbsyncer-parser/src/main/java/org/dbsyncer/parser/notify/MailNotifyServiceProvider.java:52#getMail` |
| `dbsyncer.web.security.reset-pwd` | `WebAppConfig.java:167#resetPwd`（登录时热读） |

### 不纳入

`dbsyncer.storage.type` 与 `dbsyncer.storage.mysql.*`（F12：配置存储位置不能由配置自己决定）、
`dbsyncer.parser.table.group.*` 与 `dbsyncer.plugin.notify.mail.enabled`（F17 死键）、
`server.ssl.key-store-password` / `dbsyncer.storage.mysql.password` / `dbsyncer.plugin.notify.mail.password`（凭据），
`spring.profiles.active`、`info.app.*`（`Application.java:44#info.app.version` 由 build-info 注入，非用户配置）。

## F24 `CacheProperties.timeout` 无默认值 → 启动期 NPE

`dbsyncer-common/src/main/java/org/dbsyncer/common/config/CacheConfig.java:74-76#CacheProperties`：
`Integer timeout` **无初始值**，`:68#expireAfterWrite` 直接 `config.getTimeout()` 传入 `long` 形参 →
只写 `dbsyncer.web.cache.<name>.max` 不写 `timeout` 即拆箱 NPE，且发生在 `cacheManager` bean 创建期（全站起不来）。
→ 若 UI 暴露 cache 配置，registry 必须把 `timeout` 标为**同现必填**。

## F25 `@Value` 真实只有 5 处（旧稿"122 处"作废）

`dbsyncer-web/src/main/java/org/dbsyncer/web/config/HttpRedirectConfig.java:37#server.port`、`:40#server.http.port`、
`dbsyncer-web/src/main/java/org/dbsyncer/web/config/HelpDocWebConfig.java:11#helpdoc.path`、
`dbsyncer-web/src/main/java/org/dbsyncer/web/controller/helpdoc/HelpDocController.java:43#helpdoc.path`、
`dbsyncer-web/src/main/java/org/dbsyncer/web/controller/license/LicenseController.java:77#dbsyncer.web.license.server.address`。

- 全部落在 `dbsyncer-web`，**`SystemConfig` / 8 个配置 Bean 一个都不用 `@Value`**（用 `@ConfigurationProperties`）。
- `LicenseController` 那个键**在 properties 里不存在**（默认值指向 `http://101.132.145.213:9999/...` 外网地址）→ 又一个「properties 无键但可配」的实例。
- `helpdoc.path` **两处重复声明**（WebConfig 与 Controller 各一份）→ 单一来源改造点。

## F26 `server.ip` 是真死键

`application.properties:2#server.ip` 在 `--include=*.java` 全仓 grep **无消费方**（唯一命中是
`dbsyncer-connector-sqlserver/src/test/java/ChangeDataCaptureTest.java:76` 的注释文本，无关）。
→ 属 F17 同类，但**未被 F17 列出**，本条补上。

## F27 `RetryConfig` 双入口，其中一个是 `getBean`

| 入口 | 位置 | 含义 |
|------|------|------|
| 字段注入 | `ConnectorFactory.java:73#retryConfig` | 正常 |
| `SpringContextUtil.getBean(RetryConfig.class)` | `dbsyncer-connector/dbsyncer-connector-sqlserver/src/main/java/org/dbsyncer/connector/sqlserver/ct/SqlServerCTListener.java:1222#getBean`，`:1223#getGlobal` | **连接器在 commons/parser 之外，拿不到就直接 `new RetryPolicy()`**（同 `ConnectorFactory.java:249`）。EPP 注入的 DB 值对它可见（同一 bean），但**降级路径会绕过配置** |

`RetryConfig.getPolicy(taskId)` 见 F15：任务级覆盖优先，UI 改 `global` 对已有任务级覆盖的 taskId 无效。

## F28 `SystemConfig` 与 Bean 侧是两套互不相通的写入

- `SystemConfig`（F1）：UI `POST /system/edit`（F18）→ `OperationTemplate` → DB `dbsyncer_config.JSON` → 内存缓存，**编辑即生效**（F5）
- 8 个 `@ConfigurationProperties` Bean（F9）：只有 `application.properties` / 启动参数 / 环境变量，**UI 无任何写入口**

→ **这就是 ADR-0029 的真实问题域**，也是「运行时配置 UI 化」这句话唯一站得住的含义：
把 Bean 侧配置纳入 UI，同时不破坏 F12 的自举保护。

## F29 唯一已有的「改后立刻生效」先例

`dbsyncer-parser/src/main/java/org/dbsyncer/parser/error/ErrorQueueConfig.java:49-52#fatalMatcher`
（`setFatalKeywords` 里 `fatalMatcher = null`）+ `:26#fatalMatcher`（`volatile`）。
→ 工程内**已存在**「setter 主动失效派生缓存」的正确写法，可直接作为 registry 的失效契约模板；
`RetryPolicy` 的 `volatile matcher`（F15 `:47`）与 `NotifyConfig` 漏失效（F16）是同一模式的三个变体。

## F30 现有 UI 是单表单，无 Tab（F18 的展开）

- 视图 `dbsyncer-web/src/main/resources/public/system/system.html`（85 行 / 9 输入项 / 无 Tab / **无 tooltip**）
- 提交 `static/js/system/index.js:41#serializeJson` 整表单一次全量 → 与 F8「未提交字段被写回 false」叠加，
  **是分 Tab 保存必须先解决的既有缺陷**
- 渲染入口 `dbsyncer-web/src/main/java/org/dbsyncer/web/controller/system/SystemController.java:33#index`
