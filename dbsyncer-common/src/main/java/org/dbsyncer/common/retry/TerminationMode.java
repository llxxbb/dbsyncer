package org.dbsyncer.common.retry;

/**
 * 重试终止模式
 */
public enum TerminationMode {
    /** 仅按最大次数终止，忽略 maxDuration */
    MAX_ATTEMPTS,
    /** 仅按总耗时上限终止，忽略 maxAttempts */
    MAX_DURATION,
    /** 最大次数和总耗时哪个先到就终止（默认） */
    WHICHEVER_FIRST
}
