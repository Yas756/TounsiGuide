package com.tounsiguide.locationservice.common.security;

/** The request carries no identity: it did not come through the Gateway with a valid token. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
