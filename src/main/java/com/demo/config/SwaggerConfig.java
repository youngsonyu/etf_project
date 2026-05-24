package com.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI etfOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ETF Backend API")
                .description("ETF project backend API documentation")
                .version("v1"));
    }
}
