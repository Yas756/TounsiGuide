package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.association.dto.AssociationResponse;
import com.tounsiguide.locationservice.association.dto.DecisionResponse;
import com.tounsiguide.locationservice.location.Location;
import com.tounsiguide.locationservice.matching.analysis.AnalysisResult;
import com.tounsiguide.locationservice.matching.analysis.LocationVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Entities to API responses, and the analysis to and from its stored JSON. */
@Component
public class AssociationMapper {

    private static final Logger log = LoggerFactory.getLogger(AssociationMapper.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final JsonMapper jsonMapper;

    public AssociationMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public AssociationResponse toResponse(Association association) {
        return toResponse(association, false);
    }

    /** With {@code withHistory}, the decision history is included (single association view). */
    public AssociationResponse toResponse(Association association, boolean withHistory) {
        Location location = association.getLocation();
        return new AssociationResponse(
                association.getId(),
                association.getPostId(),
                new AssociationResponse.LocationRef(location.getId(), location.getName(), location.getCity()),
                association.getOrigin(),
                association.getStatus(),
                association.getScore(),
                association.isForced(),
                readAnalysis(association.getAnalysis()),
                association.getEvidence().stream().map(AssociationMapper::toEvidence).toList(),
                association.getCreatedBy(),
                association.getCreatedAt(),
                association.getDecidedBy(),
                association.getDecidedAt(),
                association.getDecisionReason(),
                association.isPostDeleted(),
                association.getPostDeletedAt(),
                withHistory ? toHistory(association) : null
        );
    }

    public List<DecisionResponse> toHistory(Association association) {
        return association.getDecisions().stream()
                .map(d -> new DecisionResponse(d.getId(), d.getFromStatus(), d.getToStatus(), d.getActorId(),
                        d.getActorRole(), d.getReason(), d.getCreatedAt()))
                .toList();
    }

    private static AssociationResponse.EvidenceResponse toEvidence(AssociationEvidence evidence) {
        return new AssociationResponse.EvidenceResponse(evidence.getId(), evidence.getType(), evidence.getDirection(),
                evidence.getWeight(), evidence.getActorId(), evidence.getActorName(), evidence.getCommentId(),
                evidence.getNote(), evidence.getCreatedAt());
    }

    /** JSON stored on the association: provider, version, scores, reasoning, mentions, date (section 5.1). */
    public String analysisJson(AnalysisResult result, LocationVerdict verdict, Instant analysedAt) {
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("provider", result.provider());
        analysis.put("providerVersion", result.providerVersion());
        analysis.put("supportScore", verdict.supportScore());
        analysis.put("opposeScore", verdict.opposeScore());
        analysis.put("reasoning", verdict.reasoning());
        analysis.put("overallReasoning", result.reasoning());
        analysis.put("mentions", verdict.mentions().stream().map(m -> {
            Map<String, Object> mention = new LinkedHashMap<>();
            mention.put("text", m.text());
            mention.put("commentId", m.commentId());
            mention.put("type", m.type().name());
            return mention;
        }).toList());
        analysis.put("analysedAt", analysedAt.toString());
        return jsonMapper.writeValueAsString(analysis);
    }

    private Map<String, Object> readAnalysis(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return jsonMapper.readValue(json, MAP);
        } catch (JacksonException exception) {
            log.warn("Unreadable stored analysis: {}", exception.getMessage());
            return null;
        }
    }
}
