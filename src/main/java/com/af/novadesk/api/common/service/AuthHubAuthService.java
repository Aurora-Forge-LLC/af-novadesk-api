package com.af.novadesk.api.common.service;

import com.af.novadesk.api.common.config.AuthHubProperties;
import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Manages service-account authentication with af-authhub for
 * machine-to-machine (M2M) admin API calls.
 *
 * <p>Instead of forwarding the end-user's JWT (which couples the two
 * services and creates JWT secret mismatch issues), NovaDesk authenticates
 * with AuthHub using its own service account credentials, obtains a
 * short-lived access token, caches it, and refreshes before expiry.</p>
 *
 * <p>Used by {@link AuthHubClientService} for employee onboarding,
 * offboarding, and invitation resend operations.</p>
 */
@Slf4j
@Service
public class AuthHubAuthService {

    private final RestTemplate restTemplate;
    private final AuthHubProperties authHubProperties;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public AuthHubAuthService(RestTemplate restTemplate,
                              AuthHubProperties authHubProperties) {
        this.restTemplate = restTemplate;
        this.authHubProperties = authHubProperties;
    }

    /**
     * Returns a valid Bearer token for AuthHub admin API calls.
     *
     * <p>Caches the token and refreshes 60 seconds before expiry.
     * Thread-safe via volatile + synchronized double-check.</p>
     *
     * @return a valid JWT access token (without "Bearer " prefix)
     * @throws IllegalStateException if service account credentials are not configured
     * @throws AuthHubIntegrationException if login fails
     */
    public String getToken() {
        if (isTokenValid()) {
            return cachedToken;
        }
        synchronized (this) {
            if (isTokenValid()) {
                return cachedToken;
            }
            cachedToken = login();
            return cachedToken;
        }
    }

    /**
     * Forces a fresh login, bypassing the cache.
     * Useful after a 401 response indicates the cached token was invalidated.
     */
    public String refreshToken() {
        synchronized (this) {
            cachedToken = login();
            return cachedToken;
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private boolean isTokenValid() {
        return cachedToken != null && Instant.now().isBefore(tokenExpiry);
    }

    @SuppressWarnings("unchecked")
    private String login() {
        AuthHubProperties.ServiceAccount sa = authHubProperties.getServiceAccount();
        if (sa.getEmail() == null || sa.getEmail().isBlank()) {
            throw new IllegalStateException(
                    "AuthHub service account email is not configured. "
                  + "Set app.authhub.service-account.email in application.yml "
                  + "or AUTHHUB_SERVICE_ACCOUNT_EMAIL env var.");
        }
        if (sa.getPassword() == null || sa.getPassword().isBlank()) {
            throw new IllegalStateException(
                    "AuthHub service account password is not configured. "
                  + "Set app.authhub.service-account.password in application.yml "
                  + "or AUTHHUB_SERVICE_ACCOUNT_PASSWORD env var.");
        }

        String loginUrl = authHubProperties.getBaseUrl() + "/api/v1/auth/login";

        Map<String, String> body = Map.of(
                "email",    sa.getEmail(),
                "password", sa.getPassword()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        log.info("Authenticating with AuthHub as service account: {}", sa.getEmail());

        try {
            var response = restTemplate.postForEntity(loginUrl, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AuthHubIntegrationException(
                        "AuthHub service account login failed: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> content = (Map<String, Object>) responseBody.get("content");

            if (content == null || content.get("access_token") == null) {
                throw new AuthHubIntegrationException(
                        "AuthHub login response missing access_token");
            }

            String accessToken = content.get("access_token").toString();
            Number expiresIn = content.get("expires_in") instanceof Number
                    ? (Number) content.get("expires_in") : 900_000; // default 15 min

            // Set expiry 60 seconds before actual expiry for safety margin
            long expiresInMs = expiresIn.longValue();
            tokenExpiry = Instant.now().plusMillis(expiresInMs).minusSeconds(60);

            log.info("AuthHub service account authenticated. Token expires in {}s", expiresInMs / 1000);
            return accessToken;

        } catch (AuthHubIntegrationException e) {
            throw e;
        } catch (Exception e) {
            log.error("AuthHub service account login failed: {}", e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to authenticate with AuthHub: " + e.getMessage(), e);
        }
    }
}
