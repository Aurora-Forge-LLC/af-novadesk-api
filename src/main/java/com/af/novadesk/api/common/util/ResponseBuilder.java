package com.af.novadesk.api.common.util;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.ApiResponseMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
/**
 * Builder utility for creating standardized API responses.
 */
public final class ResponseBuilder {
    private ResponseBuilder() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    public static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(HttpStatus.OK.value(), message, data);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(HttpStatus.CREATED.value(), message, data);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    public static <T> ResponseEntity<ApiResponse<T>> noContent(String message) {
        ApiResponse<T> response = ApiResponse.successEmpty(HttpStatus.NO_CONTENT.value(), message);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(response);
    }
    public static <T> ResponseEntity<ApiResponse<T>> okWithMetadata(T data, String message, ApiResponseMetadata metadata) {
        ApiResponse<T> response = ApiResponse.successWithMetadata(HttpStatus.OK.value(), message, data, metadata);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
    public static <T> ResponseEntity<ApiResponse<T>> accepted(T data, String message) {
        ApiResponse<T> response = ApiResponse.success(HttpStatus.ACCEPTED.value(), message, data);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.BAD_REQUEST.value(), message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> badRequest(String message, ApiResponseMetadata metadata) {
        ApiResponse<Void> response = ApiResponse.failure(HttpStatus.BAD_REQUEST.value(), message, metadata);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> notFound(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.NOT_FOUND.value(), message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> conflict(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.CONFLICT.value(), message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> unprocessableEntity(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.UNPROCESSABLE_ENTITY.value(), message);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> forbidden(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.FORBIDDEN.value(), message);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> internalServerError(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.INTERNAL_SERVER_ERROR.value(), message);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    public static ResponseEntity<ApiResponse<Void>> serviceUnavailable(String message) {
        ApiResponse<Void> response = ApiResponse.failureSimple(HttpStatus.SERVICE_UNAVAILABLE.value(), message);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
}