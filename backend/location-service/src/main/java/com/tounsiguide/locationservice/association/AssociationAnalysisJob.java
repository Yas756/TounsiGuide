package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.community.CommunityClient;
import com.tounsiguide.locationservice.community.CommunityClient.CommentSnapshot;
import com.tounsiguide.locationservice.community.CommunityClient.PostSnapshot;
import com.tounsiguide.locationservice.matching.analysis.AnalysisContext;
import com.tounsiguide.locationservice.matching.analysis.AnalysisResult;
import com.tounsiguide.locationservice.matching.analysis.GuardedAssociationAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * Background work: the Community change ping and every analysis run here (section 5), so publishing,
 * commenting and disputing never wait for the matcher. Failures are logged; an admin rescan repairs them.
 */
@Component
public class AssociationAnalysisJob {

    private static final Logger log = LoggerFactory.getLogger(AssociationAnalysisJob.class);

    private final CommunityClient communityClient;
    private final AssociationService associationService;
    private final AnalysisContextBuilder contextBuilder;
    private final GuardedAssociationAnalyzer analyzer;

    public AssociationAnalysisJob(
            CommunityClient communityClient,
            AssociationService associationService,
            AnalysisContextBuilder contextBuilder,
            GuardedAssociationAnalyzer analyzer
    ) {
        this.communityClient = communityClient;
        this.associationService = associationService;
        this.contextBuilder = contextBuilder;
        this.analyzer = analyzer;
    }

    /** {@code POST /internal/posts/{postId}/changed}, in the background. */
    @Async
    public void postChangedLater(long postId) {
        try {
            postChanged(postId);
        } catch (RuntimeException exception) {
            log.warn("Could not process change of post {}: {}", postId, exception.getMessage());
        }
    }

    /** After the transaction that asked for it commits (dispute, rescan, score below threshold). */
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onAnalysisRequested(AnalysisRequested event) {
        try {
            analyse(event.postId(), event.trigger());
        } catch (RuntimeException exception) {
            log.warn("Analysis of post {} ({}) failed: {}", event.postId(), event.trigger(), exception.getMessage());
        }
    }

    /**
     * Reads the post, syncs the author claim from {@code Post.locationId}, then looks for mentions.
     *
     * @throws com.tounsiguide.locationservice.community.CommunityServiceUnavailableException when the
     *         Community Service cannot be reached
     */
    public void postChanged(long postId) {
        Optional<PostSnapshot> post = communityClient.findPost(postId);
        associationService.syncAuthorClaim(postId, post.orElse(null));
        post.ifPresent(found -> analyse(found, AnalysisContext.Trigger.POST_CHANGED));
    }

    public void analyse(long postId, AnalysisContext.Trigger trigger) {
        Optional<PostSnapshot> post = communityClient.findPost(postId);
        if (post.isEmpty()) {
            associationService.syncAuthorClaim(postId, null);
            return;
        }
        analyse(post.get(), trigger);
    }

    private void analyse(PostSnapshot post, AnalysisContext.Trigger trigger) {
        List<CommentSnapshot> comments = communityClient.findComments(post.id());
        AnalysisContext context = contextBuilder.build(post, comments, trigger);
        AnalysisResult result = analyzer.analyse(context);
        associationService.applyAnalysis(post.id(), result);
        log.debug("Post {} analysed by {} ({}): {} verdict(s)", post.id(), result.provider(), trigger,
                result.verdicts().size());
    }
}
