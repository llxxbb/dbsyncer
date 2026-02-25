package org.dbsyncer.parser.util;

import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TemplateExecutorTest {

    @Test
    public void testSimpleFieldReference() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");

        ParseResult result = parser.parseTemplate("F(name)", null, new ArrayList<>());
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertEquals("John", output);
    }

    @Test
    public void testMultipleFieldReferences() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("first", "John");
        row.put("last", "Doe");

        ParseResult result = parser.parseTemplate("F(first) F(last)", null, new ArrayList<>());
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertEquals("John Doe", output);
    }

    @Test
    public void testRootConverterExecution() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "test");

        List<Convert> converts = new ArrayList<>();
        Convert uuid = new Convert();
        uuid.setId("uuid_0");
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        uuid.setRoot(true);
        converts.add(uuid);

        ParseResult parseResult = parser.parseTemplate("ID: C(UUID:uuid_0)", uuid, converts);
        String output = TemplateExecutor.run(parseResult, row, new HashMap<>());

        assertNotNull(output);
        assertTrue(output.startsWith("ID: "));
        assertTrue(output.length() > 10);
    }

    @Test
    public void testDependencyChainExecution() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");

        List<Convert> converts = new ArrayList<>();

        Convert expr = new Convert();
        expr.setId("expr_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_ C(UUID:uuid_0)");
        expr.setRoot(true);
        converts.add(expr);

        Convert uuid = new Convert();
        uuid.setId("uuid_0");
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        uuid.setRoot(false);
        converts.add(uuid);

        ParseResult parseResult = parser.parseTemplate("C(EXPRESSION:expr_0) Name: F(name)", expr, converts);
        String output = TemplateExecutor.run(parseResult, row, new HashMap<>());

        assertNotNull(output);
        assertTrue(output.contains("USER_"));
        assertTrue(output.contains("Name: John"));
    }

    @Test
    public void testNonExistentField() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();

        ParseResult result = parser.parseTemplate("F(nonexistent)", null, new ArrayList<>());
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertEquals("", output);
    }

    @Test
    public void testNullExpression() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();

        ParseResult result = parser.parseTemplate(null, null, new ArrayList<>());
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertNull(output);
    }

    @Test
    public void testBlankExpression() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();

        ParseResult result = parser.parseTemplate("", null, new ArrayList<>());
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertNull(output);
    }

    @Test
    public void testNoConverterReference() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "John");

        List<Convert> converts = new ArrayList<>();
        Convert expr = new Convert();
        expr.setId("expr_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_001");
        expr.setRoot(true);
        converts.add(expr);

        ParseResult result = parser.parseTemplate("Prefix C(EXPRESSION:expr_0) Suffix", expr, converts);
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertEquals("Prefix USER_001 Suffix", output);
    }

    @Test
    public void testMixedExpression() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("first_name", "John");
        row.put("last_name", "Doe");

        List<Convert> converts = new ArrayList<>();
        Convert uuid = new Convert();
        uuid.setId("uuid_0");
        uuid.setName("user_id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        uuid.setRoot(true);
        converts.add(uuid);

        ParseResult result = parser.parseTemplate("User: F(first_name) F(last_name) ID: C(UUID:uuid_0)", uuid, converts);
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertNotNull(output);
        assertTrue(output.contains("User: John Doe"));
        assertTrue(output.contains("ID:"));
    }

    @Test
    public void testNullFieldValue() {
        TemplateParser parser = new TemplateParser();
        Map<String, Object> row = new HashMap<>();
        row.put("name", null);

        ParseResult result = parser.parseTemplate("F(name)", null, new ArrayList<>());
        String output = TemplateExecutor.run(result, row, new HashMap<>());

        assertEquals("", output);
    }
}
