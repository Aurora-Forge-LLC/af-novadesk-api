package com.af.novadesk.api.common.service;

import com.af.novadesk.api.common.config.AuthHubProperties;
import com.af.novadesk.api.common.exception.AuthHubAccessDeniedException;
import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper ERROR_BODY_MAPPER = new ObjectMapper();

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
                "user_id",         userId.toString(),
                "email",           email,
                "first_name",      firstName,
                "last_name",       lastName,
                "organization_id", organizationId.toString()
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
                String reason = extractDownstreamMessage(e,
                        "Your account is not permitted to onboard this user in AuthHub.");
                log.warn("AuthHub admin user creation denied: email={}, status={}, reason={}",
                        email, e.getStatusCode(), reason);
                throw new AuthHubAccessDeniedException(reason, e);
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
     * Extracts the {@code message} field from af-authhub's JSON error body
     * (shape: {@code {"content": null, "message": "...", "status_code": ...}})
     * so the caller-facing exception carries af-authhub's specific, actionable
     * reason (e.g. "ORG_ADMIN may only create members with the ORG_HR, ORG_IT,
     * or ORG_FINANCE role...") instead of a generic fallback. Falls back to
     * {@code fallback} if the body is missing, unparseable, or has no message.
     */
    private String extractDownstreamMessage(HttpClientErrorException e, String fallback) {
        try {
            String body = e.getResponseBodyAsString();
            if (body == null || body.isBlank()) {
                return fallback;
            }
            Map<?, ?> parsed = ERROR_BODY_MAPPER.readValue(body, Map.class);
            Object message = parsed.get("message");
            return (message instanceof String s && !s.isBlank()) ? s : fallback;
        } catch (Exception parseError) {
            return fallback;
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
        String jwtToken = identitySecurityContext.getTokenValue();
        headers.setBearerAuth(jwtToken);

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
     * Permanently deletes a user from af-authhub — removes the User, Profile,
     * and all associated data (org memberships, role/permission assignments,
     * tokens, sessions) from the AuthHub database.
     *
     * <p>Calls {@code DELETE /api/v1/admin/users/{userId}}.</p>
     *
     * <p>This is irreversible and should only be called as part of the employee
     * hard-delete flow — NOT for standard offboarding. 404 responses from AuthHub
     * are treated as non-fatal (the user may have already been deleted).</p>
     *
     * @param userId the AuthHub user UUID to permanently delete
     * @throws AuthHubIntegrationException if the delete call fails (other than 404)
     */
    public void deleteUser(UUID userId) {
        String url = authHubProperties.getBaseUrl()
                + "/api/v1/admin/users/" + userId;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String jwtToken = identitySecurityContext.getTokenValue();
        headers.setBearerAuth(jwtToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        log.info("Calling AuthHub hard-delete user: userId={}", userId);

        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.DELETE, request, Void.class);
            log.info("AuthHub hard-delete successful: userId={}", userId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == org.springframework.http.HttpStatus.NOT_FOUND) {
                log.warn("AuthHub hard-delete: user not found (already deleted?), userId={}", userId);
                // Non-fatal — proceed with local cleanup
            } else {
                log.error("AuthHub hard-delete failed: userId={}, status={}, body={}",
                        userId, e.getStatusCode(), e.getResponseBodyAsString());
                throw new AuthHubIntegrationException(
                        "Failed to hard-delete user in AuthHub: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("AuthHub hard-delete unexpected error: userId={}, error={}",
                    userId, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to hard-delete user in AuthHub: " + e.getMessage(), e);
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
        String jwtToken = identitySecurityContext.getTokenValue();
        headers.setBearerAuth(jwtToken);

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

    /**
     * Creates an entity-scoped user in af-authhub via the admin entity-user endpoint.
     *
     * <p>Unlike {@link #createUser(UUID, String, String, String, UUID)}, this method
     * calls {@code POST /api/v1/admin/entity-users} which creates a {@code User} +
     * {@code Profile} + {@code EntityUser} record without creating an {@code OrgUser}
     * membership. Entity-scoped users do NOT appear in the org_users table.</p>
     *
     * @param userId         pre-generated user UUID
     * @param email          employee email address
     * @param firstName      employee first name
     * @param lastName       employee last name
     * @param role           entity-level role (e.g. ENTITY_ADMIN, FINANCE_MANAGER, EMPLOYEE)
     * @param organizationId organisation for logging
     * @return the {@code user_id} UUID assigned by AuthHub
     * @throws AuthHubIntegrationException if the entity user creation call fails
     */
    @SuppressWarnings("unchecked")
    public UUID createEntityUser(UUID userId, String email, String firstName,
                                  String lastName, String role, UUID organizationId) {
        String url = authHubProperties.getBaseUrl() + "/api/v1/admin/entity-users";

        Map<String, Object> body = Map.of(
                "user_id",         userId.toString(),
                "email",           email,
                "first_name",      firstName,
                "last_name",       lastName,
                "role",            role,
                "organization_id", organizationId.toString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String jwtToken = identitySecurityContext.getTokenValue();
        headers.setBearerAuth(jwtToken);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling AuthHub entity-user creation: userId={}, email={}, role={}, orgId={}",
                userId, email, role, organizationId);

        try {
            var response = restTemplate.postForEntity(url, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("AuthHub entity-user creation returned non-2xx: {} for email={}",
                        response.getStatusCode(), email);
                throw new AuthHubIntegrationException(
                        "AuthHub entity-user creation returned non-2xx: " + response.getStatusCode());
            }

            Map<String, Object> responseBody = response.getBody();
            Map<String, Object> content = responseBody != null
                    ? (Map<String, Object>) responseBody.get("content")
                    : null;

            if (content == null || content.get("user_id") == null) {
                throw new AuthHubIntegrationException(
                        "AuthHub entity-user creation response missing user_id for email=" + email);
            }

            UUID authUserId = UUID.fromString(content.get("user_id").toString());
            log.info("AuthHub entity user created: userId={}, email={}, role={}",
                    authUserId, email, role);
            return authUserId;

        } catch (AuthHubIntegrationException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                log.warn("AuthHub reports entity user ID or email already exists: email={}", email);
                throw new DuplicateEmployeeException(
                        "User ID or email already registered in AuthHub: " + email);
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                String reason = extractDownstreamMessage(e,
                        "Your account is not permitted to onboard entity users in AuthHub.");
                log.warn("AuthHub entity-user creation denied: email={}, status={}, reason={}",
                        email, e.getStatusCode(), reason);
                throw new AuthHubAccessDeniedException(reason, e);
            }
            log.error("AuthHub entity-user creation client error: email={}, status={}, body={}",
                    email, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AuthHubIntegrationException(
                    "Failed to create entity user in AuthHub: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to create entity user in AuthHub: email={}, error={}",
                    email, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to create entity user in AuthHub: " + e.getMessage(), e);
        }
    }

    /**
     * Keeps AuthHub's global role assignment (the source of the JWT
     * {@code roles}/{@code permissions} claims) in step with a per-entity role
     * grant change made here — e.g. revoking an {@link com.af.novadesk.api.finance.entity.EntityUserAccess}
     * grant or changing its role.
     *
     * <p>Calls {@code PATCH /api/v1/admin/entity-users/{userId}/role}. Either
     * argument may be {@code null}: {@code previousRole} null means "nothing
     * to remove" (fresh grant), {@code newRole} null means "nothing to
     * assign" (revocation).</p>
     *
     * <p><b>Best-effort by design:</b> this keeps AuthHub's JWT claims in sync,
     * but a transient failure here must not roll back the local
     * {@code EntityUserAccess} change that already succeeded and is the
     * source of truth for entity-scoped authorization
     * ({@link com.af.novadesk.api.finance.security.EntityAccessGuard}). Failures are
     * logged loudly instead so they're operable — the affected user's JWT
     * will just carry a stale role until their next successful sync or a
     * manual reconciliation.</p>
     *
     * @param authUserId   the AuthHub user UUID whose role assignment changed
     * @param previousRole the entity-tier role to remove, or {@code null}
     * @param newRole      the entity-tier role to assign, or {@code null}
     */
    public void syncEntityRole(UUID authUserId, String previousRole, String newRole) {
        if (java.util.Objects.equals(previousRole, newRole)) {
            return;
        }
        String url = authHubProperties.getBaseUrl() + "/api/v1/admin/entity-users/" + authUserId + "/role";

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("previousRole", previousRole);
        body.put("newRole", newRole);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(identitySecurityContext.getTokenValue());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Syncing entity role in AuthHub: userId={}, {} -> {}", authUserId, previousRole, newRole);

        try {
            restTemplate.exchange(url, org.springframework.http.HttpMethod.PATCH, request, Void.class);
            log.info("Entity role synced in AuthHub: userId={}, {} -> {}", authUserId, previousRole, newRole);
        } catch (Exception e) {
            log.error("Failed to sync entity role in AuthHub: userId={}, {} -> {}, error={} "
                            + "— the user's JWT roles/permissions may be stale until this is retried",
                    authUserId, previousRole, newRole, e.getMessage());
        }
    }
}
