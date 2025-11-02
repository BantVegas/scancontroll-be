// src/main/java/com/bantvegas/sctext/config/SecurityConfig.java
package com.bantvegas.sctext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS musí byť zapnutý, aby použil nastavenia z WebMvcConfigurer (WebConfig)
                .cors(cors -> {})
                // CSRF pri multipart API netreba
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // dôležité: povoliť preflight OPTIONS
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // celé API povoľ bez prihlásenia
                        .requestMatchers("/api/**").permitAll()
                        // všetko ostatné tiež (kľudne si uprav neskôr)
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}

