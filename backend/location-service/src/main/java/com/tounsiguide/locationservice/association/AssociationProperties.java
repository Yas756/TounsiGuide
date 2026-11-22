package com.tounsiguide.locationservice.association;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Settings under {@code tounsiguide.association} (association-api.md, section 9).
 *
 * @param disputeThreshold a CONFIRMED association whose score falls below this becomes DISPUTED
 * @param priorWeight      prior added to the denominator of the score for every origin except AUTHOR
 * @param weights          weight per evidence type, e.g. {@code weights.MEMBER_DISPUTE=3} (case, '-' and '_' ignored);
 *                         missing types use the default
 * @param migrateOnStartup turn existing {@code Post.locationId} values into AUTHOR associations at startup
 */
@ConfigurationProperties("tounsiguide.association")
public record AssociationProperties(
        Integer disputeThreshold,
        Integer priorWeight,
        Map<String, Integer> weights,
        Boolean migrateOnStartup
) {

    public static final int DEFAULT_DISPUTE_THRESHOLD = 75;
    public static final int DEFAULT_PRIOR_WEIGHT = 2;

    public AssociationProperties {
        if (disputeThreshold == null) {
            disputeThreshold = DEFAULT_DISPUTE_THRESHOLD;
        }
        if (priorWeight == null) {
            priorWeight = DEFAULT_PRIOR_WEIGHT;
        }
        weights = weights == null ? Map.of() : Map.copyOf(weights);
        if (migrateOnStartup == null) {
            migrateOnStartup = true;
        }
    }

    /** Defaults everywhere (used by tests). */
    public static AssociationProperties defaults() {
        return new AssociationProperties(null, null, null, null);
    }

    /** Weight of a type: the configured value (key matched case-insensitively, '-' or '_'), else the default. */
    public int weight(EvidenceType type) {
        return resolvedWeights().get(type);
    }

    public Map<EvidenceType, Integer> resolvedWeights() {
        Map<EvidenceType, Integer> resolved = new EnumMap<>(EvidenceType.class);
        for (EvidenceType type : EvidenceType.values()) {
            resolved.put(type, type.defaultWeight());
        }
        weights.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            // Spring may drop '_' from map keys ("AUTHOR_TAG" -> "AUTHORTAG"): compare letters only.
            String name = lettersOnly(key);
            for (EvidenceType type : EvidenceType.values()) {
                if (lettersOnly(type.name()).equals(name)) {
                    resolved.put(type, Math.max(0, value));
                }
            }
        });
        return resolved;
    }

    private static String lettersOnly(String key) {
        return key.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
