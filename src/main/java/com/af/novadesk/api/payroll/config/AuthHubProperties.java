package com.af.novadesk.api.payroll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the af-authhub service connection.
 *
 * <p>Reads from {@code app.authhub.*} in application.yml.</p>
 */
@Component
@ConfigurationProperties(prefix = "app.authhub")
public class AuthHubProperties {

    /** Base URL of af-authhub, e.g. "http://af-authhub/auth" */
    private String baseUrl;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
}
