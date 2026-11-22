package com.tounsiguide.locationservice.association;

/** Who first made the claim that a post belongs to a Location. */
public enum Origin {
    /** The author picked the place when publishing or editing the post. */
    AUTHOR,
    /** A member used "Suggest a place". */
    MEMBER,
    /** The analyzer found the place named in the post or a comment. */
    MENTION,
    /** An administrator set the place directly. */
    ADMIN
}
