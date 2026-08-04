package org.dbsyncer.sdk.connector;

import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;

public interface CounterService {

    void incrementSuccess(Meta meta, TableGroup tg, int count);

    void incrementFail(Meta meta, TableGroup tg, int count);

    void decrementFail(Meta meta, TableGroup tg);

    void flush(Meta meta, TableGroup tg);
}