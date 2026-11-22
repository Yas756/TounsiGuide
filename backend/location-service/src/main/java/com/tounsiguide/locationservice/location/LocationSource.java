package com.tounsiguide.locationservice.location;

/** Where a Location comes from. */
public enum LocationSource {
    /** Added to the directory by an administrator (or seeded). */
    DIRECTORY,
    /** Chosen by a member on the map or in the external search results. */
    MAP
}
