package com.tounsiguide.locationservice.association;

/**
 * Kinds of evidence, each with its direction and default weight (association-api.md, section 3).
 * The weight actually used comes from {@code tounsiguide.association.weights.<TYPE>}.
 */
public enum EvidenceType {
    AUTHOR_TAG(Direction.SUPPORT, 10),
    AUTHOR_CONFIRMED(Direction.SUPPORT, 10),
    PAGE_MENTION(Direction.SUPPORT, 6),
    MEMBER_SUGGESTION(Direction.SUPPORT, 3),
    TEXT_MENTION(Direction.SUPPORT, 2),
    COMPETING_CLAIM(Direction.OPPOSE, 2),
    MEMBER_DISPUTE(Direction.OPPOSE, 3),
    BUSINESS_DISPUTE(Direction.OPPOSE, 10);

    private final Direction direction;
    private final int defaultWeight;

    EvidenceType(Direction direction, int defaultWeight) {
        this.direction = direction;
        this.defaultWeight = defaultWeight;
    }

    public Direction direction() {
        return direction;
    }

    public int defaultWeight() {
        return defaultWeight;
    }

    /** Support that comes from members or text, not from the author or an admin: it competes with other places. */
    public boolean competesWithOtherPlaces() {
        return this == MEMBER_SUGGESTION || this == TEXT_MENTION || this == PAGE_MENTION;
    }
}
