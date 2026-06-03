package com.af.novadesk.api.payroll.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Payroll module configuration.
 *
 * <p>JPA auditing is enabled globally via {@link
 * com.af.novadesk.api.common.config.CommonModuleConfig}.</p>
 */
@Configuration
public class PayrollModuleConfig {

    /**
     * RestTemplate for outbound HTTP calls to af-authhub.
     * Configured with 5-second connect timeout and 10-second read timeout.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
