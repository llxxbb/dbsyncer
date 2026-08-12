package org.dbsyncer.common.model;

import java.util.LinkedList;
import java.util.List;

public class Result<T> {

    /**
     * 成功数据
     */
    private final List<T> successData = new LinkedList<>();

    /**
     * 错误数据
     */
    private final List<T> failData = new LinkedList<>();

    /**
     * 每条错误数据对应的失败原因（与 failData 一一对应）
     */
    private final List<String> failReasons = new LinkedList<>();

    /**
     * 驱动表映射关系ID
     */
    private String tableGroupId;

    /**
     * 映射关系ID
     */
    private String mappingId;

    /**
     * 目标表名称
     */
    private String targetTableGroupName;

    private final Object LOCK = new Object();

    public Result() {
    }

    public Result(List<T> data) {
        this.successData.addAll(data);
    }

    public List<T> getSuccessData() {
        return successData;
    }

    public List<T> getFailData() {
        return failData;
    }

    public List<String> getFailReasons() {
        return failReasons;
    }

    /**
     * 线程安全添加单条错误数据及其原因
     *
     * @param data   错误数据
     * @param reason 失败原因
     */
    public void addFailData(T data, String reason) {
        synchronized (LOCK) {
            this.failData.add(data);
            this.failReasons.add(reason);
        }
    }

    /**
     * 线程安全添加集合
     *
     * @param failData
     */
    public void addFailData(List failData) {
        synchronized (LOCK) {
            this.failData.addAll(failData);
            for (int i = 0; i < failData.size(); i++) {
                this.failReasons.add(null);
            }
        }
    }

    /**
     * 线程安全添加集合
     *
     * @param successData
     */
    public void addSuccessData(List successData) {
        synchronized (LOCK) {
            this.successData.addAll(successData);
        }
    }

    public String getTableGroupId() {
        return tableGroupId;
    }

    public void setTableGroupId(String tableGroupId) {
        this.tableGroupId = tableGroupId;
    }

    public String getTargetTableGroupName() {
        return targetTableGroupName;
    }

    public void setTargetTableGroupName(String targetTableGroupName) {
        this.targetTableGroupName = targetTableGroupName;
    }

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
    }
}