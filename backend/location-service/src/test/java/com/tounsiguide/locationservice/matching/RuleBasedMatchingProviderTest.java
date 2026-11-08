package com.tounsiguide.locationservice.matching;

import com.tounsiguide.locationservice.location.Location;
import com.tounsiguide.locationservice.location.LocationSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedMatchingProviderTest {

    private final RuleBasedMatchingProvider matcher = new RuleBasedMatchingProvider();

    private static Location bardo() {
        Location location = new Location("Musée national du Bardo", "Tunis", null, 36.8095, 10.1347,
                "museum", LocationSource.MAP, "osm:W1", "u1");
        location.addAlias("Bardo");
        return location;
    }

    @Test
    void normalizerRemovesAccentsCaseAndPunctuation() {
        assertThat(TextNormalizer.normalize("  Sidi-Bou  Saïd ! ")).isEqualTo("sidi bou said");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void exactNameScoresHigherThanPartialName() {
        Location location = bardo();
        double exact = matcher.score("musee national du bardo", location);
        double prefix = matcher.score("Musée nat", location);
        double substring = matcher.score("national", location);

        assertThat(exact).isEqualTo(1.0);
        assertThat(prefix).isGreaterThan(substring).isLessThan(exact);
        assertThat(substring).isPositive();
    }

    @Test
    void aliasMatchesEvenWhenNameDoesNotStartWithIt() {
        assertThat(matcher.score("bardo", bardo())).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void cityAndAllWordsAreWeakerMatches() {
        Location location = bardo();
        assertThat(matcher.score("tunis", location)).isEqualTo(0.4);
        assertThat(matcher.score("bardo tunis", location)).isEqualTo(0.5);
        assertThat(matcher.score("sousse", location)).isZero();
    }

    @Test
    void directoryEntriesGetASmallBonus() {
        Location curated = new Location("Café Example", "Tunis", null, null, null, null,
                LocationSource.DIRECTORY, null, "seed");
        Location picked = new Location("Café Example", "Tunis", null, null, null, null,
                LocationSource.MAP, null, "u1");
        assertThat(matcher.score("cafe", curated)).isGreaterThan(matcher.score("cafe", picked));
    }

    @Test
    void samePlaceByExternalIdOrSameNameNearby() {
        Location existing = bardo();

        assertThat(matcher.isSamePlace(new PlaceCandidate("Other name", null, 0.0, 0.0, "osm:W1"), existing)).isTrue();
        assertThat(matcher.isSamePlace(
                new PlaceCandidate("Musee National du Bardo", null, 36.8100, 10.1350, null), existing)).isTrue();
        // Same name 5 km away: another place (e.g. another branch of a café chain).
        assertThat(matcher.isSamePlace(
                new PlaceCandidate("Musée national du Bardo", null, 36.85, 10.18, null), existing)).isFalse();
        assertThat(matcher.isSamePlace(new PlaceCandidate("Bardo", null, 36.8095, 10.1347, null), existing)).isFalse();
    }

    @Test
    void distanceIsComputedInMeters() {
        // Tunis to Carthage, about 15 km.
        assertThat(GeoDistance.meters(36.8008, 10.1800, 36.8529, 10.3233)).isBetween(13_000.0, 15_000.0);
    }
}
