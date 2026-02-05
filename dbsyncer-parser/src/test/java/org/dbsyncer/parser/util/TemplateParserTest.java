package org.dbsyncer.parser.util;

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
        uuid.setId("UUID_0");
        uuid.setName("id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        ParseResult result = parser.parseTemplate("C(UUID:UUID_0)", converts);

        assertNotNull(result);
        assertEquals(1, result.getExecutionOrder().size());
        assertEquals("UUID:UUID_0", result.getExecutionOrder().get(0));
    }

    @Test
    public void testDependencyChain() {
        TemplateParser parser = new TemplateParser();
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

        ParseResult result = parser.parseTemplate("C(EXPRESSION:EXPRESSION_0)", converts);

        assertNotNull(result);
        assertEquals(2, result.getExecutionOrder().size());
        // UUID 应该在 EXPRESSION 之前
        assertEquals("UUID:UUID_0", result.getExecutionOrder().get(0));
        assertEquals("EXPRESSION:EXPRESSION_0", result.getExecutionOrder().get(1));
    }

    @Test
    public void testCircularReference() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert a = new Convert();
        a.setId("A_0");
        a.setName("a");
        a.setConvertCode("EXPRESSION");
        a.setArgs("C(EXPRESSION:B_0)");
        converts.add(a);

        Convert b = new Convert();
        b.setId("B_0");
        b.setName("b");
        b.setConvertCode("EXPRESSION");
        b.setArgs("C(EXPRESSION:A_0)");
        converts.add(b);

        try {
            parser.parseTemplate("C(EXPRESSION:A_0)", converts);
            fail("Expected ParserException for circular reference");
        } catch (org.dbsyncer.parser.ParserException e) {
            assertTrue(e.getMessage().contains("Circular reference"));
        }
    }

    @Test
    public void testNoConverterReference() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert expr = new Convert();
        expr.setId("EXPRESSION_0");
        expr.setName("full");
        expr.setConvertCode("EXPRESSION");
        expr.setArgs("USER_001");  // 无引用
        converts.add(expr);

        ParseResult result = parser.parseTemplate("C(EXPRESSION:EXPRESSION_0)", converts);

        assertNotNull(result);
        assertEquals(1, result.getExecutionOrder().size());
        assertEquals("EXPRESSION:EXPRESSION_0", result.getExecutionOrder().get(0));
    }

    @Test
    public void testBlankExpression() {
        TemplateParser parser = new TemplateParser();
        ParseResult result = parser.parseTemplate("", new ArrayList<>());

        assertNotNull(result);
        assertTrue(result.getExecutionOrder().isEmpty());
    }

    @Test
    public void testNullExpression() {
        TemplateParser parser = new TemplateParser();
        ParseResult result = parser.parseTemplate(null, new ArrayList<>());

        assertNotNull(result);
        assertTrue(result.getExecutionOrder().isEmpty());
    }

    @Test
    public void testReferenceMapContainsCorrectConvert() {
        TemplateParser parser = new TemplateParser();
        List<Convert> converts = new ArrayList<>();

        Convert uuid = new Convert();
        uuid.setId("UUID_0");
        uuid.setName("id");
        uuid.setConvertCode("UUID");
        uuid.setArgs("");
        converts.add(uuid);

        ParseResult result = parser.parseTemplate("C(UUID:UUID_0)", converts);

        assertNotNull(result.getReferenceMap());
        assertEquals(1, result.getReferenceMap().size());

        Convert found = result.getReferenceMap().get("UUID:UUID_0");
        assertNotNull(found);
        assertEquals("UUID", found.getConvertCode());
        assertEquals("UUID_0", found.getId());
    }
}
