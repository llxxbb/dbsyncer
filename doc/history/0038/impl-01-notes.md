# ADR-0038 实现说明

## 改动文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `dbsyncer-sdk/.../sql/impl/SqlServerTemplate.java` | +`buildQueryChangeTrackingTablesSql()` 方法 |
| 2 | `dbsyncer-web/.../controller/index/MappingController.java` | +`getCTTables` 接口 + 注入 `ConnectorFactory` / `ProfileComponent` |
| 3 | `public/mapping/editTable.html` | +`sourceConnectorType` / `sourceConnectorId` 隐藏字段 |
| 4 | `static/js/mapping/editTableSelector.js` | 异步获取 CT 表 + 传 `disabledIds` |
| 5 | `static/js/components/dualListSelector.js` | 支持 `disabledIds`（渲染 + 拦截） |
| 6 | `static/css/mapping/edit.css` | `.disabled` 项 `pointer-events: none` |

## 实现要点

### 后端

- `SqlServerTemplate.buildQueryChangeTrackingTablesSql()` 查询 `sys.change_tracking_tables`，返回已启用 CT 的表名
- `MappingController.getCTTables(connectorId)` 仅对 SqlServerCT 连接器返回 CT 表名列表，其他类型返回空列表
- 接口异常时降级返回空列表（不阻塞前端）

### 前端

- `editTableSelector.js`：检测 `sourceConnectorType === 'SqlServerCT'` 时异步请求 CT 表
- 全量表名 - CT 表名 = disabledIds，传给 `SelectorManager.init`
- CT 表 option 追加 `[CT]` 后缀（仅显示）
- `DualListSelector`：`disabledSet` 拦截 `addToSelected`、单击、双击
- 渲染时 disabled 项添加 `disabled` class + `data-disabled="true"` + 半透明样式

## 向后兼容

- 非 SqlServerCT 连接器：`disabledIds` 为空数组，完全不受影响
- 已有映射任务：`getTable()` 未改动，不受影响
- CT 接口失败：降级为全部可选
