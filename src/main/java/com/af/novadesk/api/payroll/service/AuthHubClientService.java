package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.identity.security.IdentitySecurityContext;
import com.af.novadesk.api.payroll.config.AuthHubProperties;
import com.af.novadesk.api.payroll.exception.AuthHubIntegrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for calling af-authhub's admin user creation endpoint.
 *
 * <p>Used during the reversed employee onboarding flow where novadesk-api
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
     * Calls af-authhub to create a new user via the admin endpoint.
     *
     * <p>Forwards the current request's JWT token to authenticate the call.
     * The target endpoint requires {@code hasAuthority('organizations:write')},
     * which must be present in the JWT's {@code permissions} claim.</p>
     *
     * @param userId         pre-generated UUID to use as the auth user ID
     * @param email          employee email address
     * @param firstName      employee first name
     * @param lastName       employee last name
     * @param organizationId organization the employee belongs to
     * @throws AuthHubIntegrationException if the call fails or returns a non-2xx status
     */
    public void createUser(UUID userId, String email, String firstName,
                           String lastName, UUID organizationId) {
        String url = authHubProperties.getBaseUrl() + "/api/v1/admin/users";

        Map<String, Object> body = Map.of(
                "user_id", userId,
                "email", email,
                "first_name", firstName,
                "last_name", lastName,
                "organization_id", organizationId
        );

        // Forward the current request's JWT token to authenticate with af-authhub.
        // This is the same token that was validated by Spring Security against
        // af-authhub's JWKS on the inbound request.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(identitySecurityContext.getTokenValue());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.info("Calling AuthHub admin create user: userId={}, email={}, orgId={}",
                userId, email, organizationId);

        try {
            var response = restTemplate.postForEntity(url, request, Map.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("AuthHub returned non-2xx status: {} for userId={}",
                        response.getStatusCode(), userId);
                throw new AuthHubIntegrationException(
                        "AuthHub returned non-2xx: " + response.getStatusCode());
            }

            log.info("AuthHub user created successfully: userId={}, email={}",
                    userId, email);

        } catch (RestClientException e) {
            log.error("Failed to create user in AuthHub: userId={}, error={}",
                    userId, e.getMessage());
            throw new AuthHubIntegrationException(
                    "Failed to create user in AuthHub: " + e.getMessage(), e);
        }
    }
}
