package com.tounsiguide.locationservice.common.error;

/** The request is valid but clashes with existing data (answered with 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
