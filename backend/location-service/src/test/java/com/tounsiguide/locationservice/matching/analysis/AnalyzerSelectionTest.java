package com.tounsiguide.locationservice.matching.analysis;

import com.tounsiguide.locationservice.association.AssociationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "one class, one property" promise of section 5.3 with the real Spring wiring: an analyzer annotated
 * like the future AI is ignored until {@code tounsiguide.analysis.provider=ai}, then answers first.
 */
class AnalyzerSelectionTest {

    /** Stands in for the future AiAssociationAnalyzer: same annotation, nothing else registered. */
    @ConditionalOnProperty(name = "tounsiguide.analysis.provider", havingValue = "ai")
    static class FakeAiAnalyzer implements AssociationAnalyzer {
        @Override
        public String name() {
            return "ai";
        }

        @Override
        public AnalysisResult analyse(AnalysisContext context) {
            return new AnalysisResult("ai", "fake-model", List.of(), "AI reasoning");
        }
    }

    @Configuration
    @EnableConfigurationProperties({AnalysisProperties.class, AssociationProperties.class})
    @Import({RuleBasedAssociationAnalyzer.class, FakeAiAnalyzer.class, GuardedAssociationAnalyzer.class})
    static class Wiring {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Wiring.class);

    @Test
    void ruleBasedByDefaultAndTheAiClassIsNotEvenCreated() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(FakeAiAnalyzer.class);
            assertThat(context.getBean(GuardedAssociationAnalyzer.class).selectedName()).isEqualTo("rule-based");
        });
    }

    @Test
    void settingOnePropertySwitchesToTheAi() {
        runner.withPropertyValues("tounsiguide.analysis.provider=ai").run(context -> {
            GuardedAssociationAnalyzer analyzer = context.getBean(GuardedAssociationAnalyzer.class);
            assertThat(analyzer.selectedName()).isEqualTo("ai");
            assertThat(analyzer.analyse(AssociationAnalyzerContractTest.emptyContext()).provider()).isEqualTo("ai");
        });
    }
}
