package org.dbsyncer.manager;

import org.dbsyncer.manager.impl.FullIncrementPuller;
import org.dbsyncer.manager.impl.FullPuller;
import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2019/9/16 23:59
 */
@Component
public class ManagerFactory {

    Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private MessageService messageService;

    @Resource
    private NotifyConfigResolver notifyConfigResolver;

    @Resource
    private Map<String, Puller> map;

    @Resource
    private LogService logService;

    public void start(Mapping mapping) throws Exception {
        start(mapping, null);
    }

    public void start(Mapping mapping, List<TableGroup> tableGroupsToSync) throws Exception {
        Puller puller = getPuller(mapping);

        // 标记运行中，使用 Meta 类的统一方法
        Meta meta = profileComponent.getMeta(mapping.getMetaId());
        meta.setBeginTime(Instant.now().toEpochMilli());
        meta.saveState(MetaEnum.RUNNING, "");
        profileComponent.editConfigModel(meta);
        mapping.setUpdateTime(Instant.now().toEpochMilli());
        profileComponent.editConfigModel(mapping);
        
        try {
            if (puller instanceof FullPuller) {
                ((FullPuller) puller).start(mapping, tableGroupsToSync);
            } else if (puller instanceof FullIncrementPuller) {
                ((FullIncrementPuller) puller).start(mapping, tableGroupsToSync);
            } else {
                puller.start(mapping);
                profileComponent.editConfigModel(meta);
            }
        } catch (Exception e) {
            // 记录异常状态和异常信息到 Meta 对象，使用统一方法
            meta.saveState(MetaEnum.ERROR, e.getMessage());
            logger.error("Puller 启动失败：mappingId={}, metaId={}, error={}", mapping.getId(), mapping.getMetaId(), e.getMessage(), e);
            logService.log(null, LogType.START, String.format("Puller 启动失败：mappingId=%s, metaId=%s, error=%s", mapping.getId(), mapping.getMetaId(), e.getMessage()));
            
            // 发送任务中断通知
            Map<String, String> details = new LinkedHashMap<>();
            details.put("错误原因", e.getMessage());
            messageService.sendNotifyMessage(mapping, "任务中断", NotifyType.TASK_INTERRUPTED, notifyConfigResolver, details);

            throw new ManagerException(e.getMessage());
        }
    }

    public void close(Mapping mapping) throws Exception {
        Puller puller = getPuller(mapping);
        puller.close(mapping);
        Meta meta = profileComponent.getMeta(mapping.getMetaId());
        profileComponent.editConfigModel(meta);
    }

    private Puller getPuller(Mapping mapping) {
        Assert.notNull(mapping, "驱动不能为空");
        String model = mapping.getModel();
        String metaId = mapping.getMetaId();
        Assert.hasText(model, "同步方式不能为空");
        Assert.hasText(metaId, "任务 ID 不能为空");

        Puller puller = map.get(model.concat("Puller"));
        Assert.notNull(puller, String.format("未知的同步方式：%s", model));
        return puller;
    }

}
