/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.biz;

import org.dbsyncer.biz.vo.SystemConfigVo;
import org.dbsyncer.biz.vo.VersionVo;
import org.dbsyncer.common.model.ConfigModel;
import org.dbsyncer.common.model.SystemConfig;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 系统配置
 *
 * @author AE86
 * @version 1.0.0
 * @date 2020/5/30 23:14
 */
public interface ConfigService {

    /**
     * 修改系统配置
     *
     * @param params
     */
    String edit(Map<String, String> params) throws Exception;

    /**
     * 获取系统配置
     *
     * @return
     */
    SystemConfigVo getSystemConfigVo() throws Exception;

    /**
     * 获取系统配置
     *
     * @return
     */
    SystemConfig getSystemConfig() throws Exception;

    /**
     * 获取所有配置（system、user、connector、mapping、tableGroup、meta、projectGroup）
     *
     * @return
     */
    List<ConfigModel> getConfigModelAll() throws Exception;

    /**
     * 校验文件格式
     *
     * @param filename
     */
    void checkFileSuffix(String filename);

    /**
     * 更新配置
     *
     * @param file
     */
    void refreshConfig(File file);

    /**
     * 获取水印信息
     *
     * @param systemConfig
     * @return
     */
    String getWatermark(SystemConfig systemConfig);

    // ===== ADR-0029 Tab 接口 =====

    /**
     * 按 Tab 编辑配置（整块替换）
     *
     * @param tab     Tab 标识
     * @param payload JSON 字符串
     * @return 结果描述
     */
    Map<String, Object> editTab(String tab, String payload) throws Exception;

    /**
     * 获取各 Tab 当前生效值
     */
    Map<String, Object> getTabs() throws Exception;

    /**
     * 重置某个 Tab（子对象置 null，恢复 properties 默认）
     */
    String resetTab(String tab) throws Exception;

    /**
     * 获取 A 类只读信息（脱敏）
     */
    Map<String, Object> getServerInfo() throws Exception;

    /**
     * 获取版本信息
     *
     * @param username 当前用户名（用于水印占位符替换）
     * @return 版本信息
     */
    VersionVo getVersionInfo(String username) throws Exception;
}