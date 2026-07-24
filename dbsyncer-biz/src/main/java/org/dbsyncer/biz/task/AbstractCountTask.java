/**
 * DBSyncer Copyright 2020-2025 All Rights Reserved.
 */
package org.dbsyncer.biz.task;

import org.dbsyncer.biz.TableGroupService;
import org.dbsyncer.common.dispatch.AbstractDispatchTask;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.Connector;
import org.dbsyncer.sdk.enums.SyncPhaseEnum;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.parser.util.PickerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * 抽象类统计驱动总数任务
 *
 * @Author 穿云
 * @Version 1.0.0
 * @Date 2025-06-25 01:00
 */
public abstract class AbstractCountTask extends AbstractDispatchTask {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    protected String mappingId;

    protected ProfileComponent profileComponent;

    protected TableGroupService tableGroupService;

    protected ConnectorFactory connectorFactory;

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
    }

    public void setProfileComponent(ProfileComponent profileComponent) {
        this.profileComponent = profileComponent;
    }

    public void setTableGroupService(TableGroupService tableGroupService) {
        this.tableGroupService = tableGroupService;
    }

    public void setConnectorFactory(ConnectorFactory connectorFactory) {
        this.connectorFactory = connectorFactory;
    }

    protected void updateTableGroupCount(Mapping mapping, TableGroup tableGroup) throws Exception {
        long now = Instant.now().toEpochMilli();
        TableGroup group = PickerUtil.mergeTableGroupConfig(mapping, tableGroup);
        Connector connector = profileComponent.getConnector(mapping.getSourceConnectorId());
        long count = connectorFactory.getCount(connectorFactory.connect(connector.getConfig()), group.getCommand());
        tableGroup.getSourceTable().setCount(count);
        profileComponent.editConfigModel(tableGroup);
        logger.info("{}表{}, 总数:{}, {}ms", mapping.getName(), tableGroup.getSourceTable().getName(), count, (Instant.now().toEpochMilli() - now));
    }

    protected boolean shouldStop(Mapping mapping) {
        Meta meta = profileComponent.getMeta(mapping.getMetaId());
        return SyncPhaseEnum.FULL != meta.getSyncPhase();
    }
}
