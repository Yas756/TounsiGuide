package com.tounsiguide.locationservice.matching.analysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Written once, run against every {@link AssociationAnalyzer} (association-api.md, section 5.2):
 * same context in, valid result out. A new analyzer (the AI) extends this class and must pass it before
 * {@code tounsiguide.analysis.provider} is switched to it.
 */
public abstract class AssociationAnalyzerContractTest {

    /** The analyzer under test. */
    protected abstract AssociationAnalyzer analyzer();

    protected static final long CAFE_ROMA = 14L;
    protected static final long CAFE_ROME = 15L;
    protected static final long BARDO = 5L;

    protected static AnalysisContext.CandidateLocation cafeRoma() {
        return new AnalysisContext.CandidateLocation(CAFE_ROMA, "Café Roma", List.of("Roma Coffee"), "Tunis",
                36.8003, 10.1857, "cafe");
    }

    protected static AnalysisContext.CandidateLocation cafeRome() {
        return new AnalysisContext.CandidateLocation(CAFE_ROME, "Café Rome", List.of(), "Tunis", 36.8010, 10.1880, "cafe");
    }

    protected static AnalysisContext.CandidateLocation bardo() {
        return new AnalysisContext.CandidateLocation(BARDO, "Musée national du Bardo", List.of("Bardo"), "Tunis",
                36.8095, 10.1347, "museum");
    }

    protected static AnalysisContext.PostInfo post(String text, Long claimed) {
        return new AnalysisContext.PostInfo(1L, text, "author-1", "Sarra", Instant.parse("2026-11-09T10:00:00Z"),
                claimed, List.of(), Map.of());
    }

    protected static AnalysisContext.CommentInfo comment(long id, String text) {
        return new AnalysisContext.CommentInfo(id, text, "member-" + id, "Member " + id, Instant.parse("2026-11-09T11:00:00Z"));
    }

    /** Author tagged Café Rome, two comments say Café Roma, one member disputed. */
    protected static AnalysisContext disputedContext() {
        AnalysisContext.AssociationInfo authorClaim = new AnalysisContext.AssociationInfo(7L, CAFE_ROME, "AUTHOR",
                "DISPUTED", 77, List.of(
                new AnalysisContext.EvidenceInfo("AUTHOR_TAG", "SUPPORT", 10, null, null),
                new AnalysisContext.EvidenceInfo("MEMBER_DISPUTE", "OPPOSE", 3, null, "It was Café Roma")));
        return new AnalysisContext(
                post("Best espresso in town!", CAFE_ROME),
                List.of(comment(88L, "This was at Café Roma, I recognise the terrace"),
                        comment(89L, "yes cafe-roma, next to the cinema"),
                        comment(90L, "Nice photo")),
                List.of(authorClaim),
                List.of(cafeRome(), cafeRoma()),
                List.of(new AnalysisContext.PastAssociation(3L, CAFE_ROME, "CONFIRMED", true)),
                List.of(),
                AnalysisContext.Trigger.DISPUTED);
    }

    /** No tag, nothing named. */
    protected static AnalysisContext emptyContext() {
        return new AnalysisContext(post("Lovely sunset", null), List.of(comment(1L, "wow")), List.of(), List.of(),
                List.of(), List.of(), AnalysisContext.Trigger.POST_CHANGED);
    }

    /** A post naming a place in its own text, no association yet. */
    protected static AnalysisContext mentionInPostContext() {
        return new AnalysisContext(post("Visiting the Bardo today", null), List.of(), List.of(), List.of(bardo()),
                List.of(), List.of(), AnalysisContext.Trigger.POST_CHANGED);
    }

    protected static List<AnalysisContext> contexts() {
        return List.of(disputedContext(), emptyContext(), mentionInPostContext());
    }

    @Test
    void nameIsNotBlank() {
        assertThat(analyzer().name()).isNotBlank();
    }

    @Test
    void resultNamesTheProviderAndAVersion() {
        for (AnalysisContext context : contexts()) {
            AnalysisResult result = analyzer().analyse(context);
            assertThat(result).isNotNull();
            assertThat(result.provider()).isEqualTo(analyzer().name());
            assertThat(result.providerVersion()).isNotBlank();
        }
    }

    @Test
    void scoresAreBetweenZeroAndHundred() {
        for (AnalysisContext context : contexts()) {
            for (LocationVerdict verdict : analyzer().analyse(context).verdicts()) {
                assertThat(verdict.supportScore()).isBetween(0, 100);
                assertThat(verdict.opposeScore()).isBetween(0, 100);
            }
        }
    }

    @Test
    void verdictsOnlyNameKnownLocationsOncePerLocation() {
        for (AnalysisContext context : contexts()) {
            Set<Long> known = context.candidateLocations().stream()
                    .map(AnalysisContext.CandidateLocation::id).collect(Collectors.toSet());
            List<Long> ids = analyzer().analyse(context).verdicts().stream().map(LocationVerdict::locationId).toList();
            assertThat(known).containsAll(ids);
            assertThat(ids).doesNotHaveDuplicates();
        }
    }

    @Test
    void mentionsPointToThePostOrToAKnownComment() {
        for (AnalysisContext context : contexts()) {
            Set<Long> commentIds = context.comments().stream()
                    .map(AnalysisContext.CommentInfo::id).collect(Collectors.toSet());
            analyzer().analyse(context).verdicts().stream()
                    .flatMap(v -> v.mentions().stream())
                    .forEach(mention -> {
                        assertThat(mention.type()).isNotNull();
                        assertThat(mention.text()).isNotBlank();
                        if (mention.commentId() != null) {
                            assertThat(commentIds).contains(mention.commentId());
                        }
                    });
        }
    }

    @Test
    void reasoningIsNeverEmpty() {
        for (AnalysisContext context : contexts()) {
            AnalysisResult result = analyzer().analyse(context);
            assertThat(result.reasoning()).isNotBlank();
            assertThat(result.verdicts()).allSatisfy(v -> assertThat(v.reasoning()).isNotBlank());
        }
    }

    @Test
    void passesTheServiceValidation() {
        for (AnalysisContext context : contexts()) {
            AnalysisResult result = analyzer().analyse(context);
            assertThat(GuardedAssociationAnalyzer.validate(result, context)).isSameAs(result);
        }
    }

    @Test
    void doesNotChangeTheContext() {
        AnalysisContext context = disputedContext();
        List<Object> before = Stream.of(context.post(), context.comments(), context.associations(),
                context.candidateLocations()).map(Object.class::cast).toList();

        analyzer().analyse(context);

        assertThat(List.of(context.post(), context.comments(), context.associations(), context.candidateLocations()))
                .isEqualTo(before);
    }
}
