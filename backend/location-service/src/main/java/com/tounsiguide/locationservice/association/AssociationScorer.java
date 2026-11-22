package com.tounsiguide.locationservice.association;

import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Confidence score of section 3: {@code score = 100 × S / (S + O + P)}, rounded to the nearest integer.
 * S = SUPPORT weights, O = OPPOSE weights, P = 0 for an AUTHOR association and the prior for any other origin.
 */
@Component
public class AssociationScorer {

    private final AssociationProperties properties;

    public AssociationScorer(AssociationProperties properties) {
        this.properties = properties;
    }

    public int score(Association association) {
        return score(association.getOrigin(), association.getEvidence());
    }

    public int score(Origin origin, Collection<AssociationEvidence> evidence) {
        int support = 0;
        int oppose = 0;
        for (AssociationEvidence item : evidence) {
            if (item.getDirection() == Direction.SUPPORT) {
                support += item.getWeight();
            } else {
                oppose += item.getWeight();
            }
        }
        return score(support, oppose, prior(origin));
    }

    public int prior(Origin origin) {
        return origin == Origin.AUTHOR ? 0 : properties.priorWeight();
    }

    /** The formula itself. With no evidence and no prior the score is 0. */
    public static int score(int support, int oppose, int prior) {
        int total = support + oppose + prior;
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(100.0 * support / total);
    }
}
