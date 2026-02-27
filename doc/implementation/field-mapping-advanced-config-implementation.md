# 字段映射高级配置 - 实施方案（伪代码版）

## 概述
**目标**: 支持自定义字段元数据编辑，保存时执行 DDL 创建字段  
**原则**: 保存时执行 DDL、异常简化处理、向后兼容

---

## 实施清单

### 阶段一：数据模型扩展（1 天）

#### 1.1 Convert.java 新增字段

```java
// File: dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Convert.java

public class Convert {
    // ... 现有字段 ...
    
    /**
     * 字段元数据（自定义字段时填充）
     * 包含类型、长度、精度、是否允许为空、注释等
     */
    private Field fieldMetadata;
    
    // Getter/Setter
    public Field getFieldMetadata() { return fieldMetadata; }
    public void setFieldMetadata(Field fieldMetadata) { this.fieldMetadata = fieldMetadata; }
}
```

**验证**:
```bash
# 测试序列化
mvn test -Dtest=ConvertTest

# 验证 JSON 包含 fieldMetadata
curl -X POST /tableGroup/add -d '{"convert":[{"name":"custom","fieldMetadata":{...}}]}'
```

---

### 阶段二：前端修改（1 天）

#### 2.1 字段编辑对话框 HTML

```html
<!-- File: editConvert.html - 在</body>前添加 -->

<div id="fieldEditDialog" class="modal fade">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <h4>编辑字段信息</h4>
            </div>
            <div class="modal-body">
                <form id="fieldEditForm">
                    <input type="text" id="fieldName" placeholder="字段名" required />
                    <select id="fieldType">
                        <option>VARCHAR</option>
                        <option>INT</option>
                        <option>DECIMAL</option>
                        <!-- 更多类型 -->
                    </select>
                    <input type="number" id="fieldSize" placeholder="长度" />
                    <input type="number" id="fieldScale" placeholder="精度" />
                    <input type="checkbox" id="fieldNullable" checked /> 允许为空
                    <input type="text" id="fieldComment" placeholder="注释" />
                </form>
            </div>
            <div class="modal-footer">
                <button data-dismiss="modal">取消</button>
                <button id="confirmFieldEdit">确定</button>
            </div>
        </div>
    </div>
</div>
```

#### 2.2 JavaScript 数据收集

```javascript
// File: editFilterAndConvert.js

// 打开对话框
function openFieldEditDialog(sourceField) {
    $('#fieldEditDialog').modal('show');
    
    if (sourceField) {
        // 自动填充源字段信息
        $('#fieldName').val(sourceField.name + '_copy');
        $('#fieldType').val(sourceField.typeName);
        $('#fieldSize').val(sourceField.columnSize);
        $('#fieldScale').val(sourceField.ratio);
        $('#fieldNullable').prop('checked', sourceField.nullable);
    }
}

// 保存自定义字段
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
    
    // 添加到 converts 数组
    converts.push({
        id: generateId(),
        name: fieldMetadata.name,
        convertName: '固定值',
        convertCode: 'fixed_value',
        args: 'default',
        isRoot: true,
        fieldMetadata: fieldMetadata  // ← 新增
    });
    
    // 更新表单
    $("#convert").val(JSON.stringify(converts));
    $('#fieldEditDialog').modal('hide');
}

// 绑定事件
$('#confirmFieldEdit').click(saveCustomField);
$('#addTargetFieldBtn').click(function() {
    openFieldEditDialog(null);
});
```

**验证**:
```javascript
// 浏览器控制台测试
var formData = JSON.parse($("#convert").val());
console.assert(formData[0].fieldMetadata !== undefined);
```

---

### 阶段三：后端核心逻辑（2 天）

#### 3.1 TableGroupServiceImpl 新增方法

```java
// File: TableGroupServiceImpl.java

@Service
public class TableGroupServiceImpl implements TableGroupService {
    
    @Override
    public String add(Map<String, String> params) throws Exception {
        // ... 现有逻辑 ...
        
        for (int i = 0; i < tableSize; i++) {
            try {
                // 1. 验证
                TableGroup model = (TableGroup) tableGroupChecker.checkAddConfigModel(params);
                
                // 2. 保存
                String id = profileComponent.addTableGroup(model);
                
                // 3. 执行 DDL ← 新增
                List<Field> customFields = extractCustomFields(model);
                if (!customFields.isEmpty()) {
                    try {
                        executeCustomFieldDDL(model, customFields);
                        refreshTableFieldsAfterDDL(model);
                        profileComponent.editTableGroup(model);
                    } catch (Exception e) {
                        // DDL 失败，回滚
                        profileComponent.removeTableGroup(id);
                        throw e;
                    }
                }
                
                // 4. 初始化
                model.initTableGroup(parserComponent, profileComponent, connectorFactory);
                list.add(id);
                
            } catch (PrimaryKeyRequiredException e) {
                // ... 现有异常处理 ...
            }
        }
        
        // ... 后续逻辑 ...
    }
    
    @Override
    public String edit(Map<String, String> params) throws Exception {
        // ... 现有逻辑 ...
        
        // 1. 验证
        TableGroup model = (TableGroup) tableGroupChecker.checkEditConfigModel(params);
        
        // 2. 保存
        profileComponent.editTableGroup(model);
        
        // 3. 执行 DDL ← 新增
        List<Field> customFields = extractCustomFields(model);
        if (!customFields.isEmpty()) {
            try {
                executeCustomFieldDDL(model, customFields);
                refreshTableFieldsAfterDDL(model);
                profileComponent.editTableGroup(model);
            } catch (Exception e) {
                logger.error("DDL 执行失败，但配置已保存", e);
                // 注意：edit 场景不回滚，因为配置可能已使用
            }
        }
        
        // 4. 初始化
        model.initTableGroup(parserComponent, profileComponent, connectorFactory);
        return id;
    }
    
    /**
     * 提取自定义字段（有 fieldMetadata 的 convert）
     */
    private List<Field> extractCustomFields(TableGroup tableGroup) {
        List<Field> customFields = new ArrayList<>();
        
        for (Convert convert : tableGroup.getConvert()) {
            Field metadata = convert.getFieldMetadata();
            if (metadata != null) {
                customFields.add(metadata);
            }
        }
        
        return customFields;
    }
    
    /**
     * 执行自定义字段 DDL
     */
    private void executeCustomFieldDDL(TableGroup tableGroup, List<Field> customFields) 
            throws Exception {
        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        ConnectorInstance targetInstance = connectorFactory.connect(
            profileComponent.getConnector(mapping.getTargetConnectorId()).getConfig());
        
        for (Field field : customFields) {
            try {
                // 生成 DDL
                String ddl = generateAddColumnDDL(tableGroup, field);
                
                // 执行
                Result result = connectorFactory.writerDDL(targetInstance, 
                    new DDLConfig(ddl));
                
                if (result.hasError()) {
                    if (isColumnAlreadyExistsError(result.error)) {
                        logger.warn("字段已存在，跳过：{}", field.getName());
                    } else {
                        throw new RuntimeException("DDL 执行失败：" + result.error);
                    }
                } else {
                    logger.info("DDL 执行成功：{}.{}", 
                        tableGroup.getTargetTable().getName(), field.getName());
                }
            } catch (Exception e) {
                logger.error("DDL 执行异常：{}", field.getName(), e);
                throw e;
            }
        }
    }
    
    /**
     * 生成 ADD COLUMN DDL（复用 SqlTemplate）
     */
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
    
    /**
     * 判断是否为"字段已存在"错误
     */
    private boolean isColumnAlreadyExistsError(String errorMessage) {
        String[] patterns = {
            "Duplicate column name",
            "column already exists",
            "ORA-01430",
            "There is already an object",
            "列已存在",
            "already exists"
        };
        
        for (String pattern : patterns) {
            if (errorMessage.toLowerCase().contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * DDL 成功后刷新元数据
     */
    private void refreshTableFieldsAfterDDL(TableGroup tableGroup) throws Exception {
        Mapping mapping = profileComponent.getMapping(tableGroup.getMappingId());
        
        // 重新获取目标表元数据
        MetaInfo targetMetaInfo = parserComponent.getMetaInfo(
            mapping.getTargetConnectorId(),
            tableGroup.getTargetTable().getName());
        
        // 更新 TableGroup
        tableGroup.getTargetTable().setColumn(targetMetaInfo.getColumn());
        
        // 重新初始化 command
        tableGroup.initCommand(mapping, connectorFactory);
    }
}
```

---

### 阶段四：TableGroup 初始化增强（0.5 天）

#### 4.1 TableGroup.initCommand() 处理自定义字段

```java
// File: TableGroup.java

public void initCommand(Mapping mapping, ConnectorFactory connectorFactory) {
    // ... 现有逻辑 ...
    
    // 现有：处理 fieldMapping
    fieldMapping.forEach(m -> {
        if (null != m.getSource()) {
            sTable.getColumn().add(m.getSource());
        }
        if (null != m.getTarget()) {
            tTable.getColumn().add(m.getTarget());
        }
    });
    
    // 新增：处理 convert 中的自定义字段 ← 新增
    List<Convert> convert = this.getConvert();
    if (!CollectionUtils.isEmpty(convert)) {
        convert.forEach(c -> {
            Field fieldMetadata = c.getFieldMetadata();
            if (fieldMetadata != null) {
                // 检查是否已存在
                boolean exists = tTable.getColumn().stream()
                    .anyMatch(f -> f.getName().equals(fieldMetadata.getName()));
                if (!exists) {
                    tTable.getColumn().add(fieldMetadata);
                }
            }
        });
    }
    
    // ... 后续逻辑 ...
}
```

---

### 阶段五：DDL 刷新逻辑（0.5 天）

#### 5.1 DDLParserImpl.refreshFieldMappings() 处理自定义字段

```java
// File: DDLParserImpl.java

public void refreshFieldMappings(TableGroup tableGroup, DDLConfig targetDDLConfig) {
    // ... 现有逻辑：处理源表 DDL 变更 ...
    
    // 新增：处理转换配置中的自定义字段映射 ← 新增
    List<Convert> convert = tableGroup.getConvert();
    if (!CollectionUtils.isEmpty(convert)) {
        for (Convert c : convert) {
            Field fieldMetadata = c.getFieldMetadata();
            if (fieldMetadata != null) {
                // 检查是否已存在映射
                boolean exists = tableGroup.getFieldMapping().stream()
                    .anyMatch(fm -> fm.getTarget() != null && 
                                   fm.getTarget().getName().equals(fieldMetadata.getName()));
                if (!exists) {
                    // 添加映射（source 为 null）
                    tableGroup.getFieldMapping().add(
                        new FieldMapping(null, fieldMetadata));
                }
            }
        }
    }
}
```

---

### 阶段六：测试验证（1 天）

#### 6.1 单元测试

```java
// ConvertTest.java
@Test
public void testFieldMetadataSerialization() throws Exception {
    Convert convert = new Convert();
    convert.setName("custom_field");
    convert.setFieldMetadata(new Field("custom", "VARCHAR", 12, false, 255, 0));
    
    String json = JsonUtil.objToJson(convert);
    assertTrue(json.contains("fieldMetadata"));
    
    Convert deserialized = JsonUtil.jsonToObj(json, Convert.class);
    assertNotNull(deserialized.getFieldMetadata());
    assertEquals("custom", deserialized.getFieldMetadata().getName());
}
```

#### 6.2 集成测试

```java
// TableGroupServiceTest.java
@Test
public void testAddWithCustomField() throws Exception {
    Map<String, String> params = new HashMap<>();
    params.put("mappingId", "test-mapping");
    params.put("sourceTable", "source_tbl");
    params.put("targetTable", "target_tbl");
    
    // 添加自定义字段配置
    Field customField = new Field("custom_col", "VARCHAR", 12, false, 255, 0);
    Convert convert = new Convert();
    convert.setName("custom_col");
    convert.setFieldMetadata(customField);
    params.put("convert", JsonUtil.objToJson(Arrays.asList(convert)));
    
    // 执行
    String id = tableGroupService.add(params);
    
    // 验证
    TableGroup tableGroup = tableGroupService.getTableGroup(id);
    assertNotNull(tableGroup);
    assertTrue(tableGroup.getTargetTable().getColumn().stream()
        .anyMatch(f -> f.getName().equals("custom_col")));
}
```

#### 6.3 端到端测试

```bash
# 1. 创建映射关系
curl -X POST /mapping/add -d '{"name":"test","sourceConnectorId":"mysql","targetConnectorId":"kafka"}'

# 2. 添加表组（含自定义字段）
curl -X POST /tableGroup/add \
  -d 'mappingId=1' \
  -d 'sourceTable=users' \
  -d 'targetTable=user_events' \
  -d 'convert=[{"name":"custom","convertCode":"fixed_value","fieldMetadata":{"name":"custom","typeName":"VARCHAR","columnSize":255}}]'

# 3. 验证 DDL 执行
# 检查 Kafka topic schema 是否包含 custom 字段

# 4. 验证同步
# 插入源表数据，检查目标是否包含 custom 字段
```

---

## 验证清单

### 开发阶段
- [ ] Convert.fieldMetadata 字段已添加
- [ ] 前端可收集 fieldMetadata 并提交
- [ ] 后端可接收并反序列化 fieldMetadata
- [ ] DDL 执行逻辑已实现
- [ ] 字段已存在异常已处理
- [ ] TableGroup.initCommand() 已增强
- [ ] DDLParserImpl.refreshFieldMappings() 已增强

### 测试阶段
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 端到端测试通过
- [ ] 向后兼容性验证通过
- [ ] 异常场景测试通过

### 上线阶段
- [ ] 代码审查通过
- [ ] 性能测试通过
- [ ] 文档已更新
- [ ] 用户手册已更新

---

## 时间估算

| 阶段 | 任务 | 时间 | 负责人 |
|------|------|------|--------|
| 阶段一 | Convert.java 扩展 | 0.5 天 | 后端 |
| 阶段二 | 前端修改 | 1 天 | 前端 |
| 阶段三 | 后端核心逻辑 | 2 天 | 后端 |
| 阶段四 | TableGroup 增强 | 0.5 天 | 后端 |
| 阶段五 | DDL 刷新逻辑 | 0.5 天 | 后端 |
| 阶段六 | 测试验证 | 1 天 | 测试 |
| **合计** | | **5.5 天** | |

---

## 回滚方案

### 代码回滚
```bash
# 1. 恢复 Convert.java
git checkout HEAD -- dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Convert.java

# 2. 恢复 TableGroupServiceImpl.java
git checkout HEAD -- dbsyncer-biz/src/main/java/org/dbsyncer/biz/impl/TableGroupServiceImpl.java

# 3. 恢复前端文件
git checkout HEAD -- dbsyncer-web/src/main/resources/public/mapping/editConvert.html
git checkout HEAD -- dbsyncer-web/src/main/resources/static/js/mapping/editFilterAndConvert.js
```

### 数据回滚
- 旧配置自动兼容（fieldMetadata 为 null）
- 无需特殊数据迁移

---

## 监控指标

### 性能指标
- DDL 执行时间：< 1 秒/字段
- 配置保存时间增加：< 2 秒
- 同步性能影响：无

### 质量指标
- 单元测试覆盖率：> 80%
- 集成测试通过率：100%
- 生产异常率：< 0.1%

---

## 附录：关键文件清单

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `Convert.java` | 新增字段 | fieldMetadata |
| `TableGroupServiceImpl.java` | 新增方法 | executeCustomFieldDDL() 等 |
| `TableGroup.java` | 增强方法 | initCommand() |
| `DDLParserImpl.java` | 增强方法 | refreshFieldMappings() |
| `editConvert.html` | 新增组件 | 字段编辑对话框 |
| `editFilterAndConvert.js` | 增强逻辑 | 收集 fieldMetadata |
