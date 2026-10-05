package com.carevoice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class FrontendCorsConfiguration implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public FrontendCorsConfiguration(
            @Value("${carevoice.frontend.origin:http://localhost:5173}") String origin) {
        this.allowedOrigins = Arrays.stream(origin.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty() && !"*".equals(value))
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}
