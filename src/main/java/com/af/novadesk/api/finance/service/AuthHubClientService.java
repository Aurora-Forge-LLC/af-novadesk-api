package com.af.novadesk.api.common.service;

import com.af.novadesk.api.common.config.AuthHubProperties;
import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for calling af-authhub's admin user creation endpoint.
 *
 * <p>Used during the employee onboarding flow where novadesk-api
 * creates a ShadowUser first, then calls af-authhub to provision the user.</p>
 *
 * <p>Authenticates by forwarding the current request's JWT token as a Bearer
 * token in the {@code Authorization} header. This is the same JWT that was
 * validated by Spring Security against af-authhub's JWKS endpoint on the
 * inbound request, so af-authhub will accept it for outbound calls as well.</p>
 */
@Slf4j
@Service
public class AuthHubClientService {

    private final RestTemplate restTemplate;
    private final AuthHubProperties authHubProperties;
    private final IdentitySecurityContext identitySecurityContext;

    public AuthHubClientService(RestTemplate restTemplate,
                                AuthHubProperties authHubProperties,
                                IdentitySecurityContext identitySecurityContext) {
        this.restTemplate = restTemplate;
        this.authHubProperties = authHubProperties;
        this.identitySecurityContext = identitySecurityContext;
    }

    /**
     * Registers a new user in af-authhub via the public signup endpoint and
     * returns the UUID that AuthHub assigned to the new account.
     *
     * <p>A temporary password is generated automatically; the employee resets
     * it on first login via the standard forgot-password flow.</p>
     *
     * @param email          employee email address
     * @param firstName      employee first name
     * @param lastName       employee last name
     * @param organizationId organisation the employee belongs to (for logging)
     * @return the {@code user_id} UUID assigned by AuthHub
     * @throws AuthHubIntegrationException if the signup call fails
     */
    @SuppressWarnings("unchecked")
    public UUID createUser(String email, String firstName,
                           String lastName, UUID organizationId) {
        String url = authHubProperties.getBaseUrl() + "/api/v1/auth/signup";

        // Generate a temporary password — employee must reset on first login.
        String tempPassword = "Tmp@" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "1";

        Map<String, Object> body = Map.of(
                "email",      email,
                "password",   tempPassword,
                "first_name", firstName,
                "last_name",  lastName
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling AuthHub signup for employee: email={}, orgId={}", email, organizationId);

        try {
            var response = restTemplate.postForEntity(url, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("AuthHub signup returned non-2xx: {} for email={}", response.getStatusCode(), email);
                throw new AuthHubIntegrationException(
                        "AuthHub signup returned non-2xx: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> content = responseBody != null
                    ? (Map<String, Object>) responseBody.get("content")
                    : null;

            if (content == null || content.get("user_id") == null) {
                throw new AuthHubIntegrationException(
                        "AuthHub signup response missing user_id for email=" + email);
            }

            UUID authUserId = UUID.fromString(content.get("user_id").toString());
            log.info("AuthHub user registered: userId={}, email={}", authUserId, email);
            return authUserId;

        } catch (AuthHubIntegrationException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.warn("AuthHub reports email already registered: email={}", email);
                throw new DuplicateEmployeeException("Email already registered in AuthHub: " + email);
            }
            log.error("AuthHub signup client error: email={}, status={}, body={}", email, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthHubIntegrationException(
                    "Failed to create user in AuthHub: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to register user in AuthHub: email={}, error={}", email, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to create user in AuthHub: " + e.getMessage(), e);
        }
    }
}
