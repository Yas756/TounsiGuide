package com.tounsiguide.locationservice.matching;

import com.tounsiguide.locationservice.location.Location;
import com.tounsiguide.locationservice.location.LocationAlias;
import com.tounsiguide.locationservice.location.LocationSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * Matching with simple, explainable rules: normalized name, aliases, city and distance. No AI.
 */
@Component
@ConditionalOnProperty(name = "tounsiguide.matching.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedMatchingProvider implements MatchingProvider {

    /** Two descriptions with the same name closer than this are the same place. */
    static final double SAME_PLACE_RADIUS_METERS = 150;

    private static final double DIRECTORY_BONUS = 0.05;

    @Override
    public String name() {
        return "rule-based";
    }

    @Override
    public double score(String query, Location location) {
        String q = TextNormalizer.normalize(query);
        if (q.isEmpty()) {
            return 0;
        }
        String name = location.getNormalizedName();
        String city = Objects.requireNonNullElse(location.getNormalizedCity(), "");

        double best = textScore(q, name, 1.0);
        for (LocationAlias alias : location.getAliases()) {
            best = Math.max(best, textScore(q, alias.getNormalizedAlias(), 0.95));
        }
        if (!city.isEmpty()) {
            if (city.equals(q)) {
                best = Math.max(best, 0.4);
            } else if (city.contains(q)) {
                best = Math.max(best, 0.3);
            }
        }
        if (best == 0 && allWordsFound(q, location)) {
            // "cafe tunis": every word appears somewhere in the name, an alias or the city.
            best = 0.5;
        }
        if (best > 0 && location.getSource() == LocationSource.DIRECTORY) {
            best += DIRECTORY_BONUS; // curated entries first when the text matches equally well
        }
        return Math.min(best, 1.0);
    }

    @Override
    public boolean isSamePlace(PlaceCandidate candidate, Location existing) {
        if (candidate.externalId() != null && candidate.externalId().equals(existing.getExternalId())) {
            return true;
        }
        if (!TextNormalizer.normalize(candidate.name()).equals(existing.getNormalizedName())) {
            return false;
        }
        boolean candidateHasPoint = candidate.latitude() != null && candidate.longitude() != null;
        if (candidateHasPoint && existing.hasCoordinates()) {
            return GeoDistance.meters(candidate.latitude(), candidate.longitude(),
                    existing.getLatitude(), existing.getLongitude()) <= SAME_PLACE_RADIUS_METERS;
        }
        // Without coordinates, the same name in the same city is the best evidence we have.
        String city = TextNormalizer.normalize(candidate.city());
        return !city.isEmpty() && city.equals(existing.getNormalizedCity());
    }

    /** exact = full weight, prefix = 85 %, word prefix = 75 %, substring = 60 %. */
    private static double textScore(String q, String text, double weight) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        if (text.equals(q)) {
            return weight;
        }
        if (text.startsWith(q)) {
            return 0.85 * weight;
        }
        if (text.contains(" " + q)) {
            return 0.75 * weight;
        }
        if (text.contains(q)) {
            return 0.6 * weight;
        }
        return 0;
    }

    private static boolean allWordsFound(String q, Location location) {
        StringBuilder haystack = new StringBuilder(location.getNormalizedName());
        if (location.getNormalizedCity() != null) {
            haystack.append(' ').append(location.getNormalizedCity());
        }
        location.getAliases().forEach(a -> haystack.append(' ').append(a.getNormalizedAlias()));
        String text = haystack.toString();
        return Arrays.stream(q.split(" ")).allMatch(text::contains);
    }
}
