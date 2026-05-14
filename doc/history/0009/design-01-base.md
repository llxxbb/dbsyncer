# ADR-0009 设计稿（初始版本）

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | base |
| 作者 | 老李, 凌曦 |
| 日期 | 2026-05-14 |
| 关联 ADR | [ADR-0009](../../adr/0009-字段差异分析增加MAPPING_ONLY检测与修复.md) |

## 设计目标
发现 Mapping 配置了 target 字段但目标表实际不存在的情况（MAPPING_ONLY），并提供清理无效 Mapping 配置的能力，修复配置漂移问题。

## 技术方案

### 检测逻辑 (`FieldComparisonUtil`)
```text
输入：fieldMapping 列表, 目标表实际字段列表
输出：MAPPING_ONLY 差异列表

遍历 fieldMapping 中的 target 字段：
  若 target 字段在目标表实际字段列表中不存在 (使用 Field.matchesName):
    加入 MAPPING_ONLY 差异列表
```

### 修复预览 (`TableGroupServiceImpl`)
**触发时机**：用户在 UI 点击“修复”按钮后按需计算，**不**在“检测差异”时全量计算。

```text
输入：TableGroup ID
输出：修复项列表 (FixItem)

1. 根据 TableGroup ID 重新获取并计算 MAPPING_ONLY 差异
2. 遍历 MAPPING_ONLY 差异字段：
   FixItem (移除配置):
     type = REMOVE_MAPPING
     sql = null
     default_selected = true
     description = "从 mapping 移除配置"
```

### 修复执行 (`executeFieldDiffFix`)
```text
输入：用户选择的修复项列表, TableGroup 上下文

遍历修复项：
  若 type == REMOVE_MAPPING:
    从 fieldMapping 中移除该字段

最终：保存配置
```

### 前端交互

### 差异弹窗
- 增加 `MAPPING_ONLY` 区块渲染（警告色，标题"Mapping 配置多余字段"）。
- Badge 计数累加 `MAPPING_ONLY` 的数量。

### 修复流程
1. 用户在差异弹窗点击“修复”按钮。
2. 前端请求后端计算修复预览（按需计算 FixItem）。
3. 弹出修复确认框，`MAPPING_ONLY` 类型的修复项展示为**勾选框**（Checkbox）。
4. 用户提交时，收集选中的 `Operation Type` (REMOVE_MAPPING)。
- 不再涉及 SQL 预览。
