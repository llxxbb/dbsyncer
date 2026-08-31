package org.dbsyncer.web.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.AbstractProtocol;
import org.dbsyncer.common.startup.StartupGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tomcat 启动门闸实现
 * 延迟绑定端口，直到配置预加载完成后再开放
 */
@Component
public class TomcatStartupGate implements WebServerFactoryCustomizer<TomcatServletWebServerFactory>, StartupGate {

    private static final Logger logger = LoggerFactory.getLogger(TomcatStartupGate.class);

    private final List<Connector> connectors = new ArrayList<>();

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                // 延迟绑定：启动时不绑定端口，等待 open() 调用
                connector.setProperty("bindOnInit", "false");
                connectors.add(connector);
                logger.info("Tomcat Connector 设置为延迟绑定模式");
            }
        });
    }

    @Override
    public void open() {
        logger.info("开放启动门闸，绑定 Tomcat 端口...");
        for (Connector connector : connectors) {
            try {
                if (connector.getProtocolHandler() instanceof AbstractProtocol) {
                    AbstractProtocol<?> protocol = (AbstractProtocol<?>) connector.getProtocolHandler();
                    // 通过反射调用 getEndpoint() 并 resume()
                    java.lang.reflect.Method getEndpointMethod = AbstractProtocol.class.getDeclaredMethod("getEndpoint");
                    getEndpointMethod.setAccessible(true);
                    Object endpoint = getEndpointMethod.invoke(protocol);
                    java.lang.reflect.Method resumeMethod = endpoint.getClass().getMethod("resume");
                    resumeMethod.invoke(endpoint);
                    logger.info("Tomcat 端口已开放");
                }
            } catch (Exception e) {
                logger.error("开放 Tomcat 端口失败", e);
            }
        }
    }
}
