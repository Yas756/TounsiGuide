/**
 * Association analysis: the single plug-in point for the future AI (docs/api/association-api.md, section 5).
 *
 * <h2>Adding the AI: one class, one property</h2>
 * <ol>
 *   <li>Create {@code AiAssociationAnalyzer implements AssociationAnalyzer} in this package:
 *   <pre>{@code
 * @Component
 * @ConditionalOnProperty(name = "tounsiguide.analysis.provider", havingValue = "ai")
 * public class AiAssociationAnalyzer implements AssociationAnalyzer {
 *     public String name() { return "ai"; }
 *     public AnalysisResult analyse(AnalysisContext context) {
 *         // Send the context (post, comments, associations with evidence, candidate Locations, history)
 *         // to any model, then map its answer to verdicts. Return provider "ai" and the model name as version.
 *     }
 * }
 *   }</pre>
 *   Its own settings (API key, model, URL) go in its own {@code @ConfigurationProperties}, e.g.
 *   {@code tounsiguide.analysis.ai.*}. Never commit a key: read it from an environment variable.</li>
 *   <li>Write {@code class AiAssociationAnalyzerTest extends AssociationAnalyzerContractTest} (one method returning
 *   the analyzer) and make it pass.</li>
 *   <li>Set {@code tounsiguide.analysis.provider=ai} in application.properties.</li>
 * </ol>
 *
 * <h2>What the AI does not have to care about</h2>
 * <ul>
 *   <li><b>Data access</b>: {@link com.tounsiguide.locationservice.matching.analysis.AnalysisContext} is complete;
 *   the analyzer never calls another service or the database. All records serialize to JSON as they are.</li>
 *   <li><b>Failures and slowness</b>: {@link com.tounsiguide.locationservice.matching.analysis.GuardedAssociationAnalyzer}
 *   abandons a call after {@code tounsiguide.analysis.timeout} (20s) and uses the rule-based fallback when the AI
 *   throws, times out or returns an invalid result. The result says which provider answered.</li>
 *   <li><b>Waiting users</b>: every analysis runs in the background ({@code @Async}).</li>
 *   <li><b>Decisions</b>: the service turns each {@link com.tounsiguide.locationservice.matching.analysis.FoundMention}
 *   into evidence and stores the verdict for the admin. A verdict never changes a status; only people decide.</li>
 *   <li><b>Endpoints, database, frontend, other services</b>: nothing changes.</li>
 * </ul>
 * Turning the AI off again is the same property set back to {@code rule-based}.
 */
package com.tounsiguide.locationservice.matching.analysis;
