/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.biz.impl;

import org.apache.lucene.index.IndexableField;
import org.dbsyncer.biz.ErrorQueueRetrier;
import org.dbsyncer.biz.MappingService;
import org.dbsyncer.biz.MonitorService;
import org.dbsyncer.biz.ConfigService;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.biz.enums.BufferActuatorMetricEnum;
import org.dbsyncer.biz.enums.DiskMetricEnum;
import org.dbsyncer.biz.enums.MetricEnum;
import org.dbsyncer.biz.metric.MetricDetailFormatter;
import org.dbsyncer.biz.metric.MetricGroupProcessor;
import org.dbsyncer.biz.metric.impl.CpuMetricDetailFormatter;
import org.dbsyncer.biz.metric.impl.DiskMetricDetailFormatter;
import org.dbsyncer.biz.metric.impl.DoubleRoundMetricDetailFormatter;
import org.dbsyncer.biz.metric.impl.GCMetricDetailFormatter;
import org.dbsyncer.biz.metric.impl.MemoryMetricDetailFormatter;
import org.dbsyncer.biz.metric.impl.ValueMetricDetailFormatter;
import org.dbsyncer.biz.model.AppReportMetric;
import org.dbsyncer.biz.model.MetricResponse;
import org.dbsyncer.biz.vo.DataVo;
import org.dbsyncer.biz.vo.LogVo;
import org.dbsyncer.biz.vo.MappingVo;
import org.dbsyncer.biz.vo.MetaVo;
import org.dbsyncer.biz.vo.MetricResponseVo;
import org.dbsyncer.common.config.listener.ConfigChangeListener;
import org.dbsyncer.common.config.props.MonitorProps;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.model.Paging;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.scheduled.ScheduledTaskJob;
import org.dbsyncer.common.scheduled.ScheduledTaskService;
import org.dbsyncer.common.scheduled.StartupTaskRegistrar;
import org.dbsyncer.common.util.CollectionUtils;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.NumberUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.spi.LogService;
import org.dbsyncer.sdk.spi.LogType;
import org.dbsyncer.sdk.spi.LogLevel;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.sdk.model.Mapping;
import org.dbsyncer.sdk.model.Meta;
import org.dbsyncer.sdk.model.TableGroup;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.sdk.enums.FilterEnum;
import org.dbsyncer.sdk.enums.MetaEnum;
import org.dbsyncer.sdk.enums.ModelEnum;
import org.dbsyncer.sdk.enums.StorageEnum;
import org.dbsyncer.sdk.filter.BooleanFilter;
import org.dbsyncer.sdk.filter.FieldResolver;
import org.dbsyncer.sdk.filter.Query;
import org.dbsyncer.sdk.filter.impl.LongFilter;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.sdk.spi.MessageService;
import org.dbsyncer.sdk.storage.StorageService;
import org.dbsyncer.storage.enums.StorageDataStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2020-04-27 10:20
 */
@Service
public class MonitorServiceImpl implements MonitorService, ScheduledTaskJob, StartupTaskRegistrar, ConfigChangeListener {

    /** 预警定时任务的固定 key，用于热重载时 stop/restart */
    private static final String ALERT_TASK_KEY = "monitor-alert";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private MetricReporter metricReporter;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    private ErrorQueueRetrier errorQueueRetrier;

    @Resource
    private ScheduledTaskService scheduledTaskService;

    @Resource
    private StorageService storageService;

    @Resource
    private LogService logService;

    @Resource
    private ConfigService systemConfigService;

    @Resource
    private ConfigRegistry configRegistry;

    @Resource
    private MetricGroupProcessor metricGroupProcessor;

    @Resource
    private org.dbsyncer.sdk.connector.CounterService counterService;

    @Resource
    private MappingService mappingService;

    @Resource
    private MessageService messageService;

    /** 上次注册的 alertCron，用于检测变更 */
    private volatile String lastAlertCron;

    private final Map<String, MetricDetailFormatter> metricMap = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        metricMap.putIfAbsent(BufferActuatorMetricEnum.GENERAL.getCode(), new ValueMetricDetailFormatter());
        metricMap.putIfAbsent(BufferActuatorMetricEnum.STORAGE.getCode(), new ValueMetricDetailFormatter());
        metricMap.putIfAbsent(BufferActuatorMetricEnum.TABLE_GROUP.getCode(), new ValueMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.THREADS_LIVE.getCode(), new DoubleRoundMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.THREADS_PEAK.getCode(), new DoubleRoundMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.MEMORY_USED.getCode(), new MemoryMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.MEMORY_COMMITTED.getCode(), new MemoryMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.MEMORY_MAX.getCode(), new MemoryMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.CPU_USAGE.getCode(), new CpuMetricDetailFormatter());
        metricMap.putIfAbsent(MetricEnum.GC_PAUSE.getCode(), new GCMetricDetailFormatter());
        metricMap.putIfAbsent(DiskMetricEnum.THRESHOLD.getCode(), new DiskMetricDetailFormatter());
        metricMap.putIfAbsent(DiskMetricEnum.FREE.getCode(), new DiskMetricDetailFormatter());
        metricMap.putIfAbsent(DiskMetricEnum.TOTAL.getCode(), new DiskMetricDetailFormatter());
    }

    @Override
    public void registerScheduledTasks() {
        MonitorProps m = configRegistry.getConfig().getMonitor();
        lastAlertCron = m.getAlertCron();
        scheduledTaskService.start(ALERT_TASK_KEY, lastAlertCron, this);
    }

    @Override
    public void onConfigChanged(SystemConfig newConfig) {
        String newCron = newConfig.getMonitor().getAlertCron();
        if (StringUtil.equalsIgnoreCase(newCron, lastAlertCron)) {
            return;
        }
        logger.info("检测到 alertCron 变更: {} → {}，重新注册预警定时任务", lastAlertCron, newCron);
        scheduledTaskService.stop(ALERT_TASK_KEY);
        lastAlertCron = newCron;
        scheduledTaskService.start(ALERT_TASK_KEY, newCron, this);
    }

    @Override
    public List<MetaVo> getMetaAll() {
        return profileComponent.getMetaAll()
                .stream()
                .map(this::convertMeta2Vo)
                .sorted(Comparator.comparing(MetaVo::getUpdateTime).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public MetaVo getMetaVo(String metaId) {
        Meta meta = profileComponent.getMeta(metaId);
        Assert.notNull(meta, "The meta is null.");

        return convertMeta2Vo(meta);
    }

    @Override
    public String getDefaultMetaId(Map<String, String> params) {
        String id = params.get(ConfigConstant.CONFIG_MODEL_ID);
        return getDefaultMetaId(id);
    }

    @Override
    public Paging queryData(Map<String, String> params) {
        String id = params.get(ConfigConstant.CONFIG_MODEL_ID);
        int pageNum = NumberUtil.toInt(params.get("pageNum"), 1);
        int pageSize = NumberUtil.toInt(params.get("pageSize"), 10);
        String error = params.get(ConfigConstant.DATA_ERROR);
        String dataStatus = params.get("dataStatus");

        // 如果id是mappingId，通过mappingId获取对应的metaId
        List<String> metaIds = new ArrayList<>();
        if (StringUtil.isNotBlank(id)) {
            // 尝试通过id获取mapping，捕获可能的类型转换异常
            try {
                Mapping mapping = profileComponent.getMapping(id);
                if (mapping != null && StringUtil.isNotBlank(mapping.getMetaId())) {
                    // 如果是mappingId，获取对应的metaId
                    metaIds.add(mapping.getMetaId());
                } else {
                    // 如果不是mappingId，直接作为metaId
                    metaIds.add(id);
                }
            } catch (Exception e) {
                // 捕获类型转换异常，说明id是metaId
                logger.debug("未能通过 id 映射，将 id 视为 metaId：{}", id, e);
                metaIds.add(id);
            }
        }

        // 如果没有metaIds，使用默认的metaId
        if (CollectionUtils.isEmpty(metaIds)) {
            // 调用String参数的重载方法，获取默认metaId
            String defaultMetaId = getDefaultMetaId((String) null);
            if (StringUtil.isNotBlank(defaultMetaId)) {
                metaIds.add(defaultMetaId);
            } else {
                return new Paging(pageNum, pageSize);
            }
        }

        // 查询所有匹配的metaId的数据
        Paging paging = new Paging(pageNum, pageSize);
        List<DataVo> allData = new ArrayList<>();
        int total = 0;

        for (String metaId : metaIds) {
            Paging tempPaging = queryData(metaId, pageNum, pageSize, error, dataStatus);
            List<Map> data = (List<Map>) tempPaging.getData();
            total += tempPaging.getTotal();

            for (Map row : data) {
                try {
                    DataVo dataVo = convert2Vo(row, DataVo.class);
                    Map binlogData = errorQueueRetrier.getBinlogData(row, true);
                    dataVo.setJson(JsonUtil.objToJson(binlogData));
                    allData.add(dataVo);
                } catch (Exception e) {
                    logger.error(e.getLocalizedMessage(), e);
                }
            }
        }

        paging.setData(allData);
        paging.setTotal(total);
        return paging;
    }

    @Override
    public String clearData(String id) throws Exception {
        Assert.hasText(id, "驱动不存在.");

        // 如果id是mappingId，通过mappingId获取对应的metaId
        String metaId = id;
        try {
            Mapping mappingObj = profileComponent.getMapping(id);
            if (mappingObj != null && StringUtil.isNotBlank(mappingObj.getMetaId())) {
                // 如果是mappingId，获取对应的metaId
                metaId = mappingObj.getMetaId();
            }
        } catch (Exception e) {
            // 捕获类型转换异常，说明id是metaId
            logger.debug("未能通过 id 映射，将 id 视为 metaId：{}", id, e);
            metaId = id;
        }

        Meta meta = profileComponent.getMeta(metaId);
        Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        logService.log(mapping, LogType.CLEAR, LogLevel.INFO,
                String.format("%s:%s(%s)", LogType.CLEAR.getType(), mapping.getName(), mapping.getModel()));

        storageService.clear(StorageEnum.DATA, metaId);
        // 清空前重置所有tableGroup的失败计数
        List<TableGroup> allTableGroups = profileComponent.getTableGroupAll(mapping.getId());
        for (TableGroup tg : allTableGroups) {
            int failCount = (int) tg.getFail();
            if (failCount > 0) {
                counterService.decrementFail(meta, tg, failCount);
            }
        }
        return "清空同步数据成功";
    }

    @Override
    public String deleteData(String id, String messageId) throws Exception {
        Assert.hasText(id, "驱动不存在.");
        Assert.hasText(messageId, "消息ID不存在.");

        // 如果id是mappingId，通过mappingId获取对应的metaId
        String metaId = null;
        Mapping mapping = profileComponent.getMapping(id);
        if (mapping != null && StringUtil.isNotBlank(mapping.getMetaId())) {
            metaId = mapping.getMetaId();
        }

        // 查询数据获取tableGroupId，用于扣减计数
        Query query = new Query(1, 1);
        query.addFilter(ConfigConstant.CONFIG_MODEL_ID, messageId);
        query.setMetaId(metaId);
        query.setType(StorageEnum.DATA);
        Paging paging = storageService.query(query);
        if (CollectionUtils.isEmpty(paging.getData())) {
            throw new RuntimeException("数据不存在");
        }

        logService.log(mapping, LogType.CLEAR, LogLevel.INFO,
                String.format("%s:%s(%s)", LogType.CLEAR.getType(), mapping.getName(), mapping.getModel()));

        storageService.remove(StorageEnum.DATA, metaId, messageId);

        // 扣减错误计数和总数
        List<Map> dataList = (List<Map>) paging.getData();
        Map row = dataList.get(0);
        Meta meta = profileComponent.getMeta(metaId);
        String tableGroupId = (String) row.get(ConfigConstant.DATA_TABLE_GROUP_ID);
        TableGroup tg = profileComponent.getTableGroup(tableGroupId);
        counterService.decrementFail(meta, tg, 1);
        return "删除成功";
    }

    @Override
    public Paging queryLog(Map<String, String> params) {
        String modelId = params.get(ConfigConstant.CONFIG_MODEL_ID);
        Assert.hasText(modelId, "modelId 是必填参数");

        int pageNum = NumberUtil.toInt(params.get("pageNum"), 1);
        int pageSize = NumberUtil.toInt(params.get("pageSize"), 10);
        String type = params.get("type");
        String logLevel = params.get("logLevel");
        String json = params.get(ConfigConstant.CONFIG_MODEL_JSON);

        Query query = new Query(pageNum, pageSize);
        query.addFilter(ConfigConstant.LOG_MODEL_ID, modelId, false);
        if (StringUtil.isNotBlank(type)) {
            query.addFilter(ConfigConstant.CONFIG_MODEL_TYPE, type, false);
        }
        if (StringUtil.isNotBlank(logLevel)) {
            query.addFilter("logLevel", logLevel, false);
        }
        if (StringUtil.isNotBlank(json)) {
            query.addFilter(ConfigConstant.CONFIG_MODEL_JSON, json, true);
        }
        query.setType(StorageEnum.LOG);
        Paging paging = storageService.query(query);
        List<Map> data = (List<Map>) paging.getData();
        paging.setData(data.stream()
                .map(m -> convert2Vo(m, LogVo.class))
                .collect(Collectors.toList()));
        return paging;
    }

    @Override
    public String clearLog(Map<String, String> params) {
        String modelId = params.get(ConfigConstant.CONFIG_MODEL_ID);
        Assert.hasText(modelId, "modelId 是必填参数");

        Query query = new Query();
        query.addFilter(ConfigConstant.LOG_MODEL_ID, modelId, false);
        query.setType(StorageEnum.LOG);
        storageService.delete(query);
        return "清空日志成功";
    }

    @Override
    public void deleteExpiredDataAndLog() throws Exception {
        deleteExpiredData();
        deleteExpiredLog();
    }

    @Override
    public List<StorageDataStatusEnum> getStorageDataStatusEnumAll() {
        return profileComponent.getStorageDataStatusEnumAll();
    }

    @Override
    public List<MetricEnum> getMetricEnumAll() {
        return Arrays.asList(MetricEnum.values());
    }

    @Override
    public AppReportMetric queryAppReportMetric(List<MetricResponse> metrics) {
        AppReportMetric app = metricReporter.getAppReportMetric();
        app.setMetrics(getMetrics(metrics));
        return app;
    }

    @Override
    public void run() {
        try {
            List<MappingVo> allMappings = mappingService.getMappingAll();
            List<MappingVo> abnormal = allMappings.stream()
                    .filter(m -> m != null && m.getMeta() != null
                            && (m.getMeta().getState() == MetaEnum.ERROR.getCode() || m.getMeta().getFail() > 0))
                    .sorted((a, b) -> {
                        int stateA = a.getMeta().getState();
                        int stateB = b.getMeta().getState();
                        if (stateA == MetaEnum.ERROR.getCode() && stateB != MetaEnum.ERROR.getCode()) return -1;
                        if (stateA != MetaEnum.ERROR.getCode() && stateB == MetaEnum.ERROR.getCode()) return 1;
                        return -Long.compare(a.getMeta().getFail(), b.getMeta().getFail());
                    })
                    .collect(Collectors.toList());

            if (abnormal.isEmpty()) {
                return;
            }

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder msg = new StringBuilder();
            msg.append("### DBSyncer 异常任务巡检\n\n");
            msg.append("**巡检时间**: ").append(now).append("\n\n");
            msg.append("**异常数量**: ").append(abnormal.size()).append("\n\n");
            msg.append("| 任务名称 | ID | 状态 | 失败次数 | 错误摘要 |\n");
            msg.append("|---------|-----|------|---------|--------|\n");
            for (MappingVo m : abnormal) {
                String name = m.getName() != null ? m.getName() : "unknown";
                String id = m.getId() != null ? m.getId() : "unknown";
                String stateLabel = m.getMeta().getState() == MetaEnum.ERROR.getCode() ? "异常" : "运行中";
                long failCount = m.getMeta().getFail();
                String error = m.getMeta().getErrorMessage();
                if (error != null && error.length() > 50) {
                    error = error.substring(0, 50) + "...";
                }
                if (error == null) {
                    error = "-";
                }
                msg.append("| ").append(name)
                        .append(" | ").append(id)
                        .append(" | ").append(stateLabel)
                        .append(" | ").append(failCount)
                        .append(" | ").append(error).append(" |\n");
            }

            NotifyProps props = configRegistry.getConfig().getNotify();
            messageService.sendMessage("异常任务巡检", msg.toString(), NotifyType.ABNORMAL_TASK_PATROL, props);
            logger.info("异常任务巡检完成，发现 {} 个异常任务", abnormal.size());
        } catch (Exception e) {
            logger.error("异常任务巡检失败：" + e.getMessage(), e);
        }
    }

    private Paging queryData(String metaId, int pageNum, int pageSize, String error, String dataStatus) {
        // 没有驱动
        if (StringUtil.isBlank(metaId)) {
            return new Paging(pageNum, pageSize);
        }
        Query query = new Query(pageNum, pageSize);
        Map<String, FieldResolver> fieldResolvers = new ConcurrentHashMap<>();
        fieldResolvers.put(ConfigConstant.BINLOG_DATA,
                (FieldResolver<IndexableField>) field -> field.binaryValue().bytes);
        query.setFieldResolverMap(fieldResolvers);

        // 查询异常信息
        if (StringUtil.isNotBlank(error)) {
            query.addFilter(ConfigConstant.DATA_ERROR, error, true);
        }
        // 查询数据状态
        if (StringUtil.isNotBlank(dataStatus)) {
            query.addFilter(ConfigConstant.DATA_SUCCESS, NumberUtil.toInt(dataStatus));
        }
        query.setMetaId(metaId);
        query.setType(StorageEnum.DATA);
        return storageService.query(query);
    }

    private void deleteExpiredData() throws Exception {
        List<MetaVo> metaAll = getMetaAll();
        if (!CollectionUtils.isEmpty(metaAll)) {
            Query query = new Query();
            query.setType(StorageEnum.DATA);
            int expireDataDays = configRegistry.getConfig().getBasic().getExpireDataDays();
            long expiredTime = Timestamp.valueOf(LocalDateTime.now().minusDays(expireDataDays)).getTime();
            LongFilter expiredFilter = new LongFilter(ConfigConstant.CONFIG_MODEL_CREATE_TIME, FilterEnum.LT,
                    expiredTime);
            query.setBooleanFilter(new BooleanFilter().add(expiredFilter));
            metaAll.forEach(metaVo -> {
                query.setMetaId(metaVo.getId());
                storageService.delete(query);
            });
        }
    }

    private void deleteExpiredLog() throws Exception {
        Query query = new Query();
        query.setType(StorageEnum.LOG);
        int expireLogDays = configRegistry.getConfig().getBasic().getExpireLogDays();
        long expiredTime = Timestamp.valueOf(LocalDateTime.now().minusDays(expireLogDays)).getTime();
        LongFilter expiredFilter = new LongFilter(ConfigConstant.CONFIG_MODEL_CREATE_TIME, FilterEnum.LT, expiredTime);
        query.setBooleanFilter(new BooleanFilter().add(expiredFilter));
        storageService.delete(query);
    }

    private MetaVo convertMeta2Vo(Meta meta) {
        Mapping mapping = profileComponent.getMapping(meta.getMappingId());
        if (mapping == null) {
            String msg = String.format("mapping missing, id: %s, metaId: %s", meta.getMappingId(), meta.getId());
            logger.error(msg);
            logService.log(mapping, LogType.UPDATE, LogLevel.INFO,
                    String.format("mapping missing, id: %s, metaId: %s", meta.getMappingId(), meta.getId()));
            return new MetaVo("异常", msg);
        }
        ModelEnum modelEnum = ModelEnum.getModelEnum(mapping.getModel());
        MetaVo metaVo = new MetaVo(modelEnum.getName(), mapping.getName());
        BeanUtils.copyProperties(meta, metaVo);
        return metaVo;
    }

    private <T> T convert2Vo(Map map, Class<T> clazz) {
        return JsonUtil.jsonToObj(JsonUtil.objToJson(map), clazz);
    }

    private String getDefaultMetaId(String id) {
        if (StringUtil.isBlank(id)) {
            List<MetaVo> list = getMetaAll();
            if (!CollectionUtils.isEmpty(list)) {
                return list.get(0).getId();
            }
        }
        return id;
    }

    private List<MetricResponseVo> getMetrics(List<MetricResponse> metrics) {
        // 系统指标
        List<MetricResponse> metricList = metricReporter.getMetricInfo();
        // 线程池状态
        metrics.addAll(metricList);

        // 转换显示
        List<MetricResponseVo> formatMetrics = metrics.stream().map(metric -> {
            MetricResponseVo vo = new MetricResponseVo();
            vo.setCode(metric.getCode());
            vo.setGroup(metric.getGroup());
            vo.setMetricName(metric.getMetricName());
            vo.setMeasurements(metric.getMeasurements());
            metricMap.computeIfPresent(vo.getCode(), (k, mdf) -> {
                mdf.format(vo);
                return mdf;
            });
            return vo;
        }).collect(Collectors.toList());
        // 合并分组显示
        return metricGroupProcessor.process(formatMetrics);
    }

}