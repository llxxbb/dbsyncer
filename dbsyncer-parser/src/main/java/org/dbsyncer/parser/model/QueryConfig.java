/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.parser.model;

import org.dbsyncer.parser.enums.GroupStrategyEnum;
import org.dbsyncer.sdk.model.ConfigModel;

public class QueryConfig<T> {

    private final ConfigModel configModel;

    private GroupStrategyEnum groupStrategyEnum = GroupStrategyEnum.DEFAULT;

    public QueryConfig(ConfigModel configModel) {
        this.configModel = configModel;
    }

    public QueryConfig(ConfigModel configModel, GroupStrategyEnum groupStrategyEnum) {
        this.configModel = configModel;
        this.groupStrategyEnum = groupStrategyEnum;
    }

    public ConfigModel getConfigModel() {
        return configModel;
    }

    public GroupStrategyEnum getGroupStrategyEnum() {
        return groupStrategyEnum;
    }
}