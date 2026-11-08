package com.tounsiguide.communityservice.common.security;

/** The caller is known but is not allowed to do this (for example editing someone else's post). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
