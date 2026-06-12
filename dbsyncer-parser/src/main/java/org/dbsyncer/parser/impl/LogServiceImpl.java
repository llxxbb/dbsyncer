/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.impl;

import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.model.ConfigModel;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.sdk.storage.StorageService;
import org.springframework.stereotype.Component;
import org.dbsyncer.storage.impl.SnowflakeIdWorker;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * @version 1.0.0
 * @Author AE86
 * @Date 2020-05-21 23:18
 */
@Component
public class LogServiceImpl implements LogService {

    @Resource
    private StorageService storageService;

    @Resource
    private SnowflakeIdWorker snowflakeIdWorker;

    @Resource
    private Executor storageExecutor;

    @Resource
    private ProfileComponent profileComponent;

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
        storageExecutor.execute(() -> {
            Map<String, Object> params = new HashMap();
            params.put(ConfigConstant.CONFIG_MODEL_ID, String.valueOf(snowflakeIdWorker.nextId()));
            params.put(ConfigConstant.CONFIG_MODEL_TYPE, modelType);
            params.put(ConfigConstant.LOG_MODEL_ID, modelId);
            params.put(ConfigConstant.LOG_TYPE, logType.name());
            params.put(ConfigConstant.LOG_LEVEL, logLevel.name());
            int maxLen = 4000;
            try {
                if (profileComponent.getSystemConfig() != null) {
                    maxLen = profileComponent.getSystemConfig().getMaxStorageErrorLength();
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
