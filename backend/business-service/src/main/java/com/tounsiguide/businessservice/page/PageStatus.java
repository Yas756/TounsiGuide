package com.tounsiguide.businessservice.page;

/** A page is ACTIVE once verified; DELETED pages are kept (soft delete) but no longer shown. */
public enum PageStatus {
    ACTIVE,
    DELETED
}
