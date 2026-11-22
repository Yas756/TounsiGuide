package com.tounsiguide.communityservice.topic;

/** Lifecycle of a topic module (S3-US09): only APPROVED modules are visible and selectable on posts. */
public enum ModuleStatus {
    PROPOSED,
    APPROVED,
    REJECTED,
    /** No longer listed or selectable; posts keep their module id. Can be approved again. */
    ARCHIVED
}
