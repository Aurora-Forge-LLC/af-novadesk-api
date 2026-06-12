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
 * pre-generates a user UUID, creates the employee record, then calls
 * af-authhub's {@code POST /api/v1/admin/users} to provision the
 * corresponding AuthHub identity.</p>
 *
 * <p>Authenticates by forwarding the current request's JWT token as a Bearer
 * token in the {@code Authorization} header. This is the same JWT that was
 * validated by Spring Security against af-authhub's JWKS endpoint on the
 * inbound request, so af-authhub will accept it for outbound calls as well.</p>
 *
 * <p>The admin endpoint:
 * <ul>
 *   <li>Creates a User with {@code PENDING_SETUP} password (passwordless)</li>
 *   <li>Creates a Profile with firstName / lastName</li>
 *   <li>Creates an OrgUser membership with EMPLOYEE role</li>
 *   <li>Assigns the EMPLOYEE role (NOT SUPER_ADMIN)</li>
 *   <li>Generates a password-setup token and publishes a PORTAL_INVITE
 *       email event via RabbitMQ → af-authhub-mailer sends the invite</li>
 * </ul>
 * </p>
 *
 * @see com.af.infra.core.authhub.api.AdminUserApi
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
     * Creates a passwordless user in af-authhub via the admin onboarding endpoint
     * and returns the AuthHub-assigned user UUID.
     *
     * <p>The user is created with {@code PENDING_SETUP} — no password is set.
     * AuthHub generates a single-use password-setup token and publishes an invite
     * email event. The employee receives an email with a link to set their password
     * and complete onboarding.</p>
     *
     * <p>The caller's JWT is forwarded as a Bearer token so AuthHub can:
     * <ul>
     *   <li>Derive the target organization from the JWT's {@code organizationId} claim</li>
     *   <li>Authorize the request (requires {@code organizations:write})</li>
     * </ul>
     * </p>
     *
     * @param userId         pre-generated user UUID (novadesk generates this)
     * @param email          employee email address
     * @param firstName      employee first name
     * @param lastName       employee last name
     * @param organizationId organisation the employee belongs to (for logging)
     * @return the {@code user_id} UUID (same as the pre-generated {@code userId})
     * @throws AuthHubIntegrationException if the admin user creation call fails
     */
    @SuppressWarnings("unchecked")
    public UUID createUser(UUID userId, String email, String firstName,
                           String lastName, UUID organizationId) {
        String url = authHubProperties.getBaseUrl() + "/api/v1/admin/users";

        Map<String, Object> body = Map.of(
                "user_id",    userId.toString(),
                "email",      email,
                "first_name", firstName,
                "last_name",  lastName
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward the caller's JWT so AuthHub can derive the target organization
        // and authorize this admin operation (requires organizations:write).
        String jwtToken = identitySecurityContext.getTokenValue();
        headers.setBearerAuth(jwtToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling AuthHub admin user creation: userId={}, email={}, orgId={}",
                userId, email, organizationId);

        try {
            var response = restTemplate.postForEntity(url, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("AuthHub admin user creation returned non-2xx: {} for email={}",
                        response.getStatusCode(), email);
                throw new AuthHubIntegrationException(
                        "AuthHub admin user creation returned non-2xx: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> content = responseBody != null
                    ? (Map<String, Object>) responseBody.get("content")
                    : null;

            if (content == null || content.get("user_id") == null) {
                throw new AuthHubIntegrationException(
                        "AuthHub admin user creation response missing user_id for email=" + email);
            }

            UUID authUserId = UUID.fromString(content.get("user_id").toString());
            log.info("AuthHub admin user created: userId={}, email={}, status={}",
                    authUserId, email,
                    content.getOrDefault("status", "UNKNOWN"));
            return authUserId;

        } catch (AuthHubIntegrationException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.warn("AuthHub reports user ID or email already exists: email={}", email);
                throw new DuplicateEmployeeException(
                        "User ID or email already registered in AuthHub: " + email);
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                log.error("AuthHub admin user creation auth error: email={}, status={}",
                        email, e.getStatusCode());
                throw new AuthHubIntegrationException(
                        "Not authorized to create users in AuthHub — check JWT permissions", e);
            }
            log.error("AuthHub admin user creation client error: email={}, status={}, body={}",
                    email, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthHubIntegrationException(
                    "Failed to create user in AuthHub: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to create admin user in AuthHub: email={}, error={}",
                    email, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to create user in AuthHub: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy convenience overload — generates a fresh user UUID and delegates
     * to {@link #createUser(UUID, String, String, String, UUID)}.
     *
     * @param email          employee email address
     * @param firstName      employee first name
     * @param lastName       employee last name
     * @param organizationId organisation the employee belongs to (for logging)
     * @return the {@code user_id} UUID assigned by AuthHub
     * @throws AuthHubIntegrationException if the admin user creation call fails
     */
    public UUID createUser(String email, String firstName,
                           String lastName, UUID organizationId) {
        return createUser(UUID.randomUUID(), email, firstName, lastName, organizationId);
    }
}
