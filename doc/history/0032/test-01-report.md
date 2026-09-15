# ADR-0032 测试报告

## 元信息

| 字段 | 值 |
|------|-----|
| 测试人 | @lxb |
| 测试日期 | 2026-09-14 |
| 测试版本 | 基线 `650ee1aa9` 之上的工作树（本次提交） |
| 关联 ADR | [ADR-0032](../../adr/0032-SPA单容器页面模型.md) |
| 测试环境 | 本地实例 `http://127.0.0.1:18686`（`cd dbsyncer-web && mvn spring-boot:run`），含真实数据：2 个异常任务、4 个分组任务、10 个数据源、存活的 MySQL binlog 客户端 |
| 测试工具 | Playwright 1.58.2 + Chromium（headless），脚本见本目录 `e2e-0*.js` |

## 测试范围

- [x] 回归测试（用户报告的 3 个现象）
- [x] 集成测试（10 个导航页 + 任务编辑页 + 浏览器历史）
- [x] 资源泄漏测试（定时器、`window` 事件处理器、jQuery 插件）
- [ ] 单元测试（前端无单测框架，以 E2E 覆盖）
- [ ] 性能测试（不涉及）

**执行方式**：需先启动本地实例并保证可登录（`admin/admin`），然后 `node doc/history/0032/e2e-0N-*.js`。脚本自带断言与退出码（0=全通过）。

## 测试结果

### ✅ 通过项

| 编号 | 测试用例 | 预期结果 | 实际结果 |
|------|----------|----------|----------|
| 1 | 异常任务 → 数据源 → 异常任务（**用户报告的现象 3**） | 列表非空，`/index/abnormalTasks/api` 正常发出 | `rows=2`，请求已发出，0 个 JS 错误 |
| 2 | 同上，快速连续切换 4 轮 | 单容器、单份列表 DOM | 容器数 `1`，`#abnormalTasksContainer` 节点数 `1`，`rows=2` |
| 3 | 切换后静置 15s（刷新间隔 5s） | 请求数等于单个定时器的量 | `/index/abnormalTasks/api` × 3，无翻倍 |
| 4 | 切换分组「未分配」→「测试2：增量读」（**现象 1**） | 列表跟随分组变化，与卡片视图一致 | `rows` 由 1 变 4，内容一致 |
| 5 | 切回「未分配」 | `#projectGroup` 同步为空，列表回到 1 行 | 隐藏字段 `""`，`rows=1` |
| 6 | 鼠标悬停分组列表 12s（**现象 2**） | 请求数不高于空闲基线 | 与空闲基线一致（无额外请求） |
| 7 | 一次点击分组 | 只触发一次 `updateHash`，无重复整页加载 | 通过（透明覆盖按钮已删除） |
| 8 | 数据源页 fragment 化后渲染 | 列表加载、布局生效、无外壳结构残留 | 列表项 10、`display:flex`、无 `.wrapper`/`#nav` |
| 9 | 浏览数据源页后 jQuery 插件 | `$.loadingT` / `$.fn.serializeJson` / `$.fn.tableDnD` 仍为 `function` | 三者均 `function` |
| 10 | 10 个导航页各访问 2 次 | 每页内容 >300B、容器数 1、遮罩未卡死、插件完好、0 新增 JS 错误 | 20/20 通过 |
| 11 | 任务编辑页访问 2 次 | 内容正常渲染（~126KB）、0 新增 JS 错误 | 2/2 通过 |
| 12 | 浏览器后退 / 前进 | hash 历史可正常回放 | `#/datasource` / `#/monitor` 均恢复内容 |
| 13 | 编辑页轮询（`timer3`） | 页内正常轮询，离开后归零 | 页内 12s：`/mapping/get`×3 + `/mapping/getTableGroups`×3；离开后静置 15s：**0 个** |
| 14 | 编辑页每秒耗时刷新 | 句柄局部持有，离开后自毁 | 通过（不再用顶层 `setInterval(renderDuration, 1000)`） |
| 15 | 监控页定时器不叠加 | 重复进出 3 次后，单页静置 12s 请求数与单次进入相同 | `/monitor/queryAppReportMetric`×3（未翻倍） |
| 16 | 离开监控页后定时器归零 | 静置期无 monitor 请求 | **0 个** |
| 17 | 补偿机制彻底移除 | `__pageCleanups` / `registerPageCleanup` / `containerLoadSeq` / `loadingTForceClose` 均为 `undefined` | 四者均 `undefined` |
| 18 | 代际计数器生效 | `__pageEpoch` 随页面加载递增 | 15 次加载后 `__pageEpoch=15` |
| 19 | `window` 事件处理器不累积 | 无 `beforeunload`/`pagehide` 残留 | 仅 `load×4, resize×3`（外壳级），`beforeunload×0 pagehide×0` |
| 20 | 全站 JS 错误 | 4 个脚本合计 0 个 `pageerror` | **0 个** |

**汇总**：`e2e-01` 全部断言通过；`e2e-02` 16/16；`e2e-03` 25/25；`e2e-04` 8/8。

### ❌ 失败项

无。

### 修复前对照（基线 `650ee1aa9` + 后续 3 个提交的工作树）

| 指标 | 修复前 | 修复后 |
|------|--------|--------|
| 异常任务 → 数据源 → 异常任务 的列表行数 | **0（列表为空）** | 2 |
| 快速切换 4 轮后的容器数 | 2 个容器残留 | 1 |
| JS 错误 | 3 个：`$.loadingT is not a function`、`self.$container.find(...).tooltip is not a function`、`Cannot read properties of undefined (reading 'Constructor')` | 0 |
| `$.loadingT` / `$.fn.serializeJson` / `$.fn.tableDnD` | 浏览数据源页后全部 `undefined` | 全部 `function` |
| 离开编辑页后的 `/mapping/get` 轮询 | 永久持续 | 0 |
| 一次点击分组的 `updateHash` 次数 | 2（内联 onclick + 委托处理器） | 1 |

## 遗留问题

- [ ] 无阻断问题。非阻断项见[实现说明](impl-01-notes.md)"遗留问题"与"后续优化建议"。
- [ ] E2E 脚本依赖运行中的实例与真实数据（硬编码 `admin/admin`、依赖至少存在 1 个分组和 1 个任务），未纳入 CI。

## 测试结论

**通过验收。** 用户报告的 3 个现象全部消除且可复现验证；改造未引入回归（10 个导航页 + 编辑页 + 浏览器历史全绿）；定时器与事件处理器泄漏一并修复；第三方插件文件保持与上游字节一致。可以提交。
