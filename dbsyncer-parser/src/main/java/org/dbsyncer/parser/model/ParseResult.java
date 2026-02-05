package org.dbsyncer.parser.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板解析结果
 */
public class ParseResult {

    private List<String> executionOrder = new ArrayList<>();

    private Map<String, Convert> referenceMap = new HashMap<>();

    private String originalExpression;

    public List<String> getExecutionOrder() {
        return executionOrder;
    }

    public void setExecutionOrder(List<String> executionOrder) {
        this.executionOrder = executionOrder;
    }

    public Map<String, Convert> getReferenceMap() {
        return referenceMap;
    }

    public void setReferenceMap(Map<String, Convert> referenceMap) {
        this.referenceMap = referenceMap;
    }

    public String getOriginalExpression() {
        return originalExpression;
    }

    public void setOriginalExpression(String originalExpression) {
        this.originalExpression = originalExpression;
    }
}
