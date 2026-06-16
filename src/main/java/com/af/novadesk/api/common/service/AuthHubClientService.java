package com.af.novadesk.api.common.service;

import com.af.novadesk.api.common.config.AuthHubProperties;
import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
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
    private final AuthHubAuthService authHubAuthService;

    public AuthHubClientService(RestTemplate restTemplate,
                                AuthHubProperties authHubProperties,
                                AuthHubAuthService authHubAuthService) {
        this.restTemplate = restTemplate;
        this.authHubProperties = authHubProperties;
        this.authHubAuthService = authHubAuthService;
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
     * <p>Uses NovaDesk's service account JWT (obtained via
     * {@link AuthHubAuthService}) instead of forwarding the end-user's JWT.
     * The service account must have {@code organizations:write} permission.</p>
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

        // Use NovaDesk's service account JWT instead of forwarding the
        // end-user's JWT. This eliminates JWT secret mismatch issues and
        // decouples the two services.
        String serviceToken = authHubAuthService.getToken();
        headers.setBearerAuth(serviceToken);

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

    /**
     * Offboards a user from an organization in af-authhub — soft-deletes the
     * OrgUser membership, revokes all tokens/sessions, deactivates the account
     * if no other active org memberships remain.
     *
     * <p>Calls {@code POST /api/v1/admin/users/{userId}/organizations/{orgId}/offboard}.</p>
     *
     * @param userId         the AuthHub user UUID to offboard
     * @param organizationId the organization to offboard from
     * @throws AuthHubIntegrationException if the offboard call fails
     */
    @SuppressWarnings("unchecked")
    public void offboardUser(UUID userId, UUID organizationId) {
        String url = authHubProperties.getBaseUrl()
                + "/api/v1/admin/users/" + userId
                + "/organizations/" + organizationId + "/offboard";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String serviceToken = authHubAuthService.getToken();
        headers.setBearerAuth(serviceToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.info("Calling AuthHub offboard: userId={}, orgId={}", userId, organizationId);

        try {
            restTemplate.postForEntity(url, request, Map.class);
            log.info("AuthHub offboard successful: userId={}, orgId={}", userId, organizationId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.warn("AuthHub offboard: membership not found for userId={}, orgId={}", userId, organizationId);
            } else {
                log.error("AuthHub offboard failed: userId={}, orgId={}, status={}, body={}",
                        userId, organizationId, e.getStatusCode(), e.getResponseBodyAsString());
                throw new AuthHubIntegrationException(
                        "Failed to offboard user in AuthHub: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("AuthHub offboard unexpected error: userId={}, orgId={}, error={}",
                    userId, organizationId, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to offboard user in AuthHub: " + e.getMessage(), e);
        }
    }

    /**
     * Resends an invitation to a user who hasn't yet set their password.
     * Invalidates existing tokens, generates a fresh password-setup token,
     * and publishes a new invite email via RabbitMQ.
     *
     * <p>Calls {@code POST /api/v1/admin/users/{userId}/invitations/resend}.
     * Only works for users still in PENDING_SETUP state.</p>
     *
     * @param userId the AuthHub user UUID
     * @throws AuthHubIntegrationException if the resend call fails
     */
    public void resendInvitation(UUID userId) {
        String url = authHubProperties.getBaseUrl()
                + "/api/v1/admin/users/" + userId + "/invitations/resend";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String serviceToken = authHubAuthService.getToken();
        headers.setBearerAuth(serviceToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.info("Calling AuthHub resend invitation: userId={}", userId);

        try {
            restTemplate.postForEntity(url, request, Map.class);
            log.info("AuthHub invitation resent: userId={}", userId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.warn("AuthHub resend invitation: user has already accepted, userId={}", userId);
                throw new AuthHubIntegrationException(
                        "Cannot resend invitation — user has already set their password", e);
            }
            log.error("AuthHub resend invitation failed: userId={}, status={}, body={}",
                    userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthHubIntegrationException(
                    "Failed to resend invitation: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("AuthHub resend invitation unexpected error: userId={}, error={}",
                    userId, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to resend invitation: " + e.getMessage(), e);
        }
    }
}
