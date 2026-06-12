# 0016-日志查询功能分散到任务面板 - 实现说明

## 实现信息

| 字段 | 值 |
|------|-----|
| **关联 ADR** | 0016 |
| **实现日期** | 2026-06-23 |
| **实现者** | 系统 |
| **实现状态** | 进行中 |

---

## 实施计划

### 阶段 1: 数据库表结构改造

#### 1.1 修改 SQL Schema

**文件**: `dbsyncer-connector/dbsyncer-connector-mysql/src/main/resources/dbsyncer_mysql_log.sql`

**改动点**:
```sql
-- 新表结构
CREATE TABLE `dbsyncer_log` (
  `ID` varchar(64) PRIMARY KEY,
  `MODEL_TYPE` varchar(32) NOT NULL COMMENT '实体类型',
  `MODEL_ID` varchar(64) NOT NULL COMMENT '实体 ID',
  `TYPE` varchar(64) NOT NULL COMMENT '日志分类',
  `LOG_LEVEL` varchar(8),
  `CREATE_TIME` bigint,
  `JSON` mediumtext,
  INDEX `IDX_MODEL_TYPE_MODEL_ID` (`MODEL_TYPE`, `MODEL_ID`),
  INDEX `IDX_CREATE_TIME` (`CREATE_TIME`)
);
```

### 阶段 2: 数据模型改造

#### 2.1 LogVo.java

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/vo/LogVo.java`

**改动点**:
```java
public class LogVo {
    private String id;
    private String modelType;    // 新增
    private String modelId;      // 新增
    private String type;         // 保留（合并原 LOG_TYPE 和 TYPE）
    private String logLevel;
    private String json;
    private long createTime;
}
```

#### 2.2 ConfigConstant.java

**文件**: `dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/constant/ConfigConstant.java`

**改动点**:
```java
public static final String CONFIG_MODEL_TYPE = "modelType";
public static final String CONFIG_MODEL_ID_VALUE = "modelId";
```

### 阶段 3: BaseServiceImpl 重构

#### 3.1 统一日志方法

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/BaseServiceImpl.java`

**改动点**:

- 删除三个独立方法：`log(LogType, ConfigModel)`, `log(LogType, Mapping)`, `log(LogType, TableGroup)`
- 新增统一方法：`log(LogType, Object model, String message)`
- 新增辅助方法：`getModelType(Object)`, `getModelId(Object)`

### 阶段 4: MonitorServiceImpl 更新

#### 4.1 queryLog() 支持多类型过滤

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MonitorServiceImpl.java`

**改动点**:
```java
@Override
public Paging queryLog(Map<String, String> params) {
    String modelType = params.get(ConfigConstant.CONFIG_MODEL_TYPE);
    String modelId = params.get(ConfigConstant.CONFIG_MODEL_ID_VALUE);
    Assert.hasText(modelId, "modelId 是必填参数");
    
    Query query = new Query(pageNum, pageSize);
    query.addFilter(ConfigConstant.CONFIG_MODEL_TYPE, modelType, false);
    query.addFilter(ConfigConstant.CONFIG_MODEL_ID_VALUE, modelId, false);
    // ...
}
```

### 阶段 5: 前端调整

#### 5.1 日志列表显示 modelType

**文件**: `mapping/edit.html`, `mapping/edit.js`

**改动点**:
- Table 新增列：类型（modelType）
- 查询参数新增：modelType 过滤
    
    Query query = new Query(pageNum, pageSize);
    if (StringUtil.isNotBlank(metaId)) {
        query.setMetaId(metaId); // 按任务过滤
    }
    if (StringUtil.isNotBlank(json)) {
        query.addFilter(ConfigConstant.CONFIG_MODEL_JSON, json, true);
    }
    query.setType(StorageEnum.LOG);
    return storageService.query(query);
}
```

**验证点**:
- [ ] 单元测试通过
- [ ] 全局查询功能不受影响
- [ ] 任务查询功能正常

#### 1.2 修改 MonitorServiceImpl.clearLog()

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MonitorServiceImpl.java`

**改动点**:
```java
@Override
public String clearLog(String metaId) { // 新增参数
    storageService.clear(StorageEnum.LOG, metaId);
    return "清空日志成功";
}
```

**说明**: 清空日志功能需要支持按 metaId 清空指定任务日志

---

### 阶段 2: 前端改造（预计 1.5 天）

#### 2.1 修改 mapping/edit.html

**文件**: `dbsyncer-web/src/main/resources/public/mapping/edit.html`

**改动点**:
1. 在 Tab 菜单中添加日志页签
2. 添加日志查询 UI 组件

**关键代码**:
```html
<ul class="nav nav-tabs" role="tablist">
    <li role="presentation" class="active">
        <a href="#mappingBasic" aria-controls="mappingBasic" role="tab" data-toggle="tab">基本信息</a>
    </li>
    <li role="presentation">
        <a href="#mappingFields" aria-controls="mappingFields" role="tab" data-toggle="tab">字段映射</a>
    </li>
    <li role="presentation">
        <a href="#mappingLog" aria-controls="mappingLog" role="tab" data-toggle="tab">日志查询</a>
    </li>
</ul>
```

#### 2.2 修改 mapping/edit.js

**文件**: `dbsyncer-web/src/main/resources/public/js/mapping/edit.js`

**改动点**:
1. 新增 `bindMappingLogEvent(metaId)` 函数
2. 新增 `refreshLogList()` 函数
3. 新增 `showLog()` 函数
4. 在初始化函数中调用日志查询

**关键代码**:
```javascript
function bindMappingLogEvent(metaId) {
    $("#queryLogBtn").click(function () {
        var keyword = $("#searchLogKeyword").val();
        doGetter('/monitor/queryLog', {
            "id": metaId,
            "json": keyword,
            "pageNum": 1,
            "pageSize": 10
        }, function (data) {
            if (data.success == true) {
                refreshLogList(data.resultValue);
            } else {
                bootGrowl(data.resultValue, "danger");
            }
        });
    });
    // ... 其他事件绑定
}
```

---

## 关键技术点

### 1. 向后兼容性

**问题**: 如何保证全局日志查询功能不受影响？

**解决方案**: 
- metaId 参数为可选参数
- 不传 metaId 时，查询所有日志
- 保持现有 API 行为不变

### 2. 代码复用

**问题**: 任务面板和全局监控页面有相似的日志查询代码

**解决方案**:
- 短期：保持代码重复，减少改动风险
- 长期：抽取公共日志查询组件

### 3. 清空日志范围

**问题**: 如何区分全局清空和任务清空？

**解决方案**:
- 修改 `clearLog()`方法支持可选`metaId` 参数
- 不传 metaId 时，清空所有日志
- 传入 metaId 时，只清空指定任务日志

---

## 数据库变更

### 日志表结构

**是否需要变更**: 否

**说明**: 
- 日志表已支持按 metaId 过滤
- 无需修改数据库结构

---

## 依赖变更

### 新增依赖

**无**

**说明**: 使用现有依赖，无需新增

### 修改依赖

**无**

**说明**: 无依赖变更

---

## 配置变更

### 系统配置

**是否需要修改**: 否

**说明**: 无需修改系统配置

---

## 测试策略

### 单元测试

- `MonitorServiceImpl.queryLog()` 单元测试
- `MonitorServiceImpl.clearLog()` 单元测试

### 集成测试

- 任务面板日志查询功能测试
- 全局监控页面日志查询功能测试
- 清空日志功能测试

### 性能测试

- 任务日志查询响应时间测试
- 全局日志查询性能对比测试

详见：`doc/history/0015/test-requirements.md`

---

## 部署计划

### 部署步骤

1. 部署后端服务（dbsyncer-biz 模块）
2. 部署前端资源（dbsyncer-web 模块）
3. 验证功能正常

### 回滚计划

1. 回滚前端资源
2. 回滚后端服务
3. 恢复旧版本

### 注意事项

- 部署前备份数据库
- 部署后验证全局日志查询功能
- 监控日志服务性能

---

## 已知问题

### 1. 代码重复

**问题**: 任务面板和全局监控页面存在相似的日志查询代码

**影响**: 维护成本增加

**计划**: 后续版本抽取公共组件

### 2. 清空日志权限

**问题**: 清空任务日志的权限控制不明确

**影响**: 可能存在安全风险

**计划**: 增加权限验证

---

## 验收标准

### 功能验收

- [ ] 任务面板日志页签可正常显示
- [ ] 日志查询功能正常，可按照关键字筛选
- [ ] 分页功能正常（显示更多）
- [ ] 清空日志功能正常
- [ ] 全局监控页面日志查询功能不受影响

### 代码质量验收

- [ ] 无编译错误
- [ ] 无 Sonar 代码异味
- [ ] 符合单一职责原则

### 性能验收

- [ ] 任务日志查询响应时间 < 500ms
- [ ] 全局日志查询性能不受影响

---

## 历史

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| 0.1 | 2026-06-05 | 老李数字分身 | 初始版本 |
