# ADR-0032 设计稿：SPA 单容器页面模型

## 元信息

| 字段 | 值 |
|------|-----|
| 版本 | base |
| 作者 | @lxb |
| 日期 | 2026-09-14 |
| 关联 ADR | [ADR-0032](../../adr/0032-SPA单容器页面模型.md) |

## 设计目标

让任意两个页面在结构上不可能互相干扰，且旧页面的定时器/回调在导航后自动失效——不依赖任何"记得去注册清理"的隐式契约。

## 技术方案

### 导航链路（改造后）

```
用户点菜单 / 改 hash
      │
      ▼
updateHash(url) ──返回 true（hash 变了）──► doLoader 直接 return
      │                                          │
      │返回 false（hash 未变）                     ▼
      ▼                                    hashchange 事件
doLoaderWithoutHashUpdate(url) ◄────────── handleHashChange()
      │                                          (只解析 URL，不再有 route)
      ▼
① window.__pageEpoch++      ← 旧页面所有回调就此失效
② containerXHR.abort()      ← 旧页面的响应不会覆盖新页面
③ $.loadingT(true)
④ $.ajax(url, dataType:'html')
      │
      ├─ done: $.loadingT(false) → $initContainer.html(html)
      │                            └─ 同步执行片段内 <script> → 页面 init()
      │                            → syncProjectGroupId(url)
      ├─ fail(abort): 静默返回（遮罩已由新导航接管）
      └─ always: containerXHR === xhr 时才置空 + watermark()
```

### 代际守卫（页面侧）

```javascript
var guard = pageGuard();          // 页面启动时取一次

function stale() {                // 定时器 + AJAX 回调共用的入口判断
    if (isPageAlive(guard)) return false;
    clearInterval(refreshTimer); refreshTimer = null;   // 自我销毁
    return true;
}

refreshTimer = setInterval(function () {
    if (stale()) return;          // 导航离开后，下一次触发即彻底停止
    loadData();
}, interval * 1000);
```

要点：**句柄必须局部持有**。顶层 `var timer` 是全局的，重复进入页面会被新实例覆盖，旧句柄从此无法清除。

### 影响面（route 概念仅存在于 4 个文件）

| 文件 | 改动 |
|------|------|
| `public/content.html` | 7 个 `initContainerN` → 1 个 `#initContainer` |
| `public/nav.html` | 删除 10 个菜单项的 `data-route` |
| `static/js/common.js` | 删除 `route` 参数、`clearEditPageTimer`、死代码 `timerLoad`；新增 `__pageEpoch`/`pageGuard`/`isPageAlive`/`syncProjectGroupId`/`containerXHR` |
| `static/js/index.js` | `handleHashChange` 不再解析 route |

### 片段不变式

外壳 `public/index.html` 独占全部库（含 `enableCDN` 分支）；片段零库引入，只带自己的 CSS 与页面 JS。违反后果：第二个 jQuery 覆盖 `window.$`，外壳注册的插件全部丢失。已写入 `datasource.html` 注释。

## 风险点

| 风险 | 应对 |
|------|------|
| 不自管理遮罩的页面（如数据源）遮罩不关，挡到 30s 超时 | `.done()` 里写 DOM **之前**先 `$.loadingT(false)`；随后同步执行的页面脚本若需要遮罩会自行打开并由自己的回调关闭 |
| 迟到回调把新请求的 `containerXHR` 抹掉，导致新请求无法中断 | `.always()` 中加 `containerXHR === xhr` 归属判断 |
| 旧 hash / `sessionStorage.previousPage` 仍带 `route=N` | 不剥离。服务端忽略未知参数，`handleHashChange` 也不保留遗留解析 |
| 片段内 `<link>`/内联 `<style>` 每次进入重复追加 | 已知并接受。`datasource.css` 选择器过于通用（`.left-panel` 等），移入外壳会污染其它页面 |
