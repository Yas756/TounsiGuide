package com.tounsiguide.locationservice.association;

/** What an administrator decides about an association. */
public enum ReviewDecision {
    /** Confirm a disputed or proposed association. */
    VALIDATE,
    /** Reject the association, whatever its status. */
    REMOVE,
    /** Confirm and keep it confirmed even if new evidence disagrees. */
    FORCE
}
