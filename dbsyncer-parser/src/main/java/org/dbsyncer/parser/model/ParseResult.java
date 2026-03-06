package org.dbsyncer.parser.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 模板解析结果（不可变）
 * <p>
 * 线程安全：所有字段均为 final，集合通过不可变包装保护
 */
public final class ParseResult {

    private final List<String> executionOrder;

    private final Map<String, Convert> referenceMap;

    private final String originalExpression;

    /**
     * 创建空的解析结果
     */
    public ParseResult() {
        this.executionOrder = Collections.emptyList();
        this.referenceMap = Collections.emptyMap();
        this.originalExpression = null;
    }

    /**
     * 创建解析结果
     *
     * @param executionOrder     执行顺序（不可变副本）
     * @param referenceMap       引用映射（不可变副本）
     * @param originalExpression 原始表达式
     */
    public ParseResult(List<String> executionOrder, Map<String, Convert> referenceMap, String originalExpression) {
        this.executionOrder = executionOrder != null 
                ? Collections.unmodifiableList(executionOrder) 
                : Collections.emptyList();
        this.referenceMap = referenceMap != null 
                ? Collections.unmodifiableMap(referenceMap) 
                : Collections.emptyMap();
        this.originalExpression = originalExpression;
    }

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public Map<String, Convert> getReferenceMap() {
        return referenceMap;
    }

    public String getOriginalExpression() {
        return originalExpression;
    }
}