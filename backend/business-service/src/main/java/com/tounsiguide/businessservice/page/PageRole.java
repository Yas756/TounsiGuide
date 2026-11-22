package com.tounsiguide.businessservice.page;

/** Role of a member on one page. Both may edit the official information and curate the wall. */
public enum PageRole {
    /** The approved requester. */
    OWNER,
    /** Added later by an owner (post-MVP: no endpoint yet). */
    EDITOR
}
