# DBSyncer 模板功能实施文档

## 1. 实施计划

### 1.1 阶段划分
- **阶段1**：核心解析器开发（2周）
- **阶段2**：执行器开发（1周）  
- **阶段3**：安全机制实现（1周）
- **阶段4**：测试和集成（1周）
- **阶段5**：文档和部署（1周）

### 1.2 依赖关系
- 需要 Convert 和 ConvertEnum 类
- 需要 Handler 接口及其实现
- 需要现有 ExpressionUtil 的基础支持

## 2. 代码实现

### 2.1 核心类设计

#### 2.1.1 ParseResult.java
```java
package org.dbsyncer.parser.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表达式解析结果
 */
public class ParseResult {
    /**
     * 执行顺序列表
     */
    private List<String> executionOrder = new ArrayList<>();
    
    /**
     * 父节点索引列表（用于参数传递）
     */
    private List<Integer> parentIndices = new ArrayList<>();
    
    /**
     * 引用到转换器配置的映射
     */
    private Map<String, Convert> referenceMap = new HashMap<>();
    
    /**
     * 表达式模板（用于执行时替换）
     */
    private String expressionTemplate;
    
    // getters and setters
    public List<String> getExecutionOrder() {
        return executionOrder;
    }
    
    public void setExecutionOrder(List<String> executionOrder) {
        this.executionOrder = executionOrder;
    }
    
    public List<Integer> getParentIndices() {
        return parentIndices;
    }
    
    public void setParentIndices(List<Integer> parentIndices) {
        this.parentIndices = parentIndices;
    }
    
    public Map<String, Convert> getReferenceMap() {
        return referenceMap;
    }
    
    public void setReferenceMap(Map<String, Convert> referenceMap) {
        this.referenceMap = referenceMap;
    }
    
    public String getExpressionTemplate() {
        return expressionTemplate;
    }
    
    public void setExpressionTemplate(String expressionTemplate) {
        this.expressionTemplate = expressionTemplate;
    }
}
```

#### 2.1.2 TemplateParser.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板解析器
 */
public class TemplateParser {
    
    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final Pattern F_PATTERN = Pattern.compile("F\\(([^)]+)\\)");
    
    // 最大递归深度限制
    private static final int MAX_RECURSION_DEPTH = 10;
    
    /**
     * 解析模板并生成执行计划
     */
    public ParseResult parseTemplate(String template, List<Convert> converts) {
        if (StringUtil.isBlank(expression)) {
            return new ParseResult();
        }
        
        ParseResult result = new ParseResult();
        result.setExpressionTemplate(expression);
        
        // 使用线程本地变量存储解析栈
        Deque<String> parsingStack = getThreadLocalParsingStack();
        
        // 解析 C() 引用
        parseCReferences(expression, converts, result, parsingStack);
        
        return result;
    }
    
    private void parseCReferences(String template, List<Convert> converts, 
                                 ParseResult result, Deque<String> parsingStack) {
        Matcher matcher = C_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String code = matcher.group(1);
            String id = matcher.group(2);
            String refId = code + (id != null ? ":" + id : ":default");
            
            // 检查循环引用
            if (parsingStack.contains(refId)) {
                StringBuilder circularPath = new StringBuilder();
                for (String item : parsingStack) {
                    circularPath.append(item).append(" -> ");
                }
                circularPath.append(refId);
                throw new ParserException("Circular reference detected: " + circularPath.toString());
            }
            
            // 检查递归深度
            if (parsingStack.size() >= MAX_RECURSION_DEPTH) {
                throw new ParserException("Maximum recursion depth exceeded: " + MAX_RECURSION_DEPTH);
            }
            
            // 查找对应的转换器配置
            Convert referencedConvert = findConvertByCodeAndId(converts, code, id);
            if (referencedConvert != null) {
                // 记录引用映射
                result.getReferenceMap().put(refId, referencedConvert);
                
                // 如果转换器参数包含模板，递归解析
                String args = referencedConvert.getArgs();
                if (args != null && (args.contains("C(") || args.contains("F("))) {
                    // 递归解析前将当前引用压入栈
                    parsingStack.push(refId);
                    
                    try {
                        ParseResult subResult = parseTemplate(args, converts);
                        
                        // 合并子解析结果（被依赖的先执行）
                        result.getExecutionOrder().addAll(subResult.getExecutionOrder());
                        result.getParentIndices().addAll(subResult.getParentIndices());
                        
                        // 合并引用映射
                        result.getReferenceMap().putAll(subResult.getReferenceMap());
                    } finally {
                        // 解析完成后弹出栈
                        parsingStack.pop();
                    }
                }
            }
            
            // 添加当前引用到最后
            if (!result.getExecutionOrder().contains(refId)) {
                result.getExecutionOrder().add(refId);
                result.getParentIndices().add(-1); // 根节点，无父节点
            }
        }
    }
    
    /**
     * 根据转换器代码和ID查找转换器配置
     */
    private Convert findConvertByCodeAndId(List<Convert> converts, String code, String id) {
        if (converts == null || code == null) {
            return null;
        }
        
        for (Convert convert : converts) {
            if (code.equals(convert.getConvertCode())) {
                // 如果指定了ID，需要匹配ID
                if (id != null) {
                    // 在DBSyncer中，ID通常通过name字段表示
                    if (id.equals(convert.getName())) {
                        return convert;
                    }
                } else {
                    // 没有指定ID的情况下，返回第一个匹配的
                    return convert;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取线程本地的解析栈
     */
    private Deque<String> getThreadLocalParsingStack() {
        return TemplateExecutionContext.getContext().getParsingStack();
    }
}
```

#### 2.1.3 TemplateExecutor.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;
import org.dbsyncer.parser.convert.Handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板执行器
 */
public class TemplateExecutor {
    
    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final Pattern F_PATTERN = Pattern.compile("F\\(([^)]+)\\)");
    
    /**
     * 执行模板
     */
    public Object execute(String template, Map<String, Object> rowData, 
                         List<Convert> converts) {
        if (StringUtil.isBlank(template)) {
            return null;
        }
        
        // 获取解析结果（使用缓存）
        String cacheKey = generateCacheKey(template, converts);
        ParseResult parseResult = TemplateCache.getInstance().getParseResult(cacheKey);
        
        if (parseResult == null) {
            TemplateParser parser = new TemplateParser();
            parseResult = parser.parseTemplate(template, converts);
            TemplateCache.getInstance().putParseResult(cacheKey, parseResult);
        }
        
        // 执行转换器链
        Map<String, Object> intermediateResults = new HashMap<>();
        
        for (int i = 0; i < parseResult.getExecutionOrder().size(); i++) {
            String refId = parseResult.getExecutionOrder().get(i);
            Convert convert = parseResult.getReferenceMap().get(refId);
            
            if (convert != null) {
                try {
                    Handler handler = ConvertEnum.getHandler(convert.getConvertCode());
                    Object result = handler.handle(convert.getArgs(), 
                                                 rowData.get(convert.getName()), rowData);
                    intermediateResults.put(refId, result);
                } catch (Exception e) {
                    // 使用转换器配置中的替代值（如果有的话）
                    // 这里可以扩展为专门的fallback字段
                    Object fallback = getFallbackValue(convert, e);
                    intermediateResults.put(refId, fallback);
                }
            }
        }
        
        // 替换模板中的所有引用
        return replaceAllReferences(parseResult.getExpressionTemplate(), 
                                  intermediateResults, rowData);
    }
    
    /**
     * 替换表达式中的所有引用
     */
    private Object replaceAllReferences(String expression, 
                                      Map<String, Object> intermediateResults,
                                      Map<String, Object> rowData) {
        String result = expression;
        
        // 先替换 C() 引用
        result = replaceCReferences(result, intermediateResults);
        
        // 再替换 F() 引用
        result = replaceFReferences(result, rowData);
        
        return result;
    }
    
    private String replaceCReferences(String expression, Map<String, Object> intermediateResults) {
        Matcher matcher = C_PATTERN.matcher(expression);
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
        
        return buffer.toString();
    }
    
    private String replaceFReferences(String expression, Map<String, Object> rowData) {
        Matcher matcher = F_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            Object fieldValue = rowData.get(fieldName);
            String valueStr = fieldValue != null ? fieldValue.toString() : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(valueStr));
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * 获取替代值
     */
    private Object getFallbackValue(Convert convert, Exception error) {
        // 这里可以根据配置或转换器类型返回合适的替代值
        // 可以扩展为专门的fallback字段或配置
        return ""; // 默认返回空字符串
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String expression, List<Convert> converts) {
        StringBuilder sb = new StringBuilder();
        sb.append(expression).append("|");
        
        if (converts != null) {
            for (Convert convert : converts) {
                sb.append(convert.getConvertCode())
                  .append(":")
                  .append(convert.getName())
                  .append(";");
            }
        }
        
        return sb.toString();
    }
}
```

#### 2.1.4 TemplateCache.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.ParseResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模板解析结果缓存
 */
public class TemplateCache {
    
    private static final TemplateCache INSTANCE = new TemplateCache();
    
    // 使用LRU缓存，限制缓存大小
    private final Map<String, ParseResult> cache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;
    
    private TemplateCache() {}
    
    public static TemplateCache getInstance() {
        return INSTANCE;
    }
    
    public ParseResult getParseResult(String key) {
        return cache.get(key);
    }
    
    public void putParseResult(String key, ParseResult result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            // 简单的缓存淘汰策略：达到最大大小时清空一半
            int size = cache.size();
            int removeCount = size / 2;
            int count = 0;
            
            for (String k : cache.keySet()) {
                if (count >= removeCount) break;
                cache.remove(k);
                count++;
            }
        }
        
        cache.put(key, result);
    }
    
    public void clear() {
        cache.clear();
    }
    
    public int size() {
        return cache.size();
    }
}
```

#### 2.1.5 TemplateExecutionContext.java
```java
package org.dbsyncer.parser.util;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 模板执行上下文（用于线程本地存储）
 */
public class TemplateExecutionContext {
    
    private static final ThreadLocal<TemplateExecutionContext> CONTEXT = 
        new ThreadLocal<TemplateExecutionContext>() {
            @Override
            protected TemplateExecutionContext initialValue() {
                return new TemplateExecutionContext();
            }
        };
    
    private final Deque<String> parsingStack = new ArrayDeque<>();
    
    public static TemplateExecutionContext getContext() {
        return CONTEXT.get();
    }
    
    public static void resetContext() {
        CONTEXT.remove();
    }
    
    public Deque<String> getParsingStack() {
        return parsingStack;
    }
}
```

### 2.2 修改现有类

#### 2.2.1 修改 ExpressionUtil.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * 模板工具类（重构版 - 支持递归和引用机制）
 *
 * @author DBSyncer
 * @version 2.0.0
 */
public abstract class TemplateUtil {

    /**
     * 渲染模板（支持递归引用机制）
     * 
     * 支持两种占位符：
     * - F(field_name) - 引用字段值
     * - C(code[:id]) - 引用转换器配置
     * 
     * 说明：
     * 1. 支持递归引用，通过C()形成转换器链
     * 2. 自动检测循环引用并报错
     * 3. 解析与执行分离，提升性能
     * 4. 转换器执行失败时使用替代值
     * 5. 模板功能只提供简单的文本替换功能，不支持任何运算符或特殊符号
     *    （包括但不限于：+、-、*、/、(、)、[、]、=、!=、<、>、&&、||、'、"等）
     * 
     * 示例：
     * - "Hello F(name)" → "Hello John" 
     * - "C(UUID:user_id) F(name)" → "uuid_value John"
     * - "F(first_name) F(last_name)" → "JohnDoe" (无分隔符)
     * - "F(first_name)   F(last_name)" → "John   Doe" (三个空格)
     * 
     * @param template 模板（如：F(field1) C(UUID:user_id)）
     * @param row 数据行
     * @param converts 转换器配置列表
     * @return 渲染结果
     */
    public static Object evaluate(String expression, Map<String, Object> row, List<Convert> converts) {
        if (StringUtil.isBlank(expression)) {
            return null;
        }

        ExpressionExecutor executor = new ExpressionExecutor();
        return executor.execute(expression, row, converts);
    }
}
```

## 3. 测试实现

### 3.1 单元测试

#### 3.1.1 TemplateParserTest.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TemplateParserTest {
    
    @Test
    public void testSimpleTemplate() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();
        
        // 简单模板不应抛出异常
        parser.parseTemplate("F(name)", converts);
        assertTrue(true); // 只要不抛异常就算通过
    }
    
    @Test(expected = org.dbsyncer.parser.ParserException.class)
    public void testCircularReference() {
        List<Convert> converts = new ArrayList<>();
        
        // 创建循环引用：A引用B，B引用A
        Convert convertA = new Convert();
        convertA.setName("field_a");
        convertA.setConvertCode(ConvertEnum.EXPRESSION.getCode());
        convertA.setArgs("C(B:test_b)"); // A引用B
        
        Convert convertB = new Convert();
        convertB.setName("field_b");
        convertB.setConvertCode(ConvertEnum.EXPRESSION.getCode());
        convertB.setArgs("C(A:test_a)"); // B引用A
        
        converts.add(convertA);
        converts.add(convertB);
        
        ExpressionParser parser = new ExpressionParser();
        parser.parseExpression("C(A:test_a)", converts);
    }
    
    @Test
    public void testNormalReferenceChain() {
        List<Convert> converts = new ArrayList<>();
        
        // 创建正常的引用链：A -> B
        Convert convertA = new Convert();
        convertA.setName("field_a");
        convertA.setConvertCode(ConvertEnum.EXPRESSION.getCode());
        convertA.setArgs("C(B:test_b) F(other_field)");
        
        Convert convertB = new Convert();
        convertB.setName("field_b");
        convertB.setConvertCode(ConvertEnum.UUID.getCode());
        convertB.setArgs("");
        
        converts.add(convertA);
        converts.add(convertB);
        
        ExpressionParser parser = new ExpressionParser();
        // 正常的引用链不应该抛出异常
        parser.parseExpression("C(A:test_a)", converts);
        assertTrue(true);
    }
}
```

#### 3.1.2 TemplateExecutorTest.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TemplateExecutorTest {
    
    @Test
    public void testSimpleFieldReference() {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("name", "John");
        
        TemplateExecutor executor = new TemplateExecutor();
        Object result = executor.execute("F(name)", rowData, new ArrayList<>());
        
        assertEquals("John", result);
    }
    
    @Test
    public void testConverterReference() {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("test_field", "value");
        
        List<Convert> converts = new ArrayList<>();
        Convert uuidConvert = new Convert();
        uuidConvert.setName("test_field");
        uuidConvert.setConvertCode(ConvertEnum.UUID.getCode());
        uuidConvert.setArgs("");
        converts.add(uuidConvert);
        
        TemplateExecutor executor = new TemplateExecutor();
        Object result = executor.execute("C(UUID:test_field)", rowData, converts);
        
        // UUID应该不为空且长度合适
        assertNotNull(result);
        assertTrue(result.toString().length() > 10);
    }
    
    @Test
    public void testMixedTemplate() {
        Map<String, Object> rowData = new HashMap<>();
        rowData.put("first_name", "John");
        rowData.put("last_name", "Doe");
        
        List<Convert> converts = new ArrayList<>();
        Convert uuidConvert = new Convert();
        uuidConvert.setName("first_name");
        uuidConvert.setConvertCode(ConvertEnum.UUID.getCode());
        uuidConvert.setArgs("");
        converts.add(uuidConvert);
        
        TemplateExecutor executor = new TemplateExecutor();
        Object result = executor.execute("F(first_name) ' ' F(last_name) '(' C(UUID:first_name) ')'", 
                                       rowData, converts);
        
        assertTrue(result.toString().contains("John"));
        assertTrue(result.toString().contains("Doe"));
        assertTrue(result.toString().contains("("));
        assertTrue(result.toString().contains(")"));
        // 注意：引号只是普通字符，不表示字符串界定符
    }
}
```

## 4. 集成步骤

### 4.1 替换现有实现
1. 备份原有的 TemplateUtil.java
2. 用新的实现替换
3. 确保向后兼容性

### 4.2 配置更新
1. 更新 Maven 依赖（如有需要）
2. 更新相关配置文件
3. 测试现有功能不受影响

### 4.3 部署验证
1. 在测试环境中部署
2. 运行完整的测试套件
3. 验证性能和功能

## 5. 验收标准

### 5.1 功能验收
- [ ] 支持 F() 字段引用
- [ ] 支持 C() 转换器引用  
- [ ] 检测并阻止循环引用
- [ ] 正确执行转换器链
- [ ] 提供错误处理和替代值

### 5.2 性能验收
- [ ] 解析与执行分离
- [ ] 适当的缓存机制
- [ ] 不显著降低系统性能
- [ ] 内存使用合理

### 5.3 安全验收
- [ ] 防止代码注入
- [ ] 循环引用检测
- [ ] 递归深度限制
- [ ] 输入验证

---
实施文档版本：1.0
创建日期：2026-02-04