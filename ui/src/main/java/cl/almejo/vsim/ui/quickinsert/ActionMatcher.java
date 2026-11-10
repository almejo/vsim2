package cl.almejo.vsim.ui.quickinsert;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds the «Acciones» a search names. Every word of the search must appear in the row's name or its menu path,
 * ignoring case, accents and the «…» of the menus. A word of {@value #STEM_MINIMUM} letters or more also matches by
 * its stem (all but its last {@value #ENDING_LENGTH} letters), so «presentar» finds «Modo presentación» as well as
 * «Presentar lo que hace la IA». Rows whose name has every word as written come first, then those whose name has
 * them by their stem, then those found through their menu or their hidden search words («add» finds the example
 * «Sumador completo» by «full adder»); each group keeps the menu order.
 */
final class ActionMatcher {

    /** The shortest word that also matches by its stem: shorter ones («pegar», «zoom») would match too much. */
    static final int STEM_MINIMUM = 6;
    /** A Spanish infinitive or noun ending («-ar», «-ón»): what «presentar» and «presentación» differ in. */
    static final int ENDING_LENGTH = 2;
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ActionMatcher() {
    }

    /** The rows {@code query} finds, best first; a blank query finds none. */
    static List<ActionRow> filter(List<ActionRow> rows, String query) {
        List<String> words = wordsOf(query);
        if (words.isEmpty()) {
            return List.of();
        }
        record Ranked(ActionRow row, int rank) {
        }
        return rows.stream().map(row -> new Ranked(row, rank(row, words)))
                .filter(ranked -> ranked.rank() < Rank.NONE.ordinal())
                .sorted(Comparator.comparingInt(Ranked::rank)).map(Ranked::row).toList();
    }

    /** How well a row matches, best first. */
    private enum Rank { NAME, NAME_BY_STEM, PATH, NONE }

    private static int rank(ActionRow row, List<String> words) {
        String name = normalized(row.name());
        String everything = name + " " + normalized(row.menuPath()) + " " + normalized(row.searchTerms());
        if (words.stream().allMatch(name::contains)) {
            return Rank.NAME.ordinal();
        }
        if (words.stream().allMatch(word -> containsWordOrStem(name, word))) {
            return Rank.NAME_BY_STEM.ordinal();
        }
        if (words.stream().allMatch(word -> containsWordOrStem(everything, word))) {
            return Rank.PATH.ordinal();
        }
        return Rank.NONE.ordinal();
    }

    private static boolean containsWordOrStem(String text, String word) {
        return text.contains(word)
                || word.length() >= STEM_MINIMUM && text.contains(word.substring(0, word.length() - ENDING_LENGTH));
    }

    private static List<String> wordsOf(String query) {
        String text = normalized(query).strip();
        return text.isEmpty() ? List.of() : Arrays.asList(SPACES.split(text));
    }

    /** Lower case, without accents nor ellipses: «Abrir…» and «abrir» are the same. */
    static String normalized(String text) {
        String withoutEllipsis = text.replace("…", " ").replace("...", " ");
        return ACCENTS.matcher(Normalizer.normalize(withoutEllipsis, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
    }
}
