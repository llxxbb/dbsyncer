package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板解析器 - 纯粹解析，不包含业务规则
 * <p>
 * 职责：
 * 1. 解析模板表达式，提取 C() 引用
 * 2. 从根转换器开始，拓扑排序生成执行顺序
 * 3. 生成执行计划
 * <p>
 * 注意：
 * - 只负责解析，不包含业务规则
 * - root: 根转换器（解析起点，规则保证唯一）
 * - converts: 所有转换器（用于查找依赖）
 */
public class TemplateParser {

    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");
    private static final int MAX_DEPENDENCY_DEPTH = 10;

    /**
     * 解析模板并生成执行计划
     *
     * @param template 模板表达式
     * @param root     根转换器（解析起点，规则保证唯一）
     * @param converts 所有转换器（用于查找依赖）
     * @return 解析结果（包含执行顺序和引用映射）
     */
    public ParseResult parseTemplate(String template, Convert root, List<Convert> converts) {
        if (StringUtil.isBlank(template)) {
            return new ParseResult();
        }

        ParseResult result = new ParseResult();
        result.setOriginalExpression(template);

        // 1. 提取模板中引用的所有 C() 引用
        Set<String> templateRefs = extractReferences(template);

        // 2. 从根转换器开始，构建执行顺序
        List<String> executionOrder = buildExecutionOrder(templateRefs, root, converts);
        result.setExecutionOrder(executionOrder);

        // 3. 构建引用映射
        Map<String, Convert> refMap = buildReferenceMap(executionOrder, converts);
        result.setReferenceMap(refMap);

        return result;
    }

    /**
     * 从根转换器开始，构建执行顺序
     * <p>
     * 规则：
     * 1. 如果模板没有 C() 引用，返回空顺序
     * 2. 如果模板有 C() 引用，验证必须是根转换器
     */
    private List<String> buildExecutionOrder(Set<String> templateRefs, Convert root, List<Convert> converts) {
        List<String> order = new ArrayList<>();

        // 如果没有 C() 引用，无需解析
        if (templateRefs.isEmpty()) {
            return order;
        }

        String rootRefId = buildRefId(root.getConvertCode(), root.getId());

        // 验证：模板必须从根转换器开始
        if (!templateRefs.contains(rootRefId)) {
            throw new ParserException("Must start from root converter: " + rootRefId);
        }

        Set<String> visited = new HashSet<>();
        Set<String> tempMark = new HashSet<>();

        // 从根转换器开始解析
        visit(rootRefId, converts, order, visited, tempMark, 0);

        return order;
    }

    /**
     * DFS 遍历 - 从根转换器开始，递归解析依赖
     */
    private void visit(String refId, List<Convert> converts,
                       List<String> order, Set<String> visited, Set<String> tempMark,
                       int depth) {
        if (depth > MAX_DEPENDENCY_DEPTH) {
            throw new ParserException("Maximum dependency depth exceeded: " + MAX_DEPENDENCY_DEPTH);
        }

        if (tempMark.contains(refId)) {
            throw new ParserException("Circular reference detected involving: " + refId);
        }

        if (visited.contains(refId)) {
            return;
        }

        tempMark.add(refId);

        // 在所有转换器中查找（包含依赖链中的非根转换器）
        Convert convert = findConvert(converts, refId);
        if (convert != null && StringUtil.isNotBlank(convert.getArgs())) {
            Set<String> deps = extractReferences(convert.getArgs());
            for (String dep : deps) {
                visit(dep, converts, order, visited, tempMark, depth + 1);
            }
        }

        tempMark.remove(refId);
        visited.add(refId);
        order.add(refId);
    }

    /**
     * 构建引用映射
     */
    private Map<String, Convert> buildReferenceMap(List<String> executionOrder, List<Convert> converts) {
        Map<String, Convert> map = new HashMap<>();
        for (String refId : executionOrder) {
            Convert convert = findConvert(converts, refId);
            if (convert != null) {
                map.put(refId, convert);
            }
        }
        return map;
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
     * 根据引用ID查找Convert（在所有转换器中查找）
     */
    private Convert findConvert(List<Convert> converts, String refId) {
        if (converts == null || refId == null) {
            return null;
        }

        String[] parts = refId.split(":", 2);
        String code = parts[0];
        String id = parts.length > 1 ? parts[1] : "";

        for (Convert convert : converts) {
            if (code.equals(convert.getConvertCode())) {
                if (id.equals(convert.getId())) {
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
        return code + ":" + (id != null ? id : "");
    }
}
