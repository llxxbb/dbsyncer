/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.common.config;

import org.dbsyncer.common.retry.RetryPolicy;
import org.springframework.context.annotation.Configuration;

/**
 * 重试配置。
 * <p>
 * 配置统一从 ConfigRegistry 读取（RetryProps，C 类），
 * 由 RetryConfigAdapter 在配置变更时同步更新。
 *
 * @author DBSyncer
 * @version 1.0.0
 */
@Configuration
public class RetryConfig {

    /** 全局默认重试策略 */
    private RetryPolicy global = new RetryPolicy();

    public RetryPolicy getGlobal() {
        return global;
    }

    public void setGlobal(RetryPolicy global) {
        this.global = global;
    }
}
