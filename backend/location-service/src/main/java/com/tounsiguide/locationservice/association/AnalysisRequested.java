package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.matching.analysis.AnalysisContext;

/** Published when a post must be analysed again; handled in the background after the transaction commits. */
public record AnalysisRequested(long postId, AnalysisContext.Trigger trigger) {
}
