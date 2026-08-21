/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.binlog;

import com.github.shyiko.mysql.binlog.GtidSet;
import com.github.shyiko.mysql.binlog.MariadbGtidSet;
import com.github.shyiko.mysql.binlog.event.Event;
import com.github.shyiko.mysql.binlog.event.EventHeader;
import com.github.shyiko.mysql.binlog.event.EventType;
import com.github.shyiko.mysql.binlog.event.GtidEventData;
import com.github.shyiko.mysql.binlog.event.MariadbGtidEventData;
import com.github.shyiko.mysql.binlog.event.MariadbGtidListEventData;
import com.github.shyiko.mysql.binlog.event.QueryEventData;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.github.shyiko.mysql.binlog.network.ServerException;
import org.dbsyncer.common.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * GTID 集合管理器。
 * <p>
 * 负责管理 MySQL/MariaDB binlog 的 GTID 集合状态，包括：
 * <ul>
 * <li>GTID 集合的初始化、更新和查询</li>
 * <li>临时 GTID 集合的管理和修正</li>
 * <li>MariaDB GTID 格式的特殊处理</li>
 * <li>GTID 相关错误的检测</li>
 * </ul>
 * <p>
 * 该类从 {@link BinaryLogRemoteClient} 中提取，遵循单一职责原则。
 * 所有同步操作在内部完成，调用方无需关心锁。
 */
public class GtidSetManager {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Pattern MARIA_GTID_SET_PATTERN = Pattern
            .compile("^\\d{1,10}-\\d{1,10}-\\d{1,20}(\\s*,\\s*\\d{1,10}-\\d{1,10}-\\d{1,20})*$");

    private final Object lock = new Object();
    private GtidSet gtidSet;
    private String gtid;
    private boolean tx;
    private final boolean isMariaDB;
    private boolean temporaryGtidSet = false;

    public GtidSetManager(boolean isMariaDB) {
        this.isMariaDB = isMariaDB;
    }

    public void initEmpty() {
        synchronized (lock) {
            if (gtidSet == null) {
                gtidSet = isMariaDB ? new MariadbGtidSet("") : new GtidSet("");
            }
        }
    }

    public void initFromSnapshot(String gtidStr) {
        if (gtidStr == null) {
            return;
        }
        synchronized (lock) {
            if (MariadbGtidSet.isMariaGtidSet(gtidStr)) {
                this.gtidSet = new MariadbGtidSet(gtidStr);
            } else {
                this.gtidSet = new GtidSet(gtidStr);
            }
        }
    }

    public boolean initIfEmpty(String executed) {
        if (StringUtil.isBlank(executed)) {
            return false;
        }
        synchronized (lock) {
            boolean wasEmpty = gtidSet == null || gtidSet.toString().length() == 0;
            if (!wasEmpty) {
                return false;
            }
            try {
                gtidSet = isMariaDB ? new MariadbGtidSet(executed) : new GtidSet(executed);
            } catch (Exception e) {
                return false;
            }
            return true;
        }
    }

    public void markTemporary() {
        synchronized (lock) {
            this.temporaryGtidSet = true;
            logger.info("临时 GTID 集合已初始化（来自服务端），等待第一条 GTID 事件修正: {}", getGtidSetInternal());
        }
    }

    public boolean isGtidMode() {
        synchronized (lock) {
            return gtidSet != null && gtidSet.toString().length() > 0;
        }
    }

    public String getGtidSet() {
        synchronized (lock) {
            return gtidSet != null ? gtidSet.toString() : null;
        }
    }

    public GtidSet snapshotGtidSet() {
        synchronized (lock) {
            return gtidSet;
        }
    }

    public boolean isValidMariaGtidSet(String gtidStr) {
        return MARIA_GTID_SET_PATTERN.matcher(gtidStr).matches();
    }

    public void updateGtidSet(Event event) {
        synchronized (lock) {
            if (gtidSet == null) {
                return;
            }
        }
        EventHeader eventHeader = event.getHeader();
        switch (eventHeader.getEventType()) {
            case GTID:
                GtidEventData gtidEventData = (GtidEventData) EventDeserializer.EventDataWrapper
                        .internal(event.getData());
                gtid = gtidEventData.getGtid();
                if (temporaryGtidSet && gtid != null) {
                    correctGtidSetWithFirstGtid(gtid);
                }
                break;
            case MARIADB_GTID:
                MariadbGtidEventData mariadbGtidEventData = (MariadbGtidEventData) EventDeserializer.EventDataWrapper
                        .internal(event.getData());
                gtid = mariadbGtidEventData.toString();
                if (temporaryGtidSet && gtid != null) {
                    correctGtidSetWithFirstGtid(gtid);
                }
                break;
            case MARIADB_GTID_LIST:
                mergeMariadbGtidList(
                        (MariadbGtidListEventData) EventDeserializer.EventDataWrapper.internal(event.getData()));
                break;
            case XID:
                commitGtid();
                tx = false;
                break;
            case QUERY:
                QueryEventData queryEventData = (QueryEventData) EventDeserializer.EventDataWrapper
                        .internal(event.getData());
                String sql = queryEventData.getSql();
                if (sql == null) {
                    break;
                }
                if ("BEGIN".equals(sql)) {
                    tx = true;
                } else if ("COMMIT".equals(sql) || "ROLLBACK".equals(sql)) {
                    commitGtid();
                    tx = false;
                } else if (!tx) {
                    commitGtid();
                }
                break;
            default:
                break;
        }
    }

    private String getGtidSetInternal() {
        return gtidSet != null ? gtidSet.toString() : null;
    }

    private void commitGtid() {
        String currentGtid = gtid;
        gtid = null;
        if (currentGtid != null) {
            synchronized (lock) {
                gtidSet.add(currentGtid);
            }
        }
    }

    private void correctGtidSetWithFirstGtid(String firstGtid) {
        synchronized (lock) {
            if (gtidSet == null) {
                return;
            }
            if (isMariaDB) {
                throw new UnsupportedOperationException("MariaDB GTID 修正暂不支持，继续使用临时值");
            }
            String[] parts = firstGtid.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid GTID format: " + firstGtid);
            }
            String uuid = parts[0];
            long sequence = Long.parseLong(parts[1]);

            String currentGtidSetStr = gtidSet.toString();
            if (!currentGtidSetStr.contains(uuid)) {
                throw new IllegalStateException("GTID 修正失败，临时 GTID 集合不包含 uuid: " + uuid);
            }
            String correctedGtidSet = correctMysqlGtidSet(currentGtidSetStr, uuid, sequence);
            if (correctedGtidSet == null || correctedGtidSet.isEmpty()) {
                throw new IllegalStateException("GTID 修正失败，修正后的 GTID 集合为空: " + uuid);
            }
            gtidSet = new GtidSet(correctedGtidSet);
            logger.info("临时 GTID 集合已用第一条 GTID 修正: {} -> {}", currentGtidSetStr, correctedGtidSet);
            temporaryGtidSet = false;
        }
    }

    static String correctMysqlGtidSet(String currentGtidSet, String targetUuid, long sequence) {
        if (currentGtidSet == null || currentGtidSet.isEmpty()) {
            return currentGtidSet;
        }

        StringBuilder result = new StringBuilder();
        String[] intervals = currentGtidSet.split(",");
        for (int i = 0; i < intervals.length; i++) {
            String interval = intervals[i].trim();
            if (interval.isEmpty())
                continue;

            int colonIdx = interval.indexOf(':');
            if (colonIdx < 0 || colonIdx == interval.length() - 1)
                continue;

            String uuid = interval.substring(0, colonIdx);
            String ranges = interval.substring(colonIdx + 1);
            if (ranges.isEmpty())
                continue;

            if (StringUtil.equalsIgnoreCase(uuid, targetUuid)) {
                String[] rangeItems = ranges.split(":");
                StringBuilder uuidRanges = new StringBuilder();
                long maxEnd = -1;
                int maxEndIdx = -1;
                int idx = 0;
                for (String rangeItem : rangeItems) {
                    if (rangeItem.isEmpty())
                        continue;
                    String[] rangeParts = rangeItem.split("-");
                    long end = rangeParts.length > 1 ? Long.parseLong(rangeParts[1]) : Long.parseLong(rangeParts[0]);
                    if (end > maxEnd) {
                        maxEnd = end;
                        maxEndIdx = idx;
                    }
                    idx++;
                }

                idx = 0;
                for (String rangeItem : rangeItems) {
                    if (rangeItem.isEmpty())
                        continue;
                    String[] rangeParts = rangeItem.split("-");
                    long start = Long.parseLong(rangeParts[0]);
                    long end = rangeParts.length > 1 ? Long.parseLong(rangeParts[1]) : start;

                    if (sequence > maxEnd && idx == maxEndIdx) {
                        if (uuidRanges.length() > 0)
                            uuidRanges.append(":");
                        if (start == sequence) {
                            uuidRanges.append(start);
                        } else {
                            uuidRanges.append(start).append("-").append(sequence);
                        }
                    } else if (end < sequence) {
                        if (uuidRanges.length() > 0)
                            uuidRanges.append(":");
                        if (start == end) {
                            uuidRanges.append(start);
                        } else {
                            uuidRanges.append(start).append("-").append(end);
                        }
                    } else if (start <= sequence) {
                        if (uuidRanges.length() > 0)
                            uuidRanges.append(":");
                        if (start == sequence) {
                            uuidRanges.append(start);
                        } else {
                            uuidRanges.append(start).append("-").append(sequence);
                        }
                    }
                    idx++;
                }

                if (uuidRanges.length() == 0 && maxEndIdx >= 0) {
                    int fallbackIdx = 0;
                    for (String rangeItem : rangeItems) {
                        if (rangeItem.isEmpty())
                            continue;
                        String[] rangeParts = rangeItem.split("-");
                        long start = Long.parseLong(rangeParts[0]);
                        if (fallbackIdx == maxEndIdx) {
                            if (start > sequence) {
                                throw new IllegalStateException("GTID 修正失败，区间起始值大于第一条 GTID 序列号: " + rangeItem);
                            }
                            if (start == sequence) {
                                uuidRanges.append(start);
                            } else {
                                uuidRanges.append(start).append("-").append(sequence);
                            }
                            break;
                        }
                        fallbackIdx++;
                    }
                }

                if (uuidRanges.length() > 0) {
                    if (result.length() > 0)
                        result.append(",");
                    result.append(uuid).append(":").append(uuidRanges);
                }
            } else {
                if (result.length() > 0)
                    result.append(",");
                result.append(interval);
            }
        }
        return result.toString();
    }

    private void mergeMariadbGtidList(MariadbGtidListEventData gtidListEventData) {
        MariadbGtidSet receivedSet = gtidListEventData.getMariaGTIDSet();
        if (receivedSet == null) {
            return;
        }
        synchronized (lock) {
            if (gtidSet == null || gtidSet.toString().length() == 0) {
                gtidSet = receivedSet;
                return;
            }
            if (gtidSet instanceof MariadbGtidSet) {
                for (String singleGtid : StringUtil.split(receivedSet.toString(), StringUtil.COMMA)) {
                    String trimmed = singleGtid.trim();
                    if (trimmed.length() > 0) {
                        gtidSet.add(trimmed);
                    }
                }
            }
        }
    }

    public static boolean isGtidPurgedError(ServerException e) {
        return e.getErrorCode() == 1236
                && e.getMessage() != null
                && e.getMessage().contains("purged binary logs");
    }

    public static boolean isBogusDataError(ServerException e) {
        return e.getErrorCode() == 1236
                && e.getMessage() != null
                && e.getMessage().contains("bogus data");
    }
}
