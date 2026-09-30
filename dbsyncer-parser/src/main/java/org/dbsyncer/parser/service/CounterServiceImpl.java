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
 */
@Component
public class CounterServiceImpl implements CounterService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

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

    private void decrementFailBatch(Meta meta, TableGroup tableGroup, int count) {
        if (count <= 0) {
            return;
        }
        TableGroup tg = getTableGroup(tableGroup);
        synchronized (meta) {
            meta.setFail(Math.max(0, meta.getFail() - count));
            meta.setSuccess(meta.getSuccess() + count);
            tg.setFail(Math.max(0, tg.getFail() - count));
            tg.setSuccess(tg.getSuccess() + count);
            meta.markTableGroupChanged(tg.getId());
        }
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

    @Override
    public void incrementSuccess(Meta meta, TableGroup tableGroup, int successCount, boolean isErrQueueRetry) {
        if (successCount <= 0) {
            return;
        }
        if (isErrQueueRetry) {
            decrementFailBatch(meta, tableGroup, successCount);
        } else {
            incrementSuccess(meta, tableGroup, successCount);
        }
    }

    @Override
    public void decrementFail(Meta meta, TableGroup tableGroup, int count) {
        if (count <= 0) {
            return;
        }
        TableGroup tg = getTableGroup(tableGroup);
        synchronized (meta) {
            meta.setFail(Math.max(0, meta.getFail() - count));
            tg.setFail(Math.max(0, tg.getFail() - count));
            meta.setTotal(Math.max(meta.getTotal(), meta.getSuccess() + meta.getFail()));
            tg.setTotal(Math.max(tg.getTotal(), tg.getSuccess() + tg.getFail()));
            meta.markTableGroupChanged(tg.getId());
            Mapping mapping = profileComponent.getMapping(meta.getMappingId());
            logger.info("mappingId {}, tgId {}, 删除失败条数：{}", mapping.getId(), tg.getId(), count);
        }
    }

    /**
     * 注意不要使用传入的 tableGroup 对象因为这个是复制出来的，不是缓存里的。
     * @param tableGroup
     */
    private TableGroup getTableGroup(TableGroup tableGroup) {
        TableGroup tg = null;
        try {
            tg = profileComponent.getTableGroup(tableGroup.getId());
        } catch (Exception ignore) {
        }
        return tg;
    }
}
