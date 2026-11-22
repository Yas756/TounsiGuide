package com.tounsiguide.businessservice.common.error;

/** A request parameter breaks a rule that bean validation cannot express (answered with 400 VALIDATION_ERROR). */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
