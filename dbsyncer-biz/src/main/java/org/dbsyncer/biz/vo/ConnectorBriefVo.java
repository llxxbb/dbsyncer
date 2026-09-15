package org.dbsyncer.biz.vo;

/**
 * 连接器摘要，仅用于任务列表渲染。
 * <p>
 * 不可替换为 {@link ConnectorVo}：后者继承 Connector，携带 config（含数据库 url/username/password
 * 明文）与 table（源库全量表清单），序列化后会把凭据下发到浏览器。详见 ADR-0033。
 *
 * @author lxb
 */
public class ConnectorBriefVo {

    // 连接器名称
    private String name;
    // 是否运行
    private boolean running;
    // 连接器类型，前端据此取 /img/{connectorType}.png 图标；取自 config.connectorType
    private String connectorType;

    public ConnectorBriefVo() {
    }

    public ConnectorBriefVo(String name, boolean running, String connectorType) {
        this.name = name;
        this.running = running;
        this.connectorType = connectorType;
    }

    /**
     * 从 {@link ConnectorVo} 投影，只取列表渲染所需的三个字段。
     */
    public static ConnectorBriefVo from(ConnectorVo connector) {
        if (connector == null) {
            return null;
        }
        String connectorType = connector.getConfig() == null ? null : connector.getConfig().getConnectorType();
        return new ConnectorBriefVo(connector.getName(), connector.isRunning(), connectorType);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getConnectorType() {
        return connectorType;
    }

    public void setConnectorType(String connectorType) {
        this.connectorType = connectorType;
    }
}
