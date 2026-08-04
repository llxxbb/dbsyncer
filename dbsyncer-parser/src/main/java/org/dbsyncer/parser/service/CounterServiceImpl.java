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
import java.time.Instant;

@Component
public class CounterServiceImpl implements CounterService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ProfileComponent profileComponent;

    @Override
    public void incrementSuccess(Meta meta, TableGroup tg, int count) {
        if (count <= 0) {
            return;
        }
        synchronized (meta) {
            meta.setSuccess(meta.getSuccess() + count);
            tg.setSuccess(tg.getSuccess() + count);
            if (meta.getSyncPhase() == SyncPhaseEnum.FULL) {
                meta.setFullSuccess(meta.getFullSuccess() + count);
                tg.setFullSuccess(tg.getFullSuccess() + count);
            } else {
                meta.setIncrementSuccess(meta.getIncrementSuccess() + count);
                tg.setIncrementSuccess(tg.getIncrementSuccess() + count);
            }
            updateTableGroupSpeed(tg, count);
            meta.markTableGroupChanged(tg.getId());
            Mapping mapping = profileComponent.getMapping(meta.getMappingId());
            logger.info("mappingId {}, tgId {}, 写入条数：{}", mapping.getId(), tg.getId(), count);
        }
    }

    @Override
    public void incrementFail(Meta meta, TableGroup tg, int count) {
        if (count <= 0) {
            return;
        }
        synchronized (meta) {
            meta.setFail(meta.getFail() + count);
            tg.setFail(tg.getFail() + count);
            if (meta.getSyncPhase() == SyncPhaseEnum.FULL) {
                meta.setFullFail(meta.getFullFail() + count);
                tg.setFullFail(tg.getFullFail() + count);
            } else {
                meta.setIncrementFail(meta.getIncrementFail() + count);
                tg.setIncrementFail(tg.getIncrementFail() + count);
                meta.setTotal(meta.getTotal() + count);
            }
            meta.markTableGroupChanged(tg.getId());
            Mapping mapping = profileComponent.getMapping(meta.getMappingId());
            logger.info("mappingId {}, tgId {}, 失败条数：{}", mapping.getId(), tg.getId(), count);
        }
    }

    @Override
    public void decrementFail(Meta meta, TableGroup tg) {
        synchronized (meta) {
            meta.setFail(Math.max(0, meta.getFail() - 1));
            meta.setSuccess(meta.getSuccess() + 1);
            tg.setFail(Math.max(0, tg.getFail() - 1));
            tg.setSuccess(tg.getSuccess() + 1);
            syncStatus(tg);
        }
    }

    @Override
    public void flush(Meta meta, TableGroup tg) {
        try {
            meta.setUpdateTime(Instant.now().toEpochMilli());
            profileComponent.editConfigModel(meta);
            if (tg != null) {
                profileComponent.editConfigModel(tg);
            }
        } catch (Exception e) {
            logger.error("刷新计数失败: metaId={}", meta.getId(), e);
        }
    }

    private void syncStatus(TableGroup tg) {
        tg.setStatus(tg.getFail() > 0 ? TableGroup.STATUS_ERROR : TableGroup.STATUS_NORMAL);
    }

    private void updateTableGroupSpeed(TableGroup tableGroup, int successSize) {
        long currentTime = System.currentTimeMillis();
        long lastSyncTime = tableGroup.getLastSyncTime();
        long batchCount = successSize;
        if (lastSyncTime > 0 && batchCount > 0) {
            long timeDiffMs = currentTime - lastSyncTime;
            if (timeDiffMs > 0) {
                double speed = (double) batchCount / (timeDiffMs / 1000.0);
                tableGroup.setCurrentSpeed(speed);
            }
        }
        tableGroup.setLastSyncTime(currentTime);
    }
}
