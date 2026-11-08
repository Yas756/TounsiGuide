package com.tounsiguide.locationservice.matching;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a name into a comparable key: lower case, no accents, no punctuation, single spaces.
 * "Sidi Bou Saïd" and "sidi-bou-said" both become "sidi bou said".
 */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD);
        String withoutMarks = MARKS.matcher(decomposed).replaceAll("");
        return NOT_ALNUM.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }
}
