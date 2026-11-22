package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.location.Location;
import com.tounsiguide.locationservice.location.LocationSource;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Score formula and the example table of association-api.md, section 3. */
class AssociationScorerTest {

    private final AssociationProperties properties = AssociationProperties.defaults();
    private final AssociationScorer scorer = new AssociationScorer(properties);
    private final Location cafe = new Location("Café Roma", "Tunis", null, 36.8, 10.18, "cafe",
            LocationSource.DIRECTORY, null, "seed");

    private Association with(Origin origin, EvidenceType... types) {
        Association association = new Association(1L, cafe, origin, Status.PROPOSED, "u1", "Sarra");
        int actor = 0;
        for (EvidenceType type : types) {
            association.addEvidence(type, properties.weight(type), "m" + actor++, "Member", null, null);
        }
        return association;
    }

    @Test
    void formulaIsHundredTimesSupportOverSupportOpposeAndPrior() {
        assertThat(AssociationScorer.score(10, 0, 0)).isEqualTo(100);
        assertThat(AssociationScorer.score(3, 0, 2)).isEqualTo(60);
        assertThat(AssociationScorer.score(10, 3, 0)).isEqualTo(77);   // 76.9 rounded
        assertThat(AssociationScorer.score(0, 0, 0)).isZero();
        assertThat(AssociationScorer.score(0, 3, 2)).isZero();
    }

    @Test
    void priorIsZeroForAuthorAndConfiguredForOtherOrigins() {
        assertThat(scorer.prior(Origin.AUTHOR)).isZero();
        assertThat(scorer.prior(Origin.MEMBER)).isEqualTo(2);
        assertThat(scorer.prior(Origin.MENTION)).isEqualTo(2);
        assertThat(scorer.prior(Origin.ADMIN)).isEqualTo(2);
    }

    @Test
    void reproducesTheContractTable() {
        assertThat(scorer.score(with(Origin.AUTHOR, EvidenceType.AUTHOR_TAG))).isEqualTo(100);
        assertThat(scorer.score(with(Origin.AUTHOR, EvidenceType.AUTHOR_TAG, EvidenceType.BUSINESS_DISPUTE))).isEqualTo(50);
        assertThat(scorer.score(with(Origin.AUTHOR, EvidenceType.AUTHOR_TAG, EvidenceType.MEMBER_DISPUTE))).isEqualTo(77);
        assertThat(scorer.score(with(Origin.MENTION, EvidenceType.TEXT_MENTION))).isEqualTo(50);
        assertThat(scorer.score(with(Origin.MENTION, EvidenceType.TEXT_MENTION, EvidenceType.TEXT_MENTION))).isEqualTo(67);
        assertThat(scorer.score(with(Origin.MEMBER, EvidenceType.MEMBER_SUGGESTION, EvidenceType.MEMBER_SUGGESTION))).isEqualTo(75);
        assertThat(scorer.score(with(Origin.MENTION, EvidenceType.PAGE_MENTION))).isEqualTo(75);
    }

    @Test
    void competingClaimOpposes() {
        assertThat(scorer.score(with(Origin.AUTHOR, EvidenceType.AUTHOR_TAG, EvidenceType.COMPETING_CLAIM))).isEqualTo(83);
    }

    @Test
    void weightsComeFromConfigurationWithDefaultsForMissingTypes() {
        // Spring drops '_' from map keys, so both spellings must work.
        AssociationProperties custom = new AssociationProperties(80, 4,
                Map.of("MEMBERDISPUTE", 5, "text-mention", 1), null);

        assertThat(custom.weight(EvidenceType.MEMBER_DISPUTE)).isEqualTo(5);
        assertThat(custom.weight(EvidenceType.TEXT_MENTION)).isEqualTo(1);
        assertThat(custom.weight(EvidenceType.AUTHOR_TAG)).isEqualTo(10);
        assertThat(custom.disputeThreshold()).isEqualTo(80);
        assertThat(new AssociationScorer(custom).prior(Origin.MEMBER)).isEqualTo(4);
    }

    @Test
    void defaultsMatchTheContract() {
        assertThat(properties.disputeThreshold()).isEqualTo(75);
        assertThat(properties.priorWeight()).isEqualTo(2);
        assertThat(properties.resolvedWeights()).containsEntry(EvidenceType.AUTHOR_TAG, 10)
                .containsEntry(EvidenceType.AUTHOR_CONFIRMED, 10)
                .containsEntry(EvidenceType.PAGE_MENTION, 6)
                .containsEntry(EvidenceType.MEMBER_SUGGESTION, 3)
                .containsEntry(EvidenceType.TEXT_MENTION, 2)
                .containsEntry(EvidenceType.COMPETING_CLAIM, 2)
                .containsEntry(EvidenceType.MEMBER_DISPUTE, 3)
                .containsEntry(EvidenceType.BUSINESS_DISPUTE, 10);
    }
}
