package com.tounsiguide.locationservice.matching.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Everything an analyzer may need, built by the service (association-api.md, section 5.1), so that an
 * analyzer never has to call another service or the database.
 */
public record AnalysisContext(
        PostInfo post,
        List<CommentInfo> comments,
        List<AssociationInfo> associations,
        List<CandidateLocation> candidateLocations,
        List<PastAssociation> previousAssociations,
        List<BusinessPage> businessPages,
        Trigger trigger
) {

    public AnalysisContext {
        comments = comments == null ? List.of() : List.copyOf(comments);
        associations = associations == null ? List.of() : List.copyOf(associations);
        candidateLocations = candidateLocations == null ? List.of() : List.copyOf(candidateLocations);
        previousAssociations = previousAssociations == null ? List.of() : List.copyOf(previousAssociations);
        businessPages = businessPages == null ? List.of() : List.copyOf(businessPages);
    }

    /** Why the analysis runs. */
    public enum Trigger {
        POST_CHANGED,
        COMMENT_CHANGED,
        DISPUTED,
        RESCAN
    }

    /**
     * @param claimedLocationId Location chosen by the author ({@code Post.locationId}), may be null
     * @param imageUrls         empty until posts have images
     * @param metadata          image or post metadata, empty for now
     */
    public record PostInfo(
            long id,
            String text,
            String authorId,
            String authorName,
            Instant createdAt,
            Long claimedLocationId,
            List<String> imageUrls,
            Map<String, String> metadata
    ) {

        public PostInfo {
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    public record CommentInfo(long id, String text, String authorId, String authorName, Instant createdAt) {
    }

    /** An existing association of the post; origin, status and evidence types are the API names. */
    public record AssociationInfo(
            long id,
            long locationId,
            String origin,
            String status,
            int score,
            List<EvidenceInfo> evidence
    ) {

        public AssociationInfo {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public record EvidenceInfo(String type, String direction, int weight, Long commentId, String note) {
    }

    /** A directory Location named in the text, claimed by the author or already suggested. */
    public record CandidateLocation(
            long id,
            String name,
            List<String> aliases,
            String city,
            Double latitude,
            Double longitude,
            String category
    ) {

        public CandidateLocation {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }

    /** A decided association on another post, by the same author or for one of the candidate Locations. */
    public record PastAssociation(long postId, long locationId, String status, boolean sameAuthor) {
    }

    /** Business page of a candidate Location (Sprint 3, always empty for now). */
    public record BusinessPage(long locationId, String name) {
    }
}
