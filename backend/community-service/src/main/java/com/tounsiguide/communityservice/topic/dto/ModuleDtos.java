package com.tounsiguide.communityservice.topic.dto;

import com.tounsiguide.communityservice.topic.ModuleStatus;
import com.tounsiguide.communityservice.topic.TopicModule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Bodies of /api/v1/modules (docs/api/community-service-api.md, "Topic modules"). */
public final class ModuleDtos {

    private ModuleDtos() {
    }

    /** Body of POST /api/v1/modules. */
    public record ProposeModuleRequest(
            @NotBlank(message = "Le nom est obligatoire")
            @Size(min = 3, max = 60, message = "Entre 3 et 60 caractères")
            String name,

            @NotBlank(message = "La description est obligatoire")
            @Size(max = 500, message = "500 caractères au maximum")
            String description,

            @Size(max = 16, message = "Une icône (emoji) au maximum")
            String icon
    ) {
    }

    /** Body of reject (reason required, checked by the service) and archive (optional). */
    public record ModuleDecisionRequest(
            @Size(max = 1_000, message = "1000 caractères au maximum")
            String reason
    ) {
    }

    /**
     * A module. {@code postCount} = published posts in the module (filled in the public list and on the module page;
     * 0 elsewhere). Proposer and decision fields are always present (the module page only shows approved ones).
     */
    public record ModuleResponse(
            Long id,
            String slug,
            String name,
            String description,
            String icon,
            ModuleStatus status,
            String proposedByName,
            Instant createdAt,
            String decidedByName,
            Instant decidedAt,
            String decisionReason,
            long postCount
    ) {
        public static ModuleResponse from(TopicModule module, long postCount) {
            return new ModuleResponse(module.getId(), module.getSlug(), module.getName(), module.getDescription(),
                    module.getIcon(), module.getStatus(), module.getProposedByName(), module.getCreatedAt(),
                    module.getDecidedByName(), module.getDecidedAt(), module.getDecisionReason(), postCount);
        }
    }
}
