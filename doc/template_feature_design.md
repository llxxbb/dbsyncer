# DBSyncer 模板功能设计文档（修订版 v2.0）

## 1. 概述

### 1.1 背景
DBSyncer 是一款开源数据同步中间件，其模板功能用于处理数据转换和字段映射。原有实现存在安全性问题和功能局限，需要重构以支持更复杂的业务场景。

### 1.2 设计目标
- 提供安全的模板解析机制
- 支持递归调用和复杂转换链
- 保证数据正确性和功能稳定性
- 提供良好的错误处理和调试能力
- **消除运行时递归，采用线性执行模型**

---

## 2. 核心语法

### 2.1 F() 语法 - 字段引用
```
F(field_name) - 引用数据行中的字段值
示例：F(user_name) → "John"
```

### 2.2 C() 语法 - 转换器引用
```
C(code[:id]) - 引用指定的转换器
- code: 转换器代码（如 UUID, DEFAULT, UPPER 等）
- id: 可选的实例标识符（用于区分相同转换器的不同实例）
- 格式：C(UUID) 或 C(UUID:user_id)

示例：
- C(UUID) → "550e8400-e29b-41d4-a716-446655440000"
- C(UUID:user_id) → "550e8400-e29b-41d4-a716-446655440000"
```

### 2.3 混合表达式
```
支持在同一表达式中混合使用 F() 和 C()

示例：
- "F(first_name) F(last_name)"              → "JohnDoe"（无分隔符）
- "F(first_name)  F(last_name)"             → "John Doe"（两个空格）
- "ID: C(UUID) Name: F(name)"               → "ID: 550e8400-... Name: John"
- "C(TIMESTAMP) - F(event_type)"            → "2025-01-XX 10:00:00 - LOGIN"

说明：
- 表达式系统只提供简单的文本替换功能
- 不支持任何运算符或特殊符号（+、-、*、/、(、)、= 等）
- 空格和普通字符会被保留
```

---

## 3. 功能架构

### 3.1 解析器 (TemplateParser)
负责解析模板并生成执行计划

**主要功能：**
- 语法分析和验证
- 提取表达式中的所有 C() 引用
- 构建依赖图（Dependency Graph）
- 拓扑排序生成执行顺序
- 循环引用检测
- 执行计划缓存

**输出数据结构：**
```
ParseResult {
    List<String> executionOrder;           // 转换器执行顺序（拓扑排序结果）
    Map<String, Convert> refMap;           // 引用ID到转换器配置的映射
    String originalExpression;             // 原始表达式
}

Convert（转换器实例）{
    String id;           // 实例ID，前端自动生成，格式：{code}_{序号}，如：UUID_0, EXPRESSION_1
    String name;         // 目标字段名
    String convertCode;  // 转换器类型代码
    String convertName;  // 转换器类型名称（友好显示）
    String args;        // 转换参数
}

引用ID格式：code + ":" + id
示例："UUID:UUID_0", "EXPRESSION:EXPRESSION_1", "DEFAULT:DEFAULT_0"
```

### 3.2 执行器 (TemplateExecutor)
负责按照解析结果执行模板

**主要功能：**
- 按执行顺序线性执行转换器链
- 管理执行上下文（Context）
- 错误处理和替代值应用
- 替换表达式中的所有引用并返回最终结果

**执行上下文：**
```
Context {
    // 字段值（从数据行加载）
    "F:name" → "John"
    "F:status" → "ACTIVE"
    
    // 转换器执行结果
    "UUID:default" → "550e8400-..."
    "DEFAULT:status" → "ACTIVE"
    "EXPRESSION:full_name" → "John Doe"
}
```

### 3.3 缓存管理器 (TemplateCache)
管理解析结果的缓存

**主要功能：**
- 解析结果缓存（避免重复解析相同表达式）
- LRU 缓存淘汰策略
- 线程安全的缓存实现

---

## 4. 算法设计

> **说明**：以下算法使用伪代码描述，以提高可读性和便于审查。伪代码采用类 Python/JavaScript 语法，省略了具体语言细节（如类型声明、异常处理等），专注于核心逻辑。

### 4.1 解析算法（拓扑排序）

**输入：** 表达式字符串、转换器配置列表  
**输出：** ParseResult（执行顺序和引用映射）

**算法步骤：**

1. **提取 C() 引用**
   - 使用正则表达式匹配表达式中的所有 `C(code[:id])`
   - 为每个匹配生成唯一引用ID：`refId = code + ":" + (id != null ? id : "default")`

2. **构建依赖图**
   - 对每个提取的引用，查找对应的 Convert 配置
   - 如果 Convert 的 args 包含 `C()` 引用，则建立依赖关系
   - 依赖关系：当前转换器 → 依赖的转换器

3. **拓扑排序（DFS 实现）**
   ```
   function topologicalSort(refs, converts):
       executionOrder = []      // 执行顺序列表
       visited = Set()          // 已访问节点
       tempMark = Set()         // 临时标记（用于检测循环）
       
       function visit(refId):
           if refId in tempMark:
               throw CircularReferenceException
           if refId in visited:
               return
           
           tempMark.add(refId)
           
           // 获取当前转换器的依赖
           convert = findConvert(refId)
           if convert and convert.args 包含 C():
               deps = extractCReferences(convert.args)
               for dep in deps:
                   visit(dep)
           
           tempMark.remove(refId)
           visited.add(refId)
           executionOrder.add(refId)  // 后序遍历：依赖先加入
       
       // 从所有引用开始遍历
       for refId in refs:
           visit(refId)
       
       return executionOrder
   ```

4. **生成 ParseResult**
   - 执行顺序列表（executionOrder）
   - 引用到 Convert 的映射（refMap）
   - 原始表达式

**示例：**
```
表达式: "F(name) C(EXPRESSION:full_0)"
转换器配置（前端自动生成ID）:
  - full_0: code=EXPRESSION, name="full_name", args="Prefix C(UUID:uid_0) Suffix"
  - uid_0: code=UUID, name="user_id", args=""

依赖图:
  EXPRESSION:full_0 → UUID:uid_0

拓扑排序结果:
  executionOrder = ["UUID:uid_0", "EXPRESSION:full_0"]
```

### 4.2 执行算法（线性执行）

> **说明**：以下算法使用伪代码描述，专注于执行流程和上下文管理。

**输入：** ParseResult、数据行、转换器配置列表  
**输出：** 表达式执行结果

**算法步骤：**

1. **获取解析结果**
   - 生成缓存键（基于表达式和转换器配置）
   - 从缓存中获取 ParseResult，如果不存在则调用解析器生成

2. **初始化上下文**
   ```
   context = new HashMap()
   // 加载字段值到上下文
   for each (fieldName, value) in rowData:
       context.put("F:" + fieldName, value)
   ```

3. **按顺序执行转换器链**
   ```
   for refId in parseResult.executionOrder:
       convert = parseResult.refMap.get(refId)
       
       // 3.1 预处理 args：替换其中的所有 C() 和 F() 引用
       processedArgs = replaceAllReferences(convert.args, context)
       
       // 3.2 执行 Handler
       handler = ConvertEnum.getHandler(convert.convertCode)
       try:
           result = handler.handle(processedArgs, rowData.get(convert.name), rowData)
           context.put(refId, result)
       catch Exception:
           // 使用替代值
           fallback = convert.fallbackValue or ""
           context.put(refId, fallback)
   ```

4. **替换原始表达式**
   ```
   finalResult = replaceAllReferences(parseResult.originalExpression, context)
   return finalResult
   ```

**引用替换函数（伪代码）：**
```
function replaceAllReferences(template, context):
    result = template
    
    // 4.1 替换所有 C() 引用
    pattern = "C\\(([^:)]+)(?::([^)]+))?\\)"
    matcher = pattern.matcher(result)
    buffer = new StringBuffer()
    while matcher.find():
        refId = generateRefId(matcher.group(1), matcher.group(2))
        value = context.get(refId) or ""
        matcher.appendReplacement(buffer, quoteReplacement(value))
    matcher.appendTail(buffer)
    result = buffer.toString()
    
    // 4.2 替换所有 F() 引用
    pattern = "F\\(([^)]+)\\)"
    matcher = pattern.matcher(result)
    buffer = new StringBuffer()
    while matcher.find():
        fieldName = matcher.group(1)
        value = context.get("F:" + fieldName) or ""
        matcher.appendReplacement(buffer, quoteReplacement(value))
    matcher.appendTail(buffer)
    result = buffer.toString()
    
    return result
```

**完整执行示例（伪代码演示）：**
```
表达式: "ID: C(EXPRESSION:full_0) Name: F(name)"
数据行: {name: "John"}
转换器配置（前端自动生成ID）:
  - full_0: code=EXPRESSION, name="full_name", id="full_0", args="USER_ C(UUID:uid_0)"
  - uid_0: code=UUID, name="user_id", id="uid_0", args=""

执行过程:
1. 解析阶段
   - 提取 C(EXPRESSION:full_0)
   - 构建依赖图: EXPRESSION:full_0 → UUID:uid_0
   - 拓扑排序: [UUID:uid_0, EXPRESSION:full_0]

2. 执行阶段
   - 初始化上下文: {F:name: "John"}
   
   - 执行 UUID:uid_0
     * args = "" (无引用需要替换)
     * Handler 生成 UUID: "550e8400-..."
     * 上下文: {F:name: "John", UUID:uid_0: "550e8400-..."}
   
   - 执行 EXPRESSION:full_0
     * args = "USER_ C(UUID:uid_0)"
     * 替换 C(UUID:uid_0) → "USER_ 550e8400-..."
     * Handler 返回 "USER_ 550e8400-..."
     * 上下文: {F:name: "John", UUID:uid_0: "550e8400-...", EXPRESSION:full_0: "USER_ 550e8400-..."}
   
   - 替换原始表达式
     * "ID: C(EXPRESSION:full_0) Name: F(name)"
     * 替换 C(EXPRESSION:full_0) → "ID: USER_550e8400-... Name: F(name)"
     * 替换 F(name) → "ID: USER_550e8400-... Name: John"

3. 最终结果: "ID: USER_550e8400-... Name: John"
```

---

## 5. 安全机制

### 5.1 循环引用检测
- **检测时机：** 拓扑排序阶段（DFS 遍历依赖图时）
- **检测机制：** 使用临时标记集合（tempMark）检测回边
- **处理方式：** 发现循环时立即抛出异常，提供完整循环路径
- **错误信息示例：** `"Circular reference detected: EXPRESSION:A → UUID:default → EXPRESSION:A"`

### 5.2 递归深度限制
- **限制值：** 最大依赖链深度 10 层
- **检查时机：** 拓扑排序阶段
- **处理方式：** 超过限制时抛出异常

### 5.3 输入验证
- **语法验证：** 正则表达式匹配，只允许 `F()` 和 `C()` 语法
- **恶意代码过滤：** 不支持任何运算符或函数调用
- **参数长度限制：** 单参数最大长度 1000 字符

### 5.4 错误处理
- **转换器执行异常：** 捕获并记录，使用配置的替代值
- **引用不存在：** 替换为空字符串
- **字段不存在：** 替换为空字符串

---

## 6. 性能优化

### 6.1 缓存策略
- **解析结果缓存：** 相同表达式和转换器配置直接返回缓存结果
- **缓存键生成：** `expression + "|" + convertListHash`
- **LRU 淘汰：** 最大缓存 1000 条，超过时淘汰 50%
- **线程安全：** 使用 ConcurrentHashMap

### 6.2 执行优化
- **线性执行：** 无递归调用，循环遍历执行顺序
- **上下文复用：** 中间结果存入上下文，避免重复计算
- **一次性替换：** 使用正则表达式批量替换所有引用

---

## 7. 前端集成

### 7.1 现有界面兼容性
当前 `editConvert.html` 已支持表达式输入：
- 当 `argNum == -1` 时显示表达式输入框
- 支持用户输入包含 `F()` 和 `C()` 的表达式

**无需修改前端界面**，现有界面已满足需求。

### 7.2 配置示例
用户在参数输入框中可以输入：
```
F(first_name)  F(last_name)              # 两个空格分隔
C(UUID:user_id)
Prefix C(TIMESTAMP) F(name) Suffix
```

### 7.3 错误反馈（可选增强）
- 实时语法检查（前端 JavaScript 验证）
- 循环引用提示
- 执行错误详情展示

---

## 8. 测试策略

### 8.1 单元测试
- **语法解析测试：** 验证各种表达式格式正确解析
- **拓扑排序测试：** 验证依赖图正确排序
- **循环检测测试：** 验证循环引用被正确检测
- **引用替换测试：** 验证多种引用组合正确替换
- **错误处理测试：** 验证异常情况正确处理

### 8.2 集成测试
- **端到端表达式执行：** 完整流程测试
- **复杂依赖链测试：** 多层嵌套转换器测试
- **性能压力测试：** 大量表达式并发执行
- **边界条件测试：** 空值、null、特殊字符

---

## 9. 部署和运维

### 9.1 监控指标
- 解析成功率
- 执行耗时统计（P50, P95, P99）
- 缓存命中率
- 循环引用检测次数

### 9.2 日志记录
- 解析过程日志（DEBUG 级别）
- 执行结果日志（TRACE 级别）
- 异常处理日志（ERROR 级别）

---

## 10. 与旧版本差异

| 特性 | 旧版本 | 新版本（v2.0） |
|------|--------|---------------|
| 执行模型 | 运行时递归 | 线性遍历 |
| 依赖解析 | 无 | 拓扑排序 |
| 循环检测 | 无 | DFS 检测 |
| 缓存 | 无 | 解析结果缓存 |
| 上下文 | 无 | 统一上下文管理 |
| 性能 | 递归开销大 | 线性执行高效 |

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|----------|
| 2.0 | 2026-02-04 | 重构为线性执行模型，引入拓扑排序，明确解析和执行职责分离 |
| 1.0 | 2026-02-04 | 初始版本，运行时递归执行 |
