package com.tounsiguide.communityservice.media;

/** A rejected file or list of media ids. Answered as 400 VALIDATION_ERROR with {@code details[field] = message}. */
public class MediaValidationException extends RuntimeException {

    private final String field;

    public MediaValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
