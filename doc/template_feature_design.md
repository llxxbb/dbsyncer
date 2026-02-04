# DBSyncer 模板功能设计文档

## 1. 概述

### 1.1 背景
DBSyncer 是一款开源数据同步中间件，其模板功能用于处理数据转换和字段映射。原有实现存在安全性问题和功能局限，需要重构以支持更复杂的业务场景。

### 1.2 设计目标
- 提供安全的模板解析机制
- 支持递归调用和复杂转换链
- 保证数据正确性和功能稳定性
- 提供良好的错误处理和调试能力

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
示例：C(UUID:user_id) → "550e8400-e29b-41d4-a716-446655440000"
```

### 2.3 混合表达式
```
支持在同一表达式中混合使用 F() 和 C()
示例：F(first_name) ' ' F(last_name) '(' C(UUID:user_id) ')'
说明：表达式系统只提供简单的文本替换功能，不支持任何运算符或特殊符号
（包括但不限于：+、-、*、/、(、)、[、]、=、!=、<、>、&&、||、'、"等）
```

## 3. 功能架构

### 3.1 解析器 (TemplateParser)
负责解析模板并生成执行计划

#### 主要功能：
- 语法分析和验证
- 循环引用检测
- 执行顺序确定
- 执行计划缓存

#### 数据结构：
```java
class ParseResult {
    List<String> executionOrder;      // 执行顺序（反向依赖）
    List<Integer> parentIndices;      // 父节点索引列表
    Map<String, String> referenceMap; // 引用到转换器配置的映射
}
```

### 3.2 执行器 (TemplateExecutor)
负责按照解析结果执行模板

#### 主要功能：
- 按顺序执行转换器
- 错误处理和替代值应用
- 结果替换和返回

### 3.3 循环检测机制
- 使用解析栈检测循环引用
- 一旦发现循环立即抛出异常
- 提供详细的循环路径信息

## 4. 详细设计

### 4.1 解析算法

```java
class ExpressionParser {
    private Deque<String> parsingStack = new ArrayDeque<>();
    
    public ParseResult parseExpression(String expression, List<Convert> converts) {
        ParseResult result = new ParseResult();
        
        // 使用正则表达式查找所有 C() 引用
        Pattern pattern = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
        Matcher matcher = pattern.matcher(expression);
        
        int lastIndex = 0;
        while (matcher.find()) {
            // 检查循环引用
            String code = matcher.group(1);
            String id = matcher.group(2);
            String refId = code + (id != null ? ":" + id : ":default");
            
            if (detectCircularReference(refId)) {
                throw new CircularReferenceException(
                    "Circular reference detected: " + buildCircularPath(refId));
            }
            
            // 递归解析被引用的表达式
            Convert referencedConvert = findConvertByCodeAndId(converts, code, id);
            if (referencedConvert != null && referencedConvert.getArgs() != null) {
                // 递归解析引用的表达式
                pushReference(refId);
                ParseResult subResult = parseExpression(referencedConvert.getArgs(), converts);
                popReference();
                
                // 合并子解析结果
                result.executionOrder.addAll(subResult.executionOrder);
                result.parentIndices.addAll(subResult.parentIndices);
            }
            
            // 添加当前引用到最后
            result.executionOrder.add(refId);
            result.parentIndices.add(-1); // 根节点，无父节点
            
            // 记录引用映射
            result.referenceMap.put(refId, referencedConvert);
        }
        
        return result;
    }
    
    private boolean detectCircularReference(String refId) {
        return parsingStack.contains(refId);
    }
    
    private void pushReference(String refId) {
        parsingStack.push(refId);
    }
    
    private void popReference() {
        parsingStack.pop();
    }
    
    private String buildCircularPath(String refId) {
        StringBuilder path = new StringBuilder();
        for (String item : parsingStack) {
            path.append(item).append(" -> ");
        }
        path.append(refId);
        return path.toString();
    }
}
```

### 4.2 执行算法

```java
class ExpressionExecutor {
    private Map<String, ParseResult> planCache = new ConcurrentHashMap<>();
    
    public Object execute(String expression, Map<String, Object> rowData, 
                         List<Convert> converts) {
        // 获取解析结果（缓存）
        String cacheKey = generateCacheKey(expression, converts);
        ParseResult parseResult = planCache.computeIfAbsent(cacheKey, 
            k -> new ExpressionParser().parseExpression(expression, converts));
        
        // 执行转换器链
        Map<String, Object> intermediateResults = new HashMap<>();
        
        for (int i = 0; i < parseResult.executionOrder.size(); i++) {
            String refId = parseResult.executionOrder.get(i);
            Convert convert = parseResult.referenceMap.get(refId);
            
            try {
                Object result = executeConvert(convert, rowData, intermediateResults);
                intermediateResults.put(refId, result);
            } catch (Exception e) {
                // 使用前端指定的替代值
                Object fallback = convert != null ? 
                    convert.getArgs() : null; // 可以扩展为专门的fallback字段
                intermediateResults.put(refId, fallback);
            }
        }
        
        // 替换表达式中的所有引用
        return replaceReferences(expression, intermediateResults);
    }
    
    private Object executeConvert(Convert convert, Map<String, Object> rowData, 
                                Map<String, Object> intermediateResults) {
        if (convert == null) return null;
        
        // 获取转换器并执行
        Handler handler = ConvertEnum.getHandler(convert.getConvertCode());
        return handler.handle(convert.getArgs(), rowData.get(convert.getName()), rowData);
    }
    
    private Object replaceReferences(String expression, 
                                   Map<String, Object> intermediateResults) {
        String result = expression;
        
        // 替换 C() 引用
        Pattern pattern = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
        Matcher matcher = pattern.matcher(result);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String code = matcher.group(1);
            String id = matcher.group(2);
            String refId = code + (id != null ? ":" + id : ":default");
            
            Object replacement = intermediateResults.get(refId);
            String replacementStr = replacement != null ? replacement.toString() : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacementStr));
        }
        matcher.appendTail(buffer);
        
        result = buffer.toString();
        
        // 替换 F() 引用
        pattern = Pattern.compile("F\\(([^)]+)\\)");
        matcher = pattern.matcher(result);
        buffer = new StringBuffer();
        
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            Object fieldValue = intermediateResults.get("F:" + fieldName);
            if (fieldValue == null) {
                fieldValue = rowData.get(fieldName);
            }
            String valueStr = fieldValue != null ? fieldValue.toString() : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(valueStr));
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
}
```

## 5. 安全机制

### 5.1 循环检测
- 解析时使用栈检测循环引用
- 限制最大递归深度
- 详细的错误信息提示

### 5.2 输入验证
- 严格的语法验证
- 恶意代码过滤
- 参数长度限制

### 5.3 错误处理
- 转换器执行异常捕获
- 前端可配置的替代值
- 详细的错误日志记录

## 6. 性能优化

### 6.1 缓存策略
- 解析结果缓存
- LRU 缓存淘汰策略
- 线程安全的缓存实现

### 6.2 执行优化
- 顺序执行，避免并行开销
- 避免不必要的结果缓存
- 高效的字符串替换算法

## 7. 前端集成

### 7.1 配置接口
- 转换器配置界面
- 替代值设置选项
- 表达式调试工具

### 7.2 错误反馈
- 实时语法检查
- 循环引用提示
- 执行错误详情

## 8. 测试策略

### 8.1 单元测试
- 语法解析测试
- 循环检测测试
- 错误处理测试

### 8.2 集成测试
- 端到端表达式执行
- 性能压力测试
- 安全性测试

## 9. 部署和运维

### 9.1 监控指标
- 解析成功率
- 执行耗时统计
- 缓存命中率

### 9.2 日志记录
- 解析过程日志
- 执行结果日志
- 异常处理日志

---
文档版本：1.0
创建日期：2026-02-04