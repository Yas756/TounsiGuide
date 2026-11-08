package com.tounsiguide.locationservice.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body of POST /api/v1/locations (administrator adds a Location to the directory). */
public record CreateLocationRequest(
        @NotBlank(message = "Name must not be blank")
        @Size(max = 150, message = "Name must not exceed 150 characters")
        String name,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 500, message = "Address must not exceed 500 characters")
        String address,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double longitude,

        @Size(max = 60, message = "Category must not exceed 60 characters")
        String category,

        @Size(max = 20, message = "At most 20 aliases")
        List<@NotBlank(message = "Alias must not be blank")
             @Size(max = 150, message = "Alias must not exceed 150 characters") String> aliases
) {
}
