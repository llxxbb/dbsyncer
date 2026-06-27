# ADR-0019: Model 层贫血化重构

## 元信息

| 字段 | 值 |
|------|-----|
| 状态 | Accepted |
| 决策者 | @lixuebin |
| 参与者 | @lixuebin, opencode |
| 创建日期 | 2026-06-27 |
| 最后更新 | 2026-06-27 |
| 关联文档 | [过程文档](../history/0019/) |

## 背景

当前 Model 层（Mapping、TableGroup、Meta）直接依赖 ProfileComponent，导致模型对象与持久化逻辑紧密耦合。这带来以下问题：

1. **无法下移 sdk 模块**：Mapping/TableGroup 等核心 Model 位于 parser 层，因为依赖 ProfileComponent，无法移到 sdk 层供各 connector 直接访问
2. **违反领域驱动设计原则**：Model 承担了持久化职责，破坏了贫血模型设计
3. **单元测试困难**：需要 mock ProfileComponent 才能测试 Model 的业务逻辑
4. **代码复用受限**：其他模块无法独立使用 Model，必须依赖完整的 parser 上下文

目标：Model 层贫血化，剥离 ProfileComponent 依赖，让 Model 成为纯数据载体。

## 决策

采用**两步走方案**：

### Step 1: 接口解耦（低风险，优先级高）

在 sdk 层定义 `TableGroupConfig` 接口，TableGroup 实现该接口。SyncRow 持有接口引用而非具体实现。

**核心设计：**
```java
// sdk 层定义接口
public interface TableGroupConfig {
    List<Field> getTargetFields();
    List<String> getPrimaryKeys();
    List<Field> resolvePkFields(List<Field> targetFields);
    Map<String, String> getCommand();
    Plugin getPlugin();
    String getPluginExtInfo();
}

// parser 层实现接口
public class TableGroup implements TableGroupConfig {
    // 已有方法，直接实现接口
}
```

**收益：**
- SyncRow 直接持有配置，无需经过 PluginContext 传递
- PluginContext 从 16 个属性精简到 9 个
- 消除了 `batchSize`、`forceUpdate`、`targetFields`、`sourceTableName` 等冗余属性
- 配置信息从 SyncRow → 接口直接获取，职责清晰

**工作量：** ~50 个文件，每个 < 10 行改动，总计约 6.5 小时

### Step 2: Model 完全贫血化（中风险，优先级中）

移除 Model 中的 ProfileComponent 依赖，提取持久化/查询逻辑到 Service 层。

**核心改造点：**

| Model | ProfileComponent 调用 | 迁移方案 |
|-------|---------------------|---------|
| Meta | `editConfigModel(this)` ×7 | 新增 `MetaService.incrementSuccess()` 等 |
| Meta | `getTableGroupAll(id)` | 通过参数传入或 Service 查询 |
| TableGroup | `parserComponent.getMetaInfo()` | Service 层查询后通过参数传入 |
| Mapping | `getMeta(id)` | 通过参数传入或 Service 查询 |
| 静态工厂 | `Meta.create(...)` 内的持久化 | 拆分为 `create()` + Service 的 `save()` |

**改造示例：**

```java
// 当前（Model 承担持久化）
public class Meta extends ConfigModel {
    @Transient
    private ProfileComponent profileComponent;
    
    public void incrementSuccess() {
        this.success++;
        profileComponent.editConfigModel(this);  // ❌ 直接持久化
    }
}

// 重构后（Model 纯业务逻辑 + Service 持久化）
public class Meta extends ConfigModel {
    public void incrementSuccess() {
        this.success++;  // 只负责业务逻辑
    }
}

public class MetaService {
    @Resource
    private ProfileComponent profileComponent;
    
    @Transactional
    public void incrementSuccess(String metaId) {
        Meta meta = profileComponent.getMeta(metaId);
        meta.incrementSuccess();
        profileComponent.editConfigModel(meta);  // ✅ Service 负责持久化
    }
}
```

**收益：**
- Model 可下移到 sdk 模块
- SyncRow 可直接持有 Mapping/TableGroup，无需接口抽象
- 架构清晰，符合 DDD 贫血模型原则

**工作量：** ~12 个核心文件，~3-5 小时，中低风险

## 影响

- **技术影响**：
  - Step 1 后：架构清晰度提升，模块间耦合降低
  - Step 2 后：Model 可完全下移，为未来多模块拆分奠基
  - 性能无显著变化，内存占用略降
- **团队影响**：
  - 需要理解 Model 贫血化思想
  - Service 层职责增加，需要注意事务边界
  - 单元测试可独立进行，无需 mock ProfileComponent
- **时间影响**：
  - Step 1：约 6.5 小时（约 1 天）
  - Step 2：约 3-5 小时（约 0.5-1 天）
  - 建议先完成 Step 1 验证后再进行 Step 2

## 变更历史

| 日期 | 版本 | 变更内容 | 变更人 |
|------|------|----------|--------|
| 2026-06-27 | v1.0 | 初始版本 | @lixuebin |

## 过程文档索引

- [设计稿](../history/0019/design-01-base.md)
- [实施方案](../history/0019/design-02-plan.md)
- [验证方案](../history/0019/verification-plan.md) ← **重点**：24 个调用点逐项承接验证
