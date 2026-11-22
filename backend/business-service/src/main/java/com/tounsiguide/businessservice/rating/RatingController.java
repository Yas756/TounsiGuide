package com.tounsiguide.businessservice.rating;

import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.rating.RatingDtos.RateRequest;
import com.tounsiguide.businessservice.rating.RatingDtos.RatingSummary;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Star ratings of a business page (S3-US12). Reading is public; rating needs a login. PUT sets or replaces the
 * caller's stars, DELETE removes them; both answer the new summary.
 */
@RestController
@RequestMapping("/api/v1/business-pages/{pageId}/rating")
public class RatingController {

    private final RatingService service;

    public RatingController(RatingService service) {
        this.service = service;
    }

    @GetMapping
    public RatingSummary get(
            @PathVariable Long pageId,
            @RequestHeader(name = CurrentUser.ID_HEADER, required = false) String userId,
            @RequestHeader(name = CurrentUser.NAME_HEADER, required = false) String userName,
            @RequestHeader(name = CurrentUser.ROLES_HEADER, required = false) String roles
    ) {
        return service.summary(pageId, CurrentUser.fromHeadersOrNull(userId, userName, roles));
    }

    @PutMapping
    public RatingSummary rate(@PathVariable Long pageId, @Valid @RequestBody RateRequest body, CurrentUser user) {
        return service.rate(pageId, body.stars(), user);
    }

    @DeleteMapping
    public RatingSummary remove(@PathVariable Long pageId, CurrentUser user) {
        return service.remove(pageId, user);
    }
}
