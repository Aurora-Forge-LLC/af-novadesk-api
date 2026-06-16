package com.af.novadesk.api.common.config;

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

    /** Service account credentials for machine-to-machine auth */
    private ServiceAccount serviceAccount = new ServiceAccount();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    public void setServiceAccount(ServiceAccount serviceAccount) {
        this.serviceAccount = serviceAccount;
    }

    /**
     * Service account credentials used by NovaDesk to authenticate
     * with AuthHub for admin API calls (machine-to-machine).
     */
    public static class ServiceAccount {
        /** Email of the service account user in AuthHub */
        private String email;

        /** Password of the service account user in AuthHub */
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
