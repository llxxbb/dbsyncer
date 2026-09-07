/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.impl;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.common.model.ConfigModel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.storage.StorageService;
import org.springframework.stereotype.Component;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.SnowflakeIdWorker;

import javax.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-21 23:18
 */
@Component
public class LogServiceImpl implements LogService {

    @Lazy
    @Resource
    private ConfigRegistry configRegistry;

    @Lazy
    @Resource
    private StorageService storageService;

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    @Resource
    private org.dbsyncer.common.config.ExecutorFactory executorFactory;

    @Override
    public void log(Object model, LogType logType, LogLevel logLevel, String msg) {
        String modelType = "system";
        String modelId = "UNKNOWN";
        
        if (model instanceof ConfigModel) {
            ConfigModel configModel = (ConfigModel) model;
            modelType = configModel.getType();
            String id = configModel.getId();
            if (id != null && !id.isEmpty()) {
                modelId = id;
            }
        } else if (model instanceof Map) {
            Map<String, Object> modelMap = (Map<String, Object>) model;
            modelType = (String) modelMap.getOrDefault("type", "system");
            String id = (String) modelMap.get("id");
            if (id != null && !id.isEmpty()) {
                modelId = id;
            }
        }
        
        asyncWrite(modelType, modelId, logType, logLevel, msg);
    }

    private void asyncWrite(String modelType, String modelId, LogType logType, LogLevel logLevel, String msg) {
        executorFactory.getStorageExecutor().execute(() -> {
            Map<String, Object> params = new HashMap();
            params.put(ConfigConstant.CONFIG_MODEL_ID, String.valueOf(snowflakeIdWorker.nextId()));
            params.put(ConfigConstant.CONFIG_MODEL_TYPE, modelType);
            params.put(ConfigConstant.LOG_MODEL_ID, modelId);
            params.put(ConfigConstant.LOG_TYPE, logType.name());
            params.put(ConfigConstant.LOG_LEVEL, logLevel.name());
            int maxLen = 4000;
            try {
                if (configRegistry.getConfig() != null && configRegistry.getConfig().getBasic() != null) {
                    maxLen = configRegistry.getConfig().getBasic().getMaxStorageErrorLength();
                }
            } catch (Exception e) { /* ignore */ }
            params.put("json", StringUtil.substring(msg, 0, maxLen));
            params.put("createTime", Instant.now().toEpochMilli());
            try {
                storageService.add(StorageEnum.LOG, params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

}
