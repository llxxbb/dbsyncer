package org.dbsyncer.parser.error;

import org.dbsyncer.common.config.props.ErrorProps;
import org.dbsyncer.common.retry.KeywordMatcher;
import org.dbsyncer.common.util.MatchMode;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 错误队列配置。
 * <p>
 * 配置统一从 ConfigRegistry 读取（ErrorProps，C 类）。
 * 启动时由 PreloadTemplate 触发 onConfigChanged 完成初始化。
 */
@Component
public class ErrorQueueConfig implements ConfigChangeListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ConfigRegistry configRegistry;

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

    @Override
    public void onConfigChanged(SystemConfig newConfig) {
        // 从 mergedConfig 读取最终配置（DB 优先，Environment 兜底）
        ErrorProps error = configRegistry.getConfig().getError();
        if (error == null) return;
        if (error.getThreshold() != null) {
            this.threshold = error.getThreshold();
        }
        if (error.getFatalMatchMode() != null) {
            this.fatalMatchMode = error.getFatalMatchMode();
            this.fatalMatcher = null;
        }
        if (error.getFatalKeywords() != null) {
            this.fatalKeywords = error.getFatalKeywords();
            this.fatalMatcher = null;
        }
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
