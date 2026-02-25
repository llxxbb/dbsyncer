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

/**
 * 模板执行器 - 纯粹执行器
 *
 * 职责：
 * 1. 按执行计划执行转换器
 * 2. 替换模板中的占位符
 *
 * 注意：
 * - 不包含解析逻辑（由 TemplateParser 负责）
 * - 不包含业务规则（由上游调用层负责）
 * - 接收 ParseResult 作为输入
 */
public final class TemplateExecutor {

    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final Pattern F_PATTERN = Pattern.compile("F\\(([^)]+)\\)");

    private TemplateExecutor() {
    }

    /**
     * 执行模板
     *
     * @param parseResult 解析结果（执行计划），可为 null
     * @param sourceRow   源端数据行
     * @param context     转换器上下文
     * @return 执行结果
     */
    public static String run(ParseResult parseResult, Map<String, Object> sourceRow, Map<String, Object> context) {
        // 1. 初始化上下文
        initContext(sourceRow, context);

        // 2. 如果有解析结果，按执行顺序执行转换器
        executeConverters(parseResult, sourceRow, context);
        // 3. 替换模板中的占位符
        return replaceAllReferences(parseResult.getOriginalExpression(), context);
    }

    /**
     * 初始化上下文 - 加载字段值
     */
    private static void initContext(Map<String, Object> sourceRow, Map<String, Object> context) {
        if (sourceRow != null) {
            for (Map.Entry<String, Object> entry : sourceRow.entrySet()) {
                context.put("F:" + entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 按执行顺序执行转换器
     */
    private static void executeConverters(ParseResult parseResult, Map<String, Object> sourceRow, Map<String, Object> context) {
        Map<String, Convert> refMap = parseResult.getReferenceMap();
        List<Convert> converts = new ArrayList<>(refMap.values());

        for (String refId : parseResult.getExecutionOrder()) {
            Convert convert = refMap.get(refId);
            if (convert == null) {
                continue;
            }

            try {
                // 预处理 args
                String processedArgs = replaceAllReferences(convert.getArgs(), context);

                // 执行 Handler
                Handler handler = ConvertEnum.getHandler(convert.getConvertCode());
                Object value = null; // 从 context 获取
                Object result = handler.handle(processedArgs, value, sourceRow, context, converts);

                context.put(refId, result);
            } catch (Exception e) {
                context.put(refId, "");
            }
        }
    }

    /**
     * 替换模板中的所有引用（C() 和 F()）
     */
    private static String replaceAllReferences(String template, Map<String, Object> context) {
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

    /**
     * 替换 F() 引用
     */
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
