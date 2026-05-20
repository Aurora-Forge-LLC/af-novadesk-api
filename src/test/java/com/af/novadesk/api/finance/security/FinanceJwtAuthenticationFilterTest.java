package com.af.novadesk.api.finance.security;

import com.af.novadesk.api.identity.service.ShadowUserSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FinanceJwtAuthenticationFilter}.
 *
 * <p>Validates the shadow user sync behaviour on every authenticated request:
 * happy path, missing claims, sync failure, and unauthenticated requests.</p>
 *
 * @see FinanceJwtAuthenticationFilter
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FinanceJwtAuthenticationFilter")
class FinanceJwtAuthenticationFilterTest {

    @Mock
    private ShadowUserSyncService shadowUserSyncService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private FinanceJwtAuthenticationFilter filter;

    private UUID authUserId;
    private UUID orgId;
    private String email;
    private String displayName;

    @BeforeEach
    void setUp() {
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        email = "john.doe@example.com";
        displayName = "John Doe";
    }

    private void setupJwtAuthentication(String orgIdClaim, String emailClaim, String nameClaim) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(authUserId.toString());
        when(jwt.getClaimAsString("organizationId")).thenReturn(orgIdClaim);
        when(jwt.getClaimAsString("email")).thenReturn(emailClaim);
        when(jwt.getClaimAsString("name")).thenReturn(nameClaim);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    // =========================================================================
    // Happy path
    // =========================================================================

    @Nested
    @DisplayName("when JWT has all required claims")
    class HappyPath {

        @Test
        @DisplayName("should upsert shadow user and continue filter chain")
        void shouldUpsertShadowUser() throws Exception {
            // Arrange
            setupJwtAuthentication(orgId.toString(), email, displayName);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService).upsert(authUserId, orgId, email, displayName);
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should upsert with null displayName when name claim is absent")
        void shouldUpsertWithNullDisplayName() throws Exception {
            // Arrange
            setupJwtAuthentication(orgId.toString(), email, null);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService).upsert(authUserId, orgId, email, null);
            verify(filterChain).doFilter(request, response);
        }
    }

    // =========================================================================
    // Missing claims
    // =========================================================================

    @Nested
    @DisplayName("when JWT is missing required claims")
    class MissingClaims {

        @Test
        @DisplayName("should skip sync when organizationId is missing")
        void shouldSkipSyncWhenOrgIdMissing() throws Exception {
            // Arrange
            setupJwtAuthentication(null, email, displayName);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService, never()).upsert(any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should skip sync when email is missing")
        void shouldSkipSyncWhenEmailMissing() throws Exception {
            // Arrange
            setupJwtAuthentication(orgId.toString(), null, displayName);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService, never()).upsert(any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should skip sync when both organizationId and email are missing")
        void shouldSkipSyncWhenBothMissing() throws Exception {
            // Arrange
            setupJwtAuthentication(null, null, displayName);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService, never()).upsert(any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }
    }

    // =========================================================================
    // Sync failure
    // =========================================================================

    @Nested
    @DisplayName("when shadow user sync fails")
    class SyncFailure {

        @Test
        @DisplayName("should log warning and continue filter chain")
        void shouldContinueOnSyncFailure() throws Exception {
            // Arrange
            setupJwtAuthentication(orgId.toString(), email, displayName);
            doThrow(new RuntimeException("DB connection lost"))
                    .when(shadowUserSyncService).upsert(authUserId, orgId, email, displayName);

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService).upsert(authUserId, orgId, email, displayName);
            verify(filterChain).doFilter(request, response);
        }
    }

    // =========================================================================
    // Unauthenticated
    // =========================================================================

    @Nested
    @DisplayName("when no JWT authentication is present")
    class Unauthenticated {

        @Test
        @DisplayName("should be a no-op and continue filter chain")
        void shouldBeNoOp() throws Exception {
            // Arrange
            SecurityContextHolder.clearContext();

            // Act
            filter.doFilterInternal(request, response, filterChain);

            // Assert
            verify(shadowUserSyncService, never()).upsert(any(), any(), any(), any());
            verify(filterChain).doFilter(request, response);
        }
    }
}
