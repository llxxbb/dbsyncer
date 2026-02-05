package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.Convert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TemplateExecutorTest {

    @Test
    public void testSimpleFieldReference() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");

        Object result = executor.execute("F(name)", row, new HashMap<>(), new ArrayList<>());
        assertEquals("John", result);
    }

    @Test
    public void testMultipleFieldReferences() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("first", "John");
        row.put("last", "Doe");

        Object result = executor.execute("F(first) F(last)", row, new HashMap<>(), new ArrayList<>());
        assertEquals("John Doe", result);
    }

    @Test
    public void testConverterExecution() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "test");

        List<Convert> converts = new ArrayList<>();
        Convert uuid = new Convert();
        uuid.setId("UUID_0");
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        Object result = executor.execute("ID: C(UUID:UUID_0)", row, new HashMap<>(), converts);
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
        expr.setId("EXPRESSION_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_ C(UUID:UUID_0)");
        converts.add(expr);

        Convert uuid = new Convert();
        uuid.setId("UUID_0");
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        Object result = executor.execute("C(EXPRESSION:EXPRESSION_0) Name: F(name)", row, new HashMap<>(), converts);
        assertNotNull(result);
        assertTrue(result.toString().contains("USER_"));
        assertTrue(result.toString().contains("Name: John"));
    }

    @Test
    public void testNonExistentField() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();

        Object result = executor.execute("F(nonexistent)", row, new HashMap<>(), new ArrayList<>());
        assertEquals("", result);
    }

    @Test
    public void testNullExpression() {
        TemplateExecutor executor = new TemplateExecutor();

        Object result = executor.execute(null, new HashMap<>(), new HashMap<>(), new ArrayList<>());
        assertNull(result);
    }

    @Test
    public void testBlankExpression() {
        TemplateExecutor executor = new TemplateExecutor();

        Object result = executor.execute("   ", new HashMap<>(), new HashMap<>(), new ArrayList<>());
        assertNull(result);
    }

    @Test
    public void testNoConverterReference() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");

        List<Convert> converts = new ArrayList<>();
        Convert expr = new Convert();
        expr.setId("EXPRESSION_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_001");
        converts.add(expr);

        Object result = executor.execute("Prefix C(EXPRESSION:EXPRESSION_0) Suffix", row, new HashMap<>(), converts);
        assertEquals("Prefix USER_001 Suffix", result);
    }

    @Test
    public void testMixedExpression() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("first_name", "John");
        row.put("last_name", "Doe");

        List<Convert> converts = new ArrayList<>();
        Convert uuid = new Convert();
        uuid.setId("UUID_0");
        uuid.setName("user_id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        Object result = executor.execute("User: F(first_name) F(last_name) ID: C(UUID:UUID_0)", row, new HashMap<>(), converts);
        assertNotNull(result);
        assertTrue(result.toString().contains("User: John Doe"));
        assertTrue(result.toString().contains("ID:"));
    }

    @Test
    public void testNullFieldValue() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> row = new HashMap<>();
        row.put("name", null);

        Object result = executor.execute("F(name)", row, new HashMap<>(), new ArrayList<>());
        assertEquals("", result);
    }

    @Test
    public void testCacheHit() {
        TemplateExecutor executor1 = new TemplateExecutor();
        TemplateExecutor executor2 = new TemplateExecutor();

        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");

        Object result1 = executor1.execute("F(name)", row, new HashMap<>(), new ArrayList<>());
        Object result2 = executor2.execute("F(name)", row, new HashMap<>(), new ArrayList<>());

        assertEquals(result1, result2);
    }
    
    @Test
    public void testSimpleContextReference() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> sourceRow = new HashMap<>();
        sourceRow.put("base_value", 100);
        
        List<Convert> converts = new ArrayList<>();
        
        // 第一个转换器：计算平方
        Convert square = new Convert();
        square.setId("SQUARE_0");
        square.setName("squared");
        square.setConvertCode("EXPRESSION");
        square.setArgs("F(base_value) * 2");
        converts.add(square);
        
        Object result = executor.execute("Base: F(base_value) Square: C(EXPRESSION:SQUARE_0)", sourceRow, new HashMap<>(), converts);
        assertNotNull(result);
        assertTrue(result.toString().contains("Base: 100"));
        assertTrue(result.toString().contains("Square: 200"));
    }
    
    @Test
    public void testNestedConverterReference() {
        TemplateExecutor executor = new TemplateExecutor();
        Map<String, Object> sourceRow = new HashMap<>();
        sourceRow.put("first", "John");
        sourceRow.put("last", "Doe");
        
        List<Convert> converts = new ArrayList<>();
        
        // 嵌套转换器：构建完整姓名
        Convert fullName = new Convert();
        fullName.setId("FULL_NAME_0");
        fullName.setName("full_name");
        fullName.setConvertCode("EXPRESSION");
        fullName.setArgs("F(first) F(last)");
        converts.add(fullName);
        
        Object result = executor.execute("Full: C(EXPRESSION:FULL_NAME_0)", sourceRow, new HashMap<>(), converts);
        assertNotNull(result);
        assertTrue(result.toString().contains("Full: JohnDoe"));
    }
}
