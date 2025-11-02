package com.bantvegas.sctext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry reg) {
                reg.addMapping("/**")
                        .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                        .allowedHeaders("*")
                        .allowedOrigins(
                                "http://localhost:5178",
                                "http://localhost:5179",
                                "http://localhost:5179",
                                "http://127.0.0.1:5178",
                                "http://127.0.0.1:5174",
                                "http://127.0.0.1:5173"
                        )
                        .maxAge(3600);
            }
        };
    }
}


