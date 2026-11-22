package com.tounsiguide.communityservice.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every hour, removes the files uploaded more than 24 hours ago that were never attached to a post or comment. */
@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final MediaService mediaService;

    public MediaCleanupJob(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Scheduled(cron = "${tounsiguide.media.cleanup-cron:0 0 * * * *}")
    public void deleteOrphans() {
        try {
            int deleted = mediaService.deleteOrphans();
            if (deleted > 0) {
                log.info("Deleted {} unattached media file(s) older than 24 h", deleted);
            }
        } catch (RuntimeException exception) {
            log.warn("Media cleanup failed: {}", exception.getMessage());
        }
    }
}
