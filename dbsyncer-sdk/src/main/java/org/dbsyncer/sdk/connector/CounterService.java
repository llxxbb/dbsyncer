package org.dbsyncer.sdk.connector;

import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;

public interface CounterService {

    void incrementFail(Meta meta, TableGroup tg, int count);

    void incrementSuccess(Meta meta, TableGroup tg, int successCount, boolean isErrQueueRetry);
}