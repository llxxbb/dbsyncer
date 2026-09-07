/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.flush.impl;

import org.dbsyncer.common.config.ExecutorFactory;
import org.dbsyncer.parser.flush.AbstractBufferActuator;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.BufferActuatorProps;
import org.dbsyncer.common.config.props.BufferProps;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.parser.model.StorageRequest;
import org.dbsyncer.parser.model.StorageResponse;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.storage.StorageService;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;

/**
 * 持久化执行器
 *
 * @Version 1.0.0
 * @Author AE86
 * @Date 2023-03-27 16:50
 */
@Component
public final class StorageBufferActuator extends AbstractBufferActuator<StorageRequest, StorageResponse> {

    @Lazy
    @Resource
    private StorageService storageService;

    @Resource
    private ExecutorFactory executorFactory;

    @Resource
    private ConfigRegistry configRegistry;

    @PostConstruct
    private void init() {
        setConfigRegistry(configRegistry);
    }

    @Override
    protected BufferActuatorProps getBufferProps() {
        BufferProps buffers = configRegistry.getConfig().getBuffers();
        return buffers != null && buffers.getStorage() != null ? buffers.getStorage() : new BufferActuatorProps();
    }

    @Override
    protected String getPartitionKey(StorageRequest request) {
        return request.getMetaId();
    }

    @Override
    protected void partition(StorageRequest request, StorageResponse response) {
        response.setMetaId(request.getMetaId());
        response.getDataList().add(request.getRow());
    }

    @Override
    public void pull(StorageResponse response) {
        executorFactory.getStorageExecutor().execute(() -> {
            try {
                storageService.addBatch(StorageEnum.DATA, response.getMetaId(), response.getDataList());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void offerFailed(BlockingQueue<StorageRequest> queue, StorageRequest request) {
        Meta meta = profileComponent.getMeta(request.getMetaId());
        if (meta != null) {
            Mapping mapping = profileComponent.getMapping(meta.getMappingId());
            if (mapping != null) {
                logger.info("{}, data={}", mapping.getName(), request.getRow());
            }
        }
    }

    @Override
    public Executor getExecutor() {
        return executorFactory.getStorageExecutor();
    }
}
