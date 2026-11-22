package com.tounsiguide.locationservice.matching.analysis;

/**
 * The single plug-in point of the association analysis (association-api.md, section 5.1).
 *
 * <p>Sprint 2 ships {@link RuleBasedAssociationAnalyzer}. Adding the AI later means writing one class
 * that implements this interface, annotating it
 * {@code @ConditionalOnProperty(name = "tounsiguide.analysis.provider", havingValue = "ai")}, passing
 * {@code AssociationAnalyzerContractTest}, and setting {@code tounsiguide.analysis.provider=ai}.</p>
 *
 * <p>An analyzer only reads the {@link AnalysisContext}: it never calls another service or the database,
 * and its verdict never changes a status by itself.</p>
 */
public interface AssociationAnalyzer {

    /** Identifier used in {@code tounsiguide.analysis.provider} ("rule-based", "ai", ...). */
    String name();

    AnalysisResult analyse(AnalysisContext context);
}
