# impl-01: 标量不装箱与默认值权威（实施修订 R1）

关联：ADR-0029〈实施修订 R1（2026-09-09）〉、[verify.json](verify.json)

## 改动清单

| 文件 | 改动 |
|------|------|
| `dbsyncer-common/.../config/ConfigCopy.java` | 新增。无包含过滤器的 mapper 做无损深拷贝，替代 `JsonUtil` 往返 |
| `dbsyncer-common/.../config/ConfigRegistry.java` | `merge()` 重写为 `SystemTabEnum` 驱动的整块择一；每个 Tab 深拷贝；不再共享引用；A 类恒取 props |
| `props/BasicProps, BufferActuatorProps, ErrorProps, MonitorProps, RetryProps, RuntimeProps, ServerProps, WorkerProps` | 35 处标量装箱 → 基本类型（39 处中的 35 处，例外 `PoolProps` 3 项、`TemplateProps.maxDepth`），getter/setter 同步；validate() 的 null 守卫改为区间判断 |
| `props/*`（11 个类） | 36 处两参 `env.getProperty(key, Type)` → 三参 `getProperty(key, Type, 当前值)`（R3：key 缺失不再抹初值）；List / 手工解析 String 的读法保持原样（null 才是"没配"） |
| `biz/impl/ConfigServiceImpl.java` | 去掉与字段初值重复的硬编码兜底（`18686`/`false`/`1800`/`workerId:1`），注释更正为"A 类只来自 Environment" |
| `parser/.../SchedulerFactoryConfig.java` | 去掉 `availableProcessors()` 兜底（Props 初值 8 已是唯一权威） |
| `parser/.../ErrorQueueCollectorImpl.java` | `Integer t` + null 三元 → 直接读 `int` |
| `dbsyncer-common/src/test/.../ConfigRegistryMergeTest.java` | 新增 8 项：报障回归网、DB 优先、resetTab 回落、A 类忽略 DB、深拷贝不共享、schemaVersion、Tab 完整性守卫 |
| `dbsyncer-common/src/test/.../PropsDefaultValueTest.java` | 新增 9 项：空 env 加载后字段值必须逐字段不变（不变式用序列化比对，不硬编码数字） |

## 实测证据（Jackson 2.12 / Spring 5.3，本机 `~/.m2`）

1. `Include.NON_DEFAULT` 对**装箱**字段同样省略：`Integer 0` / `Boolean false` / `String ""` 都不出 JSON——推翻 S1 的"`Integer(0)` ≠ null → 序列化 ✓"。
2. `ObjectMapper.valueToTree` 也受该 mapper 的包含策略影响（`JsonUtil` 的静态 mapper 是 NON_DEFAULT），所以任何基于它的节点合并都会把 `false`/`0` 当成"没有这个 key"。
3. 给 `int` 赋 `null` 直接抛异常 → 不装箱后，两参 `getProperty` 的抹初值写法无法编译/运行，结构上堵死 R3 回退。
4. `Map` 值不受 NON_DEFAULT 影响（`{"success":false,"count":0,"name":""}` 完整输出）→ 只有 POJO 属性受害，API 里手拼的 Map 响应无影响。
5. A 类字段带 `@JsonIgnore`，`objToJson → jsonToObj` 往返会把 `server`/`worker`/`logging` 等整个子对象拷没（首次启动 `merged.getServer() == null`）——R1 的深拷贝顺带修掉。

## 构建坑

`maven-compiler-plugin:3.1` 对**字段/方法签名类型变了**的类不做重编译，`mvn compile` 会假绿（`-Dmaven.compiler.useIncrementalCompilation=false` 也无效）。改类型必须 `mvn -o -DskipTests clean compile`，本轮另有一次手工删除 `dbsyncer-common/target/classes` 造成的脏 classpath 报错（connector-file 找不到 `common.model.Result`），clean 之后消失。

## 测试基线

- `mvn -o -DskipTests clean compile`：全 18 模块通过。
- `mvn -o -pl dbsyncer-common test`：78 项全绿（含新增 17 项）。
- **既有红测**：`dbsyncer-parser/CustomFieldIntegrationTest` 4/4 失败——pristine 树（`git stash --include-untracked`）同样 4/4，断言点在 `ConvertUtil:31`，与本次改动无关。
- **无法运行**：`dbsyncer-web` 集成测试（SQLServer DDL 等）报 `Failed to load ApplicationContext`，根因是 `LockObtainFailedException: Lock held by this virtual machine: dbsyncer-web/data/config/write.lock`——本机有个跑了 1h50m 的 VSCode 调试实例（`org.dbsyncer.web.Application`，jdwp）持有该锁。未终止用户进程，故这批次未验证。

## 未做（明确记录）

- 不改 `JsonUtil` 的 `Include.NON_DEFAULT`（决策者指示），因此残留 7 个字段（8 格）"UI 设成 `false`/`0` 会被静默回退"（26 个初值非类型默认的标量里，另 14 项被 validate() 拦住、5 项属 A 类不入库）——清单见 ADR 已知限制表 / L13。
- 启动对照表不加 `src=db|props|code` 列。
- `SystemConfig.get(String)` / `get(String,T)` 死代码维持原样。
- `dbsyncer-web/src/main/resources/application.properties` 的 MySQL 连接切换是本机用户改动，与本次无关，未纳入。
