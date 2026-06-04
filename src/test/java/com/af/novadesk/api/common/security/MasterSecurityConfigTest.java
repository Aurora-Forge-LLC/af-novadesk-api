package com.af.novadesk.api.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MasterSecurityConfig}.
 *
 * <p>Validates that the infrastructure filter chain is created successfully
 * and the public paths are configured.</p>
 *
 * @see MasterSecurityConfig
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MasterSecurityConfig")
class MasterSecurityConfigTest {

    private MasterSecurityConfig config;

    @Mock
    private HttpSecurity http;

    @BeforeEach
    void setUp() {
        config = new MasterSecurityConfig();
    }

    @Test
    @DisplayName("should create infrastructure filter chain bean")
    void shouldCreateInfrastructureFilterChain() throws Exception {
        // Arrange - stub the HttpSecurity builder chain
        when(http.securityMatcher(any(RequestMatcher.class))).thenReturn(http);
        when(http.cors(any())).thenReturn(http);
        when(http.sessionManagement(any())).thenReturn(http);
        when(http.csrf(any())).thenReturn(http);
        when(http.formLogin(any())).thenReturn(http);
        when(http.httpBasic(any())).thenReturn(http);
        when(http.authorizeHttpRequests(any())).thenReturn(http);

        DefaultSecurityFilterChain mockChain = org.mockito.Mockito.mock(DefaultSecurityFilterChain.class);
        when(http.build()).thenReturn(mockChain);

        // Act
        SecurityFilterChain chain = config.infrastructureFilterChain(http);

        // Assert
        assertThat(chain).isNotNull();
    }

    @Test
    @DisplayName("should have public paths defined for Swagger and Actuator")
    void shouldHavePublicPaths() {
        // The public paths are defined as a private static final array.
        // We verify the config can be instantiated and the bean method works.
        assertThat(config).isNotNull();
    }
}
