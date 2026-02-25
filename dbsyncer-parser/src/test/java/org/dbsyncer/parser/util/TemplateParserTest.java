package org.dbsyncer.parser.util;

import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TemplateParserTest {

    @Test
    public void testSimpleTemplate() {
        TemplateParser parser = new TemplateParser();
        ParseResult result = parser.parseTemplate("F(name)", null, new ArrayList<>());

        assertNotNull(result);
        assertEquals("F(name)", result.getOriginalExpression());
        assertTrue(result.getExecutionOrder().isEmpty());
    }

    @Test
    public void testSingleRootConverter() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert uuid = new Convert();
        uuid.setId("uid_0");
        uuid.setName("user_id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        ParseResult result = parser.parseTemplate("C(UUID:uid_0)", uuid, converts);

        assertNotNull(result);
        assertEquals(1, result.getExecutionOrder().size());
        assertEquals("UUID:uid_0", result.getExecutionOrder().get(0));
    }

    @Test
    public void testMustStartFromRoot() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        // 创建两个不同的转换器
        Convert uuid = new Convert();
        uuid.setId("uid_0");
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        Convert expr = new Convert();
        expr.setId("expr_0");
        expr.setName("expr");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("");
        converts.add(expr);

        // 模板引用 UUID，但根是 EXPRESSION，应该抛出异常
        try {
            parser.parseTemplate("C(UUID:uid_0)", expr, converts);
            fail("Expected ParserException");
        } catch (ParserException e) {
            assertTrue(e.getMessage().contains("Must start from root converter"));
        }
    }

    @Test
    public void testDependencyChain() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert expr = new Convert();
        expr.setId("expr_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_ C(UUID:uid_0)");
        converts.add(expr);

        Convert uuid = new Convert();
        uuid.setId("uid_0");
        uuid.setName("uid");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        // expr 是根，依赖 uuid
        ParseResult result = parser.parseTemplate("C(EXPRESSION:expr_0)", expr, converts);

        assertNotNull(result);
        assertEquals(2, result.getExecutionOrder().size());
        // UUID 应该在 EXPRESSION 之前（依赖先执行）
        assertEquals("UUID:uid_0", result.getExecutionOrder().get(0));
        assertEquals("EXPRESSION:expr_0", result.getExecutionOrder().get(1));
    }

    @Test
    public void testCircularReference() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert a = new Convert();
        a.setId("expr_a");
        a.setName("a");
        a.setConvertCode("EXPRESSION");
        a.setArgs("C(EXPRESSION:expr_b)");
        converts.add(a);

        Convert b = new Convert();
        b.setId("expr_b");
        b.setName("b");
        b.setConvertCode("EXPRESSION");
        b.setArgs("C(EXPRESSION:expr_a)");
        converts.add(b);

        try {
            parser.parseTemplate("C(EXPRESSION:expr_a)", a, converts);
            fail("Expected ParserException for circular reference");
        } catch (ParserException e) {
            assertTrue(e.getMessage().contains("Circular reference"));
        }
    }

    @Test
    public void testNoConverterReference() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert expr = new Convert();
        expr.setId("expr_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_001");
        converts.add(expr);

        ParseResult result = parser.parseTemplate("C(EXPRESSION:expr_0)", expr, converts);

        assertNotNull(result);
        assertEquals(1, result.getExecutionOrder().size());
        assertEquals("EXPRESSION:expr_0", result.getExecutionOrder().get(0));
    }

    @Test
    public void testBlankExpression() {
        TemplateParser parser = new TemplateParser();
        ParseResult result = parser.parseTemplate("", null, new ArrayList<>());

        assertNotNull(result);
        assertTrue(result.getExecutionOrder().isEmpty());
    }

    @Test
    public void testNullExpression() {
        TemplateParser parser = new TemplateParser();
        ParseResult result = parser.parseTemplate(null, null, new ArrayList<>());

        assertNotNull(result);
        assertTrue(result.getExecutionOrder().isEmpty());
    }

    @Test
    public void testReferenceMapContainsCorrectConvert() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert uuid = new Convert();
        uuid.setId("uid_0");
        uuid.setName("id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        ParseResult result = parser.parseTemplate("C(UUID:uid_0)", uuid, converts);

        assertNotNull(result.getReferenceMap());
        assertEquals(1, result.getReferenceMap().size());

        Convert found = result.getReferenceMap().get("UUID:uid_0");
        assertNotNull(found);
        assertEquals("UUID", found.getConvertCode());
        assertEquals("uid_0", found.getId());
    }
}
