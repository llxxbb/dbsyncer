/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.web.controller.user;

import org.dbsyncer.biz.ProjectGroupService;
import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.UserGroupService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.web.controller.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 用户组管理控制器
 *
 * @author AE86
 * @version 1.0.0
 * @date 2024/01/01
 */
@Controller
@RequestMapping(value = "/userGroup")
public class UserGroupController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private UserGroupService userGroupService;

    @Resource
    private UserConfigService userConfigService;

    @Resource
    private ProjectGroupService projectGroupService;

    /**
     * 用户组列表页面
     */
    @GetMapping("")
    public String index(ModelMap model) throws Exception {
        model.put("userGroups", userGroupService.getUserGroupAll());
        return "userGroup/userGroup";
    }

    /**
     * 添加用户组页面
     */
    @GetMapping("/page/add")
    @PreAuthorize("hasRole('admin')")
    public String pageAdd(ModelMap model) throws Exception {
        model.put("users", userConfigService.getUserInfoAll(getUserName()));
        model.put("projectGroups", projectGroupService.getProjectGroupAll());
        return "userGroup/add";
    }

    /**
     * 编辑用户组页面
     */
    @GetMapping("/page/edit")
    @PreAuthorize("hasRole('admin')")
    public String pageEdit(ModelMap model, String id) {
        model.put("userGroup", userGroupService.getUserGroup(id));
        try {
            model.put("users", userConfigService.getUserInfoAll(getUserName()));
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
        }
        model.put("projectGroups", projectGroupService.getProjectGroupAll());
        return "userGroup/edit";
    }

    /**
     * 添加用户组
     */
    @PostMapping("/add")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult add(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            return RestResult.restSuccess(userGroupService.add(params));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 编辑用户组
     */
    @PostMapping("/edit")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult edit(HttpServletRequest request) {
        try {
            Map<String, String> params = getParams(request);
            return RestResult.restSuccess(userGroupService.edit(params));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    /**
     * 删除用户组
     */
    @PostMapping("/remove")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult remove(@RequestParam String id) {
        try {
            return RestResult.restSuccess(userGroupService.remove(id));
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    private String getUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Assert.hasText(username, "无法获取登录用户.");
        return username;
    }
}
