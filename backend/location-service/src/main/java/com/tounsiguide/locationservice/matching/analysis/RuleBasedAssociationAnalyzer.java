package com.tounsiguide.locationservice.matching.analysis;

import com.tounsiguide.locationservice.association.AssociationProperties;
import com.tounsiguide.locationservice.association.AssociationScorer;
import com.tounsiguide.locationservice.association.EvidenceType;
import com.tounsiguide.locationservice.association.Origin;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Sprint 2 analyzer, no AI (acceptance criterion A7): finds the names and aliases of the candidate
 * Locations in the post and comment text with {@code TextNormalizer}, and scores each Location with the
 * formula of section 3 (existing evidence + the mentions found).
 *
 * <p>It is always registered, because it is also the fallback when another analyzer fails
 * ({@code tounsiguide.analysis.fallback}). Which analyzer is used first is decided by
 * {@link GuardedAssociationAnalyzer} from {@code tounsiguide.analysis.provider}.</p>
 */
@Component
public class RuleBasedAssociationAnalyzer implements AssociationAnalyzer {

    public static final String NAME = "rule-based";
    public static final String VERSION = "rules-1";

    private final AssociationProperties properties;

    public RuleBasedAssociationAnalyzer(AssociationProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public AnalysisResult analyse(AnalysisContext context) {
        int mentionWeight = properties.weight(EvidenceType.TEXT_MENTION);
        int competingWeight = properties.weight(EvidenceType.COMPETING_CLAIM);

        Map<Long, List<FoundMention>> mentions = new LinkedHashMap<>();
        for (AnalysisContext.CandidateLocation candidate : context.candidateLocations()) {
            mentions.put(candidate.id(), findMentions(candidate, context));
        }

        // Competing signals per Location: member suggestions and mentions that speak for that Location.
        Map<Long, Integer> competingSignals = new LinkedHashMap<>();
        for (AnalysisContext.CandidateLocation candidate : context.candidateLocations()) {
            int signals = mentions.get(candidate.id()).size();
            AnalysisContext.AssociationInfo association = associationOf(context, candidate.id());
            if (association != null) {
                signals += (int) association.evidence().stream()
                        .filter(e -> isType(e, EvidenceType.MEMBER_SUGGESTION) || isType(e, EvidenceType.PAGE_MENTION))
                        .count();
            }
            competingSignals.put(candidate.id(), signals);
        }

        List<LocationVerdict> verdicts = new ArrayList<>();
        for (AnalysisContext.CandidateLocation candidate : context.candidateLocations()) {
            AnalysisContext.AssociationInfo association = associationOf(context, candidate.id());
            List<FoundMention> found = mentions.get(candidate.id());

            int support = 0;
            int oppose = 0;
            Map<String, Integer> evidenceCounts = new LinkedHashMap<>();
            if (association != null) {
                for (AnalysisContext.EvidenceInfo evidence : association.evidence()) {
                    // Text mentions and competing claims are recomputed from the text below.
                    if (isType(evidence, EvidenceType.TEXT_MENTION) || isType(evidence, EvidenceType.COMPETING_CLAIM)) {
                        continue;
                    }
                    if ("SUPPORT".equals(evidence.direction())) {
                        support += evidence.weight();
                    } else {
                        oppose += evidence.weight();
                    }
                    evidenceCounts.merge(evidence.type(), 1, Integer::sum);
                }
            }
            support += mentionWeight * found.size();
            int otherSignals = competingSignals.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(candidate.id()))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
            oppose += competingWeight * otherSignals;

            boolean authorOrigin = association != null && Origin.AUTHOR.name().equals(association.origin());
            int prior = authorOrigin ? 0 : properties.priorWeight();
            int total = support + oppose + prior;
            int supportScore = AssociationScorer.score(support, oppose, prior);
            int opposeScore = total == 0 ? 0 : 100 - supportScore;

            verdicts.add(new LocationVerdict(candidate.id(), supportScore, opposeScore, found,
                    reasoning(candidate, found, evidenceCounts, otherSignals, supportScore)));
        }
        verdicts.sort(Comparator.comparingInt(LocationVerdict::supportScore).reversed()
                .thenComparingLong(LocationVerdict::locationId));
        return new AnalysisResult(NAME, VERSION, verdicts, overallReasoning(verdicts, context));
    }

    /** One mention per text (post or comment) that names the Location or one of its aliases. */
    private static List<FoundMention> findMentions(AnalysisContext.CandidateLocation candidate, AnalysisContext context) {
        List<String> names = Stream.concat(Stream.of(candidate.name()), candidate.aliases().stream())
                .filter(Objects::nonNull)
                .toList();
        List<FoundMention> found = new ArrayList<>();
        if (context.post() != null) {
            MentionFinder.firstMentioned(context.post().text(), names)
                    .ifPresent(name -> found.add(new FoundMention(name, null, FoundMention.Type.TEXT_MENTION)));
        }
        for (AnalysisContext.CommentInfo comment : context.comments()) {
            MentionFinder.firstMentioned(comment.text(), names)
                    .ifPresent(name -> found.add(new FoundMention(name, comment.id(), FoundMention.Type.TEXT_MENTION)));
        }
        return found;
    }

    private static AnalysisContext.AssociationInfo associationOf(AnalysisContext context, long locationId) {
        return context.associations().stream()
                .filter(a -> a.locationId() == locationId)
                .findFirst()
                .orElse(null);
    }

    private static boolean isType(AnalysisContext.EvidenceInfo evidence, EvidenceType type) {
        return type.name().equals(evidence.type());
    }

    private static String reasoning(AnalysisContext.CandidateLocation candidate, List<FoundMention> found,
                                    Map<String, Integer> evidenceCounts, int otherSignals, int supportScore) {
        List<String> parts = new ArrayList<>();
        long inPost = found.stream().filter(m -> m.commentId() == null).count();
        long inComments = found.size() - inPost;
        if (inPost > 0 && inComments > 0) {
            parts.add("cité dans la publication et dans " + count(inComments, "commentaire", "commentaires"));
        } else if (inPost > 0) {
            parts.add("cité dans la publication");
        } else if (inComments > 0) {
            parts.add("cité dans " + count(inComments, "commentaire", "commentaires"));
        } else {
            parts.add("pas cité dans le texte");
        }
        evidenceCounts.forEach((type, n) -> parts.add(evidence(type, n)));
        if (otherSignals > 0) {
            parts.add(count(otherSignals, "signal", "signaux") + " pour d'autres lieux");
        }
        return "« " + candidate.name() + " » : " + String.join(" ; ", parts) + " (soutien " + supportScore + "/100).";
    }

    private static String overallReasoning(List<LocationVerdict> verdicts, AnalysisContext context) {
        if (verdicts.isEmpty()) {
            return "Aucun lieu candidat : la publication n'a pas de lieu choisi et aucun lieu de l'annuaire n'est cité "
                    + "dans la publication ou ses commentaires.";
        }
        Map<Long, String> names = new LinkedHashMap<>();
        context.candidateLocations().forEach(c -> names.put(c.id(), c.name()));
        LocationVerdict best = verdicts.getFirst();
        StringBuilder text = new StringBuilder("Meilleure correspondance : « ").append(names.get(best.locationId()))
                .append(" » (soutien ").append(best.supportScore()).append("/100).");
        verdicts.forEach(v -> text.append(' ').append(v.reasoning()));
        return text.toString();
    }

    /** French wording of an evidence count, e.g. "2 suggestions de membres" (shown to the admin). */
    private static String evidence(String type, long n) {
        return switch (type) {
            case "AUTHOR_TAG" -> count(n, "choix de l'auteur", "choix de l'auteur");
            case "AUTHOR_CONFIRMED" -> count(n, "confirmation de l'auteur", "confirmations de l'auteur");
            case "PAGE_MENTION" -> count(n, "mention de page", "mentions de page");
            case "MEMBER_SUGGESTION" -> count(n, "suggestion d'un membre", "suggestions de membres");
            case "MEMBER_DISPUTE" -> count(n, "contestation d'un membre", "contestations de membres");
            case "BUSINESS_DISPUTE" -> count(n, "contestation du gérant", "contestations du gérant");
            default -> n + " × " + type;
        };
    }

    private static String count(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
