package com.af.novadesk.api.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response envelope for all REST endpoints.
 *
 * <p>This wrapper ensures consistent response structure across all endpoints, making it
 * easier for frontend clients and API consumers to parse responses.
 * All successful responses must return data wrapped in this envelope.</p>
 *
 * <p><b>Response Structure:</b></p>
 * <ul>
 *   <li><code>success</code> - Boolean flag indicating operational success</li>
 *   <li><code>code</code> - Standard HTTP status code (must match HTTP response status)</li>
 *   <li><code>message</code> - Human-readable message for UI display</li>
 *   <li><code>data</code> - The actual payload (may be null for void operations)</li>
 *   <li><code>metadata</code> - Optional metadata (pagination, timestamps, etc.)</li>
 *   <li><code>timestamp</code> - Response generation time (ISO 8601)</li>
 * </ul>
 *
 * <p><b>Example Success Response (201 Created):</b></p>
 * <pre>{
 *   "success": true,
 *   "code": 201,
 *   "message": "Capital injection recorded successfully",
 *   "data": {
 *     "capitalInjectionId": "550e8400-e29b-41d4-a716-446655440000",
 *     "journalId": "550e8400-e29b-41d4-a716-446655440001",
 *     "amountLocal": 100000.00,
 *     "currencyLocal": "INR"
 *   },
 *   "metadata": {
 *     "executionTimeMs": 143
 *   },
 *   "timestamp": "2026-05-18T14:30:45.123Z"
 * }</pre>
 *
 * <p><b>Example Validation Error (400 Bad Request):</b></p>
 * <pre>{
 *   "success": false,
 *   "code": 400,
 *   "message": "Validation failed",
 *   "data": null,
 *   "metadata": {
 *     "errors": [
 *       {"field": "sourceAmount", "message": "must be greater than 0"},
 *       {"field": "targetEntityCode", "message": "must not be blank"}
 *     ]
 *   },
 *   "timestamp": "2026-05-18T14:30:45.123Z"
 * }</pre>
 *
 * @param <T> The type of data being returned
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Standard API response envelope for all REST endpoints")
public class ApiResponse<T> {

    @Schema(
            description = "Indicates whether the operation was successful",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private boolean success;

    @Schema(
            description = "HTTP status code matching the response status",
            example = "200",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private int code;

    @Schema(
            description = "Human-readable message for the operation result. " +
                    "For errors, this describes the issue. For success, it confirms the action.",
            example = "Capital injection recorded successfully",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            description = "The actual response payload. Null for void operations or errors.",
            example = "null"
    )
    private T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(
            description = "Optional metadata object containing pagination info, error details, or other context"
    )
    private ApiResponseMetadata metadata;

    @Schema(
            description = "ISO 8601 timestamp of when the response was generated (server time)",
            example = "2026-05-18T14:30:45.123Z",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private Instant timestamp;

    /**
     * Creates a successful API response with the given data.
     *
     * @param code HTTP status code
     * @param message Success message
     * @param data The response payload
     * @param <T> Type of data
     * @return ApiResponse with success flag set to true
     */
    public static <T> ApiResponse<T> success(int code, String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a successful API response with data and metadata.
     *
     * @param code HTTP status code
     * @param message Success message
     * @param data The response payload
     * @param metadata Response metadata (pagination, etc.)
     * @param <T> Type of data
     * @return ApiResponse with both data and metadata
     */
    public static <T> ApiResponse<T> successWithMetadata(
            int code, String message, T data, ApiResponseMetadata metadata) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a successful API response without data (void operation).
     *
     * @param code HTTP status code
     * @param message Success message
     * @param <T> Type of data
     * @return ApiResponse with null data
     */
    public static <T> ApiResponse<T> successEmpty(int code, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(null)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a failure API response.
     *
     * @param code HTTP status code
     * @param message Error message
     * @param metadata Error metadata (validation errors, etc.)
     * @param <T> Type of data
     * @return ApiResponse with success flag set to false
     */
    public static <T> ApiResponse<T> failure(int code, String message, ApiResponseMetadata metadata) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(null)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a failure API response without metadata.
     *
     * @param code HTTP status code
     * @param message Error message
     * @param <T> Type of data
     * @return ApiResponse with success flag set to false
     */
    public static <T> ApiResponse<T> failureSimple(int code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(null)
                .timestamp(Instant.now())
                .build();
    }
}

