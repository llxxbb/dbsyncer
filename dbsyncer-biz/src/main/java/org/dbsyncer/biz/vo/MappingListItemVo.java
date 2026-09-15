package org.dbsyncer.biz.vo;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务列表项，是 static/js/shared/task-list.js 的唯一数据契约，
 * 服务于首页任务列表、搜索结果、异常任务列表与数据源关联任务列表四个接口。
 * <p>
 * 字段范围由 task-list.js 的实际读取决定，不可直接返回 {@link MappingVo}：
 * 后者嵌套的连接器对象图会把数据库明文凭据与源库全量表清单一并序列化下发。详见 ADR-0033。
 * <p>
 * 编辑/详情流程不依赖本 VO：前端仅取 id 跳转 /mapping/page/edit?id=，页面数据由服务端渲染
 * 与 /mapping/get 提供。
 *
 * @author lxb
 */
public class MappingListItemVo {

    // 驱动ID
    private String id;
    // 驱动名称
    private String name;
    // 同步方式
    private String model;
    // 创建时间
    private Long createTime;
    // 更新时间
    private Long updateTime;
    // 元信息（运行状态、读写总数、失败数、错误信息）
    private MetaVo meta;
    // 源连接器摘要
    private ConnectorBriefVo sourceConnector;
    // 目标连接器摘要
    private ConnectorBriefVo targetConnector;

    /**
     * 从 {@link MappingVo} 投影为列表项。
     */
    public static MappingListItemVo from(MappingVo mapping) {
        MappingListItemVo vo = new MappingListItemVo();
        vo.setId(mapping.getId());
        vo.setName(mapping.getName());
        vo.setModel(mapping.getModel());
        vo.setCreateTime(mapping.getCreateTime());
        vo.setUpdateTime(mapping.getUpdateTime());
        vo.setMeta(mapping.getMeta());
        vo.setSourceConnector(ConnectorBriefVo.from(mapping.getSourceConnector()));
        vo.setTargetConnector(ConnectorBriefVo.from(mapping.getTargetConnector()));
        return vo;
    }

    public static List<MappingListItemVo> fromList(List<MappingVo> mappings) {
        return mappings.stream().map(MappingListItemVo::from).collect(Collectors.toList());
    }

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

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
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

    public ConnectorBriefVo getSourceConnector() {
        return sourceConnector;
    }

    public void setSourceConnector(ConnectorBriefVo sourceConnector) {
        this.sourceConnector = sourceConnector;
    }

    public ConnectorBriefVo getTargetConnector() {
        return targetConnector;
    }

    public void setTargetConnector(ConnectorBriefVo targetConnector) {
        this.targetConnector = targetConnector;
    }
}
