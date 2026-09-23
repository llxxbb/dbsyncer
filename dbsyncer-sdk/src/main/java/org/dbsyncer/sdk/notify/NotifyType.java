package org.dbsyncer.sdk.notify;

/**
 * 通知事件类型
 *
 * @author AE86
 * @version 1.0.0
 * @date 2026/06/03
 */
public enum NotifyType {

    TASK_INTERRUPTED("任务中断", "任务因异常而停止同步，需要立即排查原因并恢复"),
    DATA_ERROR_QUEUE("数据错误", "同步数据写入失败进入错误队列，可能存在数据质量或映射问题"),
    MANUAL_OPERATION("手动操作", "用户手动执行了停止、重置或重新同步等操作，用于审计追踪"),
    DATA_ERROR_CIRCUIT_BREAKER("错误队列熔断", "错误队列达到熔断阈值，任务被自动暂停以防止错误堆积"),
    FATAL_MATCHED("致命关键字命中", "异常信息匹配致命关键字，任务被立即终止且不会重试"),
    DDL("DDL操作", "检测到表结构变更（增删改字段），可能影响后续数据同步"),
    TARGET_EXCEPTION("目标异常", "目标数据库写入异常，可能是连接、权限或数据冲突问题"),
    SOURCE_EXCEPTION("数据源异常", "源数据库读取异常，可能是连接断开、权限变更或源端故障");

    /**
     * 中文显示名称
     */
    private final String label;

    /**
     * 业务描述：说明为什么需要这个通知类型
     */
    private final String description;

    NotifyType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * @deprecated 使用 {@link #getLabel()} 代替
     */
    @Deprecated
    public String getName() {
        return label;
    }
}
