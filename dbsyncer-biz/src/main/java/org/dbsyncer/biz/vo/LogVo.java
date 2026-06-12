package org.dbsyncer.biz.vo;

public class LogVo {

    private String id;
    private String modelType;    // 实体类型：mapping, system, connector
    private String modelId;      // 实体 ID
    private String logType;      // 日志类型：INSERT, UPDATE, DELETE, CONNECT
    private String logLevel;     // 日志级别：INFO, WARN, ERROR
    private String json;
    private long createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}
