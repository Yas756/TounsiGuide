package com.tounsiguide.locationservice.matching;

import com.tounsiguide.locationservice.location.Location;

/**
 * Decides how well a Location answers a search and whether two descriptions are the same real place.
 *
 * <p>Sprint 2 ships {@link RuleBasedMatchingProvider} (normalized name, alias, city, distance) so the
 * platform works with no AI (acceptance criterion A7). The future AI module for associations implements
 * this interface too and is selected with {@code tounsiguide.matching.provider}; nothing else changes.</p>
 */
public interface MatchingProvider {

    /** Identifier used in {@code tounsiguide.matching.provider}. */
    String name();

    /** How well {@code location} answers {@code query}, from 0 (no match) to 1 (exact). */
    double score(String query, Location location);

    /** True when the candidate and the existing Location describe the same real place. */
    boolean isSamePlace(PlaceCandidate candidate, Location existing);
}
