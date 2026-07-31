package org.dbsyncer.biz.impl;

import org.dbsyncer.manager.ManagerFactory;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.error.CircuitBreakEvent;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class CircuitBreakerEventListener {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ManagerFactory managerFactory;

    @Resource
    private ProfileComponent profileComponent;

    @EventListener
    public void onCircuitBreak(CircuitBreakEvent event) {
        String mappingId = event.getMappingId();
        try {
            Mapping mapping = profileComponent.getMapping(mappingId);
            if (mapping == null) {
                logger.warn("熔断事件: Mapping不存在, mappingId={}", mappingId);
                return;
            }
            Meta meta = profileComponent.getMeta(mapping.getMetaId());
            if (meta == null || !meta.isRunning()) {
                logger.info("熔断事件: 任务未运行, 无需关闭, mappingId={}", mappingId);
                return;
            }
            logger.info("熔断事件: 正在关闭任务, mappingId={}", mappingId);
            managerFactory.close(mapping);
        } catch (Exception e) {
            logger.error("熔断事件处理失败: mappingId={}", mappingId, e);
        }
    }
}
