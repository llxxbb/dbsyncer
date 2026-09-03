# SystemConfigRegistry 设计文档

**关联 ADR**：[0029 运行时配置 UI 化](../../adr/0029-运行时配置UI化.md)

**创建日期**：2026-09-02

**状态**：Proposed

---

## 背景

ADR-0029 实施后，配置管理存在以下问题：

1. **来源混乱**：properties、DB、合并后的配置混在一起，业务代码不知道拿到的是哪个
2. **访问混乱**：业务代码直接调用 `ProfileComponent.getSystemConfig()`，无法区分配置来源
3. **职责混乱**：ConfigComparator、ConfigMigrationService、RuntimeConfigService 都在操作配置，职责不清

## 设计目标

1. **三源分离**：清晰区分 properties、DB、合并后的配置
2. **单一入口**：业务代码只通过 `SystemConfigRegistry.getConfig()` 获取最终配置
3. **职责明确**：Registry 负责配置的加载、合并、变更通知

## 架构设计

### 1. 三个 SystemConfig 实例

```
┌─────────────────┐     ┌─────────────────┐
│  propsConfig    │     │    dbConfig     │
│ (from properties)│     │   (from DB)     │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
            ┌─────────────────┐
            │   mergedConfig  │
            │  (最终使用的)    │
            └─────────────────┘
                     ▲
                     │
              业务代码只访问这个
```

| 实例 | 来源 | 用途 |
|------|------|------|
| `propsConfig` | 从 Spring Environment 加载 | 存储 properties 侧的配置值 |
| `dbConfig` | 从数据库加载 | 存储 DB 侧的配置值 |
| `mergedConfig` | propsConfig + dbConfig 合并 | 业务代码使用的最终配置 |

### 2. 合并策略

**数据库优先**：DB 配置覆盖 properties 配置。

```java
private SystemConfig merge(SystemConfig props, SystemConfig db) {
    // 1. 以 props 为基底
    SystemConfig merged = deepCopy(props);
    
    // 2. DB 非 null 的子对象覆盖 props
    if (db.getServer() != null) merged.setServer(deepCopy(db.getServer()));
    if (db.getExecutors() != null) merged.setExecutors(deepCopy(db.getExecutors()));
    if (db.getBuffers() != null) merged.setBuffers(deepCopy(db.getBuffers()));
    if (db.getRetry() != null) merged.setRetry(deepCopy(db.getRetry()));
    if (db.getNotify() != null) merged.setNotify(deepCopy(db.getNotify()));
    if (db.getError() != null) merged.setError(deepCopy(db.getError()));
    if (db.getBasic() != null) merged.setBasic(deepCopy(db.getBasic()));
    
    return merged;
}
```

**合并规则**：
- A 类配置：只从 properties 读取（`@JsonIgnore`，DB 不存储）
- B/C 类配置：DB 优先，DB 为 null 时回退到 properties
- 对象级覆盖：整个子对象覆盖，不做字段级 merge

### 3. SystemConfigRegistry 类设计

```java
@Component
public class SystemConfigRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemConfigRegistry.class);
    
    @Resource
    private Environment environment;
    
    @Resource
    private OperationTemplate operationTemplate;
    
    // 三个实例
    private SystemConfig propsConfig;   // 从 Environment 加载
    private SystemConfig dbConfig;      // 从数据库加载
    private SystemConfig mergedConfig;  // 合并后的（最终使用）
    
    /**
     * 启动时初始化
     */
    @PostConstruct
    public void init() {
        this.propsConfig = loadFromEnvironment();
        this.dbConfig = loadFromDatabase();
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("SystemConfigRegistry 初始化完成");
    }
    
    /**
     * 业务代码只调用这个方法
     */
    public SystemConfig getConfig() {
        return mergedConfig;
    }
    
    /**
     * 获取 props 配置（用于对照表打印）
     */
    public SystemConfig getPropsConfig() {
        return propsConfig;
    }
    
    /**
     * 获取 DB 配置（用于对照表打印）
     */
    public SystemConfig getDbConfig() {
        return dbConfig;
    }
    
    /**
     * DB 配置变更后重新合并
     */
    public synchronized void onDbConfigChanged(SystemConfig newDbConfig) {
        this.dbConfig = newDbConfig;
        this.mergedConfig = merge(propsConfig, dbConfig);
        logger.info("SystemConfigRegistry 重新合并完成");
    }
    
    /**
     * 从 Environment 加载 A 类配置
     */
    private SystemConfig loadFromEnvironment() {
        SystemConfig config = new SystemConfig();
        
        // ServerProps
        ServerProps server = new ServerProps();
        server.setPort(environment.getProperty("server.port", Integer.class));
        server.setSslEnabled(environment.getProperty("server.ssl.enabled", Boolean.class));
        // ... 其他 A 类配置
        config.setServer(server);
        
        // 其他 A 类 Props
        // StorageConnectionProps, LoggingProps, HelpdocProps, WorkerProps, LicenseProps
        
        return config;
    }
    
    /**
     * 从数据库加载配置
     */
    private SystemConfig loadFromDatabase() {
        List<SystemConfig> list = operationTemplate.queryAll(SystemConfig.class);
        if (list == null || list.isEmpty()) {
            return new SystemConfig();
        }
        // 多记录时取最早的
        return list.stream()
            .min(Comparator.comparing(SystemConfig::getCreateTime)
                .thenComparing(SystemConfig::getId))
            .orElse(new SystemConfig());
    }
    
    /**
     * 深度合并
     */
    private SystemConfig merge(SystemConfig props, SystemConfig db) {
        SystemConfig merged = new SystemConfig();
        
        // A 类：只从 props
        merged.setServer(props.getServer());
        // ... 其他 A 类
        
        // B/C 类：DB 优先
        merged.setExecutors(db.getExecutors() != null ? db.getExecutors() : props.getExecutors());
        merged.setBuffers(db.getBuffers() != null ? db.getBuffers() : props.getBuffers());
        merged.setRetry(db.getRetry() != null ? db.getRetry() : props.getRetry());
        merged.setNotify(db.getNotify() != null ? db.getNotify() : props.getNotify());
        merged.setError(db.getError() != null ? db.getError() : props.getError());
        merged.setBasic(db.getBasic() != null ? db.getBasic() : props.getBasic());
        
        return merged;
    }
}
```

### 4. 业务代码改造

**改造前**：
```java
@Autowired
private ProfileComponent profileComponent;

public void doSomething() {
    SystemConfig config = profileComponent.getSystemConfig();
    int port = config.getServer().getPort();
}
```

**改造后**：
```java
@Autowired
private SystemConfigRegistry configRegistry;

public void doSomething() {
    SystemConfig config = configRegistry.getConfig();  // 明确获取合并后的
    int port = config.getServer().getPort();
}
```

### 5. 配置变更流程

```java
// SystemConfigServiceImpl.editTab()
public void editTab(String tab, String payload) {
    // 1. 解析新配置
    SystemConfig newConfig = parse(payload);
    
    // 2. 保存到数据库
    profileComponent.editConfigModel(newConfig);
    
    // 3. 通知 Registry 重新合并
    configRegistry.onDbConfigChanged(newConfig);
    
    // 4. 如果是 B 类配置，触发重启
    if (requiresRestart(tab)) {
        selfRestartService.scheduleRestart();
    }
}
```

### 6. 启动期配置对照表

ConfigComparator 改造为使用 Registry：

```java
@Component
public class ConfigComparator {
    
    @Resource
    private SystemConfigRegistry configRegistry;
    
    public void printStartupTable() {
        SystemConfig props = configRegistry.getPropsConfig();
        SystemConfig db = configRegistry.getDbConfig();
        SystemConfig merged = configRegistry.getConfig();
        
        // 打印对照表
        // ...
    }
}
```

## 改造步骤

| 步骤 | 内容 | 影响范围 |
|------|------|----------|
| 1 | 创建 `SystemConfigRegistry` | 新增类 |
| 2 | 迁移加载逻辑 | ConfigService → Registry |
| 3 | 迁移合并逻辑 | ConfigComparator → Registry |
| 4 | 改造业务代码 | 所有调用点改为 `configRegistry.getConfig()` |
| 5 | 删除旧接口 | `ProfileComponent.getSystemConfig()` 标记废弃 |

## 影响分析

### 需要改造的调用点

| 模块 | 调用点 | 改造方式 |
|------|--------|----------|
| dbsyncer-parser | `ProfileComponent.getSystemConfig()` | 改为 `configRegistry.getConfig()` |
| dbsyncer-biz | `ProfileComponent.getSystemConfig()` | 改为 `configRegistry.getConfig()` |
| dbsyncer-web | `SystemConfigService.getSystemConfig()` | 改为 `configRegistry.getConfig()` |

### 需要删除的类/方法

| 类/方法 | 原因 |
|---------|------|
| `ConfigService.loadAclassConfig()` | 迁移到 Registry |
| `ConfigComparator.querySystemConfigDirect()` | 迁移到 Registry |
| `ProfileComponent.getSystemConfig()` | 标记废弃，逐步删除 |

## 验收标准

1. **三源分离**：propsConfig、dbConfig、mergedConfig 三个实例独立存在
2. **单一入口**：业务代码只通过 `configRegistry.getConfig()` 获取配置
3. **DB 优先**：DB 配置覆盖 properties 配置
4. **对照表正确**：启动期打印的对照表显示三列（props、DB、最终）
5. **编译通过**：全量编译通过

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 改造范围大 | 分步骤进行，每步验证编译 |
| 调用点遗漏 | 全量 grep 查找所有调用点 |
| 并发问题 | `onDbConfigChanged()` 加 synchronized |
