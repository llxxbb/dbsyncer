# ADR-0033 设计稿（初始版本）

## 元信息

| 字段     | 值                                 |
| ------ | --------------------------------- |
| 版本     | base                              |
| 作者     | @lxb                              |
| 日期     | 2026-09-15                        |
| 关联 ADR | [ADR-0033](../../adr/0033-任务列表接口投影.md) |

## 设计目标

任务列表类接口不再把数据库明文凭据与源库表清单下发到浏览器，同时不改变前端渲染结果。

## 技术方案

### 泄漏路径

```
MappingVo extends Mapping
 ├─ MetaVo meta                       // 运行态计数，无凭据
 ├─ ConnectorVo sourceConnector ─┐
 └─ ConnectorVo targetConnector ─┴─ extends Connector extends ConfigModel
                                     ├─ ConnectorConfig config
                                     │    └─ DatabaseConfig { url, username, password }  ← 明文
                                     └─ List<Table> table                                ← 源库全量表清单
```

Jackson 直接序列化整个对象图，`config` 与 `table` 一并出网。

### 投影层

四个接口的响应体是同一个前端组件（`static/js/shared/task-list.js`）的唯一输入，因此收敛为**一个** VO：

```
ConnectorBriefVo   { name, running, connectorType }                 ← 静态工厂 from(ConnectorVo)
MappingListItemVo  { id, name, model, createTime, updateTime,       ← 静态工厂 from(MappingVo) / fromList(List)
                     meta, sourceConnector, targetConnector }
```

- 工厂方法放在 VO 上（不放 Controller 私有方法），四个接口共用一份投影，避免重复实现漂移。
- `connectorType` **平铺**到 `ConnectorBriefVo` 顶层，而不是再造一个只含单字段的 `config` 子对象。
- `meta` 整体透传（`MetaVo` 是 `/mapping/get` 既有契约，本 ADR 不再收窄）。

### 前端契约对齐

`task-list.js` 实际读取的字段（改动前后逐一核对）：

| 来源 | 字段 |
|------|------|
| task | `id` `name` `model` `createTime` `updateTime` |
| task.meta | `state` `errorMessage` `fail` `total` `success` |
| task.sourceConnector / targetConnector | `name` `running` `config.connectorType` → **改为** `connectorType` |

唯一需要改的前端代码是图标取值那一行（`_renderConnectorCell`）：

```js
- var connType = (conn.config && conn.config.connectorType) || 'default';
+ var connType = conn.connectorType || 'default';
```

其余三处读 `config.connectorType` 的代码（`mapping/edit.js:1155`、`datasource/datasource.js:55`、`index/index.js:309`）消费的是 `POST /datasource/list`，该接口未改动，不受影响。

### 接口改动清单

| 接口 | 消费者 | 改动 |
|------|--------|------|
| `GET /index/mappingdata` | `index/index.js` `loadTaskList()` | `MappingListItemVo.fromList(filteredMappings)` |
| `GET /index/searchMapping` | `index/index.js` `performMappingSearch()` | `MappingListItemVo.fromList(resultMapping)` |
| `GET /index/abnormalTasks/api` | `abnormalTasks.js` `loadData()` | `.map(MappingListItemVo::from)` |
| `GET /mapping/getRelatedMappings` | `datasource/datasource.js` `loadRelatedDrivers()` | `MappingRelatedVo` → `MappingListItemVo`（`.map(MappingListItemVo::from)`） |

`MappingRelatedVo` 与 `MappingListItemVo` 仅差 `createTime` 一个字段，且只被 `getRelatedMappings` 引用 → 删除，消除重复形状。

## 接口/数据

响应体外层不变（`RestResult`，数据在 `resultValue`）；列表元素字段集统一为：
`createTime, id, meta, model, name, sourceConnector, targetConnector, updateTime`。

## 风险点

| 风险 | 应对 |
|------|------|
| 编辑功能被破坏 | 不会。编辑页数据来自 `GET /mapping/get?id=`（服务端渲染 + 既有 `MappingJsonVo`），四个列表接口的行内操作只读 `$(this).data('id')`，详情按钮是 `window.open('/#/mapping/page/edit?id=')` |
| 前端还有别处读 `config.*` | 已 grep 全仓：仅上述 3 处，且都走 `/datasource/list` |
| `connectorType` 为空导致图标兜底 | 保留 `\|\| 'default'` 兜底；实测 8 + 4 + 40 个图标无一命中 default |
| `/datasource/list`、`/datasource/get` 仍返回明文密码 | **本次范围外**（数据源编辑页需要回填表单，需独立设计空密码保留原值的往返语义），记入 ADR 的「影响 - 已知未覆盖」 |
