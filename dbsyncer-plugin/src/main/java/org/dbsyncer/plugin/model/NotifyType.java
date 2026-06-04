package org.dbsyncer.plugin.model;

/**
 * 通知事件类型
 *
 * @author AE86
 * @version 1.0.0
 * @date 2026/06/03
 */
public enum NotifyType {

    /**
     * 任务异常中断
     */
    TASK_INTERRUPTED,

    /**
     * 数据进入错误队列
     */
    DATA_ERROR_QUEUE,

    /**
     * 手动操作（停止/重置/重新同步等）
     */
    MANUAL_OPERATION;

    /**
     * 获取通知类型的中文显示名称
     *
     * @return 中文名称
     */
    public String getName() {
        switch (this) {
            case TASK_INTERRUPTED:
                return "任务中断";
            case DATA_ERROR_QUEUE:
                return "数据错误";
            case MANUAL_OPERATION:
                return "手动操作";
            default:
                return name();
        }
    }
}
