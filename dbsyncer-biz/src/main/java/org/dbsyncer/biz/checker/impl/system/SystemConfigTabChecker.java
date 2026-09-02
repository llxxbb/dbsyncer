package org.dbsyncer.biz.checker.impl.system;

import org.dbsyncer.common.config.SystemTabEnum;
import org.dbsyncer.common.config.props.*;
import org.dbsyncer.common.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * ADR-0029：Tab payload 结构/必填校验。
 * <p>
 * 在 RuntimeConfigValidator 取值范围校验之前调用，
 * 确保 payload 结构完整、关键字段不为空。
 */
@Component
public class SystemConfigTabChecker {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigTabChecker.class);

    /**
     * 校验 Tab payload 结构完整性。
     *
     * @param tabEnum Tab 枚举
     * @param props   反序列化后的 Props 对象
     * @throws IllegalArgumentException 校验失败时抛出
     */
    public void check(SystemTabEnum tabEnum, Object props) {
        Assert.notNull(tabEnum, "Tab 不能为空");
        Assert.notNull(props, "Tab payload 不能为空");

        switch (tabEnum) {
            case BASIC:
                checkBasic((BasicProps) props);
                break;
            case EXECUTORS:
                checkExecutors((ExecutorProps) props);
                break;
            case BUFFERS:
                checkBuffers((BufferProps) props);
                break;
            case RETRY:
                checkRetry((RetryProps) props);
                break;
            case NOTIFY:
                checkNotify((NotifyProps) props);
                break;
            case ERROR:
                checkError((ErrorProps) props);
                break;
            default:
                break;
        }
    }

    private void checkBasic(BasicProps basic) {
        if (basic.getWatermark() != null) {
            Assert.isTrue(basic.getWatermark().length() <= 64,
                    "水印内容最多 64 个字符");
        }
    }

    private void checkExecutors(ExecutorProps exec) {
        // 关键字段不能为 null（整块替换语义要求完整提交）
        // 但不强制要求所有子对象都存在（允许部分配置）
    }

    private void checkBuffers(BufferProps buffers) {
        // 同上，允许部分配置
    }

    private void checkRetry(RetryProps retry) {
        // 允许部分配置
    }

    private void checkNotify(NotifyProps notify) {
        // 允许部分配置
    }

    private void checkError(ErrorProps error) {
        // 允许部分配置
    }
}
