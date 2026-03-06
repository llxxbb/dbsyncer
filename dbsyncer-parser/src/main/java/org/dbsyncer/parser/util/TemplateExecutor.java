package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateExecutor {

    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final Pattern F_PATTERN = Pattern.compile("F\\(([^)]+)\\)");

    private TemplateExecutor() {
    }

    public static String run(ParseResult parseResult, Map<String, Object> sourceRow, Map<String, Object> context) {
        assert parseResult != null : "ParseResult cannot be null";
        assert sourceRow != null : "SourceRow cannot be null";
        assert context != null : "Context cannot be null";

        initContext(sourceRow, context);

        executeConverters(parseResult, sourceRow, context);

        return replaceAllReferences(parseResult.getOriginalExpression(), context);
    }

    private static void initContext(Map<String, Object> sourceRow, Map<String, Object> context) {
        for (Map.Entry<String, Object> entry : sourceRow.entrySet()) {
            context.put("F:" + entry.getKey(), entry.getValue());
        }
    }

    private static void executeConverters(ParseResult parseResult, Map<String, Object> sourceRow, Map<String, Object> context) {
        Map<String, Convert> refMap = parseResult.getReferenceMap();
        List<Convert> converts = new ArrayList<>(refMap.values());

        for (String refId : parseResult.getExecutionOrder()) {
            Convert convert = refMap.get(refId);
            if (convert == null) {
                continue;
            }

            String processedArgs = replaceAllReferences(convert.getArgs(), context);

            Handler handler = ConvertEnum.getHandler(convert.getConvertCode());
            Object value = null;
            Object result = handler.handle(processedArgs, value, sourceRow, context, converts);

            context.put(refId, result);
        }
    }

    private static String replaceAllReferences(String template, Map<String, Object> context) {
        if (StringUtil.isBlank(template)) {
            return template;
        }

        String result = template;

        result = replaceCReferences(result, context);
        result = replaceFReferences(result, context);

        return result;
    }

    private static String replaceCReferences(String template, Map<String, Object> context) {
        Matcher matcher = C_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String code = matcher.group(1);
            String id = matcher.group(2);
            String refId = code + ":" + (id != null ? id : "");
            Object value = context.get(refId);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }

    private static String replaceFReferences(String template, Map<String, Object> context) {
        Matcher matcher = F_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String fieldName = matcher.group(1);
            Object value = context.get("F:" + fieldName);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            matcher.appendReplacement(buffer, replacement);
        }
        matcher.appendTail(buffer);

        return buffer.toString();
    }
}