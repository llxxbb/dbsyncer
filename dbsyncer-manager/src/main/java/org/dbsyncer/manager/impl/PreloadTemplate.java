/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.manager.impl;

import org.dbsyncer.parser.impl.ConfigBootstrap;
import org.dbsyncer.common.model.Paging;
import org.dbsyncer.common.scheduled.StartupTaskRegistrar;
import org.dbsyncer.common.startup.StartupGate;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.connector.base.ConnectorFactory;
import org.dbsyncer.manager.ManagerFactory;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.command.impl.PreloadCommand;
import org.dbsyncer.parser.enums.CommandEnum;
import org.dbsyncer.parser.enums.GroupStrategyEnum;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.parser.impl.OperationTemplate;
import org.dbsyncer.parser.model.*;
import org.dbsyncer.common.model.ConfigModel;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.Mapping;

import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.filter.Query;
import org.dbsyncer.sdk.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 预加载配置模板
 *
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/16 23:59
 */
@Component
public final class PreloadTemplate implements ApplicationListener<ContextRefreshedEvent> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private OperationTemplate operationTemplate;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ManagerFactory managerFactory;

    @Resource
    private ConnectorFactory connectorFactory;

    @Resource
    private StorageService storageService;

    @Resource
    private LogService logService;

    @Resource
    private List<StartupGate> startupGates;

    @Resource
    private List<StartupTaskRegistrar> startupTaskRegistrars;

    @Resource
    private ConfigBootstrap configService;

    private boolean preloadCompleted;

    public PreloadTemplate() {
        logger.info("----PreloadTemplate init----");
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 1. Load configModels（含 PRELOAD_SYSTEM，唯一一次查库）
        Arrays.stream(CommandEnum.values()).filter(commandEnum -> commandEnum.isPreload()).forEach(commandEnum -> execute(commandEnum));

        // 2. 填充 Registry + 迁移 + 打印对照表（由 ConfigService 编排）
        configService.onPreloadCompleted(profileComponent.getSystemConfig());

        // 3. 配置已完全加载，注册启动期定时任务
        if (startupTaskRegistrars != null) {
            for (StartupTaskRegistrar registrar : startupTaskRegistrars) {
                try {
                    registrar.registerScheduledTasks();
                } catch (Exception e) {
                    logger.error("注册定时任务失败: {}", registrar.getClass().getSimpleName(), e);
                }
            }
        }

        // 4. Load connectorInstances
        loadConnectorInstance();

        logger.info("+++++++++++++++配置预加载完成++++++++++++++++");

        // 5. Launch drivers（最后启动任务）
        launch();

        // 6. 开放端口
        openStartupGates();
        preloadCompleted = true;
    }

    private void openStartupGates() {
        if (startupGates != null) {
            for (StartupGate gate : startupGates) {
                try {
                    gate.open();
                } catch (Exception e) {
                    logger.error("开放启动门闸失败", e);
                }
            }
        }
    }

    /**
     * 是否完成预加载配置
     *
     * @return
     */
    public boolean isPreloadCompleted() {
        return preloadCompleted;
    }

    public void reload(String json) {
        Map<String, Map> map = JsonUtil.jsonToObj(json, Map.class);
        if (CollectionUtils.isEmpty(map)) {
            return;
        }

        // Load configModels
        Stream.of(CommandEnum.PRELOAD_SYSTEM, CommandEnum.PRELOAD_USER, CommandEnum.PRELOAD_CONNECTOR, CommandEnum.PRELOAD_MAPPING,
                CommandEnum.PRELOAD_META, CommandEnum.PRELOAD_PROJECT_GROUP, CommandEnum.PRELOAD_USER_GROUP).forEach(commandEnum -> {
            try {
                reload(map, commandEnum);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Load connectorInstances
        loadConnectorInstance();

        // Launch drivers
        launch();
    }

    private void launch() {
        List<Meta> metas = profileComponent.getMetaAll();
        if (!CollectionUtils.isEmpty(metas)) {
            metas.forEach(m -> {
                // 恢复驱动状态
                if (MetaEnum.RUNNING.getCode() == m.getState()) {
                    Mapping mapping = profileComponent.getMapping(m.getMappingId());
                    try {
                        managerFactory.start(mapping);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    private void execute(CommandEnum commandEnum) {
        Query query = new Query();
        query.setType(StorageEnum.CONFIG);
        String modelType = commandEnum.getModelType();
        query.addFilter(ConfigConstant.CONFIG_MODEL_TYPE, modelType);

        int pageNum = 1;
        int pageSize = 20;
        long total = 0;
        for (; ; ) {
            query.setPageNum(pageNum);
            query.setPageSize(pageSize);
            Paging paging = storageService.query(query);
            List<Map> data = (List<Map>) paging.getData();
            if (CollectionUtils.isEmpty(data)) {
                break;
            }
            data.forEach(map -> {
                String json = (String) map.get(ConfigConstant.CONFIG_MODEL_JSON);
                ConfigModel model = null;
                try {
                    model = (ConfigModel) commandEnum.getCommandExecutor().execute(new PreloadCommand(profileComponent, json));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                if (null != model) {
                    operationTemplate.cache(model, commandEnum.getGroupStrategyEnum());

                }
            });
            total += data.size();
            pageNum++;
        }
        logger.info("{}:{}", modelType, total);
    }

    private void reload(Map<String, Map> map, CommandEnum commandEnum) throws Exception {
        reload(map, commandEnum, commandEnum.getModelType());
    }

    private void reload(Map<String, Map> map, CommandEnum commandEnum, String groupId) throws Exception {
        Map config = map.get(groupId);
        if (null == config) {
            return;
        }
        // 使用JsonUtil.objToJson而不是toString()，因为toString()生成的是{key=value}格式，不是JSON
        Group group = JsonUtil.jsonToObj(JsonUtil.objToJson(config), Group.class);
        if (null == group || group.isEmpty()) {
            return;
        }

        for (String id : group.getIndex()) {
            Map m = map.get(id);
            // 使用JsonUtil.objToJson而不是toString()，确保生成有效的JSON格式
            ConfigModel model = (ConfigModel) commandEnum.getCommandExecutor().execute(new PreloadCommand(profileComponent, JsonUtil.objToJson(m)));
            operationTemplate.execute(new OperationConfig(model, CommandEnum.OPR_ADD, commandEnum.getGroupStrategyEnum()));
            // Load tableGroups
            if (CommandEnum.PRELOAD_MAPPING == commandEnum) {
                reload(map, CommandEnum.PRELOAD_TABLE_GROUP, operationTemplate.getGroupId(model, GroupStrategyEnum.PRELOAD_TABLE_GROUP));
            }
        }
    }

    private void loadConnectorInstance() {
        List<Connector> list = profileComponent.getConnectorAll();
        if (!CollectionUtils.isEmpty(list)) {
            list.forEach(connector -> {
                try {
                    connectorFactory.disconnect(connector.getConfig());
                    connectorFactory.connect(connector.getConfig());
                } catch (Exception e) {
                    logger.error("连接配置异常", e);
                    logService.log(connector, LogType.CONNECT, String.format("连接器连接失败：connectorType=%s, error=%s", connector.getConfig().getConnectorType(), e.getMessage()));
                }
            });
        }
    }
}