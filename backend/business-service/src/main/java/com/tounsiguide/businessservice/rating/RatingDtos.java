package com.tounsiguide.businessservice.rating;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Request and response bodies of the star rating (S3-US12). */
public final class RatingDtos {

    private RatingDtos() {
    }

    /** Body of PUT /api/v1/business-pages/{id}/rating. */
    public record RateRequest(
            @NotNull(message = "Choisissez une note")
            @Min(value = 1, message = "La note va de 1 à 5 étoiles")
            @Max(value = 5, message = "La note va de 1 à 5 étoiles")
            Integer stars
    ) {
    }

    /**
     * Ratings of one page. {@code average} is rounded to one decimal (null without any rating);
     * {@code distribution[0]} is the number of 1-star ratings … {@code distribution[4]} of 5-star ones;
     * {@code myStars} is the caller's own rating (null for a visitor or when not rated);
     * {@code canRate} is false for a visitor and for the members of the page (no self-rating).
     */
    public record RatingSummary(Long pageId, Double average, long count, List<Long> distribution, Integer myStars,
                                boolean canRate) {
    }
}
