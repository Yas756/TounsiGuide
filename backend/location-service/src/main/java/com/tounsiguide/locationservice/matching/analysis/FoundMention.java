package com.tounsiguide.locationservice.matching.analysis;

/**
 * A place name found in the post or a comment.
 *
 * @param text      the name or alias as found
 * @param commentId the comment it was found in, or null for the post text
 * @param type      {@link Type#TEXT_MENTION} for plain text, {@link Type#PAGE_MENTION} for a linked business page
 */
public record FoundMention(String text, Long commentId, Type type) {

    public enum Type {
        TEXT_MENTION,
        PAGE_MENTION
    }
}
