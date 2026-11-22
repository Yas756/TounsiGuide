package com.tounsiguide.businessservice.page;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditService;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.client.LocationClient.LocationSnapshot;
import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.Paging;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.page.dto.BusinessPageResponse;
import com.tounsiguide.businessservice.page.dto.UpdatePageRequest;
import com.tounsiguide.businessservice.rating.RatingService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Reading business pages (public) and editing their official information (page members only, S3-US04).
 * The Location details are read from the Location Service; if it is down the page still answers without them.
 */
@Service
public class BusinessPageService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final BusinessPageRepository pages;
    private final PageAccess access;
    private final LocationClient locationClient;
    private final AuditService audit;
    /** Star ratings (S3-US12); null in unit tests that do not need them. */
    private final RatingService ratings;

    public BusinessPageService(BusinessPageRepository pages, PageAccess access, LocationClient locationClient,
                               AuditService audit) {
        this(pages, access, locationClient, audit, null);
    }

    @Autowired
    public BusinessPageService(BusinessPageRepository pages, PageAccess access, LocationClient locationClient,
                               AuditService audit, RatingService ratings) {
        this.pages = pages;
        this.access = access;
        this.locationClient = locationClient;
        this.audit = audit;
        this.ratings = ratings;
    }

    /** Active pages by name; {@code q} filters on the name (case-insensitive). */
    @Transactional(readOnly = true)
    public PageResponse<BusinessPageResponse> list(String q, int page, int size) {
        String query = q == null ? "" : q.trim();
        Page<BusinessPage> result = query.isEmpty()
                ? pages.findAllByStatus(PageStatus.ACTIVE, Paging.of(page, size, BY_NAME))
                : pages.findAllByStatusAndNameContainingIgnoreCase(PageStatus.ACTIVE, query, Paging.of(page, size, BY_NAME));
        Map<Long, LocationSnapshot> locations;
        try {
            locations = locationClient.findLocations(result.map(BusinessPage::getLocationId).getContent());
        } catch (DependencyUnavailableException exception) {
            locations = Map.of();
        }
        Map<Long, LocationSnapshot> byId = locations;
        Map<Long, double[]> stars = ratings == null ? Map.of() : ratings.averages(result.map(BusinessPage::getId).getContent());
        return PageResponse.of(result, p -> BusinessPageResponse.from(p, byId.get(p.getLocationId()), null)
                .withRating(stars.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public BusinessPageResponse get(Long id, CurrentUser user) {
        return toResponse(access.activePage(id), user);
    }

    /** The active page of a Location, so that a post or a Location can link to it; 404 when there is none. */
    @Transactional(readOnly = true)
    public BusinessPageResponse byLocation(Long locationId, CurrentUser user) {
        BusinessPage page = pages.findByLocationIdAndStatus(locationId, PageStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Aucune page professionnelle pour ce lieu"));
        return toResponse(page, user);
    }

    /**
     * Replaces the official information. Members of this page only (403 otherwise, administrators included:
     * the official information belongs to the business). The Location link cannot be changed here.
     */
    @Transactional
    public BusinessPageResponse update(Long id, UpdatePageRequest body, CurrentUser user) {
        BusinessPage page = access.activePage(id);
        access.requireMember(id, user, "Seuls les membres de cette page peuvent la modifier");
        page.updateOfficialInfo(body.name().trim(), clean(body.description()), clean(body.openingHours()),
                clean(body.phone()), clean(body.email()), clean(body.website()), clean(body.address()));
        BusinessPage saved = pages.saveAndFlush(page);
        audit.record(AuditAction.PAGE_UPDATED, id, null, null, user, null);
        return toResponse(saved, user);
    }

    private BusinessPageResponse toResponse(BusinessPage page, CurrentUser user) {
        LocationSnapshot location;
        try {
            location = locationClient.findLocation(page.getLocationId()).orElse(null);
        } catch (DependencyUnavailableException exception) {
            location = null;
        }
        BusinessPageResponse response = BusinessPageResponse.from(page, location, access.roleOf(page.getId(), user));
        return ratings == null ? response : response.withRating(ratings.averages(List.of(page.getId())).get(page.getId()));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
