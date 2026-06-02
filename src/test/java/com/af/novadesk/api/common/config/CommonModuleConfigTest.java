package com.af.novadesk.api.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommonModuleConfig}.
 *
 * <p>Validates the {@code AuditorAware} bean and the {@code ObjectMapper} bean
 * configuration. These beans were moved here from {@code FinanceModuleConfig}
 * and {@code PayrollModuleConfig} to avoid bean-definition conflicts across
 * modules.</p>
 *
 * @see CommonModuleConfig
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommonModuleConfig")
class CommonModuleConfigTest {

    private CommonModuleConfig config;

    @BeforeEach
    void setUp() {
        config = new CommonModuleConfig();
    }

    // =========================================================================
    // commonAuditorAware
    // =========================================================================

    @Nested
    @DisplayName("commonAuditorAware")
    class CommonAuditorAware {

        @Test
        @DisplayName("should return JWT subject when authenticated")
        void shouldReturnJwtSubject() {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getSubject()).thenReturn("user-123");

            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(jwt);

            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);

            try {
                // Act
                Optional<String> auditor = config.commonAuditorAware().getCurrentAuditor();

                // Assert
                assertThat(auditor).isPresent();
                assertThat(auditor.get()).isEqualTo("user-123");
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("should return 'system' when no authentication is present")
        void shouldReturnSystemWhenNoAuth() {
            // Arrange
            SecurityContextHolder.clearContext();

            // Act
            Optional<String> auditor = config.commonAuditorAware().getCurrentAuditor();

            // Assert
            assertThat(auditor).isPresent();
            assertThat(auditor.get()).isEqualTo("system");
        }

        @Test
        @DisplayName("should return 'system' when principal is not a JWT")
        void shouldReturnSystemWhenPrincipalNotJwt() {
            // Arrange
            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn("anonymousUser");

            SecurityContext securityContext = mock(SecurityContext.class);
            when(securityContext.getAuthentication()).thenReturn(authentication);
            SecurityContextHolder.setContext(securityContext);

            try {
                // Act
                Optional<String> auditor = config.commonAuditorAware().getCurrentAuditor();

                // Assert
                assertThat(auditor).isPresent();
                assertThat(auditor.get()).isEqualTo("system");
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    // =========================================================================
    // commonObjectMapper
    // =========================================================================

    @Nested
    @DisplayName("commonObjectMapper")
    class CommonObjectMapper {

        @Test
        @DisplayName("should create ObjectMapper with JavaTimeModule and ISO date format")
        void shouldCreateObjectMapper() throws Exception {
            // Arrange
            ObjectMapper mapper = config.commonObjectMapper();

            // Act
            String dateResult = mapper.writeValueAsString(LocalDate.of(2026, 5, 20));
            String dateTimeResult = mapper.writeValueAsString(
                    LocalDateTime.of(2026, 5, 20, 10, 30, 0));

            // Assert
            assertThat(mapper.getRegisteredModuleIds()).contains("jackson-datatype-jsr310");
            assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
            assertThat(dateResult).contains("2026-05-20");
            assertThat(dateTimeResult).contains("2026-05-20T10:30:00");
        }
    }
}
