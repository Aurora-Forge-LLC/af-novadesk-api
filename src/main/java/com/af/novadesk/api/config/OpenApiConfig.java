package com.af.novadesk.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI / Swagger UI configuration for NovaDesk API.
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.openapi.server-url}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(new Server().url(serverUrl).description("Current environment")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    private Info apiInfo() {
        return new Info()
                .title("AF NovaDesk API")
                .description("NovaDesk — helpdesk and financial management service for Aurora Forge. " +
                        "All responses are wrapped in a standardized ApiResponse envelope with consistent error handling.")
                .version("1.0")
                .contact(new Contact()
                        .name("Aurora Forge LLC")
                        .url("https://auroraforge.co")
                )
                .license(new License()
                        .name("Private — Aurora Forge LLC")
                );
    }
}
