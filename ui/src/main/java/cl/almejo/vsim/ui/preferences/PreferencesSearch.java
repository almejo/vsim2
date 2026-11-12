package cl.almejo.vsim.ui.preferences;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How the preferences search compares text: without accents and without case, so «libreria», «LIBRERÍA» and
 * «librería» find the same words. Pure, so it is tested without Swing.
 */
final class PreferencesSearch {

    /** A matched stretch of the original text: {@code start} inclusive, {@code end} exclusive. */
    record Range(int start, int end) {
    }

    private PreferencesSearch() {
    }

    /** {@code text} in lower case with every accent (combining mark) removed. */
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Whether the trimmed {@code query} is a substring of {@code words}; a blank query matches everything. */
    static boolean matches(String words, String query) {
        String needle = fold(query.strip());
        return needle.isEmpty() || fold(words).contains(needle);
    }

    /**
     * Where the trimmed {@code query} occurs in {@code text}, as positions of the original text (accents do not shift
     * them); empty for a blank query.
     */
    static List<Range> ranges(String text, String query) {
        String needle = fold(query.strip());
        List<Range> ranges = new ArrayList<>();
        if (needle.isEmpty()) {
            return ranges;
        }
        StringBuilder folded = new StringBuilder();
        List<Integer> originalIndexes = new ArrayList<>();
        for (int index = 0; index < text.length(); index++) {
            String foldedCharacter = fold(String.valueOf(text.charAt(index)));
            for (int offset = 0; offset < foldedCharacter.length(); offset++) {
                originalIndexes.add(index);
            }
            folded.append(foldedCharacter);
        }
        int found = folded.indexOf(needle);
        while (found >= 0) {
            int last = found + needle.length() - 1;
            ranges.add(new Range(originalIndexes.get(found), originalIndexes.get(last) + 1));
            found = folded.indexOf(needle, found + needle.length());
        }
        return ranges;
    }
}
