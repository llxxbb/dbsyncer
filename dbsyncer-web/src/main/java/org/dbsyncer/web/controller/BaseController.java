package org.dbsyncer.web.controller;

import org.dbsyncer.biz.ConditionService;
import org.dbsyncer.biz.ConvertService;
import org.dbsyncer.plugin.PluginFactory;
import org.springframework.ui.ModelMap;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author AE86
 * @version 1.0.0
 * @date 2020/1/7 22:46
 */
public abstract class BaseController {

    @Resource
    private ConditionService filterService;

    @Resource
    private ConvertService convertService;

    @Resource
    private PluginFactory pluginFactory;

    /**
     * 获取请求参数
     *
     * @param request
     * @return
     */
    protected Map<String, String> getParams(HttpServletRequest request) {
        Map<String, String[]> map = request.getParameterMap();
        Map<String, String> res = new HashMap<>();
        map.forEach((k, v) -> {
            // 如果参数是数组（多选框），用逗号连接所有值
            if (v.length > 1) {
                res.put(k, Arrays.stream(v).collect(Collectors.joining(",")));
            } else {
                res.put(k, v[0]);
            }
        });
        return res;
    }

    /**
     * 初始化: 条件/转换/插件
     *
     * @param model
     */
    protected void initConfig(ModelMap model) throws Exception {
        model.put("condition", filterService.getCondition());
        model.put("convert", convertService.getConvertEnumAll());
        model.put("plugin", pluginFactory.getPluginAll());
    }

}