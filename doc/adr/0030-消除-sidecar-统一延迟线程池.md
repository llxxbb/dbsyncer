# ADR-0030: 消除 Sidecar，统一延迟线程池

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Accepted |
| 决策者 | @lxb |
| 参与者 | @lxb |
| 创建日期 | 2026-09-01 |
| 最后更新 | 2026-09-01 |
| 关联文档 | 无 |

## 背景

ADR-0029 引入了 Sidecar 机制（`dbsyncer-runtime.properties`），用于在 Web UI 保存 B 类配置后，将配置写入磁盘文件，下次启动时由 `RuntimeConfigPropertySource`（EPP）读取并注入 Spring Environment，覆盖 `application.properties` 默认值。

根本原因是**时序矛盾**：线程池 `@Bean` 在 Spring 容器刷新时创建，此时 DB 尚未连接，无法从 ConfigRegistry 获取用户保存的配置。

但 Sidecar 引入了三个问题：
1. **三套配置源**：`application.properties` → sidecar → DB，认知负担重
2. **一致性风险**：DB 与 sidecar 文件可能不一致
3. **冗余**：sidecar 本质上是在重复 DB 的工作

## 决策

**消除 Sidecar 全链路**，改用延迟线程池工厂（`ExecutorFactory`）：

1. **延迟创建线程池**：`ExecutorFactory` 在首次调用 `getXxxExecutor()` 时才创建线程池，此时 ConfigRegistry 已从 DB 加载配置
2. **统一配置入口**：所有 B 类参数从 `ConfigRegistry.getConfig()` 读取，不再需要 `@ConfigurationProperties` 绑定
3. **延迟初始化队列**：`AbstractBufferActuator` 的队列和调度器延迟到首次 `offer()` 时创建

### 删除的文件
- `SidecarPropertyWriter.java`
- `RuntimeConfigPropertySource.java`
- `spring.factories`（EPP 注册）
- `GeneralBufferConfig.java`
- `StorageConfig.java`
- `DispatchTaskConfig.java`
- `BufferActuatorConfig.java`

### 新增的文件
- `ExecutorFactory.java` — 延迟线程池工厂（DCL + volatile）
- `ExecutorFactoryConfig.java` — 注册 ExecutorFactory Bean

### 改造的文件
- `AbstractBufferActuator.java` — 从 ConfigRegistry 动态读取 buffer 参数，队列/调度器延迟初始化
- `MetaBufferActuator.java` — 去掉 GeneralBufferConfig 依赖
- `StorageBufferActuator.java` — 去掉 StorageConfig 依赖，改用 ExecutorFactory
- `BufferActuatorRouter.java` — 改用 ExecutorFactory
- `DispatchTaskServiceImpl.java` — 改用 ExecutorFactory
- `LogServiceImpl.java` — 改用 ExecutorFactory
- `ConfigServiceImpl.java` — 删除 sidecarPropertyWriter 调用

## 影响

- **技术影响**：
  - 配置源从 3 个减少到 2 个（`application.properties` + DB）
  - 线程池创建从启动期延迟到首次使用时
  - 消除了 sidecar 文件 I/O 和一致性维护开销
- **团队影响**：新人无需理解 sidecar 机制，配置流转更直观
- **时间影响**：一次性改造，无后续维护成本

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-09-01 | v1.0 | 初始版本 | @lxb |
