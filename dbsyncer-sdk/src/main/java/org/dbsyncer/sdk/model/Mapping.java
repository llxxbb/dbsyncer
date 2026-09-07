package org.dbsyncer.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.sdk.config.ListenerConfig;
import org.dbsyncer.common.constant.ConfigConstant;

import java.util.List;

public class Mapping extends AbstractConfigModel {

    public Mapping() {
        super.setType(ConfigConstant.MAPPING);
    }

    private String sourceConnectorId;
    private String targetConnectorId;
    @JsonIgnore
    private List<Field> sourceColumn;
    @JsonIgnore
    private List<Field> targetColumn;
    private String model;
    private ListenerConfig listener;
    private String metaId;
    private int readNum = 10000;
    private int batchNum = 1000;
    private int threadNum = 10;
    private boolean forceUpdate = true;
    private boolean compareData = true;
    private boolean recoverData = true;
    private boolean disableEdit = false;

    public String getSourceConnectorId() {
        return sourceConnectorId;
    }

    public void setSourceConnectorId(String sourceConnectorId) {
        this.sourceConnectorId = sourceConnectorId;
    }

    public String getTargetConnectorId() {
        return targetConnectorId;
    }

    public void setTargetConnectorId(String targetConnectorId) {
        this.targetConnectorId = targetConnectorId;
    }

    @JsonIgnore
    public List<Field> getSourceColumn() {
        return sourceColumn;
    }

    @JsonIgnore
    public void setSourceColumn(List<Field> sourceColumn) {
        this.sourceColumn = sourceColumn;
    }

    @JsonIgnore
    public List<Field> getTargetColumn() {
        return targetColumn;
    }

    @JsonIgnore
    public void setTargetColumn(List<Field> targetColumn) {
        this.targetColumn = targetColumn;
    }

    public String getModel() {
        return model;
    }

    public Mapping setModel(String model) {
        this.model = model;
        return this;
    }

    public ListenerConfig getListener() {
        return listener;
    }

    public Mapping setListener(ListenerConfig listener) {
        this.listener = listener;
        return this;
    }

    public String getMetaId() {
        return metaId;
    }

    public void setMetaId(String metaId) {
        this.metaId = metaId;
    }

    public int getReadNum() {
        return readNum;
    }

    public void setReadNum(int readNum) {
        this.readNum = readNum;
    }

    public int getBatchNum() {
        return batchNum;
    }

    public void setBatchNum(int batchNum) {
        this.batchNum = batchNum;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    public boolean isCompareData() {
        return compareData;
    }

    public void setCompareData(boolean compareData) {
        this.compareData = compareData;
    }

    public boolean isRecoverData() {
        return recoverData;
    }

    public void setRecoverData(boolean recoverData) {
        this.recoverData = recoverData;
    }

    public boolean isDisableEdit() {
        return disableEdit;
    }

    public void setDisableEdit(boolean disableEdit) {
        this.disableEdit = disableEdit;
    }

    @Deprecated
    @JsonIgnore
    public void assertDisableEdit() {
        throw new UnsupportedOperationException("请通过 MappingModelService.assertDisableEdit(Mapping) 替代");
    }
}
