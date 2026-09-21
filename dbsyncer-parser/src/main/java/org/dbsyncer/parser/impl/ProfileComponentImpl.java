/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.impl;

import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.parser.CacheService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.enums.CommandEnum;
import org.dbsyncer.parser.enums.ConvertEnum;
import org.dbsyncer.parser.enums.GroupStrategyEnum;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.common.model.ConfigModel;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.sdk.model.*;
import org.dbsyncer.parser.service.TableGroupModelService;
import org.dbsyncer.sdk.enums.FilterEnum;
import org.dbsyncer.sdk.enums.OperationEnum;
import org.dbsyncer.sdk.enums.QuartzFilterEnum;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.sdk.spi.ConnectorService;
import org.dbsyncer.storage.enums.StorageDataStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Version 1.0.0
 * @Author AE86
 * @Date 2023-11-13 21:16
 */
@Component
@DependsOn("preloadTemplate")
public class ProfileComponentImpl implements ProfileComponent {

    private final Logger logger = LoggerFactory.getLogger(getClass());


    @Resource
    private OperationTemplate operationTemplate;

    @Resource
    private CacheService cacheService;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private TableGroupModelService tableGroupModelService;

    public ProfileComponentImpl() {
        logger.info("----ProfileComponentImpl init----");
    }   

    @Override
    public Connector parseConnector(String json) {
        Map<String, Object> conn = JsonUtil.parseMap(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) conn.remove("config");
        // 使用JsonUtil.objToJson而不是toString()，因为toString()生成的是{key=value}格式，不是JSON
        Connector connector = JsonUtil.jsonToObj(JsonUtil.objToJson(conn), Connector.class);
        Assert.notNull(connector, "Connector can not be null.");
        String connectorType = (String) config.get("connectorType");
        ConnectorService connectorService = connectorFactory.getConnectorService(connectorType);
        Class<ConnectorConfig> configClass = connectorService.getConfigClass();
        // 使用JsonUtil.objToJson而不是toString()，确保生成有效的JSON格式
        connector.setConfig(JsonUtil.jsonToObj(JsonUtil.objToJson(config), configClass));

        return connector;
    }

    @Override
    public <T> T parseObject(String json, Class<T> clazz) {
        return JsonUtil.jsonToObj(json, clazz);
    }

    @Override
    public String addConfigModel(ConfigModel model) throws Exception {
        return operationTemplate.execute(new OperationConfig(model, CommandEnum.OPR_ADD));
    }

    @Override
    public String editConfigModel(ConfigModel model) throws Exception {
        return operationTemplate.execute(new OperationConfig(model, CommandEnum.OPR_EDIT));
    }

    @Override
    public void removeConfigModel(String id) throws Exception {
        operationTemplate.remove(new OperationConfig(id));
    }

    @Override
    public SystemConfig getSystemConfig() {
        List<SystemConfig> list = operationTemplate.queryAll(SystemConfig.class);
        if (CollectionUtils.isEmpty(list)) {
            logger.warn("[系统配置] 不存在！");
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        // 多条：确定性选择（createTime 最早，平手取 id 最小）
        logger.warn("[系统配置] 检测到 {} 个重复配置，将选择 createTime 最早的配置", list.size());
        return list.stream()
            .min(Comparator.comparing(SystemConfig::getCreateTime)
                .thenComparing(SystemConfig::getId))
            .orElse(list.get(0));
    }

    @Override
    public UserConfig getUserConfig() {
        List<UserConfig> list = operationTemplate.queryAll(UserConfig.class);
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        // 多条：确定性选择（createTime 最早，平手取 id 最小）
        logger.warn("[用户配置] 检测到 {} 个重复配置，将选择 createTime 最早的配置", list.size());
        return list.stream()
            .min(Comparator.comparing(UserConfig::getCreateTime)
                .thenComparing(UserConfig::getId))
            .orElse(list.get(0));
    }

    @Override
    public ProjectGroup getProjectGroup(String id) {
        return operationTemplate.queryObject(ProjectGroup.class, id);
    }

    @Override
    public List<ProjectGroup> getProjectGroupAll() {
        return operationTemplate.queryAll(ProjectGroup.class);
    }

    @Override
    public String findProjectGroupId(String mappingId) {
        if (StringUtil.isBlank(mappingId)) {
            return null;
        }
        List<ProjectGroup> allGroups = getProjectGroupAll();
        if (CollectionUtils.isEmpty(allGroups)) {
            return null;
        }
        for (ProjectGroup group : allGroups) {
            if (!CollectionUtils.isEmpty(group.getMappingIds())
                    && group.getMappingIds().contains(mappingId)) {
                return group.getId();
            }
        }
        return null;
    }

    @Override
    public UserGroup getUserGroup(String id) {
        return operationTemplate.queryObject(UserGroup.class, id);
    }

    @Override
    public List<UserGroup> getUserGroupAll() {
        return operationTemplate.queryAll(UserGroup.class);
    }

    @Override
    public Connector getConnector(String connectorId) {
        return operationTemplate.queryObject(Connector.class, connectorId);
    }

    @Override
    public List<Connector> getConnectorAll() {
        return operationTemplate.queryAll(Connector.class);
    }

    @Override
    public Mapping getMapping(String mappingId) {
        return operationTemplate.queryObject(Mapping.class, mappingId);
    }

    @Override
    public List<Mapping> getMappingAll() {
        return operationTemplate.queryAll(Mapping.class);
    }

    @Override
    public String addTableGroup(TableGroup model) throws Exception {
        return operationTemplate.execute(new OperationConfig(model, CommandEnum.OPR_ADD, GroupStrategyEnum.TABLE));
    }

    @Override
    public String editTableGroup(TableGroup model) throws Exception {
        return operationTemplate.execute(new OperationConfig(model, CommandEnum.OPR_EDIT, GroupStrategyEnum.TABLE));
    }

    @Override
    public void removeTableGroup(String id) throws Exception {
        operationTemplate.remove(new OperationConfig(id, GroupStrategyEnum.TABLE));
    }

    @Override
    public TableGroup getTableGroup(String tableGroupId) throws Exception {
        TableGroup tableGroup = operationTemplate.queryObject(TableGroup.class, tableGroupId);
        return tableGroup;
    }

    @Override
    public List<TableGroup> getTableGroupAll(String mappingId) throws Exception {
        TableGroup temp = new TableGroup().setMappingId(mappingId);
        List<TableGroup> tableGroups = operationTemplate.queryAll(new QueryConfig<>(temp, GroupStrategyEnum.TABLE));
        for (TableGroup t : tableGroups){
            Mapping mapping = profileComponent.getMapping(mappingId);
            if (mapping != null) {
                tableGroupModelService.migrateVersion(t, mapping);
                tableGroupModelService.initTableGroup(t, mapping);
            }
        }
        return tableGroups;
    }

    @Override
    public List<TableGroup> getSortedTableGroupAll(String mappingId) throws Exception {
        return getTableGroupAll(mappingId)
                .stream()
                .sorted(Comparator.comparing(TableGroup::getIndex).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getTableGroupIds(String mappingId) {
        String groupId = ConfigConstant.TABLE_GROUP + "_" + mappingId;
        List<String> ids = new ArrayList<>();
        cacheService.getCache().computeIfPresent(groupId, (k, v) -> {
            Group group = (Group) v;
            ids.addAll(group.getIndex());
            return group;
        });
        return ids;
    }

    @Override
    public int getTableGroupCount(String mappingId) {
        TableGroup tableGroup = new TableGroup().setMappingId(mappingId);
        return operationTemplate.queryCount(new QueryConfig<>(tableGroup, GroupStrategyEnum.TABLE));
    }

    @Override
    public Meta getMeta(String metaId) {
        return operationTemplate.queryObject(Meta.class, metaId);
    }

    @Override
    public List<Meta> getMetaAll() {
        return operationTemplate.queryAll(Meta.class);
    }

    @Override
    public List<OperationEnum> getOperationEnumAll() {
        return Arrays.asList(OperationEnum.values());
    }

    @Override
    public List<QuartzFilterEnum> getQuartzFilterEnumAll() {
        return Arrays.asList(QuartzFilterEnum.values());
    }

    @Override
    public List<FilterEnum> getFilterEnumAll() {
        return Arrays.asList(FilterEnum.values());
    }

    @Override
    public List<ConvertEnum> getConvertEnumAll() {
        return Arrays.asList(ConvertEnum.values());
    }

    @Override
    public List<StorageDataStatusEnum> getStorageDataStatusEnumAll() {
        return Arrays.asList(StorageDataStatusEnum.values());
    }

}