package org.dbsyncer.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class HelpDocWebConfig implements WebMvcConfigurer {

    @Value("${helpdoc.path:./helpdocs}")
    private String helpDocPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/helpdocs/**")
                .addResourceLocations("file:" + helpDocPath + "/");
    }
}
