package org.dbsyncer.sdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.dbsyncer.common.ParserException;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.enums.SyncPhaseEnum;
import org.dbsyncer.sdk.listener.Listener;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Meta extends ConfigModel {

    private String mappingId;
    private volatile int state;
    private String model;
    private volatile long total;
    private volatile long success;
    private volatile long fail;
    @JsonIgnore
    private Listener listener;
    private Map<String, String> snapshot;
    private Map<String, String> tableGroupCountMap;
    private Set<String> dereplicationTableGroupIdSet;
    private AtomicLong incrementCursor;
    private SyncPhaseEnum syncPhase;
    private String errorMessage;
    private long beginTime;
    private long endTime;

    @JsonIgnore
    private transient boolean incrementStartPointRecorded;

    @JsonIgnore
    private transient Runnable phaseHandler;

    public Meta() {
        super.setType(ConfigConstant.META);
        this.syncPhase = SyncPhaseEnum.FULL;
        this.state = MetaEnum.READY.getCode();
    }

    @JsonIgnore
    public boolean isRunning() {
        return MetaEnum.isRunning(state);
    }

    @JsonIgnore
    public void assertNotRunning() {
        if (isRunning()) {
            throw new ParserException("任务【运行中】就不允许编辑了。如需编辑请【停止】任务。");
        }
    }

    public void saveState(MetaEnum state, String errorMessage) {
        this.state = state.getCode();
        if (MetaEnum.ERROR == state) {
            this.setErrorMessage(errorMessage);
            return;
        }
        this.setErrorMessage("");
    }

    public void resetState() {
        this.state = MetaEnum.READY.getCode();
    }

    public void updateTotal(long val) {
        this.total += val;
    }

    public void updateSuccess(long val) {
        this.success += val;
    }

    public void updateFail(long val) {
        this.fail += val;
    }

    public void updateFullTotal(List<TableGroup> allTableGroups) {
        if (!CollectionUtils.isEmpty(allTableGroups)) {
            long sum = 0;
            for (TableGroup g : allTableGroups) {
                sum += g.getTotal();
            }
            this.total = sum;
        }
    }

    public void updateSnapshot(String json) {
        if (StringUtil.isNotBlank(json)) {
            Map<String, Object> parsedMap = JsonUtil.parseMap(json);
            Map<String, String> map = new HashMap<>();
            if (parsedMap != null) {
                for (Map.Entry<String, Object> entry : parsedMap.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        map.put(entry.getKey(), (String) entry.getValue());
                    }
                }
            }
            if (null == snapshot) {
                snapshot = new ConcurrentHashMap<>();
            }
            snapshot.putAll(map);
        }
    }

    public void recordIncrementStartPoint(Map<String, String> position) {
        if (null != position && !position.isEmpty()) {
            if (null == snapshot) {
                snapshot = new ConcurrentHashMap<>();
            }
            snapshot.putAll(position);
        }
    }

    public void clear() {
        this.total = 0;
        this.success = 0;
        this.fail = 0;
        this.state = MetaEnum.READY.getCode();
        this.syncPhase = SyncPhaseEnum.FULL;
        this.errorMessage = "";
        this.listener = null;
    }

    public void clear(String modelType) {
        clear();
        this.snapshot = null;
        this.tableGroupCountMap = null;
        this.dereplicationTableGroupIdSet = null;
        this.incrementCursor = null;
        if (StringUtil.isNotBlank(modelType)) {
            this.model = modelType;
        }
    }

    public void partialClear(Set<String> resetTableGroupIds, List<TableGroup> allTableGroups) {
        if (CollectionUtils.isEmpty(allTableGroups)) {
            this.total = 0;
            return;
        }
        long sum = 0;
        for (TableGroup g : allTableGroups) {
            sum += g.getTotal();
        }
        this.total = sum;
        this.success = 0;
        this.fail = 0;
        this.state = MetaEnum.READY.getCode();
        this.syncPhase = SyncPhaseEnum.FULL;
    }

    @JsonIgnore
    public boolean hasError() {
        return StringUtil.isNotBlank(errorMessage);
    }

    @JsonIgnore
    public boolean isError() {
        return hasError();
    }

    // 跟踪需要持久化的 TableGroup（增量场景）
    @JsonIgnore
    private transient Set<String> changedTableGroupIds;

    @JsonIgnore
    public Set<String> getChangedTableGroupIds() {
        if (changedTableGroupIds == null) {
            changedTableGroupIds = ConcurrentHashMap.newKeySet();
        }
        return changedTableGroupIds;
    }

    @JsonIgnore
    public void clearChangedTableGroupIds() {
        if (changedTableGroupIds != null) {
            changedTableGroupIds.clear();
        }
    }

    @JsonIgnore
    public void markTableGroupChanged(String tableGroupId) {
        getChangedTableGroupIds().add(tableGroupId);
    }

    @JsonIgnore
    public static Meta create(Mapping mapping, String newId) throws Exception {
        Meta meta = new Meta();
        meta.setId(newId);
        meta.setCreateTime(Instant.now().toEpochMilli());
        meta.setUpdateTime(Instant.now().toEpochMilli());
        meta.setMappingId(mapping.getId());
        meta.setName(mapping.getId());
        mapping.setMetaId(newId);
        return meta;
    }

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
        this.setName(mappingId) ;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getSuccess() {
        return success;
    }

    public void setSuccess(long success) {
        this.success = success;
    }

    public long getFail() {
        return fail;
    }

    public void setFail(long fail) {
        this.fail = fail;
    }

    public Listener getListener() {
        return listener;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public Map<String, String> getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Map<String, String> snapshot) {
        this.snapshot = snapshot;
    }

    public Map<String, String> getTableGroupCountMap() {
        return tableGroupCountMap;
    }

    public void setTableGroupCountMap(Map<String, String> tableGroupCountMap) {
        this.tableGroupCountMap = tableGroupCountMap;
    }

    public Set<String> getDereplicationTableGroupIdSet() {
        return dereplicationTableGroupIdSet;
    }

    public void setDereplicationTableGroupIdSet(Set<String> dereplicationTableGroupIdSet) {
        this.dereplicationTableGroupIdSet = dereplicationTableGroupIdSet;
    }

    public AtomicLong getIncrementCursor() {
        return incrementCursor;
    }

    public void setIncrementCursor(AtomicLong incrementCursor) {
        this.incrementCursor = incrementCursor;
    }

    public SyncPhaseEnum getSyncPhase() {
        return syncPhase;
    }

    public void setSyncPhase(SyncPhaseEnum syncPhase) {
        this.syncPhase = syncPhase;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(long beginTime) {
        this.beginTime = beginTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @JsonIgnore
    public boolean isIncrementStartPointRecorded() {
        return incrementStartPointRecorded;
    }

    @JsonIgnore
    public void setIncrementStartPointRecorded(boolean incrementStartPointRecorded) {
        this.incrementStartPointRecorded = incrementStartPointRecorded;
    }

    @JsonIgnore
    public Runnable getPhaseHandler() {
        return phaseHandler;
    }

    @JsonIgnore
    public void setPhaseHandler(Runnable phaseHandler) {
        this.phaseHandler = phaseHandler;
    }
}
