package com.tounsiguide.businessservice.page.dto;

import com.tounsiguide.businessservice.client.LocationClient.LocationSnapshot;
import com.tounsiguide.businessservice.page.BusinessPage;
import com.tounsiguide.businessservice.page.PageRole;
import com.tounsiguide.businessservice.page.PageStatus;

import java.time.Instant;

/**
 * A business page. {@code location} comes from the Location Service (null when it cannot be reached);
 * {@code member}/{@code memberRole} describe the caller (false/null for a visitor).
 */
public record BusinessPageResponse(
        Long id,
        Long locationId,
        LocationInfo location,
        String name,
        String description,
        String openingHours,
        String phone,
        String email,
        String website,
        String address,
        PageStatus status,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt,
        boolean member,
        PageRole memberRole,
        /** Star ratings (S3-US12): average rounded to 0.1 (null without ratings) and number of ratings. */
        Double ratingAverage,
        long ratingCount
) {

    public BusinessPageResponse(Long id, Long locationId, LocationInfo location, String name, String description,
                                String openingHours, String phone, String email, String website, String address,
                                PageStatus status, Instant verifiedAt, Instant createdAt, Instant updatedAt,
                                boolean member, PageRole memberRole) {
        this(id, locationId, location, name, description, openingHours, phone, email, website, address, status,
                verifiedAt, createdAt, updatedAt, member, memberRole, null, 0);
    }

    public static BusinessPageResponse from(BusinessPage page, LocationSnapshot location, PageRole memberRole) {
        return new BusinessPageResponse(page.getId(), page.getLocationId(), LocationInfo.from(location), page.getName(),
                page.getDescription(), page.getOpeningHours(), page.getPhone(), page.getEmail(), page.getWebsite(),
                page.getAddress(), page.getStatus(), page.getVerifiedAt(), page.getCreatedAt(), page.getUpdatedAt(),
                memberRole != null, memberRole);
    }

    /** Same, with the star ratings of the page. */
    public BusinessPageResponse withRating(double[] averageAndCount) {
        if (averageAndCount == null) return this;
        return new BusinessPageResponse(id, locationId, location, name, description, openingHours, phone, email,
                website, address, status, verifiedAt, createdAt, updatedAt, member, memberRole,
                averageAndCount[0], (long) averageAndCount[1]);
    }

    public record LocationInfo(Long id, String name, String city, String address, String category) {

        static LocationInfo from(LocationSnapshot location) {
            return location == null ? null
                    : new LocationInfo(location.id(), location.name(), location.city(), location.address(),
                    location.category());
        }
    }
}
