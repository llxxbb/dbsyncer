package org.dbsyncer.parser.util;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ParserException;
import org.dbsyncer.parser.model.Convert;
import org.dbsyncer.parser.model.ParseResult;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateParser {

    private static final Pattern C_PATTERN = Pattern.compile("C\\(([^:)]+)(?::([^)]+))?\\)");

    private static final int DEFAULT_MAX_DEPENDENCY_DEPTH = 10;

    /**
     * 最大依赖深度（可通过系统属性配置：dbsyncer.template.maxDepth）
     */
    private final int maxDependencyDepth;

    public TemplateParser() {
        this.maxDependencyDepth = getMaxDepthFromConfig();
    }

    private int getMaxDepthFromConfig() {
        String config = System.getProperty("dbsyncer.template.maxDepth");
        if (StringUtil.isNotBlank(config)) {
            try {
                int depth = Integer.parseInt(config);
                return depth > 0 ? depth : DEFAULT_MAX_DEPENDENCY_DEPTH;
            } catch (NumberFormatException e) {
                // 忽略解析错误，使用默认值
            }
        }
        return DEFAULT_MAX_DEPENDENCY_DEPTH;
    }

    public ParseResult parseTemplate(String template, Convert root, List<Convert> converts) {
        if (StringUtil.isBlank(template)) {
            return new ParseResult();
        }

        Set<String> templateRefs = extractReferences(template);

        List<String> executionOrder = buildExecutionOrder(templateRefs, root, converts);

        Map<String, Convert> refMap = buildReferenceMap(executionOrder, converts);

        return new ParseResult(executionOrder, refMap, template);
    }

    private List<String> buildExecutionOrder(Set<String> templateRefs, Convert root, List<Convert> converts) {
        List<String> order = new ArrayList<>();

        if (templateRefs.isEmpty()) {
            return order;
        }

        String rootRefId = buildRefId(root.getConvertCode(), root.getId());

        if (!templateRefs.contains(rootRefId)) {
            throw new ParserException("Must start from root converter: " + rootRefId);
        }

        Set<String> visited = new HashSet<>();
        Set<String> tempMark = new HashSet<>();

        visit(rootRefId, converts, order, visited, tempMark, 0);

        return order;
    }

    private void visit(String refId, List<Convert> converts,
                       List<String> order, Set<String> visited, Set<String> tempMark,
                       int depth) {
        if (depth > maxDependencyDepth) {
            throw new ParserException("Maximum dependency depth exceeded: " + maxDependencyDepth + 
                    " (configurable via -Ddbsyncer.template.maxDepth=N)");
        }

        if (tempMark.contains(refId)) {
            throw new ParserException("Circular reference detected involving: " + refId);
        }

        if (visited.contains(refId)) {
            return;
        }

        tempMark.add(refId);

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

    private String buildRefId(String code, String id) {
        return code + ":" + (id != null ? id : "");
    }
}