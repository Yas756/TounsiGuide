package com.tounsiguide.identity.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.getStatus(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> details.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Données invalides", req, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Corps de requête invalide", req, null);
    }

    /** Filet de sécurité en cas de course sur l'unicité de l'email. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Conflit de données (ressource déjà existante)", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
                                           Map<String, String> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
