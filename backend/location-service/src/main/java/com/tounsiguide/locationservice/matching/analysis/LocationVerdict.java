package com.tounsiguide.locationservice.matching.analysis;

import java.util.List;

/**
 * The analyzer's view of one Location for the post.
 *
 * @param supportScore 0..100
 * @param opposeScore  0..100
 * @param mentions     text found, comment id, mention type
 */
public record LocationVerdict(
        long locationId,
        int supportScore,
        int opposeScore,
        List<FoundMention> mentions,
        String reasoning
) {

    public LocationVerdict {
        mentions = mentions == null ? List.of() : List.copyOf(mentions);
    }
}
