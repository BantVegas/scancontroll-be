// src/main/java/com/bantvegas/sctext/config/SecurityConfig.java
package com.bantvegas.sctext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // preflight
                        .requestMatchers("/api/**").permitAll()                // API povolené
                        .anyRequest().permitAll()
                )
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable());

        return http.build();
    }

    /**
     * Povolené FE domény (scancontroll.eu + www + vercel preview).
     * Ak používaš iný FE origin, pridaj ho sem.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(
                "https://scancontroll.eu",
                "https://www.scancontroll.eu",
                "https://scancontroll-fe.vercel.app" // nechaj pre preview buildy
        ));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of(
                "Origin","Content-Type","Accept","Authorization",
                "X-Requested-With","Cache-Control","Pragma"
        ));
        // Cookie-based auth nepoužívame; nechávam false. Ak by si chcel cookies, prepnúť na true.
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L); // cache pre preflight

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}

