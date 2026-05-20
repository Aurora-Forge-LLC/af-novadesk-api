package com.af.novadesk.api.finance.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiResponse}.
 *
 * <p>Validates the static factory methods {@code ok(data)},
 * {@code ok(data, message)}, and {@code created(data, message)}.</p>
 *
 * @see ApiResponse
 */
@DisplayName("ApiResponse")
class ApiResponseTest {

    // =========================================================================
    // ok(data)
    // =========================================================================

    @Nested
    @DisplayName("ok(data)")
    class OkWithData {

        @Test
        @DisplayName("should create success response with data and default message")
        void shouldCreateOkWithData() {
            // Arrange
            String data = "test-data";

            // Act
            ApiResponse<String> response = ApiResponse.ok(data);

            // Assert
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getMessage()).isEqualTo("Success");
            assertThat(response.getTimestamp()).isNotNull();
            assertThat(response.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("should create success response with null data")
        void shouldCreateOkWithNullData() {
            // Act
            ApiResponse<String> response = ApiResponse.ok(null);

            // Assert
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isNull();
            assertThat(response.getMessage()).isEqualTo("Success");
            assertThat(response.getTimestamp()).isNotNull();
        }
    }

    // =========================================================================
    // ok(data, message)
    // =========================================================================

    @Nested
    @DisplayName("ok(data, message)")
    class OkWithDataAndMessage {

        @Test
        @DisplayName("should create success response with data and custom message")
        void shouldCreateOkWithDataAndMessage() {
            // Arrange
            String data = "test-data";
            String message = "Operation completed";

            // Act
            ApiResponse<String> response = ApiResponse.ok(data, message);

            // Assert
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getMessage()).isEqualTo(message);
            assertThat(response.getTimestamp()).isNotNull();
        }
    }

    // =========================================================================
    // created(data, message)
    // =========================================================================

    @Nested
    @DisplayName("created(data, message)")
    class CreatedWithDataAndMessage {

        @Test
        @DisplayName("should create created response with data and message")
        void shouldCreateCreatedWithDataAndMessage() {
            // Arrange
            String data = "new-entity";
            String message = "Entity created";

            // Act
            ApiResponse<String> response = ApiResponse.created(data, message);

            // Assert
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getData()).isEqualTo(data);
            assertThat(response.getMessage()).isEqualTo(message);
            assertThat(response.getTimestamp()).isNotNull();
        }
    }

    // =========================================================================
    // equals, hashCode, toString (from @Data)
    // =========================================================================

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when fields match")
        void shouldBeEqual() {
            // Arrange
            ApiResponse<String> r1 = ApiResponse.ok("data", "msg");
            ApiResponse<String> r2 = ApiResponse.ok("data", "msg");

            // Assert
            assertThat(r1).isEqualTo(r2);
            assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when data differs")
        void shouldNotBeEqualWhenDataDiffers() {
            // Arrange
            ApiResponse<String> r1 = ApiResponse.ok("data1");
            ApiResponse<String> r2 = ApiResponse.ok("data2");

            // Assert
            assertThat(r1).isNotEqualTo(r2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should include all fields in string representation")
        void shouldIncludeAllFields() {
            // Arrange
            ApiResponse<String> response = ApiResponse.ok("data", "msg");

            // Act
            String str = response.toString();

            // Assert
            assertThat(str).contains("success=true");
            assertThat(str).contains("data");
            assertThat(str).contains("msg");
        }
    }
}
