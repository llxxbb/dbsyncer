/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.web.controller.system;

import org.dbsyncer.biz.ConfigService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.constant.ConfigConstant;
import org.dbsyncer.common.model.SystemConfig;
import org.dbsyncer.common.util.JsonUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.sdk.notify.NotifyConfigResolver;
import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.dbsyncer.sdk.notify.NotifyType;
import org.dbsyncer.web.controller.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping(value = "/system")
@PreAuthorize("hasRole('admin')")
public class SystemController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ConfigService systemConfigService;

    @Resource
    private ProfileComponent profileComponent;

    @Resource
    @Qualifier("compositeNotifyService")
    private NotifyService notifyService;

    @RequestMapping("")
    public String index(ModelMap model) throws Exception {
        model.put("config", systemConfigService.getSystemConfigVo());
        return "system/system";
    }

    // ===== ADR-0029 Tab 接口 =====

    /**
     * 按 Tab 编辑配置（整块替换）
     */
    @PostMapping("/editTab")
    @ResponseBody
    public RestResult editTab(HttpServletRequest request) {
        try {
            // 统一使用表单参数（与 BaseController#getParams 约定一致），
            // payload 为嵌套配置的 JSON 字符串
            Map<String, String> params = getParams(request);
            String tab = params.get("tab");
            String payload = params.get("payload");
            String result = systemConfigService.editTab(tab, payload);
            return RestResult.restSuccess(result);
        } catch (Exception e) {
            logger.error("editTab 失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 获取各 Tab 当前生效值
     */
    @GetMapping("/tabs")
    @ResponseBody
    public RestResult getTabs() {
        try {
            return RestResult.restSuccess(systemConfigService.getTabs());
        } catch (Exception e) {
            logger.error("getTabs 失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 重置某个 Tab（子对象置 null，恢复 properties 默认）
     */
    @PostMapping("/resetTab")
    @ResponseBody
    public RestResult resetTab(@RequestParam String tab) {
        try {
            return RestResult.restSuccess(systemConfigService.resetTab(tab));
        } catch (Exception e) {
            logger.error("resetTab 失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 获取 A 类只读信息（脱敏）
     */
    @GetMapping("/serverInfo")
    @ResponseBody
    public RestResult getServerInfo() {
        try {
            return RestResult.restSuccess(systemConfigService.getServerInfo());
        } catch (Exception e) {
            logger.error("getServerInfo 失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    // ===== ADR-0035 分组通知配置接口 =====

    /**
     * 获取分组通知配置
     */
    @GetMapping("/groupNotifyConfigs")
    @ResponseBody
    public RestResult getGroupNotifyConfigs() {
        try {
            return RestResult.restSuccess(systemConfigService.getGroupNotifyConfigs());
        } catch (Exception e) {
            logger.error("获取分组通知配置失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 保存分组通知配置
     */
    @PostMapping("/saveGroupNotifyConfig")
    @ResponseBody
    public RestResult saveGroupNotifyConfig(@RequestParam String projectGroupId, @RequestParam String payload) {
        try {
            systemConfigService.saveGroupNotifyConfig(projectGroupId, payload);
            return RestResult.restSuccess("ok");
        } catch (Exception e) {
            logger.error("保存分组通知配置失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 删除分组通知配置
     */
    @PostMapping("/deleteGroupNotifyConfig")
    @ResponseBody
    public RestResult deleteGroupNotifyConfig(@RequestParam String projectGroupId) {
        try {
            systemConfigService.deleteGroupNotifyConfig(projectGroupId);
            return RestResult.restSuccess("ok");
        } catch (Exception e) {
            logger.error("删除分组通知配置失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 测试分组通知配置
     */
    @PostMapping("/testGroupNotify")
    @ResponseBody
    public RestResult testGroupNotify(@RequestParam String payload) {
        try {
            NotifyProps props = JsonUtil.jsonToObj(payload, NotifyProps.class);

            // 获取接收人
            List<String> receivers = new ArrayList<>();
            UserConfig userConfig = profileComponent.getUserConfig();
            if (userConfig != null && userConfig.getUserInfoList() != null) {
                userConfig.getUserInfoList().forEach(userInfo -> {
                    if (StringUtil.isNotBlank(userInfo.getEmail())) {
                        receivers.addAll(Arrays.asList(StringUtil.split(userInfo.getEmail(), StringUtil.COMMA)));
                    }
                });
            }

            NotifyMessage msg = NotifyMessage.newBuilder()
                    .setTitle("DBSyncer 分组通知测试")
                    .setContent("这是一条测试消息，用于验证分组通知配置是否正确。")
                    .setReceivers(receivers)
                    .setType(NotifyType.MANUAL_OPERATION);

            notifyService.sendMessage(msg, props);
            return RestResult.restSuccess("ok");
        } catch (Exception e) {
            logger.error("测试分组通知失败: {}", e.getMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }
}
