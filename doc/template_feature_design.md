# DBSyncer 模板功能设计文档（v2.2）

## 1. 概述

### 1.1 背景
DBSyncer 是一款开源数据同步中间件，其模板功能用于处理数据转换和字段映射。原有实现存在安全性问题和功能局限，需要重构以支持更复杂的业务场景。

### 1.2 设计目标
- 提供安全的模板解析机制
- **解析与执行完全分离**
- **从根转换器开始解析依赖链**
- 保证数据正确性和功能稳定性
- **组件职责单一，不包含业务规则**

---

## 2. 设计构成。

- 转换器可以引用其他转换器，可构成复杂转换，并避免无限循环。
- 一个字段只有一个根转换器。
- 解析和执行分离
- 解析从根转换器开始。
- 转换器间的依赖顺序和执行顺序相反。
- 执行可利用上下文将结果回填到
- 模版转换器仅实现占位符替换能力。
- 占位符只字段和引用转换器两类。


## 6. 使用示例

### 6.1 无转换器

```java
// 直接使用原字段值
mapping.addField("source_field", "target_field");
```

### 6.2 单个转换器（自动是根）

```java
Convert uuid = new Convert();
uuid.setName("target_id");
uuid.setConvertCode("UUID");
uuid.setRoot(true);  // 必须是根

// 模板: C(UUID:target_id)
```

### 6.3 多个转换器（有且只有1个根）

```java
List<Convert> converts = new ArrayList<>();

// 根转换器
Convert expr = new Convert();
expr.setId("expr_0");
expr.setName("full_name");
expr.setConvertCode("EXPRESSION");
expr.setArgs("USER_ C(UUID:uuid_0)");
expr.setRoot(true);  // 根
converts.add(expr);

// 非根转换器
Convert uuid = new Convert();
uuid.setId("uuid_0");
uuid.setName("source_id");
uuid.setConvertCode("UUID");
uuid.setRoot(false);  // 非根
converts.add(uuid);

// 模板: C(EXPRESSION:expr_0)
// 执行顺序: UUID → EXPRESSION
```


## 7. 职责边界

### 7.1 组件职责

| 组件 | 职责 | 不包含 |
|------|------|--------|
| TemplateHandler | 组装组件，业务规则（根转换器分离） | 解析/执行 |
| TemplateParser | 纯粹解析，不包含业务规则 | 业务规则/执行 |
| TemplateExecutor | 纯粹执行，不包含业务规则 | 解析/业务规则 |

