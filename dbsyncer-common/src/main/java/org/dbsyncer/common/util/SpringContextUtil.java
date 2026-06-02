package org.dbsyncer.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文工具类
 * 供非 Spring 管理的类（如连接器、监听器）获取 Spring Bean
 *
 * @author DBSyncer
 * @version 1.0.0
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    /**
     * 获取 Spring 上下文
     */
    public static ApplicationContext getContext() {
        return applicationContext;
    }

    /**
     * 根据类型获取 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext == null ? null : applicationContext.getBean(clazz);
    }

    /**
     * 根据名称和类型获取 Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext == null ? null : applicationContext.getBean(name, clazz);
    }
}
