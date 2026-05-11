# 批量添加转换配置 — 功能说明

## 概述

为多个选中的表组（TableGroup）一次性添加相同的转换规则和自定义字段。

## 入口

**表组管理页面**（`/mapping/editFull`）→ 勾选表组 → 点击 **"批量转换配置"** 按钮

## 核心逻辑

**处理策略：**
- 字段已存在 → 复用已有字段（忽略输入的类型）
- 字段不存在 → 创建新字段（DDL）+ 添加转换配置

**处理流程：** 逐个表组串行处理，完成后刷新页面。

**实现：** `edit.js` → `batchAddConvertToTableGroups()`

## 引用

| 内容 | 已有位置 |
|------|---------|
| Convert/Field 数据模型 | [`doc/adr/0001-field-mapping-advanced-config.md`](adr/0001-field-mapping-advanced-config.md) |
| 字段元数据结构 | [`org.dbsyncer.sdk.model.Field`](../../dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/model/Field.java) |
| 转换类型（ConvertEnum） | [`org.dbsyncer.parser.enums.ConvertEnum`](../../dbsyncer-parser/src/main/java/org/dbsyncer/parser/enums/ConvertEnum.java) — 22 种 |
| 数据库字段类型 | `edit.js` / `editFilterAndConvert.js` 中的 `getDatabaseFieldTypes()` |
| 字段映射标准化 | [`doc/FieldMapping.md`](FieldMapping.md) |

## 限制

| 限制 | 说明 |
|------|------|
| 单字段批量 | 一次只能配置一个字段 + 一种转换规则 |
| 手动输入字段名 | 不能从目标表字段列表中选择 |
| 串行处理 | 逐个表组处理，大量表组时耗时较长 |
| 临时主键 | 标记后需在字段配置页面保存后生效 |

---

*文档创建: 2026-05-11*
