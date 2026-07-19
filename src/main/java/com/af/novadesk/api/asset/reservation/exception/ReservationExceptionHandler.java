package com.af.novadesk.api.asset.reservation.exception;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates asset-reservation domain exceptions into the standard API error shape.
 * Ordered ahead of the module-wide asset handler so reservation-specific
 * exceptions are matched here first.
 */
@Order(3)
@RestControllerAdvice(basePackages = "com.af.novadesk.api.asset.reservation")
public class ReservationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExceptionHandler.class);

    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
            ReservationNotFoundException ex, HttpServletRequest req) {
        log.warn("Reservation not found on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseBuilder.notFound(ex.getMessage());
    }

    @ExceptionHandler(ReservationConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            ReservationConflictException ex, HttpServletRequest req) {
        log.warn("Reservation conflict on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseBuilder.conflict(ex.getMessage());
    }
}
