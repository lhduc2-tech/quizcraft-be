package com.quizcraft.quiz_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.filter.CharacterEncodingFilter characterEncodingFilter() {
        org.springframework.web.filter.CharacterEncodingFilter filter = new org.springframework.web.filter.CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        return filter;
    }
}
