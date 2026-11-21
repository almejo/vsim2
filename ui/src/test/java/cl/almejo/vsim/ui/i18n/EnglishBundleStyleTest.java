package cl.almejo.vsim.ui.i18n;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the style of the English bundle: American spelling, “ ” quotes and sentence case in menus and buttons. */
class EnglishBundleStyleTest {

    private static final Pattern BRITISH = Pattern.compile(
            "colour|\\b(cancell(ed|ing)|neighbour\\w*|practise[sd]?|centred|greys?|behaviour|favourite\\w*"
                    + "|customis\\w*|initialis\\w*|organis\\w*|recognis\\w*|licence|catalogue)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Words that keep their capital inside an item: proper nouns, products and key names. */
    private static final Set<String> PROPER_NOUNS = Set.of(
            "VSim", "Logisim", "Karnaugh", "Excel", "Markdown", "Linux", "Windows", "Java", "Gray",
            "Quine-McCluskey", "Boolean", "Hamming", "Moore", "Mealy", "Presenter", "Terminal", "My", "Magnifier", "Alt", "Shift", "Ctrl", "Del", "Esc");

    /** Keys whose text is a menu item, a button, a title or a tooltip. */
    private static final Pattern SHORT_TEXT_KEYS = Pattern.compile(
            "(action|menu|tooltip|shortcuts?|preferences\\.group|category)\\..*|dialog\\.(?!overwrite\\.message).*"
                    + "|.*\\.title(\\.many)?");

    private static ResourceBundle english() {
        return Messages.bundle(Locale.ENGLISH);
    }

    @Test
    void noBritishSpelling() {
        ResourceBundle bundle = english();
        List<String> offenders = new ArrayList<>();
        for (String key : new TreeSet<>(bundle.keySet())) {
            String value = bundle.getString(key);
            if (BRITISH.matcher(value).find()) {
                offenders.add(key + " = " + value);
            }
        }
        assertThat(offenders).as("British spelling in the English bundle").isEmpty();
    }

    @Test
    void quotesAreCurlyDoubleQuotes() {
        ResourceBundle bundle = english();
        List<String> offenders = new ArrayList<>();
        for (String key : new TreeSet<>(bundle.keySet())) {
            String value = bundle.getString(key);
            if (value.contains("«") || value.contains("»") || value.contains("\"")) {
                offenders.add(key + " = " + value);
            }
        }
        assertThat(offenders).as("« », or straight \" quotes in the English bundle").isEmpty();
    }

    @Test
    void menuItemsAndTooltipsUseSentenceCase() {
        ResourceBundle bundle = english();
        List<String> offenders = new ArrayList<>();
        for (String key : new TreeSet<>(bundle.keySet())) {
            if (!SHORT_TEXT_KEYS.matcher(key).matches()) {
                continue;
            }
            String value = bundle.getString(key);
            String firstLine = value.split("\n")[0].replaceAll("\\{[^}]*}", " ");
            String[] words = firstLine.split("[\\s/]+");
            for (int index = 1; index < words.length; index++) {
                String word = words[index].replaceAll("^[^\\p{L}]+|[^\\p{L}0-9-]+$", "");
                if (word.length() > 1 && !words[index].contains("+") && Character.isUpperCase(word.charAt(0))
                        && !word.equals(word.toUpperCase(Locale.ROOT)) && !PROPER_NOUNS.contains(word)) {
                    offenders.add(key + " = " + value);
                    break;
                }
            }
        }
        assertThat(offenders).as("Title Case in the English bundle").isEmpty();
    }
}
