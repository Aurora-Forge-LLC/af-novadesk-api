package com.af.novadesk.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI / Swagger UI configuration.
 *
 * Swagger UI is accessible at:
 *   Local : http://localhost:8080/novadesk/swagger-ui/index.html
 *   DIT   : https://novadesk-api.dit.auroraforge.co/novadesk-api/swagger-ui/index.html
 *   SIT   : https://novadesk-api.sit.auroraforge.co/novadesk-api/swagger-ui/index.html
 *
 * API docs (JSON) at:
 *   Local : http://localhost:8080/novadesk-api/v3/api-docs
 *   DIT   : https://novadesk-api.dit.auroraforge.co/novadesk-api/v3/api-docs
 *   SIT   : https://novadesk-api.sit.auroraforge.co/novadesk-api/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.openapi.server-url}")
    private String serverUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(new Server().url(serverUrl).description("Current environment")));
    }

    private Info apiInfo() {
        return new Info()
                .title("AF NovaDesk API")
                .description("NovaDesk — helpdesk and ticketing service for Aurora Forge.")
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

