package com.tounsiguide.communityservice.common.error;

/** The request is valid but clashes with the current state (answered with 409 CONFLICT). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
