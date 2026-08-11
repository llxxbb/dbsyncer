package org.dbsyncer.parser.error;

import org.dbsyncer.common.retry.KeywordMatcher;
import org.dbsyncer.common.util.MatchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "dbsyncer.error")
public class ErrorQueueConfig {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int threshold = 0;

    private MatchMode fatalMatchMode = MatchMode.CONTAINS_IGNORE_CASE;

    private List<String> fatalKeywords = new ArrayList<>();

    private volatile KeywordMatcher fatalMatcher;

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public MatchMode getFatalMatchMode() {
        return fatalMatchMode;
    }

    public void setFatalMatchMode(MatchMode fatalMatchMode) {
        this.fatalMatchMode = fatalMatchMode;
        this.fatalMatcher = null;
    }

    public List<String> getFatalKeywords() {
        return fatalKeywords;
    }

    public void setFatalKeywords(List<String> fatalKeywords) {
        this.fatalKeywords = fatalKeywords;
        this.fatalMatcher = null;
    }

    public boolean isFatalError(String message) {
        if (fatalKeywords == null || fatalKeywords.isEmpty()) {
            return false;
        }
        if (fatalMatcher == null) {
            synchronized (this) {
                if (fatalMatcher == null) {
                    List<String> validKeywords = fatalKeywords.stream()
                            .filter(k -> k != null && !k.isEmpty())
                            .collect(Collectors.toList());
                    fatalMatcher = new KeywordMatcher(validKeywords, fatalMatchMode);
                    logger.info("致命关键字匹配器已初始化: matchMode={}, keywords={}", fatalMatchMode, validKeywords);
                }
            }
        }
        String matched = fatalMatcher.findMatch(message);
        if (matched != null) {
            logger.warn("检测到致命错误消息: keyword={}, message={}", matched, message);
        }
        return matched != null;
    }
}
