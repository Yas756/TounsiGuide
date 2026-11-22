package com.tounsiguide.locationservice.matching.analysis;

import com.tounsiguide.locationservice.association.AssociationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Selection, fallback and timeout of section 5.2 (A7: everything works when the AI fails or is off). */
class GuardedAssociationAnalyzerTest {

    private final RuleBasedAssociationAnalyzer ruleBased = new RuleBasedAssociationAnalyzer(AssociationProperties.defaults());
    private final List<GuardedAssociationAnalyzer> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(GuardedAssociationAnalyzer::destroy);
    }

    private GuardedAssociationAnalyzer guarded(String provider, Duration timeout, AssociationAnalyzer... others) {
        List<AssociationAnalyzer> analyzers = new ArrayList<>(List.of(others));
        analyzers.add(ruleBased);
        GuardedAssociationAnalyzer guarded = new GuardedAssociationAnalyzer(analyzers,
                new AnalysisProperties(provider, timeout, "rule-based"));
        created.add(guarded);
        return guarded;
    }

    private static AssociationAnalyzer fake(String name, Function<AnalysisContext, AnalysisResult> behaviour) {
        return new AssociationAnalyzer() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public AnalysisResult analyse(AnalysisContext context) {
                return behaviour.apply(context);
            }
        };
    }

    @Test
    void selectedAnalyzerAnswersWhenItWorks() {
        AssociationAnalyzer ai = fake("ai", context -> new AnalysisResult("ai", "model-x", List.of(), "AI reasoning"));

        AnalysisResult result = guarded("ai", Duration.ofSeconds(5), ai).analyse(AssociationAnalyzerContractTest.disputedContext());

        assertThat(result.provider()).isEqualTo("ai");
    }

    @Test
    void failingAnalyzerFallsBackToRuleBased() {
        AssociationAnalyzer ai = fake("ai", context -> {
            throw new IllegalStateException("model down");
        });

        AnalysisResult result = guarded("ai", Duration.ofSeconds(5), ai).analyse(AssociationAnalyzerContractTest.disputedContext());

        assertThat(result.provider()).isEqualTo("rule-based");
        assertThat(result.verdicts()).isNotEmpty();
    }

    @Test
    void slowAnalyzerIsAbandonedAfterTheTimeout() {
        AssociationAnalyzer slow = fake("ai", context -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new AnalysisResult("ai", "slow", List.of(), "late");
        });

        long start = System.nanoTime();
        AnalysisResult result = guarded("ai", Duration.ofMillis(200), slow).analyse(AssociationAnalyzerContractTest.disputedContext());

        assertThat(result.provider()).isEqualTo("rule-based");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void invalidResultFallsBack() {
        AssociationAnalyzer liar = fake("ai", context -> new AnalysisResult("ai", "v1",
                List.of(new LocationVerdict(999L, 150, 0, List.of(), "unknown place")), "bad"));

        AnalysisResult result = guarded("ai", Duration.ofSeconds(5), liar).analyse(AssociationAnalyzerContractTest.disputedContext());

        assertThat(result.provider()).isEqualTo("rule-based");
    }

    @Test
    void missingProviderUsesTheFallback() {
        GuardedAssociationAnalyzer guarded = guarded("ai", Duration.ofSeconds(5));

        assertThat(guarded.selectedName()).isEqualTo("rule-based");
        assertThat(guarded.analyse(AssociationAnalyzerContractTest.emptyContext()).provider()).isEqualTo("rule-based");
    }

    @Test
    void failingFallbackGivesAnEmptyResultInsteadOfAnError() {
        AssociationAnalyzer brokenRules = fake("rule-based", context -> {
            throw new IllegalStateException("broken");
        });
        GuardedAssociationAnalyzer guarded = new GuardedAssociationAnalyzer(List.of(brokenRules),
                new AnalysisProperties("rule-based", Duration.ofSeconds(1), "rule-based"));
        created.add(guarded);

        AnalysisResult result = guarded.analyse(AssociationAnalyzerContractTest.emptyContext());

        assertThat(result.verdicts()).isEmpty();
        assertThat(result.reasoning()).startsWith("Analyse impossible");
    }
}
