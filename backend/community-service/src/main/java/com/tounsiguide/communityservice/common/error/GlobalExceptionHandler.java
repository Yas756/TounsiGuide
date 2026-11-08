package com.tounsiguide.communityservice.common.error;

import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.common.security.UnauthorizedException;
import com.tounsiguide.communityservice.location.LocationServiceUnavailableException;
import com.tounsiguide.communityservice.location.UnknownLocationException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(
            EntityNotFoundException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                "RESOURCE_NOT_FOUND",
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> details = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> details.put(error.getField(), error.getDefaultMessage()));

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                request.getRequestURI(),
                details
        );
        return ResponseEntity.badRequest().body(response);
    }

    /** The locationId of the post does not exist in the Location Service: a validation error on that field. */
    @ExceptionHandler(UnknownLocationException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownLocation(
            UnknownLocationException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                request.getRequestURI(),
                Map.of("locationId", exception.getMessage())
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(LocationServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleLocationServiceUnavailable(
            LocationServiceUnavailableException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "LOCATION_SERVICE_UNAVAILABLE",
                "The place could not be checked: the Location service is unavailable",
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(
            UnauthorizedException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHORIZED",
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            ForbiddenException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}
