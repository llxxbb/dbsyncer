package org.dbsyncer.common.util;

/**
 * 关键字匹配模式
 */
public enum MatchMode {
    /** 精确匹配 */
    EXACT,
    /** 包含匹配 */
    CONTAINS,
    /** 精确匹配（忽略大小写） */
    CASE_INSENSITIVE,
    /** 包含匹配（忽略大小写） */
    CONTAINS_IGNORE_CASE
}
