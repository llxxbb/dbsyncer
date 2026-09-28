package org.dbsyncer.manager.impl;

import org.dbsyncer.common.util.SHA1Util;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.parser.ProfileComponent;
import org.dbsyncer.parser.model.UserConfig;
import org.dbsyncer.parser.model.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 启动期管理员密码重置。
 * <p>
 * 当启动参数 {@code dbsyncer.admin.password} 存在且非空时，将 admin 密码重置为指定值并持久化。
 * <p>
 * 用法：
 * <pre>
 * java -jar dbsyncer-web.jar --dbsyncer.admin.password=NewPassword123
 * </pre>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>由 {@link PreloadTemplate} 在配置预加载完成后显式调用，保证时序正确</li>
 *   <li>仅在启动时执行一次，不侵入认证流程</li>
 *   <li>用完去掉参数重启即可，天然"一次性"</li>
 *   <li>密码由管理员指定，不是写死的默认值</li>
 * </ul>
 */
@Component
public class AdminPasswordResetor {

    private static final Logger logger = LoggerFactory.getLogger(AdminPasswordResetor.class);

    private static final String ADMIN_PASSWORD_KEY = "dbsyncer.admin.password";
    private static final String ADMIN_USERNAME = "admin";

    @Resource
    private Environment environment;

    @Resource
    private ProfileComponent profileComponent;

    /**
     * 在配置预加载完成后调用。
     * <p>
     * 如果启动参数 {@code dbsyncer.admin.password} 存在且非空，
     * 将 admin 密码重置为指定值并持久化到数据库。
     */
    public void resetIfNeeded() {
        String newPassword = environment.getProperty(ADMIN_PASSWORD_KEY);
        if (StringUtil.isBlank(newPassword)) {
            return;
        }

        try {
            UserConfig userConfig = profileComponent.getUserConfig();
            if (userConfig == null) {
                logger.warn("[AdminPasswordResetor] 用户配置未初始化，跳过密码重置");
                return;
            }

            UserInfo adminUser = userConfig.getUserInfo(ADMIN_USERNAME);
            if (adminUser == null) {
                logger.warn("[AdminPasswordResetor] 管理员账号({})不存在，跳过密码重置", ADMIN_USERNAME);
                return;
            }

            String hashedPassword = SHA1Util.b64_sha1(newPassword);
            adminUser.setPassword(hashedPassword);
            profileComponent.editConfigModel(userConfig);

            logger.warn("[AdminPasswordResetor] 管理员({})密码已重置为指定值，请及时修改并移除启动参数！", ADMIN_USERNAME);
        } catch (Exception e) {
            logger.error("[AdminPasswordResetor] 重置管理员密码失败", e);
        }
    }
}
