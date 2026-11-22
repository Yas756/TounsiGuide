package com.tounsiguide.businessservice.request;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditService;
import com.tounsiguide.businessservice.client.IdentityClient;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.client.LocationClient.LocationSnapshot;
import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.Paging;
import com.tounsiguide.businessservice.common.error.ConflictException;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.CurrentUser;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import com.tounsiguide.businessservice.page.BusinessPage;
import com.tounsiguide.businessservice.page.BusinessPageRepository;
import com.tounsiguide.businessservice.page.PageMember;
import com.tounsiguide.businessservice.page.PageMemberRepository;
import com.tounsiguide.businessservice.page.PageRole;
import com.tounsiguide.businessservice.page.PageStatus;
import com.tounsiguide.businessservice.request.dto.CreatePageRequest;
import com.tounsiguide.businessservice.request.dto.PageRequestResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Page requests (S3-US01, S3-US02, S3-US03): a member asks for the page of an existing Location with evidence,
 * an administrator approves (the page is created, linked to that Location, the requester becomes its OWNER and
 * gets the role BUSINESS_REP) or rejects with a reason. Every step is written to the audit table.
 */
@Service
public class PageRequestService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final VerificationRequestRepository requests;
    private final BusinessPageRepository pages;
    private final PageMemberRepository members;
    private final LocationClient locationClient;
    private final IdentityClient identityClient;
    private final AuditService audit;

    public PageRequestService(VerificationRequestRepository requests, BusinessPageRepository pages,
                              PageMemberRepository members, LocationClient locationClient,
                              IdentityClient identityClient, AuditService audit) {
        this.requests = requests;
        this.pages = pages;
        this.members = members;
        this.locationClient = locationClient;
        this.identityClient = identityClient;
        this.audit = audit;
    }

    /**
     * @throws EntityNotFoundException 404 when the Location does not exist
     * @throws ConflictException       409 when the Location already has a page, or this user already has a
     *                                 pending request for it
     */
    @Transactional
    public PageRequestResponse submit(CreatePageRequest body, CurrentUser user) {
        LocationSnapshot location = locationClient.findLocation(body.locationId())
                .orElseThrow(() -> new EntityNotFoundException("Lieu introuvable"));
        if (pages.existsByLocationIdAndStatus(body.locationId(), PageStatus.ACTIVE)) {
            throw new ConflictException("Ce lieu a déjà une page professionnelle vérifiée");
        }
        if (requests.existsByLocationIdAndRequesterIdAndStatus(body.locationId(), user.id(), RequestStatus.PENDING)) {
            throw new ConflictException("Vous avez déjà une demande en attente pour ce lieu");
        }
        VerificationRequest saved = requests.save(new VerificationRequest(body.locationId(), user.id(),
                user.displayName(), trimToNull(body.proposedName()), body.evidence().trim(),
                trimToNull(body.contact())));
        audit.record(AuditAction.REQUEST_SUBMITTED, null, saved.getId(), null, user, null);
        return PageRequestResponse.from(saved, location.name(), location.city());
    }

    /** The caller's own requests, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<PageRequestResponse> mine(CurrentUser user, int page, int size) {
        return withLocations(requests.findAllByRequesterId(user.id(), Paging.of(page, size, NEWEST_FIRST)));
    }

    /** Admin queue. PENDING requests come oldest first (first come, first served); other statuses newest first. */
    @Transactional(readOnly = true)
    public PageResponse<PageRequestResponse> list(RequestStatus status, int page, int size, CurrentUser user) {
        user.requireAdmin();
        Page<VerificationRequest> result = status == null
                ? requests.findAll(Paging.of(page, size, NEWEST_FIRST))
                : requests.findAllByStatus(status, Paging.of(page, size,
                status == RequestStatus.PENDING ? OLDEST_FIRST : NEWEST_FIRST));
        return withLocations(result);
    }

    /** One request: its requester or an administrator. */
    @Transactional(readOnly = true)
    public PageRequestResponse get(Long id, CurrentUser user) {
        VerificationRequest request = find(id);
        if (!user.isAdmin() && !request.getRequesterId().equals(user.id())) {
            throw new ForbiddenException("Cette demande ne vous appartient pas");
        }
        return withLocation(request);
    }

    /**
     * Creates the page linked to the request's existing Location (a Location is never created here), makes the
     * requester its OWNER and adds BUSINESS_REP to their roles in the Identity Service.
     * <p>
     * All in one transaction, and the Identity call is the last step: if the Identity Service is down (503) or
     * the account is gone (409), nothing is saved and the request stays PENDING, so the admin can simply retry.
     * Granting the role is idempotent, so a retry after a failure at commit time is harmless.
     */
    @Transactional
    public PageRequestResponse approve(Long id, CurrentUser admin) {
        admin.requireAdmin();
        VerificationRequest request = find(id);
        requirePending(request);
        LocationSnapshot location = locationClient.findLocation(request.getLocationId())
                .orElseThrow(() -> new EntityNotFoundException("Le lieu de cette demande n'existe plus"));
        if (pages.existsByLocationIdAndStatus(request.getLocationId(), PageStatus.ACTIVE)) {
            throw new ConflictException("Ce lieu a déjà une page professionnelle vérifiée");
        }
        String name = request.getProposedName() != null ? request.getProposedName() : location.name();
        // Flushed now so that the unique index on active pages answers before the Identity Service is called.
        BusinessPage page = pages.saveAndFlush(new BusinessPage(request.getLocationId(), name, location.address(),
                request.getId(), admin.id(), Instant.now()));
        members.save(new PageMember(page.getId(), request.getRequesterId(), request.getRequesterName(), PageRole.OWNER));
        request.approve(admin.id(), admin.displayName(), page.getId());
        audit.record(AuditAction.REQUEST_APPROVED, page.getId(), request.getId(), null, admin, null);
        identityClient.grantBusinessRep(request.getRequesterId());
        return PageRequestResponse.from(request, location.name(), location.city());
    }

    @Transactional
    public PageRequestResponse reject(Long id, String reason, CurrentUser admin) {
        admin.requireAdmin();
        VerificationRequest request = find(id);
        requirePending(request);
        request.reject(admin.id(), admin.displayName(), reason.trim());
        audit.record(AuditAction.REQUEST_REJECTED, null, request.getId(), null, admin, reason.trim());
        return withLocation(request);
    }

    private VerificationRequest find(Long id) {
        return requests.findById(id).orElseThrow(() -> new EntityNotFoundException("Demande introuvable"));
    }

    private static void requirePending(VerificationRequest request) {
        if (!request.isPending()) {
            throw new ConflictException("Cette demande a déjà été traitée");
        }
    }

    /** Location names are a convenience: when the Location Service is down the requests still answer. */
    private PageResponse<PageRequestResponse> withLocations(Page<VerificationRequest> result) {
        Map<Long, LocationSnapshot> locations;
        try {
            locations = locationClient.findLocations(result.map(VerificationRequest::getLocationId).getContent());
        } catch (DependencyUnavailableException exception) {
            locations = Map.of();
        }
        Map<Long, LocationSnapshot> byId = locations;
        return PageResponse.of(result, request -> {
            LocationSnapshot location = byId.get(request.getLocationId());
            return PageRequestResponse.from(request, location == null ? null : location.name(),
                    location == null ? null : location.city());
        });
    }

    private PageRequestResponse withLocation(VerificationRequest request) {
        LocationSnapshot location;
        try {
            location = locationClient.findLocation(request.getLocationId()).orElse(null);
        } catch (DependencyUnavailableException exception) {
            location = null;
        }
        return PageRequestResponse.from(request, location == null ? null : location.name(),
                location == null ? null : location.city());
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
