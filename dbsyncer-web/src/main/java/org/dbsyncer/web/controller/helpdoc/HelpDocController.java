/**
 * DBSyncer Copyright 2020-2024 All Rights Reserved.
 */
package org.dbsyncer.web.controller.helpdoc;

import org.dbsyncer.biz.UserConfigService;
import org.dbsyncer.biz.vo.RestResult;
import org.dbsyncer.biz.vo.UserInfoVo;
import org.dbsyncer.web.controller.BaseController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping(value = "/helpDoc")
public class HelpDocController extends BaseController {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private UserConfigService userConfigService;

    @Value("${helpdoc.path:./helpdocs}")
    private String helpDocPath;

    private File helpDocDir;

    @PostConstruct
    public void init() {
        File path = new File(helpDocPath);
        if (!path.isAbsolute()) {
            String userDir = System.getProperty("user.dir");
            helpDocDir = new File(userDir, helpDocPath);
        } else {
            helpDocDir = path;
        }
        if (!helpDocDir.exists()) {
            helpDocDir.mkdirs();
        }
        logger.info("帮助文档存储路径: {}", helpDocDir.getAbsolutePath());
    }

    @RequestMapping("")
    public String index(ModelMap model) throws Exception {
        String currentUsername = getCurrentUsername();
        if (currentUsername != null) {
            UserInfoVo currentUser = userConfigService.getUserInfoVo(currentUsername, currentUsername);
            model.put("currentUser", currentUser);
        }
        model.put("docs", getDocList());
        return "helpdoc/helpdoc";
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (null != authentication && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    @GetMapping("/list.json")
    @ResponseBody
    public RestResult list() {
        try {
            return RestResult.restSuccess(getDocList());
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @GetMapping("/get.json")
    @ResponseBody
    public RestResult get(@RequestParam("fileName") String fileName) {
        try {
            File file = new File(helpDocDir, fileName);
            if (!file.exists()) {
                return RestResult.restFail("文档不存在");
            }
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("content", content);
            return RestResult.restSuccess(result);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/save.json")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult save(@RequestParam("fileName") String fileName, 
                           @RequestParam("content") String content) {
        try {
            File file = new File(helpDocDir, fileName);
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
            return RestResult.restSuccess("保存成功");
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/create.json")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult create(@RequestParam("fileName") String fileName) {
        try {
            if (!fileName.endsWith(".html") && !fileName.endsWith(".md")) {
                fileName = fileName + ".html";
            }
            File file = new File(helpDocDir, fileName);
            if (file.exists()) {
                return RestResult.restFail("文档已存在");
            }
            String defaultContent = "<h1>" + fileName.replace(".html", "").replace(".md", "") + "</h1>\n<p>请在此编辑文档内容...</p>";
            Files.write(file.toPath(), defaultContent.getBytes(StandardCharsets.UTF_8));
            return RestResult.restSuccess(fileName);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/delete.json")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult delete(@RequestParam("fileName") String fileName) {
        try {
            File file = new File(helpDocDir, fileName);
            if (!file.exists()) {
                return RestResult.restFail("文档不存在");
            }
            file.delete();
            return RestResult.restSuccess("删除成功");
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/rename.json")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult rename(@RequestParam("oldName") String oldName, 
                             @RequestParam("newName") String newName) {
        try {
            File oldFile = new File(helpDocDir, oldName);
            if (!oldFile.exists()) {
                return RestResult.restFail("文档不存在");
            }
            if (!newName.endsWith(".html") && !newName.endsWith(".md")) {
                newName = newName + ".html";
            }
            File newFile = new File(helpDocDir, newName);
            if (newFile.exists()) {
                return RestResult.restFail("目标文档名已存在");
            }
            oldFile.renameTo(newFile);
            return RestResult.restSuccess("重命名成功");
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    @PostMapping("/uploadImage.json")
    @ResponseBody
    @PreAuthorize("hasRole('admin')")
    public RestResult uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return RestResult.restFail("请选择文件");
            }
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = "img_" + System.currentTimeMillis() + extension;
            File imageDir = new File(helpDocDir, "images");
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }
            File destFile = new File(imageDir, fileName);
            file.transferTo(destFile);
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("url", "images/" + fileName);
            return RestResult.restSuccess(result);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage(), e);
            return RestResult.restFail(e.getMessage());
        }
    }

    private List<Map<String, Object>> getDocList() {
        List<Map<String, Object>> docs = new ArrayList<>();
        File[] files = helpDocDir.listFiles((dir, name) -> 
            name.endsWith(".html") || name.endsWith(".md"));
        if (files != null) {
            for (File file : files) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("fileName", file.getName());
                doc.put("lastModified", file.lastModified());
                doc.put("size", file.length());
                docs.add(doc);
            }
        }
        docs.sort((a, b) -> Long.compare((Long) b.get("lastModified"), (Long) a.get("lastModified")));
        return docs;
    }

}
