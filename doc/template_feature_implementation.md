# DBSyncer 模板功能实施文档（修订版 v2.0）

## 1. 实施计划

### 1.1 阶段划分
- **阶段1**：核心解析器开发（1周）
  - TemplateParser 实现
  - 拓扑排序算法
  - 循环检测机制
  
- **阶段2**：执行器开发（1周）
  - TemplateExecutor 实现
  - 上下文管理
  - 引用替换逻辑
  
- **阶段3**：缓存和优化（3天）
  - TemplateCache 实现
  - 性能优化
  
- **阶段4**：测试和清理（3天）
  - 单元测试
  - 删除过时代码
  - 集成测试
  
- **阶段5**：部署（1天）

**总计：约 3 周**

### 1.2 依赖关系
- 需要 Convert 和 ConvertEnum 类（已有）
- 需要 Handler 接口及其实现（已有）
- **删除旧 ExpressionUtil 依赖**

---

## 2. 代码实现

### 2.1 新增核心类

#### 2.1.1 ParseResult.java
```java
package org.dbsyncer.parser.model;

import java.util.*;

/**
 * 模板解析结果
 */
public class ParseResult {
    private List<String> executionOrder = new ArrayList<>();
    private Map<String, Convert> referenceMap = new HashMap<>();
    private String originalExpression;
    
    // Getters and Setters
}
```

#### 2.1.2 Convert.java（修改）
```java
package org.dbsyncer.parser.model;

import org.dbsyncer.parser.enums.ConvertEnum;

/**
 * 字段转换（增加 id 字段）
 */
public class Convert {

    /**
     * 转换器实例ID（前端自动生成）
     * 格式：{code}_{序号}，如：UUID_0, UUID_1, EXPRESSION_0
     */
    private String id;

    /**
     * 字段名称（目标字段）
     */
    private String name;

    /**
     * 转换名称
     * @see ConvertEnum
     */
    private String convertName;

    /**
     * 转换方式
     *
     * @see ConvertEnum
     */
    private String convertCode;

    /**
     * 转换参数
     *
     * @see ConvertEnum
     */
    private String args;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConvertName() {
        return convertName;
    }

    public void setConvertName(String convertName) {
        this.convertName = convertName;
    }

    public String getConvertCode() {
        return convertCode;
    }

    public void setConvertCode(String convertCode) {
        this.convertCode = convertCode;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
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
 * 模板解析器 - 使用拓扑排序生成执行顺序
 */
public class TemplateParser {
    
    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final int MAX_DEPENDENCY_DEPTH = 10;
    
    /**
     * 解析模板并生成执行计划
     */
    public ParseResult parseTemplate(String template, List<Convert> converts) {
        if (StringUtil.isBlank(template)) {
            return new ParseResult();
        }
        
        ParseResult result = new ParseResult();
        result.setOriginalExpression(template);
        
        // 提取所有 C() 引用
        Set<String> refs = extractReferences(template);
        
        // 构建引用到 Convert 的映射
        Map<String, Convert> refMap = buildReferenceMap(refs, converts);
        result.setReferenceMap(refMap);
        
        // 拓扑排序生成执行顺序
        List<String> executionOrder = topologicalSort(refs, refMap, converts);
        result.setExecutionOrder(executionOrder);
        
        return result;
    }
    
    /**
     * 提取表达式中的所有 C() 引用
     */
    private Set<String> extractReferences(String expression) {
        Set<String> refs = new HashSet<>();
        Matcher matcher = C_PATTERN.matcher(expression);
        
        while (matcher.find()) {
            String code = matcher.group(1);
            String id = matcher.group(2);
            String refId = buildRefId(code, id);
            refs.add(refId);
        }
        
        return refs;
    }
    
    /**
     * 构建引用ID到Convert的映射
     */
    private Map<String, Convert> buildReferenceMap(Set<String> refs, List<Convert> converts) {
        Map<String, Convert> map = new HashMap<>();
        
        for (String refId : refs) {
            Convert convert = findConvertByRefId(converts, refId);
            if (convert != null) {
                map.put(refId, convert);
            }
        }
        
        return map;
    }
    
    /**
     * 拓扑排序 - 使用DFS
     */
    private List<String> topologicalSort(Set<String> refs, Map<String, Convert> refMap, 
                                         List<Convert> converts) {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> tempMark = new HashSet<>();
        
        for (String refId : refs) {
            visit(refId, refMap, converts, order, visited, tempMark, 0);
        }
        
        return order;
    }
    
    /**
     * DFS访问节点
     */
    private void visit(String refId, Map<String, Convert> refMap, List<Convert> converts,
                      List<String> order, Set<String> visited, Set<String> tempMark, 
                      int depth) {
        if (depth > MAX_DEPENDENCY_DEPTH) {
            throw new ParserException("Maximum dependency depth exceeded: " + MAX_DEPENDENCY_DEPTH);
        }
        
        if (tempMark.contains(refId)) {
            // 发现循环引用
            throw new ParserException("Circular reference detected involving: " + refId);
        }
        
        if (visited.contains(refId)) {
            return;
        }
        
        tempMark.add(refId);
        
        // 获取当前转换器的依赖
        Convert convert = refMap.get(refId);
        if (convert != null && StringUtil.isNotBlank(convert.getArgs())) {
            Set<String> deps = extractReferences(convert.getArgs());
            for (String dep : deps) {
                // 确保依赖也在 refMap 中
                if (!refMap.containsKey(dep)) {
                    Convert depConvert = findConvertByRefId(converts, dep);
                    if (depConvert != null) {
                        refMap.put(dep, depConvert);
                    }
                }
                visit(dep, refMap, converts, order, visited, tempMark, depth + 1);
            }
        }
        
        tempMark.remove(refId);
        visited.add(refId);
        order.add(refId);
    }
    
    /**
     * 根据引用ID查找Convert
     */
    private Convert findConvertByRefId(List<Convert> converts, String refId) {
        if (converts == null) return null;
        
        String[] parts = refId.split(":");
        String code = parts[0];
        String id = parts.length > 1 ? parts[1] : null;
        
        for (Convert convert : converts) {
            // 1. 先匹配 convertCode
            if (code.equals(convert.getConvertCode())) {
                // 2. 再匹配 id（前端自动生成的唯一标识）
                String convertId = convert.getId();
                if (convertId != null && convertId.equals(refId)) {
                    return convert;
                }
            }
        }
        
        // 兼容旧数据：如果没有 id 字段，使用 name 作为后备匹配
        if (id != null) {
            for (Convert convert : converts) {
                if (code.equals(convert.getConvertCode()) && 
                    id.equals(convert.getName())) {
                    return convert;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 构建引用ID
     */
    private String buildRefId(String code, String id) {
        return code + ":" + (id != null ? id : "default");
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板执行器 - 线性执行转换器链
 */
public class TemplateExecutor {
    
    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final Pattern F_PATTERN = Pattern.compile("F\\(([^)]+)\\)");
    
    private final TemplateParser parser;
    
    public TemplateExecutor() {
        this.parser = new TemplateParser();
    }
    
    /**
     * 执行模板
     */
    public Object execute(String template, Map<String, Object> rowData, 
                         List<Convert> converts) {
        if (StringUtil.isBlank(template)) {
            return null;
        }
        
        // 1. 获取解析结果（使用缓存）
        String cacheKey = generateCacheKey(template, converts);
        ParseResult parseResult = TemplateCache.getInstance().getParseResult(cacheKey);
        
        if (parseResult == null) {
            parseResult = parser.parseTemplate(template, converts);
            TemplateCache.getInstance().putParseResult(cacheKey, parseResult);
        }
        
        // 2. 初始化上下文
        Map<String, Object> context = new HashMap<>();
        // 加载字段值到上下文（F:前缀）
        if (rowData != null) {
            for (Map.Entry<String, Object> entry : rowData.entrySet()) {
                context.put("F:" + entry.getKey(), entry.getValue());
            }
        }
        
        // 3. 按顺序执行转换器链
        for (String refId : parseResult.getExecutionOrder()) {
            Convert convert = parseResult.getReferenceMap().get(refId);
            if (convert == null) continue;
            
            try {
                // 3.1 预处理 args：替换其中的引用
                String processedArgs = replaceAllReferences(convert.getArgs(), context);
                
                // 3.2 执行 Handler
                Handler handler = ConvertEnum.getHandler(convert.getConvertCode());
                Object value = rowData != null ? rowData.get(convert.getName()) : null;
                Object result = handler.handle(processedArgs, value, rowData);
                
                // 3.3 存入上下文
                context.put(refId, result);
            } catch (Exception e) {
                // 使用替代值（可以扩展 Convert 支持 fallback 字段）
                context.put(refId, "");
            }
        }
        
        // 4. 替换原始表达式中的所有引用
        return replaceAllReferences(template, context);
    }
    
    /**
     * 替换模板中的所有引用（C() 和 F()）
     */
    private String replaceAllReferences(String template, Map<String, Object> context) {
        if (StringUtil.isBlank(template)) {
            return template;
        }
        
        String result = template;
        
        // 先替换 C() 引用
        result = replaceCReferences(result, context);
        
        // 再替换 F() 引用
        result = replaceFReferences(result, context);
        
        return result;
    }
    
    /**
     * 替换 C() 引用
     */
    private String replaceCReferences(String template, Map<String, Object> context) {
        Matcher matcher = C_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String code = matcher.group(1);
            String id = matcher.group(2);
            String refId = code + ":" + (id != null ? id : "default");
            
            Object value = context.get(refId);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * 替换 F() 引用
     */
    private String replaceFReferences(String template, Map<String, Object> context) {
        Matcher matcher = F_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            Object value = context.get("F:" + fieldName);
            String replacement = value != null ? value.toString() : "";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        
        return buffer.toString();
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String template, List<Convert> converts) {
        StringBuilder sb = new StringBuilder();
        sb.append(template).append("|");
        
        if (converts != null) {
            for (Convert c : converts) {
                sb.append(c.getConvertCode()).append(":").append(c.getName()).append(";");
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
    private static final int MAX_CACHE_SIZE = 1000;
    
    private final Map<String, ParseResult> cache = new ConcurrentHashMap<>();
    
    private TemplateCache() {}
    
    public static TemplateCache getInstance() {
        return INSTANCE;
    }
    
    public ParseResult getParseResult(String key) {
        return cache.get(key);
    }
    
    public void putParseResult(String key, ParseResult result) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            // 简单的淘汰策略：清空一半
            int removeCount = cache.size() / 2;
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
}
```

### 2.2 修改现有类

#### 2.2.1 修改 Handler.java 接口
```java
package org.dbsyncer.parser.convert;

import org.dbsyncer.parser.model.Convert;

import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 2.0.0
 * @date 2019/10/8 22:55
 */
public interface Handler {

    /**
     * 值转换
     *
     * @param args 参数
     * @param value 当前字段值
     * @param sourceRow 源端数据行（用于 F() 字段引用）
     * @param context 转换器上下文（存储已计算的转换器值，用于 C() 递归引用）
     * @param converts 转换器配置列表
     * @return 转换后的值
     */
    Object handle(String args, Object value,
                  Map<String, Object> sourceRow,
                  Map<String, Object> context,
                  List<Convert> converts);
}
```

#### 2.2.2 修改 ExpressionHandler.java
```java
package org.dbsyncer.parser.convert.handler;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.util.TemplateExecutor;

import java.util.List;
import java.util.Map;

/**
 * 表达式处理器（重构版 - 使用 TemplateExecutor）
 */
public class ExpressionHandler implements Handler {
    
    private final TemplateExecutor executor;
    
    public ExpressionHandler() {
        this.executor = new TemplateExecutor();
    }
    
    @Override
    public Object handle(String args, Object value,
                         Map<String, Object> sourceRow,
                         Map<String, Object> context,
                         List<Convert> converts) {
        if (StringUtil.isBlank(args) || sourceRow == null) {
            return null;
        }

        // 使用 TemplateExecutor 执行表达式
        return executor.execute(args, sourceRow, context, converts);
    }
}
```

#### 2.2.3 修改其他 Handler 实现类
所有实现 `Handler` 接口的类都需要更新方法签名：

**修改前：**
```java
@Override
public Object handle(String args, Object value, Map<String, Object> row) {
    // ...
}
```

**修改后：**
```java
@Override
public Object handle(String args, Object value,
                     Map<String, Object> sourceRow,
                     Map<String, Object> context,
                     List<Convert> converts) {
    // sourceRow: 源端数据，用于 F() 字段引用
    // context: 转换器上下文，用于 C() 递归引用
    // ...
}
```

**需要修改的 Handler 列表：**
- `UUIDHandler.java`
- `TimestampHandler.java`
- `DefaultHandler.java`
- `AesEncryptHandler.java`
- `AesDecryptHandler.java`
- `Sha1Handler.java`
- `ReplaceHandler.java`
- `PrependHandler.java`
- `AppendHandler.java`
- `UpperHandler.java`
- `LowerHandler.java`
- ...（所有实现 Handler 接口的类）

**参数说明：**
- `sourceRow`: 源端数据行，用于 `F(fieldName)` 字段引用
- `context`: 转换器上下文，存储已计算的转换器值，用于 `C(code:id)` 递归引用
- `converts`: 转换器配置列表

**注意：** 大多数 Handler 只需要使用 `sourceRow`，`context` 主要用于 ExpressionHandler 处理嵌套转换器。

---

## 3. 代码清理清单

### 3.1 删除过时代码

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/util/ExpressionUtil.java` | **删除** | 被 TemplateExecutor 替代 |
| `dbsyncer-parser/src/test/java/org/dbsyncer/parser/util/ExpressionUtilTest.java` | **删除** | 旧测试代码 |

### 3.2 需要修改的文件

| 文件路径 | 修改内容 |
|---------|---------|
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/Convert.java` | **增加 id 字段**（前端自动生成） |
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/convert/Handler.java` | **修改为：sourceRow + context + converts 参数** |
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/convert/handler/ExpressionHandler.java` | 使用 TemplateExecutor 替代 ExpressionUtil |
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/convert/handler/*Handler.java` | 更新方法签名：sourceRow + context + converts |
| `dbsyncer-web/.../editConvert.html` | **前端增加 ID 自动生成逻辑** |

### 3.3 新增文件

| 文件路径 | 说明 |
|---------|------|
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/model/ParseResult.java` | 解析结果模型 |
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/util/TemplateParser.java` | 模板解析器 |
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/util/TemplateExecutor.java` | 模板执行器 |
| `dbsyncer-parser/src/main/java/org/dbsyncer/parser/util/TemplateCache.java` | 解析结果缓存 |

---

## 4. 测试实现

### 4.1 单元测试

#### 4.1.1 TemplateParserTest.java
```java
package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TemplateParserTest {
    
    @Test
    public void testSimpleTemplate() {
        TemplateParser parser = new TemplateParser();
        ParseResult result = parser.parseTemplate("F(name)", new ArrayList<>());
        
        assertNotNull(result);
        assertEquals("F(name)", result.getOriginalExpression());
        assertTrue(result.getExecutionOrder().isEmpty());
    }
    
    @Test
    public void testSingleConverter() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();
        
        Convert uuid = new Convert();
        uuid.setName("id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);
        
        ParseResult result = parser.parseTemplate("C(UUID:id)", converts);
        
        assertNotNull(result);
        assertEquals(1, result.getExecutionOrder().size());
        assertEquals("UUID:id", result.getExecutionOrder().get(0));
    }
    
    @Test
    public void testDependencyChain() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();
        
        // A 依赖 UUID
        Convert expr = new Convert();
        expr.setName("full_id");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_ C(UUID:uid)");
        converts.add(expr);
        
        Convert uuid = new Convert();
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);
        
        ParseResult result = parser.parseTemplate("C(EXPRESSION:full_id)", converts);
        
        assertNotNull(result);
        assertEquals(2, result.getExecutionOrder().size());
        // UUID 应该在 EXPRESSION 之前
        assertEquals("UUID:uid", result.getExecutionOrder().get(0));
        assertEquals("EXPRESSION:full_id", result.getExecutionOrder().get(1));
    }
    
    @Test(expected = org.dbsyncer.parser.ParserException.class)
    public void testCircularReference() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();
        
        // A 依赖 B，B 依赖 A
        Convert a = new Convert();
        a.setName("a");
        a.setConvertCode("EXPRESSION");
        a.setArgs("C(EXPRESSION:b)");
        converts.add(a);
        
        Convert b = new Convert();
        b.setName("b");
        b.setConvertCode("EXPRESSION");
        b.setArgs("C(EXPRESSION:a)");
        converts.add(b);
        
        parser.parseTemplate("C(EXPRESSION:a)", converts);
    }
    
    @Test(expected = org.dbsyncer.parser.ParserException.class)
    public void testMaxDepthExceeded() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();
        
        // 创建超过10层的依赖链
        for (int i = 0; i < 12; i++) {
            Convert c = new Convert();
            c.setName("c" + i);
            c.setConvertCode("EXPRESSION");
            if (i < 11) {
                c.setArgs("C(EXPRESSION:c" + (i + 1) + ")");
            }
            converts.add(c);
        }
        
        parser.parseTemplate("C(EXPRESSION:c0)", converts);
    }
}
```

#### 4.1.2 TemplateExecutorTest.java
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
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");
        
        Object result = executor.execute("F(name)", row, new ArrayList<>());
        assertEquals("John", result);
    }
    
    @Test
    public void testMultipleFieldReferences() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("first", "John");
        row.put("last", "Doe");
        
        Object result = executor.execute("F(first) F(last)", row, new ArrayList<>());
        assertEquals("John Doe", result);
    }
    
    @Test
    public void testConverterExecution() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "test");
        
        List<Convert> converts = new ArrayList<>();
        Convert uuid = new Convert();
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);
        
        Object result = executor.execute("ID: C(UUID:uid)", row, converts);
        assertNotNull(result);
        assertTrue(result.toString().startsWith("ID: "));
        assertTrue(result.toString().length() > 10);
    }
    
    @Test
    public void testDependencyChainExecution() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");
        
        List<Convert> converts = new ArrayList<>();
        
        Convert expr = new Convert();
        expr.setId("EXPRESSION_0");  // 前端自动生成ID
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_ C(UUID:UUID_0)");
        converts.add(expr);
        
        Convert uuid = new Convert();
        uuid.setId("UUID_0");  // 前端自动生成ID
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);
        
        Object result = executor.execute("C(EXPRESSION:EXPRESSION_0) Name: F(name)", row, converts);
        assertNotNull(result);
        assertTrue(result.toString().contains("USER_"));
        assertTrue(result.toString().contains("Name: John"));
    }
    
    @Test
    public void testNonExistentField() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        
        Object result = executor.execute("F(nonexistent)", row, new ArrayList<>());
        assertEquals("", result);
    }
    
    @Test
    public void testNullExpression() {
        TemplateExecutor executor = new TemplateExecutor();
        
        Object result = executor.execute(null, new HashMap<>(), new ArrayList<>());
        assertNull(result);
    }
    
    @Test
    public void testCache() {
        TemplateExecutor executor1 = new TemplateExecutor();
        TemplateExecutor executor2 = new TemplateExecutor();
        
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");
        
        // 第一次执行
        Object result1 = executor1.execute("F(name)", row, new ArrayList<>());
        
        // 第二次执行（应该使用缓存）
        Object result2 = executor2.execute("F(name)", row, new ArrayList<>());
        
        assertEquals(result1, result2);
    }
}
```

### 4.2 集成测试

#### 4.2.1 TemplateIntegrationTest.java
```java
package org.dbsyncer.parser.integration;

import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.util.TemplateExecutor;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TemplateIntegrationTest {
    
    @Test
    public void testComplexScenario() {
        // 模拟真实场景：用户数据同步
        Map<String, Object> row = new HashMap<>();
        row.put("first_name", "John");
        row.put("last_name", "Doe");
        row.put("email", "john@example.com");
        
        List<Convert> converts = new ArrayList<>();
        
        // 生成用户ID
        Convert userId = new Convert();
        userId.setId("UUID_0");  // 前端自动生成ID
        userId.setName("user_id");
        userId.setConvertCode("UUID");
        userId.setArgs("");
        converts.add(userId);
        
        // 生成时间戳
        Convert timestamp = new Convert();
        timestamp.setId("SYSTEM_TIMESTAMP_0");  // 前端自动生成ID
        timestamp.setName("created_at");
        timestamp.setConvertCode("SYSTEM_TIMESTAMP");
        timestamp.setArgs("");
        converts.add(timestamp);
        
        // 组合全名
        Convert fullName = new Convert();
        fullName.setId("EXPRESSION_0");  // 前端自动生成ID
        fullName.setName("full_name");
        fullName.setConvertCode("EXPRESSION");
        fullName.setArgs("F(first_name)  F(last_name)");  // 两个空格分隔
        converts.add(fullName);
        
        TemplateExecutor executor = new TemplateExecutor();
        
        // 测试复杂表达式
        String template = "User: C(EXPRESSION:EXPRESSION_0) ID: C(UUID:UUID_0) At: C(SYSTEM_TIMESTAMP:SYSTEM_TIMESTAMP_0)";
        Object result = executor.execute(template, row, converts);
        
        assertNotNull(result);
        String resultStr = result.toString();
        assertTrue(resultStr.contains("User: John Doe"));
        assertTrue(resultStr.contains("ID:"));
        assertTrue(resultStr.contains("At:"));
    }
}
```

---

## 5. 前端改造

### 5.1 现有界面评估

当前 `editConvert.html` 已支持大部分功能：
- ✅ 支持 `argNum == -1` 的表达式输入
- ✅ 参数输入框允许输入任意字符串
- ✅ 已显示转换器说明和示例

### 5.2 需要增加的改造

**需要给 Convert 增加 id 字段的存储和显示：**

1. **前端自动生成 ID**
   - 当用户添加转换器时，前端自动生成 ID
   - 格式：`{convertCode}_{序号}`
   - 序号从 0 开始，同一类型递增

2. **存储 ID**
   - 将生成的 ID 设置到 Convert 对象的 `id` 字段
   - 保存到后端时带上 `id` 字段

3. **显示 ID（可选）**
   - 在转换器列表中显示 ID，方便用户编写表达式
   - 格式示例：`UUID_0`, `EXPRESSION_1`, `SYSTEM_TIMESTAMP_0`

### 5.3 使用示例

用户在转换配置界面可以这样使用：

| 转换器ID | 转换类型 | 目标字段 | 参数/表达式 | 说明 |
|---------|---------|---------|------------|------|
| EXPRESSION_0 | EXPRESSION | full_name | `F(first_name)  F(last_name)` | 组合姓名 |
| UUID_0 | UUID | user_id | (空) | 生成用户ID |
| SYSTEM_TIMESTAMP_0 | SYSTEM_TIMESTAMP | created_at | (空) | 生成时间戳 |

**表达式示例：**
```
C(EXPRESSION:EXPRESSION_0)              → 引用第一个表达式转换器
C(UUID:UUID_0)                           → 引用第一个UUID转换器
C(SYSTEM_TIMESTAMP:SYSTEM_TIMESTAMP_0)   → 引用第一个时间戳转换器
```

---

## 6. 部署步骤

### 6.1 代码部署

1. **删除过时代码**
   ```bash
   rm dbsyncer-parser/src/main/java/org/dbsyncer/parser/util/ExpressionUtil.java
   rm dbsyncer-parser/src/test/java/org/dbsyncer/parser/util/ExpressionUtilTest.java
   ```

2. **添加新代码**
   - 添加 ParseResult.java
   - 添加 TemplateParser.java
   - 添加 TemplateExecutor.java
   - 添加 TemplateCache.java

3. **修改现有代码**
   - 修改 Handler.java（增加 converts 参数）
   - 修改所有 *Handler.java（更新方法签名）
   - 修改 ExpressionHandler.java（使用 TemplateExecutor）

4. **添加测试**
   - 添加 TemplateParserTest.java
   - 添加 TemplateExecutorTest.java
   - 添加 TemplateIntegrationTest.java

### 6.2 验证步骤

1. 编译项目：`mvn clean compile`
2. 运行测试：`mvn test`
3. 启动应用并验证转换功能

---

## 7. 验收标准

### 7.1 功能验收
- [ ] 支持 F() 字段引用
- [ ] 支持 C() 转换器引用
- [ ] 支持多层依赖链
- [ ] 循环引用检测并报错
- [ ] 最大深度限制
- [ ] 缓存机制正常工作
- [ ] 错误处理正确

### 7.2 性能验收
- [ ] 解析结果缓存有效
- [ ] 线性执行无递归
- [ ] 执行耗时 < 10ms（简单表达式）
- [ ] 内存使用合理

### 7.3 代码质量
- [ ] 旧 ExpressionUtil 已删除
- [ ] 旧测试已删除
- [ ] 新代码有完整单元测试
- [ ] 集成测试通过

---

## 8. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Handler 接口修改影响其他转换器 | 低 | 所有 Handler 统一增加 converts 参数，大多数 Handler 忽略该参数即可 |
| 循环检测性能问题 | 低 | 限制最大深度，使用高效数据结构 |
| 缓存内存泄漏 | 低 | 实现 LRU 淘汰策略 |
| 表达式语法不兼容 | 低 | 新语法是旧语法的超集 |

---

## 修订记录

| 版本 | 日期 | 修订内容 |
|------|------|----------|
| 2.0 | 2026-02-04 | 重构为线性执行模型，明确代码清理清单，更新测试用例 |
| 1.0 | 2026-02-04 | 初始版本 |
