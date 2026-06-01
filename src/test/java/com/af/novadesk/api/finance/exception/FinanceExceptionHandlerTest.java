package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.finance.exception.FinanceExceptionHandler.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FinanceExceptionHandler}.
 *
 * <p>Validates that each domain exception is mapped to the correct HTTP status
 * code and error response structure.</p>
 *
 * @see FinanceExceptionHandler
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FinanceExceptionHandler")
class FinanceExceptionHandlerTest {

    private FinanceExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    private UUID entityId;
    private UUID authUserId;
    private UUID accessId;

    @BeforeEach
    void setUp() {
        handler = new FinanceExceptionHandler();
        entityId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        authUserId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        accessId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        when(request.getRequestURI()).thenReturn("/api/v1/legal-entities/" + entityId);
    }

    // =========================================================================
    // Finance domain exceptions
    // =========================================================================

    @Nested
    @DisplayName("EntityNotFoundException")
    class HandleEntityNotFound {

        @Test
        @DisplayName("should return 404 NOT_FOUND")
        void shouldReturn404() {
            // Arrange
            var ex = new EntityNotFoundException(entityId);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_ENTITY_001");
            assertThat(response.getBody().getMessage()).contains(entityId.toString());
            assertThat(response.getBody().getPath()).isEqualTo(request.getRequestURI());
        }
    }

    @Nested
    @DisplayName("DuplicateEntityException")
    class HandleDuplicateEntity {

        @Test
        @DisplayName("should return 409 CONFLICT")
        void shouldReturn409() {
            // Arrange
            var ex = new DuplicateEntityException("name", "Test Entity");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleDuplicateEntity(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_ENTITY_002");
            assertThat(response.getBody().getMessage()).contains("Test Entity");
        }
    }

    @Nested
    @DisplayName("InvalidEntityStateException")
    class HandleInvalidEntityState {

        @Test
        @DisplayName("should return 422 UNPROCESSABLE_ENTITY")
        void shouldReturn422() {
            // Arrange
            var ex = new InvalidEntityStateException(entityId, "APPROVED", "approve");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleInvalidEntityState(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_ENTITY_003");
            assertThat(response.getBody().getMessage()).contains("approve");
        }
    }

    @Nested
    @DisplayName("EntityAccessDeniedException")
    class HandleAccessDenied {

        @Test
        @DisplayName("should return 403 FORBIDDEN")
        void shouldReturn403() {
            // Arrange
            var ex = new EntityAccessDeniedException(authUserId, entityId);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_ACCESS_003");
            assertThat(response.getBody().getMessage()).contains(authUserId.toString());
        }
    }

    @Nested
    @DisplayName("UserAccessNotFoundException")
    class HandleUserAccessNotFound {

        @Test
        @DisplayName("should return 404 NOT_FOUND")
        void shouldReturn404() {
            // Arrange
            var ex = new UserAccessNotFoundException(accessId);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleUserAccessNotFound(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_ACCESS_001");
            assertThat(response.getBody().getMessage()).contains(accessId.toString());
        }
    }

    @Nested
    @DisplayName("DuplicateUserAccessException")
    class HandleDuplicateUserAccess {

        @Test
        @DisplayName("should return 409 CONFLICT")
        void shouldReturn409() {
            // Arrange
            var ex = new DuplicateUserAccessException(authUserId, entityId);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleDuplicateUserAccess(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_ACCESS_002");
            assertThat(response.getBody().getMessage()).contains(authUserId.toString());
        }
    }

    @Nested
    @DisplayName("FiscalYearSettingNotFoundException")
    class HandleFiscalYearNotFound {

        @Test
        @DisplayName("should return 404 NOT_FOUND")
        void shouldReturn404() {
            // Arrange
            var ex = new FiscalYearSettingNotFoundException(entityId);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleFiscalYearNotFound(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_FISCAL_001");
            assertThat(response.getBody().getMessage()).contains(entityId.toString());
        }
    }

    @Nested
    @DisplayName("ShadowUserNotFoundException")
    class HandleShadowUserNotFound {

        @Test
        @DisplayName("should return 404 NOT_FOUND")
        void shouldReturn404() {
            // Arrange
            var ex = new ShadowUserNotFoundException(authUserId);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleShadowUserNotFound(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_SHADOW_001");
            assertThat(response.getBody().getMessage()).contains(authUserId.toString());
        }
    }

    @Nested
    @DisplayName("OutboxPublishException")
    class HandleOutboxFailure {

        @Test
        @DisplayName("should return 500 INTERNAL_SERVER_ERROR")
        void shouldReturn500() {
            // Arrange
            var cause = new RuntimeException("DB connection lost");
            var ex = new OutboxPublishException("ENTITY_CREATED", entityId, cause);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleOutboxFailure(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_OUTBOX_001");
            assertThat(response.getBody().getMessage()).isEqualTo("An internal error occurred. Please try again.");
        }
    }

    @Nested
    @DisplayName("AuthenticationRequiredException")
    class HandleAuthenticationRequired {

        @Test
        @DisplayName("should return 401 UNAUTHORIZED")
        void shouldReturn401() {
            // Arrange
            var ex = new AuthenticationRequiredException();

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleAuthenticationRequired(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_AUTH_001");
            assertThat(response.getBody().getMessage()).contains("Authentication required");
        }
    }

    // =========================================================================
    // Standard library exceptions
    // =========================================================================

    @Nested
    @DisplayName("UnsupportedOperationException")
    class HandleNotImplemented {

        @Test
        @DisplayName("should return 501 NOT_IMPLEMENTED")
        void shouldReturn501() {
            // Arrange
            var ex = new UnsupportedOperationException("Not implemented yet");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleNotImplemented(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_NOT_IMPLEMENTED");
            assertThat(response.getBody().getMessage()).isEqualTo("Not implemented yet");
        }

        @Test
        @DisplayName("should return default message when exception message is null")
        void shouldReturnDefaultMessageWhenNull() {
            // Arrange
            var ex = new UnsupportedOperationException();

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleNotImplemented(ex, request);

            // Assert
            assertThat(response.getBody().getMessage()).isEqualTo("This endpoint is not yet implemented");
        }
    }

    @Nested
    @DisplayName("IllegalStateException")
    class HandleIllegalState {

        @Test
        @DisplayName("should return 500 INTERNAL_SERVER_ERROR")
        void shouldReturn500() {
            // Arrange
            var ex = new IllegalStateException("Unexpected internal state");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleIllegalState(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("FIN_INTERNAL_ERROR");
            assertThat(response.getBody().getMessage()).contains("contact support");
        }
    }

    // =========================================================================
    // Spring MVC validation
    // =========================================================================

    @Nested
    @DisplayName("MethodArgumentNotValidException")
    class HandleValidation {

        @Test
        @DisplayName("should return 400 BAD_REQUEST with field errors")
        void shouldReturn400WithFieldErrors() {
            // Arrange
            BindingResult bindingResult = org.mockito.Mockito.mock(BindingResult.class);
            var fieldError1 = new FieldError("object", "entityName", "Entity name is required");
            var fieldError2 = new FieldError("object", "entityCode", "Entity code is required");

            when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
            
            // Create a MethodArgumentNotValidException with a mocked MethodParameter
            MethodArgumentNotValidException ex = org.mockito.Mockito.mock(MethodArgumentNotValidException.class);
            when(ex.getBindingResult()).thenReturn(bindingResult);

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().getDetails()).isInstanceOf(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, String> fieldErrors = (Map<String, String>) response.getBody().getDetails();
            assertThat(fieldErrors).containsEntry("entityName", "Entity name is required");
            assertThat(fieldErrors).containsEntry("entityCode", "Entity code is required");
        }
    }

    // =========================================================================
    // Catch-all
    // =========================================================================

    @Nested
    @DisplayName("unexpected Exception")
    class HandleUnexpected {

        @Test
        @DisplayName("should return 500 INTERNAL_SERVER_ERROR for any unexpected exception")
        void shouldReturn500() throws Exception {
            // Arrange
            var ex = new RuntimeException("Something went terribly wrong");

            // Act
            ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().isSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred. Please try again.");
        }
    }
}
