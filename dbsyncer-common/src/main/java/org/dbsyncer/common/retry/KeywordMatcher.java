package org.dbsyncer.common.retry;

import org.dbsyncer.common.util.MatchMode;
import org.dbsyncer.common.util.StringUtil;

import java.util.List;

/**
 * 关键字匹配器
 * 支持多种匹配模式：精确、包含、忽略大小写等
 */
public class KeywordMatcher {

    private final List<String> keywords;
    private final MatchMode matchMode;

    public KeywordMatcher(List<String> keywords, MatchMode matchMode) {
        this.keywords = keywords;
        this.matchMode = matchMode;
    }

    /**
     * 查找消息中匹配的关键字
     *
     * @param message 待匹配的消息
     * @return 匹配到的关键字，未匹配返回 null
     */
    public String findMatch(String message) {
        if (message == null || keywords == null || keywords.isEmpty()) {
            return null;
        }

        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            if (match(message, keyword)) {
                return keyword;
            }
        }
        return null;
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
