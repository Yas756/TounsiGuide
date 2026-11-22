package com.tounsiguide.communityservice.report;

/** A moderator decision on one post or comment (S3-US08). */
public enum ModerationAction {
    /** The open reports were not founded; the content stays published. */
    DISMISS,
    /** The content becomes RESTRICTED platform-wide. */
    RESTRICT,
    /** A restricted content is published again. */
    RESTORE
}
