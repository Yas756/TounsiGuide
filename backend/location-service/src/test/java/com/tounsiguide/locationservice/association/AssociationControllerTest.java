package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.association.dto.AssociationResponse;
import com.tounsiguide.locationservice.association.dto.PageResponse;
import com.tounsiguide.locationservice.association.dto.PostAssociationSummary;
import com.tounsiguide.locationservice.common.error.ConflictException;
import com.tounsiguide.locationservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.locationservice.common.security.CurrentUserArgumentResolver;
import com.tounsiguide.locationservice.common.security.ForbiddenException;
import com.tounsiguide.locationservice.community.CommunityServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssociationControllerTest {

    @Mock
    private AssociationService service;

    @Mock
    private AssociationAnalysisJob job;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AssociationController(service), new InternalPostController(job))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    private static AssociationResponse response(Status status) {
        return new AssociationResponse(7L, 1L, new AssociationResponse.LocationRef(14L, "Café Roma", "Tunis"),
                Origin.MEMBER, status, 60, false, null, List.of(), "member-1", Instant.now(), null, null, null, false, null, null);
    }

    @Test
    void listForPostIsPublic() throws Exception {
        when(service.listForPost(1L)).thenReturn(List.of(response(Status.PROPOSED)));

        mockMvc.perform(get("/api/v1/posts/1/associations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].location.name").value("Café Roma"))
                .andExpect(jsonPath("$[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$[0].history").doesNotExist());
    }

    @Test
    void newSuggestionIs201AndExistingOneIs200() throws Exception {
        when(service.suggest(eq(1L), any(), any()))
                .thenReturn(new AssociationService.SuggestResult(response(Status.PROPOSED), true))
                .thenReturn(new AssociationService.SuggestResult(response(Status.PROPOSED), false));
        String body = """
                {"locationId":14,"note":"It's Café Roma, the sign is visible.","commentId":88}
                """;

        mockMvc.perform(post("/api/v1/posts/1/associations").header("X-User-Id", "member-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(60));
        mockMvc.perform(post("/api/v1/posts/1/associations").header("X-User-Id", "member-2")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void writingWithoutIdentityIs401() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/associations")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locationId\":14}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(service);
    }

    @Test
    void suggestionWithoutLocationIs400() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/associations").header("X-User-Id", "member-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"somewhere\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.locationId").exists());
    }

    @Test
    void disputeNeedsANote() throws Exception {
        mockMvc.perform(post("/api/v1/associations/7/dispute").header("X-User-Id", "member-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.note").exists());
    }

    @Test
    void reviewNeedsAReason() throws Exception {
        mockMvc.perform(post("/api/v1/associations/7/review").header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"VALIDATE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").exists());
    }

    @Test
    void forbiddenAndConflictUseTheCommonErrorFormat() throws Exception {
        when(service.confirm(eq(7L), any())).thenThrow(new ForbiddenException("Only the author of the post can do this"));
        when(service.reject(eq(7L), any())).thenThrow(new ConflictException("Cannot reject an association that is CONFIRMED"));

        mockMvc.perform(post("/api/v1/associations/7/confirm").header("X-User-Id", "member-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(post("/api/v1/associations/7/reject").header("X-User-Id", "author-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void communityDownIs503() throws Exception {
        when(service.dispute(eq(7L), any(), any()))
                .thenThrow(new CommunityServiceUnavailableException(new RuntimeException("refused")));

        mockMvc.perform(post("/api/v1/associations/7/dispute").header("X-User-Id", "member-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"wrong place\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMUNITY_SERVICE_UNAVAILABLE"));
    }

    @Test
    void summaryIsKeyedByPostId() throws Exception {
        Map<String, PostAssociationSummary> summary = new LinkedHashMap<>();
        summary.put("1", new PostAssociationSummary(new PostAssociationSummary.Confirmed(7L, 14L, 100), 0));
        summary.put("2", new PostAssociationSummary(null, 2));
        when(service.summary(List.of(1L, 2L))).thenReturn(summary);

        mockMvc.perform(get("/api/v1/associations/summary").param("postIds", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1'].confirmed.locationId").value(14))
                .andExpect(jsonPath("$['2'].confirmed").isEmpty())
                .andExpect(jsonPath("$['2'].openProposals").value(2));
    }

    @Test
    void queueParsesStatusesAndPaging() throws Exception {
        when(service.queue(eq(List.of(Status.DISPUTED, Status.PROPOSED)), eq(0), eq(20), any()))
                .thenReturn(new PageResponse<>(List.of(response(Status.DISPUTED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/associations").param("status", "DISPUTED,PROPOSED")
                        .header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DISPUTED"))
                .andExpect(jsonPath("$.content[0].postDeleted").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void adminSetIs201AndRescanIs202() throws Exception {
        when(service.adminSet(eq(1L), any(), any())).thenReturn(response(Status.CONFIRMED));

        mockMvc.perform(post("/api/v1/posts/1/associations/admin").header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"locationId\":14,\"reason\":\"Correction\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/posts/1/associations/rescan").header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isAccepted());
        verify(service).rescan(eq(1L), any());
    }

    @Test
    void locationWallListsConfirmedPostIds() throws Exception {
        when(service.confirmedPostIds(14L, 0, 20)).thenReturn(new PageResponse<>(List.of(3L, 1L), 0, 20, 2, 1));

        mockMvc.perform(get("/api/v1/locations/14/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value(3));
    }

    @Test
    void internalChangePingIsAcceptedWithoutIdentity() throws Exception {
        mockMvc.perform(post("/internal/posts/5/changed"))
                .andExpect(status().isAccepted());
        verify(job).postChangedLater(5L);
    }

    @Test
    void unknownStatusIs400() throws Exception {
        mockMvc.perform(get("/api/v1/associations").param("status", "MAYBE").header("X-User-Id", "admin-1"))
                .andExpect(status().isBadRequest());
    }
}
