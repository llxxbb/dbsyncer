package org.dbsyncer.common.config.props;

import org.dbsyncer.common.config.ConfigKeys;
import org.dbsyncer.common.config.AppProps;
import org.springframework.core.env.Environment;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL Server 增量同步配置（C 类，立即生效）。
 * <p>
 * 控制 SQL Server CT（Change Tracking）连接器的轮询行为。
 * 通过 {@code ConfigRegistry.syncSqlServerCTPropsToSystem()} 桥接到
 * {@code System.getProperty}，供 {@code SqlServerCTListener} 每次轮询时现读。
 */
public class SqlServerCTProps implements Serializable, AppProps {

    private static final long serialVersionUID = 1L;

    /** 版本号轮询间隔（毫秒），默认 30000（与 application.properties 产品默认值一致） */
    private int pollIntervalMs = 30000;

    /** 快照持久化间隔（毫秒），默认 30000 */
    private int snapshotTimeIntervalMs = 30000;

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getSnapshotTimeIntervalMs() {
        return snapshotTimeIntervalMs;
    }

    public void setSnapshotTimeIntervalMs(int snapshotTimeIntervalMs) {
        this.snapshotTimeIntervalMs = snapshotTimeIntervalMs;
    }

    @Override
    public void loadFromEnvironment(Environment env) {
        this.pollIntervalMs = env.getProperty(ConfigKeys.SQLSERVER_CT_POLL_INTERVAL_MS, Integer.class, pollIntervalMs);
        this.snapshotTimeIntervalMs = env.getProperty(ConfigKeys.SQLSERVER_CT_SNAPSHOT_TIME_INTERVAL_MS, Integer.class, snapshotTimeIntervalMs);
    }

    @Override
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (pollIntervalMs < 100) {
            errors.add("版本号轮询间隔必须 >= 100 毫秒");
        }
        if (snapshotTimeIntervalMs < 1000) {
            errors.add("快照持久化间隔必须 >= 1000 毫秒");
        }
        return errors;
    }

    @Override
    public List<ConfigEntry> toConfigEntries() {
        List<ConfigEntry> entries = new ArrayList<>();
        entries.add(new ConfigEntry(ConfigKeys.SQLSERVER_CT_POLL_INTERVAL_MS, pollIntervalMs));
        entries.add(new ConfigEntry(ConfigKeys.SQLSERVER_CT_SNAPSHOT_TIME_INTERVAL_MS, snapshotTimeIntervalMs));
        return entries;
    }
}
