package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.community.CommunityClient.CommentSnapshot;
import com.tounsiguide.locationservice.community.CommunityClient.PostSnapshot;
import com.tounsiguide.locationservice.location.Location;
import com.tounsiguide.locationservice.location.LocationAlias;
import com.tounsiguide.locationservice.location.LocationRepository;
import com.tounsiguide.locationservice.matching.TextNormalizer;
import com.tounsiguide.locationservice.matching.analysis.AnalysisContext;
import com.tounsiguide.locationservice.matching.analysis.MentionFinder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Builds the {@link AnalysisContext} (section 5.1) so that an analyzer never calls another service or the database.
 *
 * <p>Candidate Locations are the directory Locations whose name or alias appears in the post or a comment,
 * plus the place claimed by the author and every place that already has an association on the post.
 * Sprint 2 scans the whole directory, which is fine for a directory of a few hundred places.</p>
 */
@Component
public class AnalysisContextBuilder {

    static final int MAX_PREVIOUS = 50;

    private final AssociationRepository repository;
    private final LocationRepository locationRepository;

    public AnalysisContextBuilder(AssociationRepository repository, LocationRepository locationRepository) {
        this.repository = repository;
        this.locationRepository = locationRepository;
    }

    @Transactional(readOnly = true)
    public AnalysisContext build(PostSnapshot post, List<CommentSnapshot> comments, AnalysisContext.Trigger trigger) {
        List<Association> associations = repository.findByPostId(post.id());

        List<String> texts = new ArrayList<>();
        texts.add(TextNormalizer.normalize(post.content()));
        comments.forEach(c -> texts.add(TextNormalizer.normalize(c.content())));

        Set<Long> wanted = new LinkedHashSet<>();
        if (post.locationId() != null) {
            wanted.add(post.locationId());
        }
        associations.forEach(a -> wanted.add(a.getLocation().getId()));

        List<AnalysisContext.CandidateLocation> candidates = new ArrayList<>();
        for (Location location : locationRepository.findAllWithAliases()) {
            if (wanted.contains(location.getId()) || isNamed(location, texts)) {
                candidates.add(toCandidate(location));
            }
        }
        List<Long> candidateIds = candidates.stream().map(AnalysisContext.CandidateLocation::id).toList();

        AnalysisContext.PostInfo postInfo = new AnalysisContext.PostInfo(post.id(), post.content(), post.authorId(),
                post.authorName(), post.createdAt(), post.locationId(), List.of(), Map.of());
        List<AnalysisContext.CommentInfo> commentInfos = comments.stream()
                .map(c -> new AnalysisContext.CommentInfo(c.id(), c.content(), c.authorId(), c.authorName(), c.createdAt()))
                .toList();
        List<AnalysisContext.AssociationInfo> associationInfos = associations.stream()
                .map(AnalysisContextBuilder::toInfo)
                .toList();
        List<AnalysisContext.PastAssociation> previous = repository.findPrevious(
                        post.id(),
                        Objects.requireNonNullElse(post.authorId(), ""),
                        candidateIds.isEmpty() ? List.of(-1L) : candidateIds,
                        List.of(Status.CONFIRMED, Status.REJECTED),
                        PageRequest.of(0, MAX_PREVIOUS)).stream()
                .map(a -> new AnalysisContext.PastAssociation(a.getPostId(), a.getLocation().getId(), a.getStatus().name(),
                        Objects.equals(a.getPostAuthorId(), post.authorId())))
                .toList();

        return new AnalysisContext(postInfo, commentInfos, associationInfos, candidates, previous, List.of(), trigger);
    }

    private static boolean isNamed(Location location, List<String> normalizedTexts) {
        List<String> names = Stream.concat(Stream.of(location.getNormalizedName()),
                location.getAliases().stream().map(LocationAlias::getNormalizedAlias)).toList();
        return normalizedTexts.stream().anyMatch(text -> names.stream().anyMatch(name -> MentionFinder.contains(text, name)));
    }

    private static AnalysisContext.CandidateLocation toCandidate(Location location) {
        return new AnalysisContext.CandidateLocation(location.getId(), location.getName(),
                location.getAliases().stream().map(LocationAlias::getAlias).toList(), location.getCity(),
                location.getLatitude(), location.getLongitude(), location.getCategory());
    }

    private static AnalysisContext.AssociationInfo toInfo(Association association) {
        return new AnalysisContext.AssociationInfo(association.getId(), association.getLocation().getId(),
                association.getOrigin().name(), association.getStatus().name(), association.getScore(),
                association.getEvidence().stream()
                        .map(e -> new AnalysisContext.EvidenceInfo(e.getType().name(), e.getDirection().name(),
                                e.getWeight(), e.getCommentId(), e.getNote()))
                        .toList());
    }
}
