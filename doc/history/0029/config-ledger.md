# 运行时配置逐项总账（提示文案与影响说明）

> 检查日期：2026-08-31 · HEAD = `58fd164df`
> 口径：**只写实测到的**。每行的锚点都经 `.scratch/0029-runtime-config-ui/tools/verify-anchors.py` 原文子串复验（**1113 条锚点，0 失效**）。
> 与 [ADR-0029](../../adr/0029-运行时配置UI化.md) 的分工：ADR 记**决策**（为什么这样设计），本文记**条目本身**（做什么用、改了会怎样、悬浮提示写什么）。分类（A/B/C）以 ADR 为准，本文不重定义。

## 0. 先看这五条（决定了后面所有文案怎么写）

1. **`-D` 系统属性、环境变量、命令行参数对 `dbsyncer.storage.*` 完全无效。**
   `dbsyncer-storage/src/main/java/org/dbsyncer/storage/StorageSupportConfiguration.java:58`
   按 `name.contains("application")` 过滤属性源，`systemProperties`/`systemEnvironment`/`commandLineArgs`
   都不含 "application" → 被丢弃。**只有 `application*.properties` 进得来。**
   ⇒ 任何"命令行可覆盖"的通用说法在这个前缀上是错的，提示文案不得写"或用 `-D` 覆盖"。
2. **同一个 `dbsyncer.storage` 前缀有两套互不相通的读法**：`StorageConfig.java:20`（relaxed binding，
   `driverClassName`/`driver-class-name` 都认）与 `StorageSupportConfiguration.java:34`（手工抓前缀进
   `Properties` 快照，`Properties.getProperty` **精确匹配**）。
   ⇒ 提示里必须写清这个键走哪条读法；`dbsyncer.storage.mysql.driver-class-name` 写成
   `driverClassName` **静默不生效**（`MySQLStorageService.java:74`）。
3. **写回必须原地改 setter，不能换引用**（证据见 ADR §2《运行期回写协议》）。
   ⇒ 长驻线程持有旧实例时，UI 会"保存成功、日志显示生效、实际不变"。
4. **有派生缓存的字段必须走 setter**：`RetryPolicy.java:120-123`、`:138-142` 会把
   `volatile matcher`（`:47`）置 null 由 `:159-160` 懒重建。反射/`BeanUtils` 直写字段 → 改值不生效。
5. **悬浮提示的现成约定**：`label` 后挂 `<i class="fa fa-question-circle fa_gray" title="…">`
   （`dbsyncer-web/src/main/resources/public/system/system.html:58` 已在用；`.fa_gray` 定义
   `dbsyncer-web/src/main/resources/static/css/common.css:11`）。
   ⚠️ `common.css:15-16` 只把 **`.tooltip.top` 覆写成红色**（那是给校验报错用的）。
   ⇒ **提示一律不要用 `top` 方向**（否则问号悬浮变红，看着像报错），或补一条中性样式覆写。
   长文本用 popover（现成例子 `dbsyncer-web/src/main/resources/static/js/license/license.js:19`）。
6. **通知链路当前是"整体静默"的**，界面上任何"通知已开启"的回显都是假的。
   `dbsyncer.plugin.notify.channels` **未在 properties 里配置**（该文件 `:101/:103/:105` 只配了 `mail.enabled/username/password`），
   而 `CompositeNotifyService.java:50-53` 在 channels 为空时直接 return ⇒ **现在一条告警都不会发出去**。
   叠加两条：`mail.enabled` 是死键（`MailConfig` 无此字段）、`Session` 只在 `@PostConstruct` 建一次
   （`MailNotifyServiceProvider.java:50-55`、`:81-83`）。
   ⇒ 提示文案**不得写"保存后立即生效"**；通知 Tab 必须显式说明"首次配置需重启，且 SMTP 固定走 smtp.qq.com"。
   ⚠️ 更危险的是 `mail.username` 属**半热**：SMTP 登录凭据在启动时快照（`MailNotifyServiceProvider.java:61`、`:71`），
   而发件人头每次现读（`:94`）。运行期单改 username ⇒ 认证失败，异常被 `:113-115` 吞掉只写日志 ⇒
   **界面提示"保存成功"，邮件却永久发不出去**。故 username/password 必须成对编辑 + 标注"重启生效"。

## 1. 影响范围速查（先建立"改这里会不会炸到别的模块"的直觉）

| 键族 | 一处改动会波及 | 说明 |
|---|---|---|
| `dbsyncer.parser.general.*` 线程池/缓冲 | 所有普通驱动通道的**写入并发与延迟** | 池在 `@Bean` 期建死，改 properties 要重启 |
| `dbsyncer.storage.*`（`StorageConfig` 侧） | 存储队列并发 | 与上一行同形；**但与 `dbsyncer.storage.type` 不是一条链路** |
| `dbsyncer.common.dispatch.*` | 任务派发线程池 | `Math.min` 静默截断 core>max，填错不报错 |
| `dbsyncer.web.scheduler.pool-size` | 全部定时任务（含监控、心跳） | 调小会让任务排队 |
| `dbsyncer.retry.global.*` | 走 `RetryInterceptor` 的**部分**链路 | 见 §3 诱饵：MySQL keepalive 不受控 |
| `dbsyncer.error.*` | 错误队列熔断与致命关键字判定 | `threshold=0` 是"不熔断"的合法值 |
| `dbsyncer.plugin.notify.*` | 告警邮件/钉钉 | 收件人每次现读，通道开关即时 |
| `server.*` | 容器与登录链路 | 自我断链风险，一律只读 |
| `dbsyncer.web.security.reset-pwd` | 管理员口令 | **开启期间任何人可用默认口令改写 admin 口令** |

## 2. 条目总账

安全档：**L0** 纯展示 / 改错无害；**L1** 影响吞吐·延迟·重试语义，误操作可自愈；
**L2** 涉及安全·凭据·不可逆数据，默认只读 + 二次确认。

### Tab 线程池

| 键 | 作用（说人话） | 改大的后果 | 改小的后果 | 生效 | 档 | 悬浮提示文案 |
|---|---|---|---|---|---|---|
| `dbsyncer.parser.general.thread-core-size`（properties:46 = 8；初值 CPU×2） | 普通驱动的常驻写库线程数 | DB 连接与锁竞争上升 | 批量写入排队、延迟上升 | 重启 JVM（`@Bean` 建死） | L1 | 普通驱动落库线程的常驻数量。池在应用启动时创建，改完必须重启。调大会增加对目标库的并发连接，请先确认目标库 `max_connections` 有余量。 |
| `dbsyncer.parser.general.max-thread-size`（:48 = 16；初值 CPU×2） | 上面那个池的峰值线程数 | 峰值占用上升 | 队列更容易堆积 | 重启 | L1 | 峰值线程数。与核心线程数同时填写：**核心值大于峰值值时会被静默截断为相等**（取 `Math.min`），不会报错。 |
| `dbsyncer.parser.general.thread-queue-capacity`（:50 = **64**；初值 1000） | 线程来不及处理时最多排多少任务 | 内存占用上升，故障暴露变慢 | 队列满会触发拒绝（任务被丢弃/报错） | 重启 | L1 | 等待队列长度。调大会把"写不进去"延后成"积压后一次性爆发"；调小会更早触发拒绝策略。默认值与配置文件里的值相同，验证是否生效时请换一个值测。 |
| `dbsyncer.storage.thread-core-size` / `max-thread-size` / `thread-queue-capacity`（:83/:85/:87 = 4/8/64；初值 CPU/CPU/500） | 存储侧队列的线程池 | 同上 | 同上 | 重启 | L1 | 这是**存储侧**的线程池，和"通用驱动"那组同名不同物。三组前缀都有 `thread-core-size`，请按屏区分。 |
| `dbsyncer.common.dispatch.thread-core-size` / `max-thread-size` / `thread-queue-capacity`（:37/:39/:41 = 8/16/64；初值 CPU×2/CPU×2/64） | 任务派发（启停驱动、调度分发）的线程池 | 派发并发上升 | 驱动启停/派发排队 | 重启 | L1 | 派发线程池，影响"启动/停止驱动"这类操作的响应速度，不直接影响数据写入吞吐。`keepAlive` 固定 30 秒（硬编码，不是配置项）。 |
| `dbsyncer.web.scheduler.pool-size`（:31 = 8；初值 CPU） | 定时任务线程数（监控采集、心跳等） | 定时任务并发上升 | 定时任务排队、监控刷新变慢 | 重启 | L1 | 调度线程池。所有周期任务共用它；调小会让监控页刷新和心跳检测延迟。 |

> **线程池三组共同的实现陷阱**（实测，写提示文案前必读）：
> 1. **`GeneralBufferConfig` 的三个 getter 全仓 0 调用**（`dbsyncer-common/src/main/java/org/dbsyncer/common/config/GeneralBufferConfig.java:41`、`:49`、`:58`）；
>    建池路径是**字段直读**（`GeneralBufferConfig.java:37`、`:38` → `dbsyncer-common/src/main/java/org/dbsyncer/common/util/ThreadPoolUtil.java:24`、`:27`、`:29`）。
> 2. ⇒ 后果：`GeneralBufferConfig.java:49-51` 里"确保 maxThreadSize 不小于 threadCoreSize"的保护
>    **在建池路径上从来没生效过**，真正起作用的是 `:37` 的 `Math.min` 静默截断。
>    **界面必须自己做 core ≤ max 的校验**，不要指望 bean 兜底（填反了不报错，只是核心数被悄悄改小）。
> 3. 三组前缀（`dbsyncer.parser.general` / `dbsyncer.storage` / `dbsyncer.common.dispatch`）用的是**完全相同的子键名**
>    `thread-core-size`/`max-thread-size`/`thread-queue-capacity` ⇒ 按屏分组、标题带前缀，否则运维必然改错一组。

### Tab 缓冲区

| 键 | 作用 | 改了会怎样 | 生效 | 档 | 悬浮提示文案 |
|---|---|---|---|---|---|
| `dbsyncer.parser.general.buffer-queue-capacity`（properties:56 = 50000；初值 30000） | 缓冲队列容量（攒批的大小） | 内存上升 / 单批变大后失败回滚范围变大 | **停止并重启该驱动即生效**（不必重启 JVM） | L1 | 缓冲队列容量，每个通道建执行器时读一次。改完值后，**已在跑的通道仍用旧队列**；要生效需停止并重启该驱动。内存峰值与「拉取条数」直接相乘。⚠️ 调大**不会扩容已存在的队列**（容量在建队时定格），且监控页的容量是另一次读取，运行期改值会出现"界面显示容量 ≠ 实际队列容量"。 |
| `dbsyncer.parser.general.buffer-period-millisecond`（properties:58 = 300；**初值也是 300**） | 攒批的最长等待时间 | 调大→吞吐↑但延迟↑；调小→延迟↓但小事务变多 | **停止并重启该驱动即生效** | L1 | 攒批等待上限（毫秒）。数据不到容量就先等到这个时间也会落库，所以它是**延迟上界**而不是固定延迟。建执行器时注册定时任务读一次，需重启驱动生效。 |
| `dbsyncer.parser.general.buffer-pull-count`（properties:54 = **20000**；初值 1000） | **真正决定批次切分的开关** | 调大→单次内存峰值↑、单批失败回滚面↑ | **M4：重启应用**（原为立即，统一为 B 类） | L1 | 攒够多少条就落一次库。M4 已决统一按 B 类走，重启应用生效。它同时定义了“一批”的大小，改大会让单批失败时的回滚范围变大。 |
| `dbsyncer.parser.general.buffer-writer-count`（properties:52 = 1000；初值 100）→ **L5 正式改名为"任务中断上报失败数"** | 任务中断时上报给错误队列的失败计数 | 只改变告警文案里的那个数字 | **M4：重启应用**（原为立即，统一为 B 类） | L1 | L5 已确认：全仓 `getBufferWriterCount()` 只有 1 个真实调用点（`MetaBufferActuator.java:124`），它作为 `failCount` 传给 `ErrorQueueCollectorImpl.closeTask()`。真正切批的是上面的"拉取条数"。**Props 字段名保持 `bufferWriterCount`（与 Bean 对齐），UI label 统一用「任务中断上报失败数」**。M4 统一 B 类。见 §3。 |
| `dbsyncer.storage.buffer-queue-capacity` / `buffer-period-millisecond`（properties:89/:93） | 存储侧队列容量与攒批周期 | 同 parser.general 对应项 | **需重启应用**（`StorageBufferActuator` 是 `@Component` 单例，`@PostConstruct` 只跑一次） | L1 | 存储侧的队列容量与周期。与 `parser.general` 同名不同物：**必须重启整个应用**，不能只重启驱动。 |
| `dbsyncer.storage.buffer-pull-count` / `buffer-writer-count`（properties:91/:95） | 存储侧拉取条数与失败计数 | 同上 | **M4：重启应用** / **死键不暴露** | L1 | `pull-count` M4 统一 B 类；`writer-count` 存储链路零调用（死键，见 §3）。 |

> ~~同一 Tab 内混着"重启生效"和"立即生效"两类~~ → **M4 已决：统一按 B 类（重启应用）走**。Tab 3 可统一标注「重启应用生效」，不再需要逐行区分生效方式。
> 依据（均为实测）：
> - **每次现读 → 立即生效**：`dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java:263`
>   比较 `getBufferPullCount()`；同文件 `:294` 现读 `getBufferWriterCount()`。
> - **建执行器时读一次 → 重启驱动生效**：队列在
>   `dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/AbstractBufferActuator.java:95`
>   （`new LinkedBlockingQueue<>(config.getBufferQueueCapacity())`，由 `buildQueueConfig()` 创建），
>   周期在 `AbstractBufferActuator.java:87`（`scheduledTaskService.start(taskKey, config.getBufferPeriodMillisecond(), this)`），
>   两者都在 `buildConfig()`（`:80`）内。
> - **为什么不必重启 JVM**：`dbsyncer-parser/src/main/java/org/dbsyncer/parser/flush/impl/BufferActuatorRouter.java:101`
>   `unbind(metaId)` 会把执行器从 `metaActuatorMap` 移除（`:104` `return null`），调用方是驱动停止路径
>   `dbsyncer-manager/src/main/java/org/dbsyncer/manager/impl/IncrementPuller.java:157`；下次
>   `BufferActuatorRouter.java:145` `getOrCreateActuator()` 重建时重跑 `buildConfig()`（`:148`），
>   并从**当前 live** 的 `GeneralBufferConfig` bean 取值（`:160` 注入）。
>   ⇒ 只要原地 setter 改了 bean 字段，**重启驱动即可拿到新值**——这比 ADR 现在的"重启应用生效"更轻。

### Tab 重试策略

| 键 | 作用 | 改了会怎样 | 生效 | 档 | 悬浮提示文案 |
|---|---|---|---|---|---|
| `dbsyncer.retry.global.disable`（:113 = false） | 一键关掉所有重试 | 失败立即抛出 | 立即 | L1 | 总开关。打开后**所有**通道的失败重试都停止，故障会直接冒泡给驱动。 |
| `dbsyncer.retry.global.max-attempts`（:115 = 3；初值 10） | 最多重试几次 | 调大→故障时长时间占住线程 | 立即 | L1 | 最大重试次数。⚠️ "是否含首次"注释与实现的静态推演不一致（**待核**，需跑一次实测），文案在实测前只说"重试次数上限"，不要写死总执行次数。 |
| `dbsyncer.retry.global.initial-interval-ms`（:117 = 1000） | 首次退避时长 | 指数退避的基数 | 立即 | L0 | 首次重试前的等待（毫秒）。间隔按下面的倍数指数增长。 |
| `dbsyncer.retry.global.max-interval-ms`（:119 = 60000） | 退避上限 | 调大→恢复后追上进度更久 | 立即 | L0 | 退避间隔的上限（毫秒），`Math.min` 截断。 |
| `dbsyncer.retry.global.multiplier`（:121 = 2.0） | 指数退避倍数 | <1 或负值会让退避间隔反常 | 立即 | L0 | 退避倍数。填小于 1 的数或负数没有下限校验，会导致等待时间反常。 |
| `dbsyncer.retry.global.max-duration-ms`（:123 = 20000；初值 3600000） | 重试总时长预算 | `0` = 不限制 | 立即 | L1 | 重试总时长上限（毫秒）。**填 0 表示"不限制"**，与"未填写"无法区分。 |
| `dbsyncer.retry.global.termination-mode`（:125 = MAX_ATTEMPTS） | 次数和时长谁先到算谁 | 改变终止判定 | 立即 | L1 | 终止判定模式。下拉值请原样展示（代码里的常量拼写就是 `WHICHEVER_FIRST`，**不要顺手改正**，改了绑不上枚举）。 |
| `dbsyncer.retry.global.use-keyword`（:127 = false） | 是否按异常文本白名单重试 | false = **无条件重试所有异常**（含不该重试的） | 立即 | L1 | 关键字匹配开关。关闭时任何异常都会重试（包括参数错误这类不可能靠重试恢复的异常）。 |
| `dbsyncer.retry.global.match-mode`（:129 = CONTAINS_IGNORE_CASE） | 关键字匹配方式 | 影响命中范围 | 立即（须走 setter） | L1 | 匹配方式。改这个值会重建内部匹配器，**只有走配置接口才生效**，绕过它会导致改了不生效。 |
| `dbsyncer.retry.global.keywords[n]`（:131 = deadlock） | 命中才重试的关键字 | 漏配→该重试的不重试 | 立即（须走 setter） | L1 | 关键字列表，按下标存（`keywords[0]`…）。UI 需要支持增删，不要让用户手填下标。 |
| `dbsyncer.retry.global.exclude-keywords[n]`（**properties 未暴露**） | 命中就**不**重试直接抛 | 反向开关 | 立即 | L1 | 排除关键字：命中即不重试、直接抛出。当前配置文件里没有这一项，属本功能首次引入，需定义空值语义。 |

### Tab 错误处理 / 通知

| 键 | 作用 | 生效 | 档 | 悬浮提示文案 |
|---|---|---|---|---|
| `dbsyncer.error.threshold`（:139 = 1；未配置时初值 0） | 连续失败多少次进错误队列 | 立即 | L1 | 熔断阈值。**`0` 的含义是"不启用熔断"**，不是"关闭"也不是留空。配置文件里写的是 1。 |
| `dbsyncer.error.fatal-match-mode` / `fatal-keywords`（properties 里**只有注释示例**） | 判定"致命错误、不再重试" | 立即（setter 已正确清缓存） | L1 | 致命错误关键字。当前配置文件里只有被注释掉的示例，界面上要能表达"未配置"。 |
| `dbsyncer.plugin.notify.channels` | 启用的通知通道列表（真开关） | 立即 | L1 | **告警总开关就在这一项，不在 `mail.enabled`**（那是死键，见 §3）。留空 ⇒ `CompositeNotifyService.java:50-53` 直接 return，**所有通知静默跳过**。⚠️ **当前 `application.properties` 根本没配这个键**（只配了 mail.*），所以现在任何告警都不会发出去 —— 界面首次给出该项时应默认填 `mail`/`dingtalk` 之外的空值并在文案里点明"当前系统实际未启用任何通道"。取值须精确小写 `mail`/`dingtalk`（`NotifyConfig.java:62` 用 `contains`，大小写敏感）。 |
| `dbsyncer.plugin.notify.mail.{username,password}` | 发告警邮件的账号 | **重启应用**（不是立即！） | **L2** | 邮件凭据。**保存后要重启才生效**：`Session` 只在 `MailNotifyServiceProvider.java:50-55` 的 `@PostConstruct` 里建一次，username 当时为空就永久不建，之后 `:81-83` 每次静默 return。另 **SMTP host 硬编码 `smtp.qq.com`（`:60`）不可配** ⇒ 只能填 QQ 邮箱。密码字段必须脱敏显示。 |
| `dbsyncer.plugin.notify.mail.notify-types` | 哪些事件类型发邮件 | **须先补 `setNotifyTypes` 的缓存失效**（`NotifyConfig.java:118` 未清 `notifyTypeSet`）才谈得上生效 | L1 | 三个坑叠加：① `:118` 不清缓存 ⇒ 未修复前改完**永不生效**；② `shouldNotify():110` 是 `notifyTypeSet.isEmpty() ||` —— **留空 = 全部类型都通知**，不是"都不通知"，文案不能写成"未选择则不发送"；③ 非法枚举名被 `:105-106` `catch (IllegalArgumentException ignored)` 静默吞掉 ⇒ **拼错等于没写**。⇒ 界面必须给**固定下拉**（8 个枚举名，见下），不要放开文本输入。 |
| `dbsyncer.plugin.notify.dingtalk.webhookUrl` | 钉钉机器人地址 | **立即**（`DingTalkNotifyServiceProvider.java:39-40`、`:58` 每次现读） | **L2** | 钉钉与邮件不同：它没有 `@PostConstruct` 快照，是**真热更**。留空则 `:40` 直接 return。它属"外呼地址"，保存前需二次确认（会把内部告警内容发往该 URL）。 |
| `dbsyncer.plugin.notify.dingtalk.secret` | 看起来是签名密钥 | **不生效（死字段）** | — | ❌ **不要出现在界面上**：`getSecret()`（`NotifyConfig.java:147`）全仓**零调用者**，`DingTalkNotifyServiceProvider` 从不签名。填了也不生效，还会让人以为消息已加签。 |
| `dbsyncer.plugin.notify.dingtalk.notify-types` | 哪些事件发钉钉 | 同 mail.notify-types（`NotifyConfig.java:150` 同样不清缓存） | L1 | 同 `mail.notify-types` 的三个坑（锚点换为 `:131-142`、`:150`）。 |

> **`notify-types` 可选值（界面下拉直接用，实测自 `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/notify/NotifyType.java:15-39`）**：
> `TASK_INTERRUPTED`（任务异常中断）、`DATA_ERROR_QUEUE`（数据进错误队列）、`MANUAL_OPERATION`（手动停止/重置/重新同步）、
> `DATA_ERROR_CIRCUIT_BREAKER`（错误队列熔断）、`FATAL_MATCHED`（命中致命关键字）、`DDL`、`TARGET_EXCEPTION`、`SOURCE_EXCEPTION`。
> 必须是**全大写枚举名**——`NotifyType.valueOf` 不认别名（`:104`）。


### A 类（只读展示，改动请走配置文件 + 重启）

| 键 | 为什么只读 | 档 | 悬浮提示文案 |
|---|---|---|---|
| `server.ip` / `server.port`（:2 / :4） | 改了自我断链 | L1 | 服务监听地址与端口。改错会让你再也打不开这个页面，请改配置文件后重启。 |
| `server.servlet.session.timeout`（:22 = 1800） | 容器创建期固化 | L1 | 会话超时（秒）。由 Servlet 容器在建容器时确定，改完必须重启，**界面上不可编辑**。 |
| `server.ssl.enabled` + `server.http.enabled`（:7 / :15） | **两个必须同时为 true** 才装配 HTTPS/HTTP 相关 bean | **L2** | 这两个开关是**与**关系：只开一个不产生任何效果（条件装配要求两者皆 true）。 |
| `server.http.redirect`（:19 = false） | **它不是安全开关** | **L2** | ⚠️ 设为 false **不会关闭 8080 明文端口**。它只控制"要不要自动跳转"，明文 connector 在 ssl+http 开着时照样挂上。不要用这项当作"禁用明文访问"。 |
| `dbsyncer.storage.type`（:78 = mysql） | 决定"配置存在哪"，切换≈换元数据库 | **L2** | 元数据存储后端。切到 `disk` 会去读本地 Lucene 索引目录，**已有数据不会出现在界面上**（不是删除，是看不见），近似不可逆操作。且**只能用配置文件改，`-D` 与环境变量无效**。 |
| `dbsyncer.storage.mysql.url` / `username` / `password`（:79-:81） | 凭据 + 自举环 | **L2** | 元数据库连接串与账号。只读展示，**密码必须脱敏**。注意：代码里有硬编码默认值（`MySQLStorageService.java:69-71`，密码默认 `"123456"`），且完整连接串会被打进 info 日志（`:75`）。 |
| `dbsyncer.web.worker.id` / `data-center-id`（:29 / 未暴露） | 分布式 ID 冲突与**位污染** | **L2** | 雪花 ID 的机器号。集群内必须互不相同。⚠️ 位拼接处**没有 5 bit 掩码**：填大于 31 的值会污染其它位段，**产生重复 ID 且不可逆**。本功能不提供在线修改。 |
| `dbsyncer.web.security.reset-pwd`（:33 = false） | 开启即以默认口令重置 admin | **L2** | 恢复默认密码开关。开启后**任何人**都能用默认口令把管理员密码改掉，属"动作"而非"配置"，界面只读并提示按需在配置文件里临时打开。 |
| `dbsyncer.web.license.server.address`（**properties 未配置**） | 出站授权回连点 | **L2** | 许可证校验的远程地址，默认值是**明文 http 的公网 IP**（`LicenseController.java:77`）。它不属任何配置 bean，改它只能改配置+重启。**UI 只读脱敏显示**（如 `http://***.145.***:9999/...`），避免泄露完整地址。 |
| `logging.level.root`（:151 = info） | 只覆盖 root，与 `logging.level.<package>` 粒度不一致 | L1 | 根日志级别。这一项只管 root，单独设过包级日志的不会被它覆盖，容易误判。 |
| `helpdoc.path`（:148 = ./helpdocs） | 同一个 key 注入到**两个 bean** | L1 | 帮助文档目录。它被 MVC 资源映射和文档控制器各注入一份，热更新会两边不一致，因此只读。 |
| `user.dir`（JVM 内置） | 改它等于改数据目录 | **L2** | 进程工作目录，决定 `data/`、`conf/`、`helpdocs/` 的落盘位置。**不进界面**：改了会让已有数据"看不见"。 |
| `info.app.*`（:50-:52） | 由代码写入 Environment | L0 | 版本信息。由 `Application.java:44-:47` 以 `setDefaultProperties` 注入，属**最低优先级**源，会被 `application.properties` 覆盖。纯展示。 |

## 3. 诱饵与死键清单（**绝对不能出现在界面上**）

给用户一个改了不生效的开关，比不给开关更糟——它会吃掉排障时间。下表 **13 类**（展开约 30 个键）：properties 里有值但零消费者，或绑得上但无人读。

| 键 | 假象 | 实测真相 |
|---|---|---|
| `server.ip`（properties:2） | 看起来是监听地址 | Spring Boot **没有这个属性**，主代码零读路径。监听地址实际由 `server.address` 决定 |
| `dbsyncer.parser.table.group.*`（properties:61-:73，7 项） | 表分组缓冲参数 | 2.12.2 起废弃，**无绑定类、无消费者**，可整组删除 |
| `dbsyncer.plugin.notify.mail.enabled`（properties:101） | 邮件开关 | `MailConfig` **根本没有 `enabled` 字段**；通道启停实际由 `channels` 列表控制 |
| `sqlserver.ct.poll.interval.ms`（properties:136 = 500） | 500ms 轮询 | 代码用 `System.getProperty` 读（`SqlServerCTListener.java:84-85`），Boot 不会把 properties 灌进 JVM 属性 ⇒ **实际跑 30000**，且 `:109` 日志会打印 30000，极易误判 |
| `sqlserver.ct.snapshot.time.interval.ms` | 同上 | 同形 bug，未配置时也是 30000 |
| `dbsyncer.storage.buffer-writer-count`（properties:89） | 存储写并发 | `@ConfigurationProperties` 绑得上，但**存储链路零调用**。**不暴露到 UI**（死键）。 |
| `dbsyncer.web.cache.<name>.{timeout,max}` | 缓存 TTL/容量 | **整族死键**：全仓 0 处 `@Cacheable`（只有 `Application.java:24` 开了 `@EnableCaching`），且 `cache` 字段无 getter → 配了没人用、界面也读不回 |
| `dbsyncer.parser.general.buffer-writer-count`（properties:52） | 看起来是“每批写线程数” | 唯一消费者是 `MetaBufferActuator.java:124`，作为 `failCount` 传给 `ErrorQueueCollectorImpl.java:196`，只进 `:202` 的告警文案 ⇒ **不影响并发、不影响批次**。批次大小实际由 `AbstractBufferActuator.java:263` 的 `getBufferPullCount()` 决定。**已决定改名为“任务中断上报的失败数”暴露** |
| `dbsyncer.retry.task.<taskId>.*`（11 子键） | 任务级重试覆盖 | `getPolicy(taskId)` 全仓 **0 调用者** → 做了就是假功能 |
| `dbsyncer.retry.global.*`（部分） | 全局重试权威 | `BinaryLogRemoteClient.java:819-825` 自建 `new RetryPolicy()` 并逐项覆盖 ⇒ **MySQL keepalive 断线重连不受界面控制** |
| `dbsyncer.template.maxDepth` | 可配置 | 只认 `System.getProperty`；且 `TemplateHandler.java:26` 字段级 new（要重启）与 `ConvertUtil.java:135` 方法级 new（每次现读）**新鲜度不一致** |
| `server.ssl.key-store`/`-password`/`-type`/`key-alias`、`server.http.port` | 主代码引用 | 主代码零引用，仅 `server.ssl.enabled=true` 时才由框架装配 |
| `application copy.properties` | 第二份配置 | **永不加载**（profile 是 `default`，只会找 `application-default.properties`），但会被打进 jar、会让任何按 glob 找配置文件的写入逻辑读到它 |

**两个必须避开的实现陷阱**：
- `dbsyncer.storage.mysql.driver-class-name` 走 `Properties.getProperty` **精确匹配**，
  relaxed binding 不适用 ⇒ 写成 `driverClassName` 静默不生效（`MySQLStorageService.java:74`）。
- `RetryConfig.java:25-:27` 的 javadoc 示例把键名写成 `initial-interval`/`max-interval`（**缺 `-ms`**）
  ⇒ 生成提示文案时**不要照抄源码注释**，照抄会得到绑不上的死键。

## 4. 界面改动的落地边界（实测，非设计）

| 事项 | 实测结论 |
|---|---|
| 现有配置页 | `dbsyncer-web/src/main/resources/public/system/system.html`，**没有 tab**，整页一个 `form id="configEditForm"`（模板在 `public/` 下，前缀由 `Application.java:47` 设成 `classpath:/public/`） |
| 抄 tab 结构 | `dbsyncer-web/src/main/resources/public/mapping/edit.html:200-:234`（`nav.nav-tabs` + `data-toggle="tab"` + `tab-pane`） |
| 保存通路 | `dbsyncer-web/src/main/resources/static/js/system/index.js` 第 41 行 `serializeJson()` ⇒ **整表单全量提交**，多 tab 会一起提交 |
| 新增字段的硬前提 | 必须先在 `SystemConfig` 加字段，否则 `BeanUtil.java:80` 返回 null → `:21` `field.getType()` NPE → 保存整页 500 |
| 权限 | `SystemController.java:24` 类级 `@PreAuthorize("hasRole('admin')")` |
| CSRF | `WebAppConfig.java:126` 全局关闭 |
| 现成的"运行期改配置即生效"通路 | 只有 `SystemConfig`(ConfigModel) 链路：`OperationTemplate.java:106` 写库后回灌内存缓存 ⇒ 下次读即新值。**11 个 `@ConfigurationProperties` bean 完全没有运行期重绑实现**（全仓 0 处 `@RefreshScope`、0 处对 config bean 的 `setXxx` 调用） |
| 可复用的"批量刷新"骨架 | `PreloadTemplate.java:119 reload(json)`（经 `/config/upload`），但它会**重建连接器并重启 RUNNING 驱动**，改一个数值不该付这个代价 |
| 已有的"生效方式"暗示 | 界面已用"需刷新页面生效"文案（`system.html:35`、`system.html:63`），监控刷新间隔页面各自加载时取一次（`MonitorController.java:262`） |
| 持久化真实形态 | **没有 `t_system` 表**（全仓 0 命中）；MySQL 后端表是 `dbsyncer_config`（`dbsyncer_mysql_config.sql:1`，整对象 JSON 存进 `json` 列），disk 后端是 `${user.dir}/data/config` 的 Lucene 索引 |
| 陈旧注释（**不要据此设计**） | `AppConfigServiceImpl.java:38` 提到 `SingletonConfigBootstrap`，该类**仓库中不存在** |

## 5. 待核（明确挂账，不当作事实使用）

| # | 待核项 | 验证方式 |
|---|---|---|
| P1 | `max-attempts` 到底含不含首次（静态推演为 `maxAttempts+1` 次执行） | 实跑一次 `RetryInterceptor`，打印 attempt 计数 |
| P2 | `StorageSupportConfiguration.java:62` 的 profile 源名判定在 `spring.profiles.active=default` 下的实际列表 | 启动期打印 `environment.getPropertySources()` 名字 |
| P3 | `dbsyncer.storage.type` 从 mysql 切 disk 后原数据是否可读、有无迁移路径 | 双模式实测 |
| P4 | `MySQLStorageService.java:74` 的 `driver-class-name` 为 null 时驱动如何推断 | 追进 `sdk/connector/database` |
| P5 | `system.enableStorageWriteFail` 判死键的依据是 getter 0 调用；若插件/SPI 侧反射读则判定需修正 | 插件侧反射排查 |
| P6 | `ConfigController.java:86` 的 `tmpdir + filename` 未加分隔符且 filename 来自上传，是否构成路径穿越 | 上传路径实测 |
| P7 | lane-1b（parser bean 读路径）与 lane-2（缓冲区构造固化）的 `.md` 表格尚未落盘 | 等侦察回表后并入 §2 |
