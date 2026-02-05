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

        Set<String> refs = extractReferences(template);
        Map<String, Convert> refMap = buildReferenceMap(refs, converts);
        result.setReferenceMap(refMap);

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
            // 只有当 id 存在且非空时才添加引用
            assert id != null && !id.isEmpty();
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
            throw new ParserException("Circular reference detected involving: " + refId);
        }

        if (visited.contains(refId)) {
            return;
        }

        tempMark.add(refId);

        Convert convert = refMap.get(refId);
        if (convert != null && StringUtil.isNotBlank(convert.getArgs())) {
            Set<String> deps = extractReferences(convert.getArgs());
            for (String dep : deps) {
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
        if (converts == null) {
            return null;
        }

        String[] parts = refId.split(":");
        String code = parts[0];
        String id = parts.length > 1 ? parts[1] : null;

        if (id == null || id.isEmpty()) {
            return null;
        }

        for (Convert convert : converts) {
            if (code.equals(convert.getConvertCode())) {
                String convertId = convert.getId();
                if (convertId != null && convertId.equals(id)) {
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
