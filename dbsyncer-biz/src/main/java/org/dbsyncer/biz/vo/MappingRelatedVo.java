package org.dbsyncer.biz.vo;

/**
 * 数据源关联的驱动VO（用于数据源页面显示关联任务列表）
 *
 * @author AE86
 * @version 1.0.0
 */
public class MappingRelatedVo {

    /**
     * 驱动ID
     */
    private String id;

    /**
     * 驱动名称
     */
    private String name;

    /**
     * 同步方式
     */
    private String model;

    /**
     * 更新时间
     */
    private Long updateTime;

    /**
     * 元信息
     */
    private MetaVo meta;

    /**
     * 源连接器
     */
    private ConnectorVo sourceConnector;

    /**
     * 目标连接器
     */
    private ConnectorVo targetConnector;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public MetaVo getMeta() {
        return meta;
    }

    public void setMeta(MetaVo meta) {
        this.meta = meta;
    }

    public ConnectorVo getSourceConnector() {
        return sourceConnector;
    }

    public void setSourceConnector(ConnectorVo sourceConnector) {
        this.sourceConnector = sourceConnector;
    }

    public ConnectorVo getTargetConnector() {
        return targetConnector;
    }

    public void setTargetConnector(ConnectorVo targetConnector) {
        this.targetConnector = targetConnector;
    }
}
