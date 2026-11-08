package com.tounsiguide.locationservice.common.error;

import com.tounsiguide.locationservice.common.security.ForbiddenException;
import com.tounsiguide.locationservice.common.security.UnauthorizedException;
import com.tounsiguide.locationservice.geocoding.GeocodingUnavailableException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Same error format as the Community Service: {timestamp, status, code, message, path, details}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                             HttpServletRequest request) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request, details);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MissingServletRequestParameterException missing
                ? "Missing parameter: " + missing.getParameterName()
                : exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for parameter: " + mismatch.getName()
                : "Malformed request body";
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message, request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException exception,
                                                               HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(GeocodingUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleGeocoding(GeocodingUnavailableException exception,
                                                            HttpServletRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "GEOCODING_UNAVAILABLE",
                "Map search is temporarily unavailable", request, Map.of());
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message,
                                                          HttpServletRequest request, Map<String, String> details) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(Instant.now(), status.value(), code, message, request.getRequestURI(), details));
    }
}
