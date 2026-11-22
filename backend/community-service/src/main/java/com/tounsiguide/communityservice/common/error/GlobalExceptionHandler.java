package com.tounsiguide.communityservice.common.error;

import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.common.security.UnauthorizedException;
import com.tounsiguide.communityservice.location.LocationServiceUnavailableException;
import com.tounsiguide.communityservice.location.UnknownLocationException;
import com.tounsiguide.communityservice.media.MediaValidationException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    /** A rejected photo/video or list of media ids: a validation error on that field. */
    @ExceptionHandler(MediaValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaValidation(
            MediaValidationException exception,
            HttpServletRequest request
    ) {
        return validationError(request, Map.of(exception.getField(), exception.getMessage()));
    }

    /** A query parameter out of range, e.g. GET /api/v1/posts?size=500. */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidParameter(
            InvalidParameterException exception,
            HttpServletRequest request
    ) {
        return validationError(request, Map.of(exception.getParameter(), exception.getMessage()));
    }

    /** A path id or query parameter of the wrong type (/posts/abc, ?page=x) or a body that is not valid JSON. */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? "Invalid value for parameter: " + mismatch.getName()
                : "Malformed request body";
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                message,
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.badRequest().body(response);
    }

    /** POST /api/v1/media without a "file" part. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return validationError(request, Map.of(exception.getRequestPartName(), "Aucun fichier reçu."));
    }

    /** The multipart body is bigger than spring.servlet.multipart.max-file-size / max-request-size. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONTENT_TOO_LARGE.value(),
                "PAYLOAD_TOO_LARGE",
                "Le fichier est trop volumineux (photo : 10 Mo, vidéo : 50 Mo maximum).",
                request.getRequestURI(),
                Map.of("file", "Le fichier est trop volumineux.")
        );
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(response);
    }

    /** A body announced as multipart that cannot be read. */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handleMultipart(
            MultipartException exception,
            HttpServletRequest request
    ) {
        return validationError(request, Map.of("file", "Envoi du fichier invalide."));
    }

    private static ResponseEntity<ApiErrorResponse> validationError(
            HttpServletRequest request,
            Map<String, String> details
    ) {
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

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            ConflictException exception,
            HttpServletRequest request
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "CONFLICT",
                exception.getMessage(),
                request.getRequestURI(),
                Map.of()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
