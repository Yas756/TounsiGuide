package com.tounsiguide.businessservice.common.error;

/**
 * Another TounsiGuide service cannot be reached or failed. Answered with 503 and the code
 * {@code LOCATION_SERVICE_UNAVAILABLE}, {@code COMMUNITY_SERVICE_UNAVAILABLE} or {@code IDENTITY_SERVICE_UNAVAILABLE}.
 */
public class DependencyUnavailableException extends RuntimeException {

    private final String code;

    public DependencyUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
