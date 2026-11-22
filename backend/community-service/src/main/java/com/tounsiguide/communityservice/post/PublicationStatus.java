package com.tounsiguide.communityservice.post;

/**
 * Whether a post is shown. A deleted post is kept in the database (soft delete, S2-B04) with its comments and
 * media, but hidden from everyone except administrators.
 */
public enum PublicationStatus {
    PUBLISHED,
    DELETED,
    /**
     * Sprint 3 (S3-B07): an administrator restricted the content after reports. Hidden platform-wide (feed, walls,
     * GET by id answers 404) except to administrators and to the author, who sees it with a notice. Reversible.
     */
    RESTRICTED
}
