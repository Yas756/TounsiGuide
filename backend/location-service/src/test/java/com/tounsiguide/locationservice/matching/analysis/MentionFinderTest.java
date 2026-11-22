package com.tounsiguide.locationservice.matching.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Generic words never count as a mention on their own (2026-11-15). */
class MentionFinderTest {

    @Test
    void genericWordsWithOrWithoutArticleAreGeneric() {
        assertThat(MentionFinder.isGeneric("medina")).isTrue();
        assertThat(MentionFinder.isGeneric("la medina")).isTrue();
        assertThat(MentionFinder.isGeneric("plage")).isTrue();
        assertThat(MentionFinder.isGeneric("cafe")).isTrue();
        assertThat(MentionFinder.isGeneric("medina de tunis")).isFalse();
        assertThat(MentionFinder.isGeneric("medina restaurant")).isFalse();
        assertThat(MentionFinder.isGeneric("bardo")).isFalse();
    }

    @Test
    void aGenericAliasIsSkippedButTheFullNameOrAnotherAliasStillMatches() {
        List<String> medina = List.of("Médina de Tunis", "Medina", "La Médina");

        assertThat(MentionFinder.firstMentioned("Un café près de la médina", medina)).isEmpty();
        assertThat(MentionFinder.firstMentioned("Balade dans la médina de Tunis", medina)).contains("Médina de Tunis");
        assertThat(MentionFinder.firstMentioned("I think this was at Medina Restaurant", List.of("Medina Restaurant")))
                .contains("Medina Restaurant");
    }
}
