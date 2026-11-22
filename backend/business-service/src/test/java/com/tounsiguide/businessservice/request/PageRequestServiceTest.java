package com.tounsiguide.businessservice.request;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditService;
import com.tounsiguide.businessservice.client.IdentityClient;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.common.PageResponse;
import com.tounsiguide.businessservice.common.error.ConflictException;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tounsiguide.businessservice.TestData.ADMIN;
import static com.tounsiguide.businessservice.TestData.MEMBER;
import static com.tounsiguide.businessservice.TestData.OTHER;
import static com.tounsiguide.businessservice.TestData.location;
import static com.tounsiguide.businessservice.TestData.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class PageRequestServiceTest {

    @Mock
    private VerificationRequestRepository requests;
    @Mock
    private BusinessPageRepository pages;
    @Mock
    private PageMemberRepository members;
    @Mock
    private LocationClient locationClient;
    @Mock
    private IdentityClient identityClient;
    @Mock
    private AuditService audit;

    @InjectMocks
    private PageRequestService service;

    private static CreatePageRequest body(long locationId) {
        return new CreatePageRequest(locationId, "  Café officiel ", " Registre de commerce ", "   ");
    }

    // ----------------------------------------------------------------- submit (S3-US01)

    @Test
    void aMemberSubmitsARequestThatIsStoredPendingWithEvidence() {
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(requests.save(any())).thenAnswer(invocation -> {
            VerificationRequest saved = invocation.getArgument(0);
            setField(saved, "id", 7L);
            return saved;
        });

        PageRequestResponse response = service.submit(body(14), MEMBER);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(RequestStatus.PENDING);
        assertThat(response.requesterId()).isEqualTo("user-1");
        assertThat(response.requesterName()).isEqualTo("Sarra");
        assertThat(response.locationId()).isEqualTo(14L);
        assertThat(response.locationName()).isEqualTo("Café des Délices");
        assertThat(response.proposedName()).isEqualTo("Café officiel");
        assertThat(response.evidence()).isEqualTo("Registre de commerce");
        assertThat(response.contact()).isNull();
        verify(audit).record(AuditAction.REQUEST_SUBMITTED, null, 7L, null, MEMBER, null);
    }

    @Test
    void aRequestForAnUnknownLocationIs404() {
        when(locationClient.findLocation(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(body(99), MEMBER)).isInstanceOf(EntityNotFoundException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void aRequestForALocationThatAlreadyHasAPageIs409() {
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(pages.existsByLocationIdAndStatus(14L, PageStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(body(14), MEMBER)).isInstanceOf(ConflictException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void aSecondPendingRequestBySameUserForSameLocationIs409() {
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(requests.existsByLocationIdAndRequesterIdAndStatus(14L, "user-1", RequestStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(body(14), MEMBER)).isInstanceOf(ConflictException.class);
        verify(requests, never()).save(any());
    }

    @Test
    void theLocationServiceDownWhileSubmittingIs503() {
        when(locationClient.findLocation(14L)).thenThrow(new DependencyUnavailableException(
                "LOCATION_SERVICE_UNAVAILABLE", "down", null));

        assertThatThrownBy(() -> service.submit(body(14), MEMBER)).isInstanceOf(DependencyUnavailableException.class);
    }

    // ----------------------------------------------------------------- reads

    @Test
    void theQueueIsAdminOnly() {
        assertThatThrownBy(() -> service.list(RequestStatus.PENDING, 0, 20, MEMBER)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(requests);
    }

    @Test
    void thePendingQueueIsOldestFirstAndStillAnswersWhenLocationIsDown() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(requests.findAllByStatus(eq(RequestStatus.PENDING), pageable.capture()))
                .thenReturn(new PageImpl<>(List.of(request(1, 14, MEMBER)), PageRequest.of(0, 20), 1));
        when(locationClient.findLocations(any())).thenThrow(new DependencyUnavailableException("X", "down", null));

        PageResponse<PageRequestResponse> result = service.list(RequestStatus.PENDING, 0, 20, ADMIN);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).locationName()).isNull();
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isAscending()).isTrue();
    }

    @Test
    void mineListsOnlyTheCallersRequests() {
        when(requests.findAllByRequesterId(eq("user-1"), any()))
                .thenReturn(new PageImpl<>(List.of(request(1, 14, MEMBER)), PageRequest.of(0, 20), 1));
        when(locationClient.findLocations(any())).thenReturn(Map.of(14L, location(14)));

        PageResponse<PageRequestResponse> result = service.mine(MEMBER, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).locationCity()).isEqualTo("Sidi Bou Saïd");
    }

    @Test
    void someoneElsesRequestIs403() {
        when(requests.findById(1L)).thenReturn(Optional.of(request(1, 14, MEMBER)));

        assertThatThrownBy(() -> service.get(1L, OTHER)).isInstanceOf(ForbiddenException.class);
    }

    // ----------------------------------------------------------------- approve (S3-US02, S3-US03, S3-B06)

    @Test
    void approvingIsAdminOnly() {
        assertThatThrownBy(() -> service.approve(1L, MEMBER)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(requests, pages, identityClient);
    }

    @Test
    void approvingCreatesThePageOnTheExistingLocationMakesTheRequesterOwnerAndGrantsTheRole() {
        VerificationRequest pending = request(1, 14, MEMBER);
        when(requests.findById(1L)).thenReturn(Optional.of(pending));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(pages.saveAndFlush(any())).thenAnswer(invocation -> {
            BusinessPage page = invocation.getArgument(0);
            setField(page, "id", 30L);
            return page;
        });

        PageRequestResponse response = service.approve(1L, ADMIN);

        ArgumentCaptor<BusinessPage> page = ArgumentCaptor.forClass(BusinessPage.class);
        verify(pages).saveAndFlush(page.capture());
        assertThat(page.getValue().getLocationId()).isEqualTo(14L);
        assertThat(page.getValue().getName()).isEqualTo("Café des Délices officiel");
        assertThat(page.getValue().getAddress()).isEqualTo("Rue Habib Thameur");
        assertThat(page.getValue().getVerifiedBy()).isEqualTo("admin-1");
        assertThat(page.getValue().isActive()).isTrue();

        ArgumentCaptor<PageMember> member = ArgumentCaptor.forClass(PageMember.class);
        verify(members).save(member.capture());
        assertThat(member.getValue().getPageId()).isEqualTo(30L);
        assertThat(member.getValue().getUserId()).isEqualTo("user-1");
        assertThat(member.getValue().getRole()).isEqualTo(PageRole.OWNER);

        assertThat(response.status()).isEqualTo(RequestStatus.APPROVED);
        assertThat(response.pageId()).isEqualTo(30L);
        assertThat(response.decidedBy()).isEqualTo("admin-1");
        assertThat(response.decidedAt()).isNotNull();

        // The Identity call is the last step, so that its failure rolls everything back.
        InOrder order = inOrder(pages, members, audit, identityClient);
        order.verify(pages).saveAndFlush(any());
        order.verify(members).save(any());
        order.verify(audit).record(AuditAction.REQUEST_APPROVED, 30L, 1L, null, ADMIN, null);
        order.verify(identityClient).grantBusinessRep("user-1");
    }

    @Test
    void withoutAProposedNameThePageTakesTheLocationName() {
        VerificationRequest pending = request(1, 14, MEMBER);
        setField(pending, "proposedName", null);
        when(requests.findById(1L)).thenReturn(Optional.of(pending));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(pages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.approve(1L, ADMIN);

        ArgumentCaptor<BusinessPage> page = ArgumentCaptor.forClass(BusinessPage.class);
        verify(pages).saveAndFlush(page.capture());
        assertThat(page.getValue().getName()).isEqualTo("Café des Délices");
    }

    @Test
    void identityDownMakesTheApprovalFailWith503() {
        VerificationRequest pending = request(1, 14, MEMBER);
        when(requests.findById(1L)).thenReturn(Optional.of(pending));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(pages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DependencyUnavailableException("IDENTITY_SERVICE_UNAVAILABLE", "down", null))
                .when(identityClient).grantBusinessRep("user-1");

        assertThatThrownBy(() -> service.approve(1L, ADMIN)).isInstanceOf(DependencyUnavailableException.class);
        // The exception leaves the @Transactional method: Spring rolls back the page, member, audit and decision.
    }

    @Test
    void approvingWhenTheLocationAlreadyHasAPageIs409AndCreatesNothing() {
        when(requests.findById(1L)).thenReturn(Optional.of(request(1, 14, MEMBER)));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));
        when(pages.existsByLocationIdAndStatus(14L, PageStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.approve(1L, ADMIN)).isInstanceOf(ConflictException.class);
        verify(pages, never()).saveAndFlush(any());
        verifyNoInteractions(identityClient);
    }

    @Test
    void approvingWhenTheLocationNoLongerExistsIs404() {
        when(requests.findById(1L)).thenReturn(Optional.of(request(1, 14, MEMBER)));
        when(locationClient.findLocation(14L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(1L, ADMIN)).isInstanceOf(EntityNotFoundException.class);
        verify(pages, never()).saveAndFlush(any());
    }

    @Test
    void anAlreadyDecidedRequestCannotBeDecidedAgain() {
        VerificationRequest decided = request(1, 14, MEMBER);
        decided.reject("admin-1", "Admin", "Preuve insuffisante");
        when(requests.findById(1L)).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> service.approve(1L, ADMIN)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.reject(1L, "x", ADMIN)).isInstanceOf(ConflictException.class);
    }

    @Test
    void anUnknownRequestIs404() {
        when(requests.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(5L, ADMIN)).isInstanceOf(EntityNotFoundException.class);
    }

    // ----------------------------------------------------------------- reject

    @Test
    void rejectingIsAdminOnly() {
        assertThatThrownBy(() -> service.reject(1L, "Non", OTHER)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(requests);
    }

    @Test
    void rejectingRecordsTheReasonAndTheAdminAndCreatesNoPage() {
        when(requests.findById(1L)).thenReturn(Optional.of(request(1, 14, MEMBER)));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));

        PageRequestResponse response = service.reject(1L, "  Preuve insuffisante ", ADMIN);

        assertThat(response.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(response.decisionReason()).isEqualTo("Preuve insuffisante");
        assertThat(response.decidedByName()).isEqualTo("Admin");
        assertThat(response.pageId()).isNull();
        verify(audit).record(eq(AuditAction.REQUEST_REJECTED), isNull(), eq(1L), isNull(), eq(ADMIN),
                eq("Preuve insuffisante"));
        verifyNoInteractions(pages, members, identityClient);
    }
}
