# 0028 系统配置启动期重复创建防护 - 设计稿

## 1. 问题全景

### 1.1 现象

MySQL 配置库 `dbsyncer_config` 表中存在多条 `TYPE='system'` 的记录。用户编辑了配置 A，重启后生效的可能是配置 B，看起来"配置丢失/重复"。

### 1.2 根因链（4 条，缺一不可）

```
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot 启动时序                                            │
│                                                                 │
│  onRefresh() ──→ createWebServer() ──→ Tomcat 绑定端口          │
│       ↓                                                         │
│  finishBeanFactoryInitialization() ──→ 实例化所有 Bean          │
│       ↓                                                         │
│  finishRefresh() ──→ ContextRefreshedEvent ──→ PreloadTemplate  │
│                                                                 │
│  ↑ 端口已开 ↑                                    ↑ 配置才进缓存  │
│  ───────── 启动窗口 ─────────────────────────────────────────── │
│  此窗口内：                                                      │
│  · 浏览器访问 / 或 /login.html → DefaultController              │
│  · AppConfigServiceImpl 构造函数                                 │
│  · MonitorServiceImpl 定时任务                                   │
│  都调用 getSystemConfig() → 缓存空 → 创建 → INSERT 重复配置     │
└─────────────────────────────────────────────────────────────────┘
```

| # | 位置 | 问题 | 证据 |
|---|------|------|------|
| R1 | `PreloadTemplate:79-93` | 预加载挂在 `ContextRefreshedEvent`，端口在 `onRefresh()` 已绑定 | Tomcat 9.0.63 `AbstractEndpoint.bindOnInit` 默认 true |
| R2 | `SystemConfigServiceImpl:77-89`、`UserConfigServiceImpl:229-242` | "缓存里没有就创建并写库" | `checkAddConfigModel` → `storageService.add` → MySQL INSERT |
| R3 | `AppConfigServiceImpl:33-37` | 构造函数在 bean 实例化阶段调用 `getSystemConfig()` | commit `085b81dd3` "修复系统配置加载问题" |
| R4 | `ProfileComponentImpl:105-110` | 取 `list.get(0)`，MySQL 排序 `order by update_time, create_time desc`（`update_time` 漏了 desc） | `MySQLStorageService:239-247` |

**MySQL 存储语义**：
- `add` → `batchInsert` → `INSERT INTO ... VALUES (...)`（`SqlTemplate:389-397`，无 `ON DUPLICATE KEY`）。
- `edit` → `batchUpdate` → `UPDATE ... WHERE id = ?`（非 upsert，影响 0 行时静默丢失）。
- 每次创建用 `SnowflakeIdWorker` 生成新 ID → 重复配置 ID 不同。

### 1.3 为什么 `INSERT ... WHERE NOT EXISTS` 不够

1. **判定对象错位**：应用层依据缓存判空（`ProfileComponentImpl.getSystemConfig()` 读 cache），DB 层 `NOT EXISTS` 无法阻止内存对象返回。`INSERT` 影响 0 行后，`SystemConfigServiceImpl` 仍返回内存对象 → 后续 `edit` 静默丢失。
2. **并发不可靠**：两个事务同时 `INSERT ... WHERE NOT EXISTS`，在 RR/RC 下都能通过 → 照样两条。真要"杜绝"需唯一约束。
3. **层次错位**：`StorageService.add` 是 config/log/data 共用 SPI，塞入 `type='system'` 业务规则违反"数据库只做存储检索"约定。

---

## 2. 设计方案

### 2.1 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot 启动时序（修复后）                                   │
│                                                                 │
│  onRefresh() ──→ createWebServer() ──→ Tomcat 创建但 bindOnInit │
│       ↓                                    =false → 端口未绑定  │
│  finishBeanFactoryInitialization() ──→ 实例化所有 Bean          │
│       ↓                                                         │
│  finishRefresh() ──→ ContextRefreshedEvent ──→ PreloadTemplate  │
│       ↓                                                         │
│  PreloadTemplate.preload() ──→ 加载配置到缓存                   │
│       ↓                                                         │
│  StartupGate.open() ──→ ProtocolHandler.resume() ──→ 端口绑定  │
│                                                                 │
│  ↑ 配置已进缓存 ↑                              ↑ 端口才开放     │
│  ────────────────────────────────────────────────────────────── │
│  启动窗口消失：浏览器访问时配置已在缓存，不会触发创建             │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 L1 端口延迟开放

#### 2.2.1 新增接口 `StartupGate`（`dbsyncer-common`）

```java
package org.dbsyncer.common.startup;

public interface StartupGate {
    /**
     * 开放端口，允许外部访问。
     * 应在配置预加载完成后调用。
     */
    void open();
}
```

#### 2.2.2 新增实现 `TomcatStartupGate`（`dbsyncer-web`）

```java
package org.dbsyncer.web.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;
import org.dbsyncer.common.startup.StartupGate;

import java.util.ArrayList;
import java.util.List;

@Component
public class TomcatStartupGate implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, StartupGate {

    private final List<ProtocolHandler> handlers = new ArrayList<>();

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                // 延迟绑定：启动时不绑定端口，等待 open() 调用
                connector.getProtocolHandler().setBindOnInit(false);
                handlers.add(connector.getProtocolHandler());
            }
        });
    }

    @Override
    public void open() {
        for (ProtocolHandler handler : handlers) {
            try {
                handler.resume(); // 绑定端口并开始接受连接
            } catch (Exception e) {
                throw new RuntimeException("Failed to open startup gate", e);
            }
        }
    }
}
```

#### 2.2.3 修改 `PreloadTemplate`

```java
// 新增依赖
@Resource
private List<StartupGate> startupGates; // 注入所有实现（可能为空）

// 修改 preload() 方法
public void preload() {
    try {
        // ... 原有预加载逻辑 ...
        logger.info("系统配置预加载完成");
    } finally {
        // 无论预加载是否成功，都开放端口（避免永久不可达）
        openStartupGates();
    }
}

private void openStartupGates() {
    for (StartupGate gate : startupGates) {
        try {
            gate.open();
        } catch (Exception e) {
            logger.error("开放启动门闸失败", e);
        }
    }
}
```

**超时保护**：若预加载卡死（如 MySQL 连接无限等待），端口永久不开放。解决方案：

```java
@Resource
private SingletonConfigBootstrap singletonConfigBootstrap;

@Resource
private List<StartupGate> startupGates;

private final ExecutorService preloadExecutor = Executors.newSingleThreadExecutor(
    r -> new Thread(r, "preload-timeout-guard")
);

public void preload() {
    Future<?> future = preloadExecutor.submit(() -> {
        // 1. 加载配置到缓存
        loadConfigsToCache();
        
        // 2. 确保 singleton 配置存在
        singletonConfigBootstrap.ensure();
    });
    
    try {
        // 等待预加载完成，最多 30 秒
        future.get(30, TimeUnit.SECONDS);
        logger.info("系统配置预加载完成");
    } catch (TimeoutException e) {
        logger.error("系统配置预加载超时（30s），强制开放端口", e);
        future.cancel(true); // 尝试中断
    } catch (Exception e) {
        logger.error("系统配置预加载失败", e);
    } finally {
        // 无论成功、失败、超时，都开放端口
        openStartupGates();
    }
}

private void openStartupGates() {
    for (StartupGate gate : startupGates) {
        try {
            gate.open();
        } catch (Exception e) {
            logger.error("开放启动门闸失败", e);
        }
    }
}
```

**关键点**：
- `future.get(30, TimeUnit.SECONDS)` 阻塞最多 30 秒，超时抛 `TimeoutException`。
- 超时后调用 `future.cancel(true)` 尝试中断预加载线程（不保证成功，取决于预加载代码是否响应中断）。
- `finally` 块确保端口一定开放。

### 2.3 L2 唯一创建点

#### 2.3.1 删除 `AppConfigServiceImpl` 构造函数中的调用

```java
// 修改前
public AppConfigServiceImpl() {
    SystemConfig systemConfig = systemConfigService.getSystemConfig();
    // ... 使用 systemConfig.getVersionInfo() ...
}

// 修改后
public AppConfigServiceImpl() {
    // 删除 getSystemConfig() 调用
    // getVersionInfo() 改为懒加载（首次调用时获取）
}
```

**注意**：`getVersionInfo()` 在 `AppConfigServiceImpl` 中被多处调用，需确认改为懒加载后不影响功能。

#### 2.3.2 新增 `SingletonConfigBootstrap`（`dbsyncer-biz`）

```java
package org.dbsyncer.biz.impl;

import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.SystemConfig;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class SingletonConfigBootstrap {

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private UserConfigService userConfigService;

    /**
     * 确保 singleton 配置存在。
     * 在配置预加载完成后、端口开放前调用。
     */
    public void ensure() {
        // 确保系统配置存在
        if (profileComponent.getSystemConfig() == null) {
            SystemConfig defaultConfig = createDefaultSystemConfig();
            systemConfigService.addSystemConfig(defaultConfig);
        }

        // 确保用户配置存在
        if (profileComponent.getUserConfig() == null) {
            UserConfig defaultConfig = createDefaultUserConfig();
            userConfigService.addUserConfig(defaultConfig);
        }
    }

    private SystemConfig createDefaultSystemConfig() {
        SystemConfig config = new SystemConfig();
        config.setId(ConfigConstant.SYSTEM); // 固定 ID
        config.setName("系统配置");
        // ... 设置默认值 ...
        return config;
    }

    private UserConfig createDefaultUserConfig() {
        UserConfig config = new UserConfig();
        config.setId(ConfigConstant.USER); // 固定 ID
        config.setUsername("admin");
        config.setPassword(sha1("admin")); // 默认密码
        // ... 设置默认值 ...
        return config;
    }
}
```

**调用时机**：在 `PreloadTemplate.preload()` 中，配置加载到缓存后、端口开放前调用。

```java
@Resource
private SingletonConfigBootstrap singletonConfigBootstrap;

public void preload() {
    try {
        // 1. 加载配置到缓存
        loadConfigsToCache();
        
        // 2. 确保 singleton 配置存在（如果缓存为空，创建默认配置）
        //    必须在端口开放前执行，否则首次安装时浏览器访问会触发重复创建
        singletonConfigBootstrap.ensure();
    } finally {
        // 3. 开放端口（允许外部访问）
        //    超时保护：如果前面卡死，强制开放端口避免应用永久不可达
        openStartupGates();
    }
}
```

### 2.4 L3 确定化读取

#### 2.4.1 修改 `ProfileComponentImpl`

```java
// 修改前
public SystemConfig getSystemConfig() {
    List<SystemConfig> list = operationTemplate.queryAll(StorageEnum.CONFIG, ConfigConstant.SYSTEM, SystemConfig.class);
    return list.isEmpty() ? null : list.get(0);
}

// 修改后
public SystemConfig getSystemConfig() {
    List<SystemConfig> list = operationTemplate.queryAll(StorageEnum.CONFIG, ConfigConstant.SYSTEM, SystemConfig.class);
    if (list.isEmpty()) {
        return null;
    }
    if (list.size() == 1) {
        return list.get(0);
    }
    // 多条：确定性选择（createTime 最早，平手取 id 最小）
    logger.warn("[系统配置] 检测到 {} 个重复配置，将选择 createTime 最早的配置", list.size());
    return list.stream()
        .min(Comparator.comparing(SystemConfig::getCreateTime)
            .thenComparing(SystemConfig::getId))
        .orElse(list.get(0));
}
```

**同理修改 `getUserConfig()`**。

### 2.5 L4 启动期重复告警

在 `PreloadTemplate.preload()` 完成后，检测重复并记录 WARN：

```java
private void checkDuplicateConfigs() {
    List<SystemConfig> systemConfigs = operationTemplate.queryAll(StorageEnum.CONFIG, ConfigConstant.SYSTEM, SystemConfig.class);
    if (systemConfigs.size() > 1) {
        List<String> ids = systemConfigs.stream().map(SystemConfig::getId).collect(Collectors.toList());
        String retainedId = systemConfigs.stream()
            .min(Comparator.comparing(SystemConfig::getCreateTime).thenComparing(SystemConfig::getId))
            .map(SystemConfig::getId).orElse("unknown");
        logger.warn("[系统配置] 检测到 {} 个重复配置，保留 id={}, 其他 id={}", 
            systemConfigs.size(), retainedId, ids);
    }

    List<UserConfig> userConfigs = operationTemplate.queryAll(StorageEnum.CONFIG, ConfigConstant.USER, UserConfig.class);
    if (userConfigs.size() > 1) {
        List<String> ids = userConfigs.stream().map(UserConfig::getId).collect(Collectors.toList());
        String retainedId = userConfigs.stream()
            .min(Comparator.comparing(UserConfig::getCreateTime).thenComparing(UserConfig::getId))
            .map(UserConfig::getId).orElse("unknown");
        logger.warn("[用户配置] 检测到 {} 个重复配置，保留 id={}, 其他 id={}", 
            userConfigs.size(), retainedId, ids);
    }
}
```

---

## 3. 改动清单

| 模块 | 文件 | 改动类型 | 说明 |
|------|------|----------|------|
| `dbsyncer-common` | `org.dbsyncer.common.startup.StartupGate` | 新增 | 启动门闸接口 |
| `dbsyncer-web` | `org.dbsyncer.web.config.TomcatStartupGate` | 新增 | Tomcat 实现 |
| `dbsyncer-manager` | `org.dbsyncer.manager.template.PreloadTemplate` | 修改 | 注入 `StartupGate`，预加载后调用 `open()` |
| `dbsyncer-biz` | `org.dbsyncer.biz.impl.SingletonConfigBootstrap` | 新增 | 确保 singleton 配置存在 |
| `dbsyncer-biz` | `org.dbsyncer.biz.impl.AppConfigServiceImpl` | 修改 | 删除构造函数中的 `getSystemConfig()` 调用 |
| `dbsyncer-parser` | `org.dbsyncer.parser.impl.ProfileComponentImpl` | 修改 | 确定化读取（`getSystemConfig`、`getUserConfig`） |
| `dbsyncer-manager` | `org.dbsyncer.manager.template.PreloadTemplate` | 修改 | 预加载后调用 `checkDuplicateConfigs()` 和 `singletonConfigBootstrap.ensure()` |

---

## 4. 验证清单

### 4.1 首次安装（无配置）

1. 清空 MySQL `dbsyncer_config` 表。
2. 启动应用。
3. 验证：
   - 启动日志显示"系统配置预加载完成"。
   - 启动日志显示"确保 singleton 配置存在"（创建了默认配置）。
   - 浏览器访问 `/` → 显示登录页。
   - MySQL `dbsyncer_config` 表中 `TYPE='system'` 和 `TYPE='user'` 各 1 条。

### 4.2 正常启动（有配置）

1. MySQL `dbsyncer_config` 表中已有配置。
2. 启动应用。
3. 验证：
   - 启动日志显示"系统配置预加载完成"。
   - 启动日志**不显示**"确保 singleton 配置存在"（未创建新配置）。
   - 浏览器访问 `/` → 显示登录页。
   - MySQL `dbsyncer_config` 表中配置数量不变。

### 4.3 启动期浏览器访问

1. 启动应用（端口延迟开放）。
2. 在启动过程中（端口未开放时）浏览器访问 `/`。
3. 验证：
   - 浏览器显示"connection refused"或"无法访问此网站"。
   - 启动完成后，浏览器刷新 → 正常显示登录页。
   - MySQL `dbsyncer_config` 表中配置数量不变（未触发创建）。

### 4.4 定时任务触发

1. 启动应用。
2. 等待 `MonitorServiceImpl` 定时任务触发。
3. 验证：
   - 定时任务正常执行（未因配置缺失而失败）。
   - MySQL `dbsyncer_config` 表中配置数量不变。

### 4.5 存量重复配置

1. MySQL `dbsyncer_config` 表中手动插入多条 `TYPE='system'` 配置（不同 id、不同 createTime）。
2. 启动应用。
3. 验证：
   - 启动日志显示 WARN：`[系统配置] 检测到 N 个重复配置，保留 id=xxx, 其他 id=[yyy, zzz]`。
   - 浏览器访问 `/` → 显示登录页。
   - 编辑配置并保存 → 重启后生效的是保留的那条（createTime 最早）。

### 4.6 预加载超时

1. 模拟预加载卡死（如 MySQL 连接超时）。
2. 启动应用。
3. 验证：
   - 30s 后端口强制开放（或 Tomcat 连接超时后端口开放）。
   - 启动日志显示 ERROR："预加载超时，强制开放端口"。
   - 浏览器访问 `/` → 显示登录页（可能配置为空，触发懒加载创建）。

---

## 5. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `ProtocolHandler.resume()` 在 Tomcat 9.0.63 行为不符预期 | 端口无法开放，应用不可达 | 已确认 Tomcat 9.0.63 支持 `bindOnInit=false` + `resume()`；若失败，回退到 503 启动页方案 |
| 预加载卡死导致端口永久不开放 | 应用不可达 | 超时保护（30s）+ ERROR 日志 + 强制开放 |
| `SingletonConfigBootstrap` 创建的配置 ID 与存量冲突 | 唯一约束冲突（若未来加 DB 唯一索引） | 使用固定 ID（`ConfigConstant.SYSTEM`、`ConfigConstant.USER`），存量清理 SQL 中保留 createTime 最早的 |
| `ProfileComponentImpl` 确定化读取性能下降 | 多条配置时排序开销 | 配置数量通常 < 10，影响可忽略；若 > 100，考虑缓存排序结果 |
| `AppConfigServiceImpl` 构造函数删除 `getSystemConfig()` 后，`getVersionInfo()` 懒加载失败 | 版本信息显示异常 | 确认 `getVersionInfo()` 所有调用点，必要时改为 `@PostConstruct` 初始化 |

---

## 6. 后续优化（可选）

1. **DB 层唯一约束**：若未来需要多实例共用配置库，可加生成列 + 唯一索引（`singleton_key`）+ `ON DUPLICATE KEY UPDATE`。
2. **503 启动页**：若需要更友好的 UX（如显示进度），可叠加 Filter 返回 503 + 自刷新 HTML。
3. **自动清理存量**：提供配置开关，启动时自动删除重复配置（默认关闭）。
