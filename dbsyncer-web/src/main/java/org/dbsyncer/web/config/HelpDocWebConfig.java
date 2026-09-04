package org.dbsyncer.web.config;

import org.dbsyncer.common.config.ConfigKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HelpDocWebConfig implements WebMvcConfigurer {

    @Value("${" + ConfigKeys.HELPDOC_PATH + ":./helpdocs}")
    private String helpDocPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/helpdocs/**")
                .addResourceLocations("file:" + helpDocPath + "/");
    }
}
