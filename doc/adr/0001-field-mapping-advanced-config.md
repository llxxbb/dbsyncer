# ADR 0001: 字段映射高级配置 - 自定义字段元数据编辑

**提议中** → **审查通过** → **实施中** → **已完成**

## 背景

### 问题场景
用户在转换配置界面添加自定义字段时，目标字段不存在，无法确定类型、长度、精度等元数据信息。

### 现状
- ✅ 可输入自定义字段名称
- ❌ 无法指定字段类型、长度、精度
- ❌ 无法指定是否允许为空、注释
- ❌ 后端无法验证字段合法性

### 约束条件
1. **必须与 DDL 同步集成** - 自定义字段通过 DDL 自动创建到目标数据库
2. **保存时执行 DDL** - 配置保存后立即执行 DDL，非运行时
3. **异常简化处理** - 字段已存在时打印警告，不影响后续
4. **向后兼容** - 不影响现有配置和功能

## 决策

### 核心方案
为 `Convert` 类新增 `fieldMetadata` 字段，保存自定义字段的完整元数据，配置保存时执行 DDL 创建字段。

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│  前端 (editConvert.html + editFilterAndConvert.js)          │
│  ┌─────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ 字段编辑对话框│→│ 收集 fieldMetadata│→│ JSON 序列化    │   │
│  └─────────────┘  └──────────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ↓ POST /tableGroup/add or /edit
┌─────────────────────────────────────────────────────────────┐
│  后端 (TableGroupServiceImpl)                               │
│  ┌─────────────┐  ┌──────────────────┐  ┌──────────────┐   │
│  │ 验证配置     │→│ 保存配置         │→│ 执行 DDL      │   │
│  │             │  │                  │  │              │   │
│  │             │  │ Convert.fieldMetadata │ ALTER TABLE │   │
│  │             │  │                  │  │ ADD COLUMN  │   │
│  └─────────────┘  └──────────────────┘  └──────────────┘   │
│                                              ↓              │
│  ┌─────────────┐  ┌──────────────────┐     │              │
│  │ 刷新元数据   │←│ 持久化更新       │←────┘              │
│  │ getMetaInfo │  │ editTableGroup   │                    │
│  └─────────────┘  └──────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

### 数据模型扩展

#### Convert 类（新增字段）

```java
// Convert.java
public class Convert {
    private String id;
    private String name;
    private String convertName;
    private String convertCode;
    private String args;
    private boolean isRoot;
    
    // ========== 新增 ==========
    private Field fieldMetadata;  // 自定义字段元数据
    // =========================
    
    @JsonIgnore
    private transient ParseResult parseResultCache;
}
```

#### Field 类（已有，无需修改）

```java
// Field.java - 已包含所有必要元数据
public class Field {
    private String name;          // 字段名
    private String typeName;      // 类型名 (VARCHAR, INT)
    private int type;             // 类型编码 (java.sql.Types)
    private boolean pk;           // 是否主键
    private long columnSize;      // 长度
    private int ratio;            // 精度
    private Boolean nullable;     // 是否允许为空
    private String comment;       // 注释
    private Boolean isSizeFixed;  // 长度是否固定
    private boolean autoincrement;// 是否自增
    private Integer srid;         // 空间参考系 (Geometry)
}
```

### 关键流程

#### 1. 前端数据收集

```javascript
// editFilterAndConvert.js
function saveCustomField() {
    var fieldMetadata = {
        name: $('#fieldName').val(),
        typeName: $('#fieldType').val(),
        type: getTypeCode($('#fieldType').val()),
        columnSize: $('#fieldSize').val(),
        ratio: $('#fieldScale').val(),
        nullable: $('#fieldNullable').is(':checked'),
        comment: $('#fieldComment').val()
    };
    
    // 添加到 convert 数组
    converts.push({
        id: generateId(),
        name: fieldMetadata.name,
        convertName: '固定值',
        convertCode: 'fixed_value',
        args: 'default',
        isRoot: true,
        fieldMetadata: fieldMetadata  // ← 新增
    });
    
    // 提交
    $("#convert").val(JSON.stringify(converts));
}
```

#### 2. 保存时执行 DDL

```java
// TableGroupServiceImpl.add()
public String add(Map<String, String> params) throws Exception {
    // 1. 验证配置
    TableGroup model = (TableGroup) tableGroupChecker.checkAddConfigModel(params);
    
    // 2. 保存配置
    String id = profileComponent.addTableGroup(model);
    
    // 3. 执行自定义字段 DDL
    List<Field> customFields = extractCustomFields(model);
    if (!customFields.isEmpty()) {
        try {
            executeCustomFieldDDL(model, customFields);
            refreshTableFieldsAfterDDL(model);
            profileComponent.editTableGroup(model);
        } catch (Exception e) {
            // DDL 失败，回滚配置
            profileComponent.removeTableGroup(id);
            throw e;
        }
    }
    
    // 4. 初始化
    model.initTableGroup(parserComponent, profileComponent, connectorFactory);
    return id;
}
```

#### 3. DDL 执行逻辑

```java
// TableGroupServiceImpl
private void executeCustomFieldDDL(TableGroup tableGroup, List<Field> customFields) {
    Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
    ConnectorInstance targetInstance = connectorFactory.connect(
        profileComponent.getConnector(mapping.getTargetConnectorId()).getConfig());
    
    for (Field field : customFields) {
        try {
            // 生成 ALTER TABLE ADD COLUMN
            String ddl = generateAddColumnDDL(tableGroup, field);
            
            // 执行 DDL
            Result result = connectorFactory.writerDDL(targetInstance, 
                new DDLConfig(ddl));
            
            if (result.hasError()) {
                if (isColumnAlreadyExistsError(result.error)) {
                    logger.warn("字段已存在，跳过：{}", field.getName());
                } else {
                    throw new RuntimeException("DDL 执行失败：" + result.error);
                }
            }
        } catch (Exception e) {
            logger.error("DDL 执行异常：{}", field.getName(), e);
            throw e;
        }
    }
}

private boolean isColumnAlreadyExistsError(String errorMessage) {
    String[] patterns = {
        "Duplicate column name",      // MySQL
        "column already exists",       // PostgreSQL
        "ORA-01430",                   // Oracle
        "There is already an object",  // SQL Server
        "already exists"               // 通用
    };
    
    for (String pattern : patterns) {
        if (errorMessage.toLowerCase().contains(pattern.toLowerCase())) {
            return true;
        }
    }
    return false;
}
```

#### 4. DDL 生成（复用现有）

```java
// 复用 MySQLTemplate.buildAddColumnSql() 等方法
private String generateAddColumnDDL(TableGroup tableGroup, Field field) {
    ConnectorService targetConnector = connectorFactory.getConnectorService(
        profileComponent.getConnector(
            profileComponent.getMapping(tableGroup.getMappingId())
                .getTargetConnectorId()).getConfig());
    
    if (targetConnector instanceof Database) {
        SqlTemplate sqlTemplate = ((Database) targetConnector).getSqlTemplate();
        return sqlTemplate.buildAddColumnSql(tableGroup.getTargetTable().getName(), field);
    }
    
    throw new UnsupportedOperationException("不支持的连接器类型");
}
```

#### 5. TableGroup 初始化增强

```java
// TableGroup.initCommand()
public void initCommand(Mapping mapping, ConnectorFactory connectorFactory) {
    // ... 现有逻辑 ...
    
    // 新增：处理转换配置中的自定义字段
    List<Convert> convert = this.getConvert();
    if (!CollectionUtils.isEmpty(convert)) {
        convert.forEach(c -> {
            Field fieldMetadata = c.getFieldMetadata();
            if (fieldMetadata != null) {
                boolean exists = tTable.getColumn().stream()
                    .anyMatch(f -> f.getName().equals(fieldMetadata.getName()));
                if (!exists) {
                    tTable.getColumn().add(fieldMetadata);
                }
            }
        });
    }
}
```

### 影响评估

#### 向后兼容性
- ✅ `Convert.fieldMetadata` 为可选字段，旧配置不受影响
- ✅ JSON 序列化自动处理 null 值
- ✅ 旧版本加载时 `fieldMetadata` 为 null，功能正常

#### 性能影响
- ✅ DDL 执行在保存时，不影响运行时同步性能
- ⚠️ 保存时间增加（DDL 执行时间），通常 < 1 秒

#### 风险点
| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| DDL 执行失败 | 配置保存失败 | 回滚配置，用户修正后重试 |
| 字段已存在 | 警告，继续 | 打印警告日志，不影响 |
| 类型转换错误 | DDL 语法错误 | 复用 SqlTemplate，已充分测试 |
| 权限不足 | DDL 执行失败 | 明确错误提示，用户授权 |
## 实施状态

### 已完成
- [x] 需求分析和方案设计
- [x] 技术可行性验证
- [x] 文档编写（ADR + 实施方案）
- [x] Convert.java 新增 fieldMetadata 字段
- [x] 前端 editFilterAndConvert.js 收集 fieldMetadata
- [x] editConvert.html 添加字段编辑对话框
- [x] TableGroupServiceImpl 新增 DDL 执行逻辑
- [x] TableGroup.initCommand() 处理自定义字段
- [x] 编译验证通过

### 待实施
- [ ] 测试验证

## 实施说明

### 实现细节

1. **DDL 执行位置**: 在 `TableGroupServiceImpl.add()` 和 `edit()` 方法中执行，失败时 add 场景会回滚配置，edit 场景仅记录日志。

2. **DDL 生成**: 复用 `SqlTemplate.buildAddColumnSql()` 方法生成 DDL 语句。

3. **字段已存在处理**: 通过 `isColumnAlreadyExistsError()` 方法判断多种数据库的错误模式，打印警告日志后继续执行。

4. **元数据刷新**: DDL 成功后调用 `refreshTableFieldsAfterDDL()` 重新获取目标表元数据并更新 TableGroup。

### 已完成
- [x] 需求分析和方案设计
- [x] 技术可行性验证
- [x] 文档编写（ADR + 实施方案）

### 待实施
- [ ] Convert.java 新增 fieldMetadata 字段
- [ ] 前端 editFilterAndConvert.js 收集 fieldMetadata
- [ ] TableGroupServiceImpl 新增 DDL 执行逻辑
- [ ] TableGroup.initCommand() 处理自定义字段
- [ ] 测试验证

## 决策日期
2026-02-27

## 决策者
DBSyncer 开发团队

## 参考文档
- `org.dbsyncer.sdk.model.Field` - 字段元数据定义
- `org.dbsyncer.parser.model.Convert` - 转换器配置
- `org.dbsyncer.sdk.connector.database.sql.SqlTemplate.buildAddColumnSql()` - DDL 生成
- `doc/ddl-config.md` - DDL 同步机制
