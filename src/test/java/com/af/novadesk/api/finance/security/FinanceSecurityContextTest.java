package com.af.novadesk.api.finance.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.af.novadesk.api.finance.exception.JwtClaimMissingException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FinanceSecurityContext}.
 *
 * <p>Validates JWT claim extraction and error handling when no
 * authenticated principal is present.</p>
 *
 * @see FinanceSecurityContext
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FinanceSecurityContext")
class FinanceSecurityContextTest {

    private FinanceSecurityContext context;

    private UUID authUserId;
    private UUID orgId;
    private String email;
    private String displayName;

    @BeforeEach
    void setUp() {
        context = new FinanceSecurityContext();
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        orgId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        email = "john.doe@example.com";
        displayName = "John Doe";
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupJwtAuthentication(Map<String, Object> claims) {
        Jwt jwt = mock(Jwt.class);
        lenient().when(jwt.getSubject()).thenReturn(authUserId.toString());
        lenient().when(jwt.getClaimAsString("organizationId")).thenReturn((String) claims.get("organizationId"));
        lenient().when(jwt.getClaimAsString("email")).thenReturn((String) claims.get("email"));
        lenient().when(jwt.getClaimAsString("name")).thenReturn((String) claims.get("name"));

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(jwt);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    // =========================================================================
    // getAuthUserId
    // =========================================================================

    @Nested
    @DisplayName("getAuthUserId")
    class GetAuthUserId {

        @Test
        @DisplayName("should return auth user ID from JWT sub claim")
        void shouldReturnAuthUserId() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "organizationId", orgId.toString(),
                    "email", email,
                    "name", displayName
            ));

            // Act
            UUID result = context.getAuthUserId();

            // Assert
            assertThat(result).isEqualTo(authUserId);
        }

        @Test
        @DisplayName("should throw IllegalStateException when no authentication present")
        void shouldThrowWhenNoAuth() {
            // Arrange
            SecurityContextHolder.clearContext();

            // Act & Assert
            assertThatThrownBy(() -> context.getAuthUserId())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No authenticated JWT principal");
        }
    }

    // =========================================================================
    // getOrganizationId
    // =========================================================================

    @Nested
    @DisplayName("getOrganizationId")
    class GetOrganizationId {

        @Test
        @DisplayName("should return organization ID from JWT claim")
        void shouldReturnOrgId() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "organizationId", orgId.toString(),
                    "email", email,
                    "name", displayName
            ));

            // Act
            UUID result = context.getOrganizationId();

            // Assert
            assertThat(result).isEqualTo(orgId);
        }

        @Test
        @DisplayName("should throw IllegalStateException when organizationId claim is missing")
        void shouldThrowWhenOrgIdMissing() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "email", email,
                    "name", displayName
            ));

            // Act & Assert
            assertThatThrownBy(() -> context.getOrganizationId())
                    .isInstanceOf(JwtClaimMissingException.class)
                    .hasMessageContaining("organizationId");
        }
    }

    // =========================================================================
    // getEmail
    // =========================================================================

    @Nested
    @DisplayName("getEmail")
    class GetEmail {

        @Test
        @DisplayName("should return email from JWT claim")
        void shouldReturnEmail() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "organizationId", orgId.toString(),
                    "email", email,
                    "name", displayName
            ));

            // Act
            String result = context.getEmail();

            // Assert
            assertThat(result).isEqualTo(email);
        }

        @Test
        @DisplayName("should return null when email claim is missing")
        void shouldReturnNullWhenMissing() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "organizationId", orgId.toString(),
                    "name", displayName
            ));

            // Act
            String result = context.getEmail();

            // Assert
            assertThat(result).isNull();
        }
    }

    // =========================================================================
    // getDisplayName
    // =========================================================================

    @Nested
    @DisplayName("getDisplayName")
    class GetDisplayName {

        @Test
        @DisplayName("should return display name from JWT claim")
        void shouldReturnDisplayName() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "organizationId", orgId.toString(),
                    "email", email,
                    "name", displayName
            ));

            // Act
            String result = context.getDisplayName();

            // Assert
            assertThat(result).isEqualTo(displayName);
        }

        @Test
        @DisplayName("should return null when name claim is missing")
        void shouldReturnNullWhenMissing() {
            // Arrange
            setupJwtAuthentication(Map.of(
                    "organizationId", orgId.toString(),
                    "email", email
            ));

            // Act
            String result = context.getDisplayName();

            // Assert
            assertThat(result).isNull();
        }
    }
}
