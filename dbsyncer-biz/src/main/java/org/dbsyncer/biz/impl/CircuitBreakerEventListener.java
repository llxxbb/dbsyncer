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

    /**
     * 熔断事件处理：关闭数据同步任务
     * <p>
     * 关闭判据是"增量资源是否仍绑定"而非"任务是否运行中"：closeTask 先置 ERROR 再发事件，
     * 用运行状态做判据恒为假，会导致监听器、执行器、binlog 连接永不释放（假终止）。
     *
     * @param event 熔断事件
     * @throws Exception 
     */
    @EventListener
    public void onCircuitBreak(CircuitBreakEvent event) throws Exception {
        String mappingId = event.getMappingId();
        Mapping mapping = profileComponent.getMapping(mappingId);
        if (mapping == null) {
            logger.warn("熔断事件: Mapping不存在, mappingId={}", mappingId);
            throw new IllegalStateException("熔断事件: Mapping不存在, mappingId=" + mappingId);
        }
        managerFactory.close(mapping);
    }
}
