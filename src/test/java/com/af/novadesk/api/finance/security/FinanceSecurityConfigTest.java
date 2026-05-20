package com.af.novadesk.api.finance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for inner components of {@link FinanceSecurityConfig}.
 *
 * <p>Uses reflection to access the {@code private static} inner classes
 * {@code JwtAudienceValidator} and {@code JwtDecoderDecorator}, and the
 * {@code private} method {@code jwtAuthenticationConverter()}.</p>
 *
 * @see FinanceSecurityConfig
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FinanceSecurityConfig")
class FinanceSecurityConfigTest {

    private FinanceSecurityConfig config;

    @Mock
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        config = new FinanceSecurityConfig(jwtDecoder);
    }

    // =========================================================================
    // JwtAudienceValidator (private static inner class)
    // =========================================================================

    @Nested
    @DisplayName("JwtAudienceValidator")
    class JwtAudienceValidatorTest {

        private Object validator;

        @BeforeEach
        void setUp() throws Exception {
            Class<?> clazz = Class.forName(
                    "com.af.novadesk.api.finance.security.FinanceSecurityConfig$JwtAudienceValidator");
            Constructor<?> ctor = clazz.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            validator = ctor.newInstance("novadesk-api");
        }

        @Test
        @DisplayName("should succeed when audience contains expected value")
        void shouldValidateWhenAudienceMatches() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getAudience()).thenReturn(List.of("novadesk-api", "other-service"));

            // Act
            Object result = validator.getClass().getMethod("validate", Jwt.class).invoke(validator, jwt);

            // Assert
            assertThat(result).isNotNull();
            boolean hasErrors = (boolean) result.getClass().getMethod("hasErrors").invoke(result);
            assertThat(hasErrors).isFalse();
        }

        @Test
        @DisplayName("should fail when audience does not contain expected value")
        void shouldFailWhenAudienceMismatch() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getAudience()).thenReturn(List.of("other-service"));

            // Act
            Object result = validator.getClass().getMethod("validate", Jwt.class).invoke(validator, jwt);

            // Assert
            boolean hasErrors = (boolean) result.getClass().getMethod("hasErrors").invoke(result);
            assertThat(hasErrors).isTrue();
        }

        @Test
        @DisplayName("should fail when audience is null")
        void shouldFailWhenAudienceNull() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getAudience()).thenReturn(null);

            // Act
            Object result = validator.getClass().getMethod("validate", Jwt.class).invoke(validator, jwt);

            // Assert
            boolean hasErrors = (boolean) result.getClass().getMethod("hasErrors").invoke(result);
            assertThat(hasErrors).isTrue();
        }
    }

    // =========================================================================
    // JwtDecoderDecorator (private static inner class)
    // =========================================================================

    @Nested
    @DisplayName("JwtDecoderDecorator")
    class JwtDecoderDecoratorTest {

        private Object decorator;
        private Object validator;

        @BeforeEach
        @SuppressWarnings("unchecked")
        void setUp() throws Exception {
            Class<?> validatorClazz = Class.forName(
                    "com.af.novadesk.api.finance.security.FinanceSecurityConfig$JwtAudienceValidator");
            Constructor<?> validatorCtor = validatorClazz.getDeclaredConstructor(String.class);
            validatorCtor.setAccessible(true);
            validator = validatorCtor.newInstance("novadesk-api");

            // Wrap in DelegatingOAuth2TokenValidator using Collection<OAuth2TokenValidator<Jwt>>
            Class<?> delegatingClazz = Class.forName(
                    "org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator");
            Constructor<?> delegatingCtor = delegatingClazz.getDeclaredConstructor(Collection.class);
            delegatingCtor.setAccessible(true);
            Object delegatingValidator = delegatingCtor.newInstance(List.of(validator));

            Class<?> decoratorClazz = Class.forName(
                    "com.af.novadesk.api.finance.security.FinanceSecurityConfig$JwtDecoderDecorator");
            Constructor<?> decoratorCtor = decoratorClazz.getDeclaredConstructor(
                    JwtDecoder.class,
                    OAuth2TokenValidator.class);
            decoratorCtor.setAccessible(true);
            decorator = decoratorCtor.newInstance(jwtDecoder, delegatingValidator);
        }

        @Test
        @DisplayName("should decode and validate successfully")
        void shouldDecodeAndValidate() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("valid-token")).thenReturn(jwt);
            when(jwt.getAudience()).thenReturn(List.of("novadesk-api"));

            // Act
            Jwt result = (Jwt) decorator.getClass().getMethod("decode", String.class)
                    .invoke(decorator, "valid-token");

            // Assert
            assertThat(result).isEqualTo(jwt);
        }

        @Test
        @DisplayName("should throw JwtValidationException when audience validation fails")
        void shouldThrowOnValidationFailure() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwtDecoder.decode("invalid-token")).thenReturn(jwt);
            when(jwt.getAudience()).thenReturn(List.of("other-service"));

            // Act & Assert
            assertThatThrownBy(() -> {
                try {
                    decorator.getClass().getMethod("decode", String.class)
                            .invoke(decorator, "invalid-token");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            })
                    .isInstanceOf(JwtValidationException.class)
                    .hasMessageContaining("JWT validation failed");
        }

        @Test
        @DisplayName("should propagate JwtException from delegate decoder")
        void shouldPropagateDecoderException() throws Exception {
            // Arrange
            when(jwtDecoder.decode("malformed")).thenThrow(new JwtException("Malformed token"));

            // Act & Assert
            assertThatThrownBy(() -> {
                try {
                    decorator.getClass().getMethod("decode", String.class)
                            .invoke(decorator, "malformed");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            })
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("Malformed token");
        }
    }

    // =========================================================================
    // jwtAuthenticationConverter (private method)
    // =========================================================================

    @Nested
    @DisplayName("jwtAuthenticationConverter")
    class JwtAuthenticationConverterTest {

        private Object converter;

        @BeforeEach
        void setUp() throws Exception {
            Method method = FinanceSecurityConfig.class.getDeclaredMethod("jwtAuthenticationConverter");
            method.setAccessible(true);
            converter = method.invoke(config);
        }

        @Test
        @DisplayName("should map permissions claim to granted authorities")
        void shouldMapPermissionsToAuthorities() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getClaim("permissions")).thenReturn(List.of("organizations:write", "organizations:read"));

            // Act
            Object authentication = converter.getClass().getMethod("convert", Jwt.class).invoke(converter, jwt);

            // Assert - JwtAuthenticationConverter returns JwtAuthenticationToken
            // which has getAuthorities() method
            assertThat(authentication).isNotNull();
            Method getAuthorities = authentication.getClass().getMethod("getAuthorities");
            Collection<?> authorities = (Collection<?>) getAuthorities.invoke(authentication);
            assertThat(authorities).hasSize(2);
        }

        @Test
        @DisplayName("should return empty authorities when permissions claim is null")
        void shouldReturnEmptyWhenPermissionsNull() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getClaim("permissions")).thenReturn(null);

            // Act
            Object authentication = converter.getClass().getMethod("convert", Jwt.class).invoke(converter, jwt);

            // Assert
            assertThat(authentication).isNotNull();
            Method getAuthorities = authentication.getClass().getMethod("getAuthorities");
            Collection<?> authorities = (Collection<?>) getAuthorities.invoke(authentication);
            assertThat(authorities).isEmpty();
        }

        @Test
        @DisplayName("should return empty authorities when permissions claim is empty")
        void shouldReturnEmptyWhenPermissionsEmpty() throws Exception {
            // Arrange
            Jwt jwt = mock(Jwt.class);
            when(jwt.getClaim("permissions")).thenReturn(List.of());

            // Act
            Object authentication = converter.getClass().getMethod("convert", Jwt.class).invoke(converter, jwt);

            // Assert
            assertThat(authentication).isNotNull();
            Method getAuthorities = authentication.getClass().getMethod("getAuthorities");
            Collection<?> authorities = (Collection<?>) getAuthorities.invoke(authentication);
            assertThat(authorities).isEmpty();
        }
    }
}
