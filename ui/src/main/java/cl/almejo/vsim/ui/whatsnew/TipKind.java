package cl.almejo.vsim.ui.whatsnew;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The kinds of tip of the day (mockup v3): a small circuit VSim draws with the user's theme, two pictures side by side
 * with the big key between them («antes y después»), a picture with little or no text, or a circuit drawn twice in
 * two panes, whole and zoomed in («Dividir la vista», drawn by VSim so it needs no capture).
 */
enum TipKind {
    CIRCUIT("circuit"), BEFORE_AFTER("before-after"), IMAGE("image"), SPLIT("split");

    private final String word;

    TipKind(String word) {
        this.word = word;
    }

    /** The kind the tips file names with {@code word} («circuit», «before-after», «image», «split»). */
    static Optional<TipKind> of(String word) {
        return Arrays.stream(values()).filter(kind -> kind.word.equals(word)).findFirst();
    }

    /** Whether {@code pictures} (resource names) are what this kind shows. */
    boolean accepts(List<String> pictures) {
        return switch (this) {
            case BEFORE_AFTER -> pictures.size() == 2 && pictures.stream().allMatch(TipKind::isPicture);
            case IMAGE -> pictures.size() == 1 && isImage(pictures.get(0));
            case CIRCUIT, SPLIT -> pictures.size() == 1 && isCircuit(pictures.get(0));
        };
    }

    static boolean isCircuit(String resource) {
        return resource.endsWith(".vsim");
    }

    static boolean isImage(String resource) {
        return resource.endsWith(".png");
    }

    private static boolean isPicture(String resource) {
        return isCircuit(resource) || isImage(resource);
    }
}
