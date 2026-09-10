package org.dbsyncer.biz.impl;

import org.dbsyncer.common.config.ExecutorFactory;
import org.dbsyncer.parser.flush.BufferActuator;
import org.dbsyncer.parser.flush.impl.BufferActuatorRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * ADR-0031：业务热重载编排器。
 * <p>
 * 替代 SelfRestartService 的进程级重启，只重建持有缓存资源的组件。
 */
@Component
public class BusinessReloader {

    private static final Logger logger = LoggerFactory.getLogger(BusinessReloader.class);

    @Resource
    private ExecutorFactory executorFactory;

    @Resource
    private List<BufferActuator> bufferActuators;

    @Resource
    private BufferActuatorRouter bufferActuatorRouter;

    /**
     * 执行热重载。
     *
     * @param changedKeys 变更的配置 Tab key 列表（如 ["executors", "buffers"]）
     */
    public void reload(List<String> changedKeys) {
        logger.info("===== 业务热重载开始: {} =====", changedKeys);

        if (changedKeys.contains("executors")) {
            try {
                executorFactory.reload();
            } catch (Exception e) {
                logger.error("[ExecutorFactory] 热重载失败", e);
            }
        }

        if (changedKeys.contains("buffers")) {
            // Spring 管理的 BufferActuator（如 StorageBufferActuator）
            for (BufferActuator actuator : bufferActuators) {
                try {
                    actuator.reload();
                } catch (Exception e) {
                    logger.error("[{}] 热重载失败", actuator.getClass().getSimpleName(), e);
                }
            }
            // 动态创建的 MetaBufferActuator（每个 meta 独享一个）
            bufferActuatorRouter.getMetaActuatorMap().forEach((metaId, actuator) -> {
                try {
                    actuator.reload();
                } catch (Exception e) {
                    logger.error("[MetaBufferActuator:{}] 热重载失败", metaId, e);
                }
            });
        }

        logger.info("===== 业务热重载完成 =====");
    }
}
