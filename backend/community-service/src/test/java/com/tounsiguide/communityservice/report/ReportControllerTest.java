package com.tounsiguide.communityservice.report;

import com.tounsiguide.communityservice.common.error.ConflictException;
import com.tounsiguide.communityservice.common.error.GlobalExceptionHandler;
import com.tounsiguide.communityservice.common.error.InvalidParameterException;
import com.tounsiguide.communityservice.common.security.CurrentUserArgumentResolver;
import com.tounsiguide.communityservice.common.security.ForbiddenException;
import com.tounsiguide.communityservice.report.dto.ReportDtos.ReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .build();
    }

    @Test
    void reportingNeedsALogin() throws Exception {
        mvc.perform(post("/api/v1/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":12,\"reason\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void aReportIsCreated() throws Exception {
        when(service.report(any(), any())).thenReturn(new ReportResponse(1L, ReportTargetType.POST, 12L, 12L, "Ali",
                ReportReason.SPAM, null, ReportStatus.OPEN, Instant.now(), null, null, null));

        mvc.perform(post("/api/v1/reports").header("X-User-Id", "user-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":12,\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void aReportWithoutReasonOrWithAnUnknownReasonIs400() throws Exception {
        mvc.perform(post("/api/v1/reports").header("X-User-Id", "user-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":12}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").exists());
        mvc.perform(post("/api/v1/reports").header("X-User-Id", "user-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":12,\"reason\":\"BORING\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/reports").header("X-User-Id", "user-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":12,\"reason\":\"OTHER\",\"detail\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.detail").exists());
    }

    @Test
    void conflictsAndForbiddenAreMapped() throws Exception {
        when(service.report(any(), any())).thenThrow(new ConflictException("Vous avez déjà signalé ce contenu"));
        mvc.perform(post("/api/v1/reports").header("X-User-Id", "user-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"POST\",\"targetId\":12,\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        when(service.queue(eq(0), eq(20), any())).thenThrow(new ForbiddenException("Action réservée aux modérateurs"));
        mvc.perform(get("/api/v1/reports/queue").header("X-User-Id", "user-2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void restrictingWithoutReasonIs400() throws Exception {
        when(service.restrict(eq(ReportTargetType.POST), eq(12L), isNull(), any()))
                .thenThrow(new InvalidParameterException("reason", "Le motif de la restriction est obligatoire"));

        mvc.perform(post("/api/v1/reports/targets/POST/12/restrict").header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.reason").exists());
    }

    @Test
    void anUnknownTargetTypeIs400() throws Exception {
        mvc.perform(post("/api/v1/reports/targets/USER/12/restrict").header("X-User-Id", "admin-1"))
                .andExpect(status().isBadRequest());
    }
}
