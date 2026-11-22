package com.tounsiguide.locationservice.matching.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code tounsiguide.analysis} (association-api.md, section 5.2).
 *
 * @param provider which {@link AssociationAnalyzer} answers first ("rule-based" by default)
 * @param timeout  a call longer than this is abandoned and the fallback answers
 * @param fallback used when the selected analyzer throws, times out, returns an invalid result or is missing
 */
@ConfigurationProperties("tounsiguide.analysis")
public record AnalysisProperties(String provider, Duration timeout, String fallback) {

    public AnalysisProperties {
        if (provider == null || provider.isBlank()) {
            provider = RuleBasedAssociationAnalyzer.NAME;
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            timeout = Duration.ofSeconds(20);
        }
        if (fallback == null || fallback.isBlank()) {
            fallback = RuleBasedAssociationAnalyzer.NAME;
        }
    }
}
