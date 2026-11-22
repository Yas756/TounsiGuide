package com.tounsiguide.communityservice.common.error;

/** A query parameter out of its allowed range: answered as 400 VALIDATION_ERROR with {@code details.<parameter>}. */
public class InvalidParameterException extends RuntimeException {

    private final String parameter;

    public InvalidParameterException(String parameter, String message) {
        super(message);
        this.parameter = parameter;
    }

    public String getParameter() {
        return parameter;
    }
}
