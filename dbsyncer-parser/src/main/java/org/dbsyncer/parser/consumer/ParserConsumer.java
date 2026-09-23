package org.dbsyncer.parser.consumer;

import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.parser.flush.impl.BufferActuatorRouter;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.listener.ChangedEvent;
import org.dbsyncer.sdk.listener.QuartzListenerContext;
import org.dbsyncer.sdk.listener.Watcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @Version 1.0.0
 * @Author AE86
 * @Date 2023-11-12 01:32
 */
public final class ParserConsumer implements Watcher {
    private static final Logger logger = LoggerFactory.getLogger(ParserConsumer.class);
    
    private final BufferActuatorRouter bufferActuatorRouter;
    private final ProfileComponent profileComponent;
    private final LogService logService;
    private final MessageService messageService;
    private final NotifyConfigResolver notifyConfigResolver;
    private final String metaId;
    private final Runnable onClose;

    public ParserConsumer(BufferActuatorRouter bufferActuatorRouter, ProfileComponent profileComponent, LogService logService, String metaId, List<TableGroup> tableGroups, MessageService messageService, NotifyConfigResolver notifyConfigResolver, Runnable onClose) {
        this.bufferActuatorRouter = bufferActuatorRouter;
        this.profileComponent = profileComponent;
        this.logService = logService;
        this.metaId = metaId;
        this.messageService = messageService;
        this.notifyConfigResolver = notifyConfigResolver;
        this.onClose = onClose;  // 初始化 onClose
        // 注册到路由服务中
        bufferActuatorRouter.getOrCreateActuator(metaId);
    }

    @Override
    public void changeEventBefore(QuartzListenerContext context) {
        // 插件系统已移除，前置处理为空
    }

    @Override
    public void changeEvent(ChangedEvent event) {
        // 所有到达 changeEvent 的事件都是任务事件（已通过过滤和配置检查）
        // execute() 方法会自动设置 pending 状态
        bufferActuatorRouter.execute(metaId, event);
    }

    @Override
    public void flushEvent(Map<String, String> snapshot) throws Exception {
        Meta meta = profileComponent.getMeta(metaId);
        if (meta == null) {
            throw new IllegalStateException("Meta not found for metaId: " + metaId);
        }
        // 监听器已解绑：已关闭的监听器不得再写快照，否则会覆盖新监听器的续读点（ADR-0036 决策 5）
        if (meta.getListener() == null) {
            logger.warn("监听器已解绑，丢弃快照写入: metaId={}, snapshot={}", metaId, snapshot);
            return;
        }
        meta.setSnapshot(snapshot);
        // 更新 updateTime 以记录持久化时间
        meta.setUpdateTime(Instant.now().toEpochMilli());
        profileComponent.editConfigModel(meta);

        // 持久化发生变化的 TableGroup（增量场景）
        // 由于在 Meta 中记录变化的表已经非常准确，无需额外的计数比较
        Set<String> changedTableGroupIds = meta.getChangedTableGroupIds();
        if (changedTableGroupIds.isEmpty()) {
            return;
        }
        for (String tableGroupId : changedTableGroupIds) {
            TableGroup tableGroup = profileComponent.getTableGroup(tableGroupId);
            if (tableGroup != null) {
                profileComponent.editConfigModel(tableGroup);
            }
        }
        // 持久化完成后清空标记
        meta.clearChangedTableGroupIds();
    }

    @Override
    public void errorEvent(Exception e) {
        // 记录错误到数据库
        Meta meta = profileComponent.getMeta(metaId);
        String mappingId = meta != null ? meta.getMappingId() : null;
        Mapping mapping = profileComponent.getMapping(mappingId);
        logService.log(mapping, LogType.SYNC, LogLevel.ERROR, String.format("增量同步异常：映射关系 ID=%s，错误=%s", metaId, e.getMessage()));
        
        // 更新 Meta 状态为 ERROR，确保任务状态正确显示为"异常"
        try {
            if (meta != null && !meta.isError()) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                meta.saveState(MetaEnum.ERROR, errorMessage);
                logger.info("Meta 状态已更新为 ERROR: metaId={}, errorMessage={}", metaId, errorMessage);
                
                // 发送任务中断通知
                sendTaskInterruptedNotification(meta, errorMessage);
            }
        } catch (Exception ex) {
            logger.error("更新 Meta 状态失败：metaId={}", metaId, ex);
        }
        
        // 异步触发完整关闭（避免阻塞 errorEvent 调用方）
        if (onClose != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    onClose.run();
                    logger.info("errorEvent 触发关闭成功：metaId={}", metaId);
                } catch (Exception ex) {
                    logger.error("errorEvent 触发关闭失败：metaId={}", metaId, ex);
                }
            });
        }
    }
    
    /**
     * 发送任务中断通知
     */
    private void sendTaskInterruptedNotification(Meta meta, String errorMessage) {
        try {
            String mappingId = meta.getMappingId();
            // 通过 mappingId 获取 Mapping 对象
            Mapping mapping = profileComponent.getMapping(mappingId);
            if (mapping == null) {
                logger.warn("找不到 Mapping 配置，无法发送通知：mappingId={}", mappingId);
                return;
            }
            
            Map<String, String> details = new LinkedHashMap<>();
            details.put("错误原因", errorMessage);
            messageService.sendNotifyMessage(mapping, "任务中断", NotifyType.TASK_INTERRUPTED, notifyConfigResolver, details);

            logger.info("发送任务中断通知成功：metaId={}, mappingId={}", metaId, mappingId);
        } catch (Exception ex) {
            logger.error("发送任务中断通知失败：metaId={}", metaId, ex);
        }
    }

    @Override
    public long getMetaUpdateTime() {
        Meta meta = profileComponent.getMeta(metaId);
        return meta != null ? meta.getUpdateTime() : 0L;
    }

    @Override
    public boolean hasPendingTask() {
        // 查询该 meta 的 pending 状态
        return bufferActuatorRouter.hasPendingTask(metaId);
    }
}
