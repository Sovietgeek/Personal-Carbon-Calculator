package com.infosys.milestone3_goals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 🔥 IMPORTS
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Milestone3GoalsApplication {

    public static void main(String[] args) {
        SpringApplication.run(Milestone3GoalsApplication.class, args);
    }

    // 🔐 JWT FILTER REGISTER
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilter() {
        FilterRegistrationBean<JwtFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new JwtFilter());
        bean.addUrlPatterns("/goals/*"); // protect goals API
        return bean;
    }
}