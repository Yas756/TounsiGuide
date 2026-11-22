package com.tounsiguide.locationservice.matching.analysis;

import java.util.List;

/**
 * What every analyzer returns (association-api.md, section 5.1).
 *
 * @param provider        "rule-based", "ai", ...: the analyzer that actually answered (the fallback when the AI failed)
 * @param providerVersion model or rules version, kept for audit
 * @param verdicts        one per Location considered
 * @param reasoning       explanation shown to the admin
 */
public record AnalysisResult(
        String provider,
        String providerVersion,
        List<LocationVerdict> verdicts,
        String reasoning
) {

    public AnalysisResult {
        verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
    }
}
