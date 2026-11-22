package com.tounsiguide.locationservice.association;

import com.tounsiguide.locationservice.community.CommunityClient;
import com.tounsiguide.locationservice.community.CommunityClient.PostSnapshot;
import com.tounsiguide.locationservice.community.CommunityServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * One-time migration (Sprint 2): every existing post with a {@code locationId} gets its AUTHOR association.
 *
 * <p>Idempotent: posts whose author claim is already recorded are skipped, so running it at every start
 * only processes posts that are still missing. If the Community Service is not running, it is skipped and
 * tried again at the next start (an admin rescan also repairs a single post). Turn it off with
 * {@code tounsiguide.association.migrate-on-startup=false}.</p>
 */
@Component
public class AuthorClaimMigration {

    private static final Logger log = LoggerFactory.getLogger(AuthorClaimMigration.class);

    private final CommunityClient communityClient;
    private final AssociationRepository repository;
    private final AssociationAnalysisJob job;
    private final AssociationProperties properties;

    public AuthorClaimMigration(
            CommunityClient communityClient,
            AssociationRepository repository,
            AssociationAnalysisJob job,
            AssociationProperties properties
    ) {
        this.communityClient = communityClient;
        this.repository = repository;
        this.job = job;
        this.properties = properties;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (Boolean.TRUE.equals(properties.migrateOnStartup())) {
            migrate();
        }
    }

    /** @return number of posts migrated */
    public int migrate() {
        int migrated = 0;
        try {
            for (PostSnapshot post : communityClient.findAllPosts()) {
                if (post.id() == null || post.locationId() == null
                        || repository.existsByPostIdAndLocationIdAndAuthorClaimTrue(post.id(), post.locationId())) {
                    continue;
                }
                try {
                    job.postChanged(post.id());
                    migrated++;
                } catch (RuntimeException exception) {
                    log.warn("Migration of post {} failed: {}", post.id(), exception.getMessage());
                }
            }
        } catch (CommunityServiceUnavailableException exception) {
            log.warn("Author claim migration skipped: Community Service unavailable ({}). It runs again at next start.",
                    exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage());
            return migrated;
        }
        if (migrated > 0) {
            log.info("Author claim migration: {} post(s) turned into AUTHOR associations", migrated);
        }
        return migrated;
    }
}
