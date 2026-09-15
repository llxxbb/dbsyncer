# ADR-0033 实现说明

## 元信息

| 字段 | 值 |
|------|-----|
| 作者 | @lxb |
| 日期 | 2026-09-15 |
| 关联设计 | [设计稿](design-01-base.md) |

## 实现概述

新增 `ConnectorBriefVo` 与 `MappingListItemVo` 两个投影 VO，替换四个任务列表接口的响应体；删除 `MappingRelatedVo`；前端 `task-list.js` 同步调整 `connectorType` 取值路径。

## 关键实现细节

### 工厂方法放在 VO 上

```java
// ConnectorBriefVo.java
public static ConnectorBriefVo from(ConnectorVo connector) {
    if (connector == null) return null;
    ConnectorBriefVo brief = new ConnectorBriefVo();
    brief.setName(connector.getName());
    brief.setRunning(connector.isRunning());
    // connectorType 平铺到顶层，不再嵌套整个 config
    ConnectorConfig config = connector.getConfig();
    if (config != null) {
        brief.setConnectorType(config.getConnectorType());
    }
    return brief;
}

// MappingListItemVo.java
public static MappingListItemVo from(MappingVo mapping) {
    if (mapping == null) return null;
    MappingListItemVo vo = new MappingListItemVo();
    vo.setId(mapping.getId());
    vo.setName(mapping.getName());
    vo.setModel(mapping.getModel());
    vo.setCreateTime(mapping.getCreateTime());
    vo.setUpdateTime(mapping.getUpdateTime());
    vo.setMeta(mapping.getMeta());
    vo.setSourceConnector(ConnectorBriefVo.from(mapping.getSourceConnector()));
    vo.setTargetConnector(ConnectorBriefVo.from(mapping.getTargetConnector()));
    return vo;
}

public static List<MappingListItemVo> fromList(List<MappingVo> list) {
    if (list == null || list.isEmpty()) return Collections.emptyList();
    return list.stream().map(MappingListItemVo::from).collect(Collectors.toList());
}
```

工厂方法放在 VO 上而不是 Controller 私有方法，是为了让 `IndexController` 与 `MappingController` 共用同一份投影逻辑，避免未来两份实现漂移。

### connectorType 平铺

`ConnectorBriefVo` 把 `connectorType` 平铺到顶层，而不是保留 `config` 嵌套：

```java
// 投影 VO
public class ConnectorBriefVo {
    private String name;
    private boolean running;
    private String connectorType;  // ← 平铺
}
```

前端同步调整：

```js
// task-list.js _renderConnectorCell
- var connType = (conn.config && conn.config.connectorType) || 'default';
+ var connType = conn.connectorType || 'default';
```

平铺优于嵌套的理由：`ConnectorBriefVo` 只有一个字段来自 `config`，再造一个只含单字段的 `config` 子对象是过度设计；平铺后前端取值更直接，且 `|| 'default'` 兜底逻辑保持不变。

### MappingRelatedVo 删除

`MappingRelatedVo` 与 `MappingListItemVo` 仅差 `createTime` 一个字段，且只被 `MappingController.getRelatedMappings` 引用：

```bash
$ grep -rn "MappingRelatedVo" --include=*.java .
# 仅 MappingController.java 与 MappingRelatedVo.java 自身
```

删除 `MappingRelatedVo`，`getRelatedMappings` 改用 `MappingListItemVo.from`，四个接口统一为同一个 VO 形状。

### 前端影响范围核对

`config.connectorType` 在 `static/js/` 下还有三处读取：

| 文件 | 行号 | 消费接口 | 是否受影响 |
|------|------|---------|-----------|
| `mapping/edit.js` | 1155 | `POST /datasource/list` | ✗ 未改动 |
| `datasource/datasource.js` | 55 | `POST /datasource/list` | ✗ 未改动 |
| `index/index.js` | 309 | `POST /datasource/list` | ✗ 未改动 |

`/datasource/list` 仍返回完整 `Connector` 对象（含 `config`），不在本 ADR 范围内。

## 与设计的差异

无偏离。

## 遗留问题

- [ ] `/datasource/list` 与 `/datasource/get` 仍返回完整 `Connector.config`（含明文密码），因为数据源编辑页需要回填表单。需要单独 ADR 设计「空密码保留原值」的往返语义。

## 后续优化建议

- [ ] 单独 ADR 设计数据源编辑页的密码脱敏与回填方案（前端空密码提交时保留原值，而非覆盖为明文）。
- [ ] 考虑在 `ConnectorBriefVo` 上增加 `@JsonIgnoreProperties` 注解，防止未来误加字段时意外暴露敏感信息（白名单 fail-safe）。
