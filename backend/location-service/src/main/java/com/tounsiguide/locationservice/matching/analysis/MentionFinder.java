package com.tounsiguide.locationservice.matching.analysis;

import com.tounsiguide.locationservice.matching.TextNormalizer;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a place name inside a text with {@link TextNormalizer}: whole words only, accents and punctuation
 * ignored ("on a mangé au cafe-roma!" names "Café Roma"). Names shorter than {@link #MIN_NAME_LENGTH}
 * letters are ignored to avoid false positives.
 *
 * <p>Since 2026-11-15, {@link #firstMentioned} also ignores a name or alias that is only a generic word ("Medina",
 * "La Médina", "plage", "café"…, see {@link #GENERIC_WORDS}): "près de la médina" must not propose "Médina de Tunis".
 * The full name ("Médina de Tunis", "Medina Restaurant") still counts. This only concerns the rule-based analyzer;
 * candidate selection for other analyzers (the future AI) is unchanged.</p>
 */
public final class MentionFinder {

    public static final int MIN_NAME_LENGTH = 3;

    /** Words that name a kind of place, not a place. Normalized (lower case, no accents). Keep this list small. */
    public static final Set<String> GENERIC_WORDS = Set.of(
            "medina", "souk", "plage", "cafe", "restaurant", "hotel", "parc", "park", "musee", "mosquee", "port",
            "lac", "village", "marche", "centre", "avenue", "residence");

    /** Articles ignored in front of a generic word: "la medina", "le souk", "el medina". */
    private static final Set<String> ARTICLES = Set.of("le", "la", "les", "l", "el", "the");

    private MentionFinder() {
    }

    /** True when the normalized name appears in the normalized text as whole words. */
    public static boolean contains(String normalizedText, String normalizedName) {
        if (normalizedText == null || normalizedName == null || normalizedName.length() < MIN_NAME_LENGTH) {
            return false;
        }
        return (" " + normalizedText + " ").contains(" " + normalizedName + " ");
    }

    /** True for a normalized name that is only a generic word, with or without an article ("la medina"). */
    public static boolean isGeneric(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return false;
        }
        String[] words = normalizedName.trim().split("\\s+");
        int start = words.length > 1 && ARTICLES.contains(words[0]) ? 1 : 0;
        return words.length - start == 1 && GENERIC_WORDS.contains(words[start]);
    }

    /** The first of {@code names} (name, then aliases) that the raw text mentions; generic words are skipped. */
    public static Optional<String> firstMentioned(String rawText, Collection<String> names) {
        String text = TextNormalizer.normalize(rawText);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        for (String name : names) {
            String normalized = TextNormalizer.normalize(name);
            if (!isGeneric(normalized) && contains(text, normalized)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }
}
