package org.dbsyncer.parser.service;

import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.connector.CounterService;
import org.dbsyncer.sdk.enums.SyncPhaseEnum;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 计数服务
 * 注意不要使用传入的 tableGroup 对象因为这个是复制出来的，不是缓存里的。
 * CounterServiceImpl
 */
@Component
public class CounterServiceImpl implements CounterService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Override
    public void incrementSuccess(Meta meta, TableGroup tableGroup, int count) {
        if (count <= 0) {
            return;
        }
        TableGroup tg = getTableGroup(tableGroup);
        synchronized (meta) {
            meta.setSuccess(meta.getSuccess() + count);
            tg.setSuccess(tg.getSuccess() + count);
            if (meta.getSyncPhase() == SyncPhaseEnum.INCREMENTAL) {
                tg.setTotal(tg.getTotal() + count);
                meta.setTotal(meta.getTotal() + count);
            }
            updateTableGroupSpeed(tg, count);
            meta.markTableGroupChanged(tg.getId());
            Mapping mapping = profileComponent.getMapping(meta.getMappingId());
            logger.info("mappingId {}, tgId {}, 写入条数：{}", mapping.getId(), tg.getId(), count);
        }
    }

    @Override
    public void incrementFail(Meta meta, TableGroup tableGroup, int count) {
        if (count <= 0) {
            return;
        }
        TableGroup tg = getTableGroup(tableGroup);
        synchronized (meta) {
            meta.setFail(meta.getFail() + count);
            tg.setFail(tg.getFail() + count);
            if (meta.getSyncPhase() == SyncPhaseEnum.INCREMENTAL) {
                tg.setTotal(tg.getTotal() + count);
                meta.setTotal(meta.getTotal() + count);
            }
            meta.markTableGroupChanged(tg.getId());
            Mapping mapping = profileComponent.getMapping(meta.getMappingId());
            logger.info("mappingId {}, tgId {}, 失败条数：{}", mapping.getId(), tg.getId(), count);
        }
    }

    @Override
    public void decrementFail(Meta meta, TableGroup tableGroup) {
        TableGroup tg = getTableGroup(tableGroup);
        synchronized (meta) {
            meta.setFail(Math.max(0, meta.getFail() - 1));
            meta.setSuccess(meta.getSuccess() + 1);
            tg.setFail(Math.max(0, tg.getFail() - 1));
            tg.setSuccess(tg.getSuccess() + 1);
            syncStatus(tg);
        }
    }

    private void syncStatus(TableGroup tg) {
        tg.setStatus(tg.getFail() > 0 ? TableGroup.STATUS_ERROR : TableGroup.STATUS_NORMAL);
    }

    private void updateTableGroupSpeed(TableGroup tableGroup, int successSize) {
        TableGroup tg = getTableGroup(tableGroup);
        long currentTime = System.currentTimeMillis();
        long lastSyncTime = tg.getLastSyncTime();
        long batchCount = successSize;
        if (lastSyncTime > 0 && batchCount > 0) {
            long timeDiffMs = currentTime - lastSyncTime;
            if (timeDiffMs > 0) {
                double speed = (double) batchCount / (timeDiffMs / 1000.0);
                tg.setCurrentSpeed(speed);
            }
        }
        tg.setLastSyncTime(currentTime);
    }

    private TableGroup getTableGroup(TableGroup tableGroup) {
        TableGroup tg = null;
        try {
            tg = profileComponent.getTableGroup(tableGroup.getId());
        } catch (Exception ignore) {
        }
        return tg;
    }
}
