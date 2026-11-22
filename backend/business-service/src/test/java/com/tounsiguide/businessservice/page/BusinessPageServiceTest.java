package com.tounsiguide.businessservice.page;

import com.tounsiguide.businessservice.audit.AuditAction;
import com.tounsiguide.businessservice.audit.AuditService;
import com.tounsiguide.businessservice.client.LocationClient;
import com.tounsiguide.businessservice.common.error.DependencyUnavailableException;
import com.tounsiguide.businessservice.common.security.ForbiddenException;
import com.tounsiguide.businessservice.page.dto.BusinessPageResponse;
import com.tounsiguide.businessservice.page.dto.UpdatePageRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tounsiguide.businessservice.TestData.ADMIN;
import static com.tounsiguide.businessservice.TestData.MEMBER;
import static com.tounsiguide.businessservice.TestData.OTHER;
import static com.tounsiguide.businessservice.TestData.location;
import static com.tounsiguide.businessservice.TestData.page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessPageServiceTest {

    @Mock
    private BusinessPageRepository pages;
    @Mock
    private PageMemberRepository members;
    @Mock
    private LocationClient locationClient;
    @Mock
    private AuditService audit;

    private BusinessPageService service;

    private static final UpdatePageRequest EDIT = new UpdatePageRequest(" Café des Délices ", "Vue sur la mer",
            "8h-23h", "+216 71 000 000", "contact@cafe.tn", "https://cafe.tn", "  ");

    @BeforeEach
    void setUp() {
        service = new BusinessPageService(pages, new PageAccess(pages, members), locationClient, audit);
        lenient().when(members.findByPageIdAndUserId(30L, "user-1"))
                .thenReturn(Optional.of(new PageMember(30L, "user-1", "Sarra", PageRole.OWNER)));
    }

    @Test
    void thePageShowsTheLocationAndTellsAMemberTheirRole() {
        when(pages.findByIdAndStatus(30L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));

        BusinessPageResponse response = service.get(30L, MEMBER);

        assertThat(response.location().name()).isEqualTo("Café des Délices");
        assertThat(response.location().city()).isEqualTo("Sidi Bou Saïd");
        assertThat(response.member()).isTrue();
        assertThat(response.memberRole()).isEqualTo(PageRole.OWNER);
    }

    @Test
    void aVisitorIsNotAMemberAndThePageStillAnswersWhenLocationIsDown() {
        when(pages.findByIdAndStatus(30L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));
        when(locationClient.findLocation(14L)).thenThrow(new DependencyUnavailableException("X", "down", null));

        BusinessPageResponse response = service.get(30L, null);

        assertThat(response.member()).isFalse();
        assertThat(response.memberRole()).isNull();
        assertThat(response.location()).isNull();
        assertThat(response.name()).isEqualTo("Café des Délices");
    }

    @Test
    void aDeletedOrUnknownPageIs404() {
        when(pages.findByIdAndStatus(31L, PageStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(31L, MEMBER)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void byLocationFindsTheActivePageOr404() {
        when(pages.findByLocationIdAndStatus(14L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));
        when(pages.findByLocationIdAndStatus(15L, PageStatus.ACTIVE)).thenReturn(Optional.empty());
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));

        assertThat(service.byLocation(14L, null).id()).isEqualTo(30L);
        assertThatThrownBy(() -> service.byLocation(15L, null)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void theListFiltersOnTheNameAndAddsLocations() {
        when(pages.findAllByStatusAndNameContainingIgnoreCase(eq(PageStatus.ACTIVE), eq("café"), any()))
                .thenReturn(new PageImpl<>(List.of(page(30, 14)), PageRequest.of(0, 20), 1));
        when(locationClient.findLocations(List.of(14L))).thenReturn(Map.of(14L, location(14)));

        var result = service.list(" café ", 0, 20);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).location().city()).isEqualTo("Sidi Bou Saïd");
    }

    @Test
    void aMemberEditsTheOfficialInformationAndItIsAudited() {
        when(pages.findByIdAndStatus(30L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));
        when(pages.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationClient.findLocation(14L)).thenReturn(Optional.of(location(14)));

        BusinessPageResponse response = service.update(30L, EDIT, MEMBER);

        assertThat(response.name()).isEqualTo("Café des Délices");
        assertThat(response.description()).isEqualTo("Vue sur la mer");
        assertThat(response.openingHours()).isEqualTo("8h-23h");
        assertThat(response.website()).isEqualTo("https://cafe.tn");
        assertThat(response.address()).isNull();
        assertThat(response.locationId()).isEqualTo(14L);
        verify(audit).record(AuditAction.PAGE_UPDATED, 30L, null, null, MEMBER, null);
    }

    @Test
    void aNonMemberCannotEdit403EvenWithTheBusinessRepRole() {
        when(pages.findByIdAndStatus(30L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));

        assertThatThrownBy(() -> service.update(30L, EDIT, OTHER)).isInstanceOf(ForbiddenException.class);
        verify(pages, never()).saveAndFlush(any());
    }

    @Test
    void anAdministratorWhoIsNotAMemberCannotEditTheOfficialInformation() {
        when(pages.findByIdAndStatus(30L, PageStatus.ACTIVE)).thenReturn(Optional.of(page(30, 14)));

        assertThatThrownBy(() -> service.update(30L, EDIT, ADMIN)).isInstanceOf(ForbiddenException.class);
        verify(pages, never()).saveAndFlush(any());
    }
}
