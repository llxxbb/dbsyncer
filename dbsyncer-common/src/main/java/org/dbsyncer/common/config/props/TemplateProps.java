package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.EnvironmentLoadable;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 模板引擎配置（C 类：立即生效）
 */
public class TemplateProps implements Serializable, EnvironmentLoadable {

    private static final long serialVersionUID = 1L;

    /**
     * 最大依赖深度。
     * <p>
     * <b>例外（ADR-0029 实施修订 R1 / D2）：保持装箱。</b>{@code dbsyncer.template.maxDepth}
     * 没有 Java 消费方，实际只经 {@code System.getProperty} 生效（见 config-ledger.md:174），
     * 不为它编造一个从未生效过的产品默认值。
     */
    private Integer maxDepth;

    public Integer getMaxDepth() { return maxDepth; }
    public void setMaxDepth(Integer maxDepth) { this.maxDepth = maxDepth; }

    public void loadFromEnvironment(Environment env) {
        String val = env.getProperty(ConfigKeys.TEMPLATE_MAX_DEPTH);
        if (val != null) {
            try {
                this.maxDepth = Integer.parseInt(val);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (maxDepth != null && maxDepth < 1) {
            errors.add("模板最大依赖深度必须 >= 1");
        }
        return errors;
    }

    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.TEMPLATE_MAX_DEPTH, maxDepth));
        return entries;
    }
}
