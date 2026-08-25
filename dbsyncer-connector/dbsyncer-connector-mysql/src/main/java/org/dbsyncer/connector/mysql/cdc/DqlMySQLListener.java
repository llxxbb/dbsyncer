/**
 * DBSyncer Copyright 2020-2023 All Rights Reserved.
 */
package org.dbsyncer.connector.mysql.cdc;

/**
 * @Author AE86
 * @Version 1.0.0
 * @Date 2022-05-28 22:02
 */
public class DqlMySQLListener extends MySQLListener {

    @Override
    public void start() throws Exception {
        super.postProcessDqlBeforeInitialization();
        // DQL 场景：postProcessDqlBeforeInitialization() 重建了 filterTable，需要同步更新缓存
        rebuildFilterTableLowerCache();
        super.start();
    }
}