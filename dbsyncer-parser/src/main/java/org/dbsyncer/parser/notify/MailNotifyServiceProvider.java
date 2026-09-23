package org.dbsyncer.parser.notify;

import com.sun.mail.util.MailSSLSocketFactory;
import org.dbsyncer.common.config.AppConfig;
import org.dbsyncer.common.config.ConfigRegistry;
import org.dbsyncer.common.config.props.MailProps;
import org.dbsyncer.common.config.props.NotifyProps;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.sdk.notify.NotifyMessage;
import org.dbsyncer.sdk.notify.NotifyService;
import org.dbsyncer.sdk.notify.NotifyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Properties;


/**
 * 邮件通知服务实现
 *
 * @author AE86
 * @version 1.0.0
 * @date 2022/11/13 22:20
 */
@Component("mailNotifyService")
public final class MailNotifyServiceProvider implements NotifyService {

    private static final Logger logger = LoggerFactory.getLogger(MailNotifyServiceProvider.class);

    private final AppConfig appConfig;
    private final ConfigRegistry configRegistry;

    public MailNotifyServiceProvider(AppConfig appConfig, ConfigRegistry configRegistry) {
        this.appConfig = appConfig;
        this.configRegistry = configRegistry;
    }

    private MailProps getMailProps() {
        if (configRegistry.getConfig().getNotify() == null) return null;
        return configRegistry.getConfig().getNotify().getMail();
    }

    /**
     * 根据指定的 MailProps 创建 Session。
     */
    private Session createSession(MailProps mail) {
        if (mail == null || StringUtil.isBlank(mail.getUsername())) {
            return null;
        }
        try {
            final Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", "smtp.qq.com");
            props.put("mail.user", mail.getUsername());
            props.put("mail.password", mail.getPassword());
            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.socketFactory", sf);
            return Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mail.getUsername(), mail.getPassword());
                }
            });
        } catch (GeneralSecurityException e) {
            logger.error("MailNotifyServiceProvider session init error!", e);
            return null;
        }
    }

    @Override
    public void sendMessage(NotifyMessage notifyMessage) {
        sendMessage(notifyMessage, configRegistry.getConfig().getNotify());
    }

    /**
     * 发送通知消息，使用显式传入的 NotifyProps。
     * <p>
     * ADR-0035：覆盖接口默认实现，从 NotifyProps 中提取 MailProps 发送。
     */
    @Override
    public void sendMessage(NotifyMessage notifyMessage, NotifyProps props) {
        MailProps cfg = extractAndValidate(notifyMessage, props);
        if (cfg == null) return;

        Session currentSession = createSession(cfg);
        if (currentSession == null) return;

        doSend(currentSession, cfg.getUsername(), notifyMessage);
    }

    /**
     * 提取 MailProps 并校验必填项。
     *
     * @return MailProps，校验失败返回 null
     */
    private MailProps extractAndValidate(NotifyMessage notifyMessage, NotifyProps props) {
        if (notifyMessage == null || StringUtil.isBlank(notifyMessage.getContent())) return null;
        MailProps cfg = props != null ? props.getMail() : getMailProps();
        if (cfg == null || StringUtil.isBlank(cfg.getUsername())) return null;
        if (!shouldNotify(notifyMessage.getType(), cfg.getExcludedNotifyTypes())) return null;
        return cfg;
    }

    /**
     * 判断当前通知类型是否应发送（基于 excludedNotifyTypes 排除列表）。
     * <p>
     * excludedNotifyTypes 为空表示全部放行；type 为 null 也放行；
     * 命中排除列表的类型将被跳过。
     */
    private boolean shouldNotify(NotifyType type, List<String> excludedNotifyTypes) {
        if (excludedNotifyTypes == null || excludedNotifyTypes.isEmpty()) {
            return true;
        }
        if (type == null) {
            return true;
        }
        for (String name : excludedNotifyTypes) {
            if (StringUtil.equalsIgnoreCase(type.name(), name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 构建邮件消息并发送。
     */
    private void doSend(Session currentSession, String username, NotifyMessage notifyMessage) {
        try {
            checkMail(notifyMessage);
            String title = String.format("【%s通知】%s", appConfig.getName(), notifyMessage.getTitle());
            String content = createTemplate(appConfig.getName(), notifyMessage.getContent());

            MimeMessage message = new MimeMessage(currentSession);
            message.setFrom(new InternetAddress(username));

            List<String> receivers = notifyMessage.getReceivers();
            InternetAddress[] addresses = new InternetAddress[receivers.size()];
            for (int i = 0; i < receivers.size(); i++) {
                addresses[i] = new InternetAddress(receivers.get(i));
            }
            message.setRecipients(Message.RecipientType.TO, addresses);
            message.setSubject(title);
            message.setContent(content, "text/html;charset=UTF-8");
            Transport.send(message);
            logger.info("邮件发送成功");
        } catch (Exception e) {
            logger.error("邮件发送失败", e);
        }
    }

    private String createTemplate(String appName, String content) {
        String temp = "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<meta charset=\"UTF-8\">\n" +
                "<title>${appName}通知</title>\n" +
                "</head>\n" +
                "<body>\n" +
                "${content}\n" +
                "<p><a href=\"http://gitee.com/ghi/dbsyncer\">访问项目</a></p>\n" +
                "</body>\n" +
                "</html>";
        String replace = StringUtil.replace(temp, "${appName}", appName);
        replace = StringUtil.replace(replace, "${content}", content);
        return replace;
    }

    private void checkMail(NotifyMessage notifyMessage) {
        Assert.notNull(notifyMessage, "通知请求不能为空");
        Assert.notNull(notifyMessage.getTitle(), "邮件主题不能为空");
        Assert.notNull(notifyMessage.getContent(), "邮件内容不能为空");
        Assert.notEmpty(notifyMessage.getReceivers(), "邮件收件人不能为空");
    }
}
