package com.tounsiguide.communityservice.topic;

import com.tounsiguide.communityservice.common.error.ConflictException;
import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.common.security.CurrentUserArgumentResolver;
import com.tounsiguide.communityservice.topic.dto.ModuleDtos.ModuleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TopicModuleControllerTest {

    @Mock
    private TopicModuleService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TopicModuleController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    private static ModuleResponse response(ModuleStatus status) {
        return new ModuleResponse(3L, "cafes", "Cafés", "Où boire un café", "☕", status, "Sarra", Instant.now(),
                null, null, null, 4);
    }

    @Test
    void theListIsPublic() throws Exception {
        when(service.approved()).thenReturn(List.of(response(ModuleStatus.APPROVED)));

        mvc.perform(get("/api/v1/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("cafes"))
                .andExpect(jsonPath("$[0].postCount").value(4));
    }

    @Test
    void theModulePageTellsTheServiceWhetherTheCallerIsAdmin() throws Exception {
        when(service.bySlug("cafes", true)).thenReturn(response(ModuleStatus.PROPOSED));

        mvc.perform(get("/api/v1/modules/cafes").header("X-User-Roles", "MEMBER,ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROPOSED"));
    }

    @Test
    void proposingNeedsALoginAndANameAndADescription() throws Exception {
        mvc.perform(post("/api/v1/modules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cafés\",\"description\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/modules").header("X-User-Id", "u").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ab\",\"description\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.description").exists());
        verifyNoInteractions(service);
    }

    @Test
    void aProposalIsCreatedAndADuplicateIs409() throws Exception {
        when(service.propose(any(), any())).thenReturn(response(ModuleStatus.PROPOSED));
        mvc.perform(post("/api/v1/modules").header("X-User-Id", "u").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cafés\",\"description\":\"Où boire un café\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"));

        when(service.propose(any(), any())).thenThrow(new ConflictException("Un module porte déjà ce nom"));
        mvc.perform(post("/api/v1/modules").header("X-User-Id", "u").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cafés\",\"description\":\"Où boire un café\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void theQueueDefaultsToProposedAndAnUnknownStatusIs400() throws Exception {
        when(service.byStatus(any(), any(Integer.class), any(Integer.class), any())).thenReturn(null);
        mvc.perform(get("/api/v1/modules/queue").header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isOk());
        verify(service).byStatus(org.mockito.ArgumentMatchers.eq(ModuleStatus.PROPOSED), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20), any());

        mvc.perform(get("/api/v1/modules/queue").param("status", "MAYBE").header("X-User-Id", "admin-1"))
                .andExpect(status().isBadRequest());
    }
}
