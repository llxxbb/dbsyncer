# ADR-0032 实现说明

## 元信息

| 字段 | 值 |
|------|-----|
| 作者 | @lxb |
| 日期 | 2026-09-14 |
| 关联设计 | [设计稿](design-01-base.md) |

## 实现概述

以 `650ee1aa9`（统一任务列表）为基线重做，最终改动 10 个文件（4 模板 + 6 JS），258 增 / 319 删（净减 61 行）。第三方插件 `static/plugins/js/loading-plus/loading-plus.js` 已 `git checkout` 回基线，**字节一致**，不在改动集内。

| 文件 | 改动 |
|------|------|
| `public/content.html` | 7 个 `initContainerN` → 1 个 `#initContainer`；改为裸 fragment 写法（与 `abnormalTasks.html` 一致） |
| `public/nav.html` | 删除 10 个菜单项的 `data-route` |
| `public/datasource/datasource.html` | 完整独立页面 → 裸片段；删除 DOCTYPE/head/body/`.wrapper`/`.header`/`#nav` 与 6 处 CDN 库重引入 |
| `public/index/index.html` | 删除两个透明全尺寸 `onclick="groupShow(...)"` 覆盖按钮；分组项加 `cursor: pointer`；右侧下拉容器 `z-index: 2` |
| `static/js/common.js` | SPA 加载层重写（见下）；删除 `clearEditPageTimer`、死代码 `timerLoad` |
| `static/js/index.js` | `handleHashChange` 不再解析 route；菜单点击 `updateHash(url)` 单参数 |
| `static/js/abnormalTasks.js` | `guard`/`stale()` 代际守卫；删除 `$(window).on('beforeunload')` |
| `static/js/index/index.js` | 同上；`init()` 读 `#projectGroup`；`groupShow()` 去掉多余 `loadTaskList()`；删除死代码 `getUrlParam` 与 `window.groupShow` 导出 |
| `static/js/mapping/edit.js` | `timer3` 与每秒耗时刷新加守卫；删除 `bindNavbarEvents()`（每次进入都往外壳导航叠处理器）与 `$(window).on('beforeunload pagehide')`；4 处 `doLoaderWithoutHashUpdate(url, 0)` → 单参数 |
| `static/js/monitor/index.js` | 删除全局 `monitorTimer` / `clearMonitorTimer` / `$(window).on('beforeunload hashchange')`，改为局部句柄 + 守卫 |
| `static/js/datasource/datasource.js` | **不改动**（无定时器，见设计稿"守卫按需使用"） |

## 关键实现细节

### 难点 1：`.load()` 回调里那次不成对的 `$.loadingT(false)`

- **问题**：初版重写把它当作"多余的、不配对的调用"删掉，结果数据源页遮罩卡死——该页不自管理遮罩，那次调用是它唯一的关闭时机。
- **解决方案**：在 `.done()` 中**写 DOM 之前**关闭遮罩。随后 `.html()` 同步执行页面脚本，需要遮罩的页面（如列表页 `init()`）会在那一刻自行打开，并由其自身 AJAX 回调关闭，不会被提前掩盖。

### 难点 2：`containerXHR` 句柄被迟到回调抹掉

- **问题**：旧请求的 `.always()` 在新请求发起后才触发，无条件 `containerXHR = null` 会抹掉**新**请求的句柄，导致新请求再也无法被中断。
- **解决方案**：`.always()` 内加归属判断 `if (containerXHR === xhr)`。发起前先 `abort()` 再置 null，因此旧回调到达时句柄要么为 null 要么已属于新请求，判断恒不成立。

### 难点 3：`.html()` 与 `.load()` 的脚本执行语义必须等价

- **问题**：改造后页面脚本必须仍在 DOM 就位后同步执行，否则 `abnormalTasks.js` 末尾的直接 `init()` 会找不到 `#abnormalTasksContainer`。
- **解决方案**：jQuery 1.11.3 的 `rnoInnerhtml`（`/script|style|link/i`）会阻止 `innerHTML` 捷径，`.html(string)` 退化为 `.empty().append()` → `domManip` → `_evalUrl`/`globalEval`，即**先插入 DOM 再同步执行 `<script>`**，与 `.load()` 一致。已由 E2E 验证（编辑页 126KB 内容正常渲染）。

### 难点 4：`doLoaderWithoutHashUpdate` 的函数名必须保留

- **问题**：`mapping/edit.js` 有 4 处调用它（保存/删除映射后原地重载编辑页）。
- **解决方案**：保留该公开名作为"不改哈希直接加载"的唯一入口，`doLoader` 在其之上叠加哈希更新。中间层 `loadPage(url, afterLoad)` 因只有一个调用者、`afterLoad` 从无传入者而被合并删除（YAGNI）。

## 与设计的差异

| 设计内容 | 实际实现 | 偏离原因 |
|----------|----------|----------|
| `content.html` 保留 `nav`/`footer` 的 `th:replace` | 删除 | 外壳 `public/index.html` 自己引入了 nav 与 footer，而 `th:replace="content :: content"` 只输出带 `th:fragment="content"` 的元素，这两行是被忽略的死标记 |
| 独立 `loadPage(url, afterLoad)` 函数 | 合并进 `doLoaderWithoutHashUpdate(url)` | 只有一层转发且 `afterLoad` 无调用者，属投机抽象 |

## 遗留问题

- [ ] `public/index/index.html` 含内联 `<style>`，每次进入都会再追加一份（基线既有行为，为控制改动面未动）。
- [ ] `.contentDiv` class 在 CSS/JS 中均无引用，属死类名（基线既有，保留作容器识别钩子）。
- [ ] `mapping/edit.js` 中 `currentMappingId` 有两处顶层 `var` 声明（842 行与 2575 行），基线既有。

## 后续优化建议

- [ ] 片段不变式（禁止库重引入）目前只靠注释约束，可加一条构建期检查：扫描 `public/**/*.html` 中除 `index.html` 外是否出现 `<script src=` 指向公共库。
- [ ] `datasource.css` 的通用选择器（`.left-panel`/`.right-panel`/`.resizer`）应加页面前缀，之后即可移入外壳，消除 `<link>` 重复追加。
