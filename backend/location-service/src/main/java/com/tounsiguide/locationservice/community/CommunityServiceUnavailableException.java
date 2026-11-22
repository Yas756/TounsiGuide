package com.tounsiguide.locationservice.community;

/** The Community Service cannot be reached or failed (answered with 503 COMMUNITY_SERVICE_UNAVAILABLE). */
public class CommunityServiceUnavailableException extends RuntimeException {

    public CommunityServiceUnavailableException(Throwable cause) {
        super("Community Service unavailable", cause);
    }
}
