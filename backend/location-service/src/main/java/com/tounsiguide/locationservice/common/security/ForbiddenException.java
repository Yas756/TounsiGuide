package com.tounsiguide.locationservice.common.security;

/** The caller is known but is not allowed to do this (for example a member editing the directory). */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
