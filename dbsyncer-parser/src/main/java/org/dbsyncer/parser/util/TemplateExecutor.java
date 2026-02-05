package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.convert.Handler;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;

import java.util.List;
import java.util.Map;
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
     *
     * @param template  模板表达式
     * @param sourceRow 源端数据行
     * @param context   转换器上下文（存储已计算的转换器值）
     * @param converts  转换器配置列表
     * @return 执行结果
     */
    public Object execute(String template, Map<String, Object> sourceRow,
                          Map<String, Object> context, List<Convert> converts) {
        if (StringUtil.isBlank(template)) {
            return null;
        }

        String cacheKey = generateCacheKey(template, converts);
        ParseResult parseResult = TemplateCache.getInstance().getParseResult(cacheKey);

        if (parseResult == null) {
            parseResult = parser.parseTemplate(template, converts);
            TemplateCache.getInstance().putParseResult(cacheKey, parseResult);
        }

        // 将源端数据放入上下文，用于 F() 引用
        if (sourceRow != null) {
            for (Map.Entry<String, Object> entry : sourceRow.entrySet()) {
                context.put("F:" + entry.getKey(), entry.getValue());
            }
        }

        for (String refId : parseResult.getExecutionOrder()) {
            Convert convert = parseResult.getReferenceMap().get(refId);
            if (convert == null) {
                continue;
            }

            try {
                String processedArgs = replaceAllReferences(convert.getArgs(), context);

                Handler handler = ConvertEnum.getHandler(convert.getConvertCode());
                Object value = sourceRow != null ? sourceRow.get(convert.getName()) : null;
                Object result = handler.handle(processedArgs, value, sourceRow, context, converts);

                context.put(refId, result);
            } catch (Exception e) {
                context.put(refId, "");
            }
        }

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

        result = replaceCReferences(result, context);

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
            String refId = code + ":" + (id != null ? id : "");
            Object value = context.get(refId);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            matcher.appendReplacement(buffer, replacement);
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
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            matcher.appendReplacement(buffer, replacement);
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
                sb.append(c.getConvertCode()).append(":").append(c.getId()).append(";");
            }
        }

        return sb.toString();
    }
}
