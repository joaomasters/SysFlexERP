package com.acougue.config;

import com.acougue.modules.industrial.ModuloIndustrialInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ModuloIndustrialInterceptor moduloIndustrial;

    public WebConfig(ModuloIndustrialInterceptor moduloIndustrial) {
        this.moduloIndustrial = moduloIndustrial;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moduloIndustrial)
                .addPathPatterns("/industrial/**")
                .excludePathPatterns("/industrial/situacao");
    }
}
