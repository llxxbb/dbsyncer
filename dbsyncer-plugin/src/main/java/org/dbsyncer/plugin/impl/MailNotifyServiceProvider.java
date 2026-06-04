package org.dbsyncer.plugin.impl;

import com.sun.mail.util.MailSSLSocketFactory;
import org.dbsyncer.common.config.AppConfig;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.plugin.config.NotifyConfig;
import org.dbsyncer.plugin.model.NotifyMessage;
import org.dbsyncer.plugin.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
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
    private final NotifyConfig notifyConfig;

    /**
     * 邮箱会话
     */
    private Session session;

    public MailNotifyServiceProvider(AppConfig appConfig, NotifyConfig notifyConfig) {
        this.appConfig = appConfig;
        this.notifyConfig = notifyConfig;
    }

    @PostConstruct
    private void init() {
        NotifyConfig.MailConfig mail = notifyConfig.getMail();
        if (StringUtil.isBlank(mail.getUsername())) {
            return;
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
            // 构建授权信息，用于进行SMTP身份验证
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mail.getUsername(), mail.getPassword());
                }
            });
        } catch (GeneralSecurityException e) {
            logger.error("MailNotifyServiceProvider init error!", e);
        }
    }

    @Override
    public void sendMessage(NotifyMessage notifyMessage) {
        if (session == null) {
            return;
        }

        try {
            checkMail(notifyMessage);
            // 统一应用标题
            String title = String.format("【%s通知】%s", appConfig.getName(), notifyMessage.getTitle());
            String content = createTemplate(appConfig.getName(), notifyMessage.getContent());

            // 创建邮件消息
            MimeMessage message = new MimeMessage(session);
            // 设置发件人
            message.setFrom(new InternetAddress(notifyConfig.getMail().getUsername()));

            // 接收人
            List<String> messageReceivers = notifyMessage.getReceivers();
            int size = messageReceivers.size();
            InternetAddress[] addresses = new InternetAddress[size];
            for (int i = 0; i < size; i++) {
                addresses[i] = new InternetAddress(messageReceivers.get(i));
            }
            message.setRecipients(Message.RecipientType.TO, addresses);

            // 设置邮件标题
            message.setSubject(title);

            // 设置邮件的内容体
            message.setContent(content, "text/html;charset=UTF-8");
            // 发送邮件
            Transport.send(message);
            logger.info("simple mail send success");
        } catch (Exception e) {
            logger.error("simple mail send error!", e);
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
