package org.dbsyncer.common.retry;

import org.dbsyncer.common.util.StringUtil;

import java.util.List;

/**
 * 关键字匹配器
 * 支持多种匹配模式：精确、包含、忽略大小写等
 */
public class KeywordMatcher {

    private final List<String> keywords;
    private final RetryPolicy.MatchMode matchMode;

    public KeywordMatcher(List<String> keywords, RetryPolicy.MatchMode matchMode) {
        this.keywords = keywords;
        this.matchMode = matchMode;
    }

    /**
     * 判断消息是否匹配任一关键字
     *
     * @param message 待匹配的消息
     * @return 是否匹配
     */
    public boolean matches(String message) {
        if (message == null || keywords == null || keywords.isEmpty()) {
            return false;
        }

        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            if (match(message, keyword)) {
                return true;
            }
        }
        return false;
    }

    boolean match(String message, String keyword) {
        if (message == null) {
            return false;
        }
        switch (matchMode) {
            case EXACT:
                return StringUtil.equals(message, keyword);
            case CONTAINS:
                return StringUtil.contains(message, keyword);
            case CASE_INSENSITIVE:
                return StringUtil.equalsIgnoreCase(message, keyword);
            case CONTAINS_IGNORE_CASE:
                return StringUtil.containsIgnoreCase(message, keyword);
            default:
                return StringUtil.contains(message, keyword);
        }
    }
}
