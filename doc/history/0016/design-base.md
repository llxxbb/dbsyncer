# 0016-日志查询功能分散到任务面板 - 设计基础

## 文档信息

| 字段 | 值 |
|------|-----|
| **ADR 编号** | 0016 |
| **标题** | 日志查询功能分散到任务面板 |
| **最后更新** | 2026-06-23 |
| **状态** | Proposed |
| **作者** | 老李数字分身 |

---

## 设计目标

将监控页面的日志查询功能迁移到任务编辑页面，移除全局日志查询功能。

### 核心改进

1. **数据模型升级**：使用 `model_type + model_id` 双字段替代单一 `mapping_id`
2. **日志方法统一**：BaseServiceImpl 合并三个独立方法为统一方法
3. **TableGroup 日志优化**：直接关联到父级 Mapping，避免日志分散

### 清理范围

1. **后端**：移除不传 model_id 的查询，只保留按实体 ID 查询
2. **前端监控页面**：移除监控页面中的日志查询 UI 和代码
3. **历史日志**：清理已有日志数据，新功能只查询新日志

---

## 设计方案

### 后端改造

**关键代码**：
```java
@Override
public Paging queryLog(Map<String, String> params) {
    String mappingId = params.get(ConfigConstant.CONFIG_MAPPING_ID);
    Assert.hasText(mappingId, "mappingId 是必填参数");
    
    Query query = new Query(pageNum, pageSize);
    query.addFilter(ConfigConstant.CONFIG_MAPPING_ID, mappingId, false);
    query.addFilter(ConfigConstant.CONFIG_MODEL_JSON, json, true);
    query.setType(StorageEnum.LOG);
    return storageService.query(query);
}

@Override
public String clearLog(Map<String, String> params) {
    String mappingId = params.get(ConfigConstant.CONFIG_MAPPING_ID);
    Assert.hasText(mappingId, "mappingId 是必填参数");
    
    Query query = new Query();
    query.addFilter(ConfigConstant.CONFIG_MAPPING_ID, mappingId, false);
    query.setType(StorageEnum.LOG);
    storageService.delete(query);
    return "清空日志成功";
}
```

**废弃方法**：
- `clearLog()` 无参版本
- `queryLog()` 不传 mappingId 调用

### 前端改造

1. **映射编辑页**：添加"日志查询"Tab
2. **监控页面**：删除日志查询 UI
### 详细设计

#### 1. 后端改造

**文件**: `dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/MonitorServiceImpl.java`

**关键代码**：
```java
@Override
public Paging queryLog(Map<String, String> params) {
    String mappingId = params.get(ConfigConstant.CONFIG_MAPPING_ID);
    Assert.hasText(mappingId, "mappingId 是必填参数");
    
    int pageNum = NumberUtil.toInt(params.get("pageNum"), 1);
    int pageSize = NumberUtil.toInt(params.get("pageSize"), 10);
    String logType = params.get("logType");
    String logLevel = params.get("logLevel");
    String json = params.get(ConfigConstant.CONFIG_MODEL_JSON);
    
    Query query = new Query(pageNum, pageSize);
    query.addFilter(ConfigConstant.CONFIG_MAPPING_ID, mappingId, false);
    if (StringUtil.isNotBlank(logType)) {
        query.addFilter("logType", logType, false);
    }
    if (StringUtil.isNotBlank(logLevel)) {
        query.addFilter("logLevel", logLevel, false);
    }
    if (StringUtil.isNotBlank(json)) {
        query.addFilter(ConfigConstant.CONFIG_MODEL_JSON, json, true);
    }
    query.setType(StorageEnum.LOG);
    return storageService.query(query);
}

@Override
public String clearLog(Map<String, String> params) {
    String mappingId = params.get(ConfigConstant.CONFIG_MAPPING_ID);
    Assert.hasText(mappingId, "mappingId 是必填参数");
    
    Query query = new Query();
    query.addFilter(ConfigConstant.CONFIG_MAPPING_ID, mappingId, false);
    query.setType(StorageEnum.LOG);
    storageService.delete(query);
    return "清空日志成功";
}
```

**清理废弃方法**：
```java
// 删除旧方法（无参数清空所有日志）
@Override
public String clearLog() {  // ❌ 废弃
    storageService.clear(StorageEnum.LOG, null);
    return "清空日志成功";
}

// 删除旧查询方法（不传 mappingId）
@Override
public Paging queryLog(Map<String, String> params) {  // ❌ 已替换
    // 旧实现不再需要
}

// 使用新方法（按 mappingId 过滤）
@Override
public String clearLog(Map<String, String> params) {  // ✅ 新增
    String mappingId = params.get(ConfigConstant.CONFIG_MAPPING_ID);
    Assert.hasText(mappingId, "mappingId 是必填参数");
    
    Query query = new Query();
    query.addFilter(ConfigConstant.CONFIG_MAPPING_ID, mappingId, false);
    query.setType(StorageEnum.LOG);
    storageService.delete(query);
    return "清空日志成功";
}
```

**说明**：
- 日志表直接存储 mappingId 字段
- 查询通过 query.addFilter("mappingId", ...) 直接过滤
- 清空通过 storageService.delete() 删除指定 mappingId 的记录

在 Tab 菜单中添加日志页签：

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

<div class="tab-content">
    <!-- 日志查询页签 -->
    <div role="tabpanel" class="tab-pane fade" id="mappingLog">
        <div class="panel panel-default">
            <div class="panel-heading">
                <h4 class="panel-title">查询日志</h4>
            </div>
            <div class="panel-body">
                <!-- 查询条件 -->
                <div class="form-group">
                    <div class="col-sm-4">
                        <input id="searchLogKeyword" class="form-control" type="text" 
                               maxlength="32" placeholder="请输入日志关键字 (最多 32 个字)." />
                    </div>
                    <div class="col-sm-2">
                        <select id="logTypeFilter" class="form-control">
                            <option value="">所有类型</option>
                            <option value="TASK">任务日志</option>
                            <option value="SYSTEM">系统日志</option>
                        </select>
                    </div>
                    <div class="col-sm-2">
                        <select id="logLevelFilter" class="form-control">
                            <option value="">所有级别</option>
                            <option value="INFO">INFO</option>
                            <option value="WARN">WARN</option>
                            <option value="ERROR">ERROR</option>
                        </select>
                    </div>
                    <div class="col-md-1">
                        <button id="queryLogBtn" type="button" class="btn btn-primary">查询日志</button>
                    </div>
                    <div class="col-md-3"></div>
                    <div class="col-md-3 text-right">
                        <button id="clearLogBtn" type="button" class="btn btn-default">清空日志</button>
                    </div>
                </div>
                
                <!-- 日志表格 -->
                <table class="table table-hover">
                    <thead>
                    <tr>
                        <th style="width:3%;">#</th>
                        <th style="width:15%;">类型</th>
                        <th style="width:8%;">级别</th>
                        <th style="width:59%;">内容</th>
                        <th style="width:15%;">时间</th>
                    </tr>
                    </thead>
                    <tbody id="logList"></tbody>
                </table>
                
                <!-- 分页 -->
                <div class="form-group">
                    <div class="col-md-5">共计：<span id="logTotal">0</span>条</div>
                    <div class="col-md-7">
                        <a href="javascript:void(0);" id="queryLogMore" num="1">显示更多<i class="fa fa-angle-double-down"></i></a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
```

#### 3. 前端改造 - JavaScript

**文件**: `dbsyncer-web/src/main/resources/static/js/mapping/edit.js`

```javascript
// 任务面板日志查询
function bindMappingLogEvent(mappingId) {
    // 查询日志
    $('#queryLogBtn').click(function () {
        var keyword = $('#searchLogKeyword').val();
        var logType = $('#logTypeFilter').val();
        var logLevel = $('#logLevelFilter').val();
        
        doGetter('/monitor/queryLog', {
            mappingId: mappingId,
            logType: logType,
            logLevel: logLevel,
            json: keyword,
            pageNum: 1,
            pageSize: 10
        }, function (data) {
            if (data.success === true) {
                refreshLogList(data.resultValue);
            } else {
                bootGrowl(data.resultValue, "danger");
            }
        });
    });
    
    // 显示更多
    $('#queryLogMore').click(function () {
        var num = parseInt($(this).attr('num'));
        var keyword = $('#searchLogKeyword').val();
        var logType = $('#logTypeFilter').val();
        var logLevel = $('#logLevelFilter').val();
        
        showMore($(this), '/monitor/queryLog', {
            mappingId: mappingId,
            logType: logType,
            logLevel: logLevel,
            json: keyword,
            pageNum: num + 1,
            pageSize: 10
        }, function (resultValue) {
            refreshLogList(resultValue, true);
        });
    });
    
    // 清空日志
    $('#clearLogBtn').click(function () {
        BootstrapDialog.show({
            title: '警告',
            type: BootstrapDialog.TYPE_DANGER,
            message: '确认清空该任务的所有日志？',
            size: BootstrapDialog.SIZE_NORMAL,
            buttons: [{
                label: '确定',
                action: function (dialog) {
                    doPoster('/monitor/clearLog', { mappingId: mappingId }, function (data) {
                        if (data.success === true) {
                            bootGrowl('清空日志成功!', "success");
                            refreshLogList({ data: [], total: 0 });
                        } else {
                            bootGrowl(data.resultValue, "danger");
                        }
                    });
                    dialog.close();
                }
            }, {
                label: '取消',
                action: function (dialog) {
                    dialog.close();
                }
            }]
        });
    });
}
```

// 辅助函数
function refreshLogList(resultValue, append) {
    var $logList = $("#logList");
    var $logTotal = $("#logTotal");
    var html = showLog($logList, resultValue.data, append);
    if (append) {
        $logList.append(html);
    } else {
        $logList.html(html);
        $("#queryLogMore").attr("num", 1);
    }
    $logTotal.html(resultValue.total);
}

function showLog($logList, arr, append) {
    var size = arr.length;
    var html = '';
    if (size > 0) {
        var start = append ? $logList.find("tr").size() : 0;
        for (var i = 0; i < size; i++) {
            html += '<tr>';
            html += '<td>' + (start + i + 1) + '</td>';
            html += '<td>' + (arr[i].logType || '-') + '</td>';
            html += '<td>' + (arr[i].logLevel || '-') + '</td>';
            html += '<td>' + arr[i].json + '</td>';
            html += '<td>' + formatDate(arr[i].createTime) + '</td>';
            html += '</tr>';
        }
    }
    return html;
}
```

#### 4. 初始化调用

在 `edit.js` 的初始化函数中调用：

```javascript
$(function () {
    var mappingId = $('#mappingId').val();
    if (mappingId) {
        bindMappingLogEvent(mappingId);
    }
});
```

## 前端改造

### 1. 映射编辑页

**编辑页 Tab** (`edit.html`):
```html
<li role="presentation">
    <a href="#mappingLog" data-toggle="tab">日志查询</a>
</li>
```

**JS 绑定** (`edit.js`):
```javascript
function bindMappingLogEvent(mappingId) {
    $('#queryLogBtn').click(() => {
        doGetter('/monitor/queryLog', { mappingId }, success);
    });
}
```

### 2. 监控页面

删除以下内容：
- `monitor.html` 中"查询日志"面板
- `monitor/index.js` 中日志查询事件绑定

---

## 验收标准

### 功能验收

- [ ] 任务面板日志页签可正常显示，仅显示当前任务日志
- [ ] 日志查询功能正常，支持类型、级别、关键字组合过滤
- [ ] 分页功能正常
- [ ] 清空日志功能正常

### 代码质量验收

- [ ] 遵循项目代码规范
- [ ] 无编译错误
- [ ] 清理废弃方法

---

## 相关文件

- `MonitorService.java`
- `MonitorServiceImpl.java`
- `LogVo.java`
- `ConfigConstant.java`
- `edit.html`
- `edit.js`
- `monitor.html`
- `monitor/index.js`
- `dbsyncer_mysql_log.sql`

---

## 历史

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| 0.1 | 2026-06-05 | - | 初始设计 |
| 0.2 | 2026-06-23 | - | 简化设计，移除 mapping->meta 映射 |

---

## LogType 重构补充设计

### 背景

原设计中 `LogType` 被定义为 interface，通过 9 个内部 enum 实现（SystemLog, ConnectorLog, MappingLog, TableGroupLog 等），每个 enum 都重复实现 `getName()`, `getType()`, `getMessage()` 方法，导致代码冗余。

### 重构方案

将 `LogType` 从 interface 改为单一 enum，所有日志类型统一在一个 enum 中管理：

**变更前**：
```java
public interface LogType {
    String getName();
    String getType();
    String getMessage();
}

enum SystemLog implements LogType { ... }
enum ConnectorLog implements LogType { ... }
enum MappingLog implements LogType { ... }
// ... 共 9 个 enum
```

**变更后**：
```java
public enum LogType {
    SYSTEMINFO("10", "正常"),
    SYSTEMWARN("11", "警告"),
    CONNECTORINSERT("20", "新增"),
    MAPPINGINSERT("30", "新增"),
    // ... 共 68+ 个值
}
```

### 分类机制

**关键点**：日志表已有 `model_type` 字段区分分类，不再需要从 LogType 提取分类名称。

**设计改进**：
- 移除 `getName()` 方法（不再需要）
- 保留 `getCode()` 和 `getMessage()` 方法（用于日志显示）
- 分类由数据库 `model_type` 字段决定，而非 LogType

### 好处

1. **代码量减少**：从 ~314 行 → ~200 行（减少 ~36%）
2. **统一管理**：所有日志类型在一个 enum 中，新增更简单
3. **消除重复**：不需要为每个分类写重复的方法实现
4. **简化实现**：从 interface+9 个 enum → 单一 enum

### 影响范围

需要全局替换所有 `LogType.XXXLog.YYY` 调用为 `LogType.XXXYYY`：

**示例**：
- `LogType.MappingLog.INSERT` → `LogType.MAPPINGINSERT`
- `LogType.UserLog.UPDATE` → `LogType.USERUPDATE`
- `LogType.SystemLog.ERROR` → `LogType.SYSTEMERROR`

预计影响：100+ 处调用

### 迁移策略

**阶段 1**：重构 LogType 定义（已完成）
- 创建新的 enum 结构
- 统一所有日志类型

**阶段 2**：全局替换所有调用（进行中）
- 使用 sed 或查找替换工具
- 验证编译通过

**阶段 3**：清理废弃代码
- 删除不再使用的接口定义
- 确认功能正常

---

## 验收标准（补充）

### LogType 重构验收

- [ ] LogType 改为单一 enum
- [ ] 所有 68+ 日志类型定义正确
- [ ] 全局替换所有调用成功
- [ ] 编译无错误
- [ ] 功能测试通过

---

## 实施说明

### 执行时间
2026-06-24

### 执行结果

**1. LogType 重构完成**
- 从 interface + 9 个 enum → 单一 enum
- 共 68 + 个日志类型定义
- 向后兼容：保留 getType(), getName(), getMessage() 方法

**2. 全局替换统计**
- 处理的文件数：19 个
- 替换次数：70 + 次  
- 涉及模块：
  - dbsyncer-biz: 10 个文件
  - dbsyncer-manager: 5 个文件
  - dbsyncer-parser: 2 个文件
  - dbsyncer-web: 2 个文件

**3. 编译验证**
- Maven 编译：✅ 成功
- 所有模块：✅ 构建成功

**4. 修复的问题**
- ParserConsumer: 添加 tableGroups 字段，修正 errorEvent 方法
- LogType：添加 getType() 和 getName() 方法保持兼容
- LicenseController: 修复 USERUPLOAD_LICENSE_FILE → USERUPLOADLICENSE 等

### 待完成
- ⏳ 启动服务进行功能测试
- ⏳ 验证日志查询功能

---

## 变更文件清单

| 文件 | 变更说明 |
|------|----------|
| dbsyncer-sdk/src/main/java/org/dbsyncer/sdk/spi/LogType.java | LogType 重构为单一 enum |
| dbsyncer-biz/**/*.java | 10 个文件，LogType 调用替换 |
| dbsyncer-manager/**/*.java | 5 个文件，LogType 调用替换 |
| dbsyncer-parser/**/*.java | 2 个文件，LogType 调用替换 |
| dbsyncer-web/**/*.java | 2 个文件，LogType 调用替换 |

---

**执行者**: 系统自动执行  
**状态**: ✅ 代码重构完成，待功能验证
