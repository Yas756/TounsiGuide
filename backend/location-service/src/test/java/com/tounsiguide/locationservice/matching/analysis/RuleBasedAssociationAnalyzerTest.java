package com.tounsiguide.locationservice.matching.analysis;

import com.tounsiguide.locationservice.association.AssociationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the analyzer contract, then checks the rule-based behaviour itself. */
class RuleBasedAssociationAnalyzerTest extends AssociationAnalyzerContractTest {

    private final RuleBasedAssociationAnalyzer analyzer = new RuleBasedAssociationAnalyzer(AssociationProperties.defaults());

    @Override
    protected AssociationAnalyzer analyzer() {
        return analyzer;
    }

    private static LocationVerdict verdict(AnalysisResult result, long locationId) {
        return result.verdicts().stream().filter(v -> v.locationId() == locationId).findFirst().orElseThrow();
    }

    @Test
    void findsNamesInCommentsIgnoringAccentsAndPunctuation() {
        AnalysisResult result = analyzer.analyse(disputedContext());

        LocationVerdict roma = verdict(result, CAFE_ROMA);
        assertThat(roma.mentions()).extracting(FoundMention::commentId).containsExactly(88L, 89L);
        assertThat(roma.mentions()).allSatisfy(m -> assertThat(m.type()).isEqualTo(FoundMention.Type.TEXT_MENTION));
        assertThat(verdict(result, CAFE_ROME).mentions()).isEmpty();
    }

    @Test
    void scoresWithTheFormulaOfSectionThree() {
        AnalysisResult result = analyzer.analyse(disputedContext());

        // Café Roma: S = 2 mentions x 2 = 4, O = 0, P = 2 -> 67
        assertThat(verdict(result, CAFE_ROMA).supportScore()).isEqualTo(67);
        assertThat(verdict(result, CAFE_ROMA).opposeScore()).isEqualTo(33);
        // Café Rome: S = 10 (author tag), O = 3 (dispute) + 2 x 2 (two signals for Café Roma), P = 0 -> 59
        assertThat(verdict(result, CAFE_ROME).supportScore()).isEqualTo(59);
    }

    @Test
    void bestMatchComesFirstAndReasoningExplainsIt() {
        AnalysisResult result = analyzer.analyse(disputedContext());

        assertThat(result.verdicts().getFirst().locationId()).isEqualTo(CAFE_ROMA);
        assertThat(result.reasoning()).startsWith("Meilleure correspondance : « Café Roma »");
        assertThat(verdict(result, CAFE_ROMA).reasoning()).contains("cité dans 2 commentaires");
        assertThat(verdict(result, CAFE_ROME).reasoning())
                .contains("1 choix de l'auteur").contains("1 contestation d'un membre").contains("2 signaux pour d'autres lieux");
        assertThat(result.provider()).isEqualTo("rule-based");
        assertThat(result.providerVersion()).isEqualTo("rules-1");
    }

    @Test
    void findsAliasesInThePostText() {
        AnalysisResult result = analyzer.analyse(mentionInPostContext());

        assertThat(verdict(result, BARDO).mentions()).singleElement().satisfies(m -> {
            assertThat(m.text()).isEqualTo("Bardo");
            assertThat(m.commentId()).isNull();
        });
        assertThat(verdict(result, BARDO).supportScore()).isEqualTo(50);   // 2 / (2 + 2)
    }

    @Test
    void matchesWholeWordsOnly() {
        AnalysisContext context = new AnalysisContext(post("Bardolino wine and romance", null), List.of(), List.of(),
                List.of(bardo(), cafeRoma()), List.of(), List.of(), AnalysisContext.Trigger.POST_CHANGED);

        AnalysisResult result = analyzer.analyse(context);

        assertThat(result.verdicts()).allSatisfy(v -> assertThat(v.mentions()).isEmpty());
    }

    @Test
    void aGenericWordAloneIsNotAMentionButTheFullNameIs() {
        AnalysisContext.CandidateLocation medina = new AnalysisContext.CandidateLocation(4L, "Médina de Tunis",
                List.of("Medina", "La Médina"), "Tunis", 36.79, 10.17, "attraction");
        AnalysisContext generic = new AnalysisContext(post("Un super café avec terrasse, près de la médina.", null),
                List.of(), List.of(), List.of(medina), List.of(), List.of(), AnalysisContext.Trigger.POST_CHANGED);
        AnalysisContext full = new AnalysisContext(post("Balade dans la Médina de Tunis", null),
                List.of(), List.of(), List.of(medina), List.of(), List.of(), AnalysisContext.Trigger.POST_CHANGED);

        assertThat(analyzer.analyse(generic).verdicts()).allSatisfy(v -> assertThat(v.mentions()).isEmpty());
        assertThat(verdict(analyzer.analyse(full), 4L).mentions()).singleElement()
                .satisfies(m -> assertThat(m.text()).isEqualTo("Médina de Tunis"));
    }

    @Test
    void nothingToAnalyseStillGivesAReason() {
        AnalysisResult result = analyzer.analyse(emptyContext());

        assertThat(result.verdicts()).isEmpty();
        assertThat(result.reasoning()).contains("Aucun lieu candidat");
    }
}
