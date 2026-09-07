package org.dbsyncer.sdk.model;

import org.dbsyncer.common.model.ConfigModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractConfigModel extends ConfigModel {

    private Map<String, String> params = new HashMap<>();

    private List<Filter> filter = new ArrayList<>();

    private List<Convert> convert = new ArrayList<>();

    public Map<String, String> getParams() {
        return params;
    }

    public AbstractConfigModel setParams(Map<String, String> params) {
        this.params = params;
        return this;
    }

    public List<Filter> getFilter() {
        return filter;
    }

    public void setFilter(List<Filter> filter) {
        this.filter = filter;
    }

    public List<Convert> getConvert() {
        return convert;
    }

    public void setConvert(List<Convert> convert) {
        this.convert = convert;
    }
}
