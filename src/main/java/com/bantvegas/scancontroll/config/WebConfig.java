package com.bantvegas.scancontroll.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/data/master/**")
                .addResourceLocations("file:data/master/");
        registry
                .addResourceHandler("/data/scan/**")
                .addResourceLocations("file:data/scan/");
        registry
                .addResourceHandler("/data/output/**")
                .addResourceLocations("file:data/output/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns( // ← TOTO je dôležité!
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}

