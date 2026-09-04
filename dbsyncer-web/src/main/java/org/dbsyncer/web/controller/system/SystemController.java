/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.web.controller.system;

import org.dbsyncer.biz.ConfigService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.web.controller.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@Controller
@RequestMapping(value = "/system")
@PreAuthorize("hasRole('admin')")
public class SystemController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private ConfigService systemConfigService;

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
    public RestResult editTab(@RequestBody Map<String, String> body) {
        try {
            String tab = body.get("tab");
            String payload = body.get("payload");
            Map<String, Object> result = systemConfigService.editTab(tab, payload);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (success) {
                return RestResult.restSuccess(result);
            } else {
                return RestResult.restFail(result);
            }
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
}
