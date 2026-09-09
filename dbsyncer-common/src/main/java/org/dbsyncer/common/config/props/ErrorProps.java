package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.dbsyncer.common.util.MatchMode;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tab 6 错误处理。
 * 标量不装箱（ADR-0029 实施修订 R1 / D1）：类型默认值就是有效值，运行期不出现 null。
 */
public class ErrorProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    private int threshold = 1;
    private MatchMode fatalMatchMode;
    private List<String> fatalKeywords;

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
    }

    public List<String> getFatalKeywords() {
        return fatalKeywords;
    }

    public void setFatalKeywords(List<String> fatalKeywords) {
        this.fatalKeywords = fatalKeywords;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadFromEnvironment(Environment env) {
        this.threshold = env.getProperty(ConfigKeys.ERROR_THRESHOLD, Integer.class, threshold);
        String fatalMatchMode = env.getProperty(ConfigKeys.ERROR_FATAL_MATCH_MODE, String.class);
        if (fatalMatchMode != null) {
            try {
                this.fatalMatchMode = MatchMode.valueOf(fatalMatchMode);
            } catch (IllegalArgumentException ignored) {
            }
        }
        List<String> fatalKeywords = env.getProperty(ConfigKeys.ERROR_FATAL_KEYWORDS, List.class);
        if (fatalKeywords != null) {
            this.fatalKeywords = fatalKeywords;
        }
    }

    /**
     * 校验错误处理配置，返回错误信息列表。
     *
     * @return 错误信息列表，为空表示校验通过
     */
    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (threshold < 0) {
            errors.add("熔断阈值不能 < 0（0 表示不启用熔断）");
        }
        return errors;
    }

    /**
     * 返回配置条目列表，用于启动期配置对照表打印。
     */
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.ERROR_THRESHOLD, threshold));
        entries.add(new ConfigEntry(ConfigKeys.ERROR_FATAL_MATCH_MODE, fatalMatchMode));
        entries.add(new ConfigEntry(ConfigKeys.ERROR_FATAL_KEYWORDS, fatalKeywords));
        return entries;
    }
}
