package cl.almejo.vsim.ui.whatsnew;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The three kinds of tip of the day (mockup v3): a small circuit VSim draws with the user's theme, two pictures side by
 * side with the big key between them («antes y después»), or a picture with little or no text.
 */
enum TipKind {
    CIRCUIT("circuit"), BEFORE_AFTER("before-after"), IMAGE("image");

    private final String word;

    TipKind(String word) {
        this.word = word;
    }

    /** The kind the tips file names with {@code word} («circuit», «before-after», «image»). */
    static Optional<TipKind> of(String word) {
        return Arrays.stream(values()).filter(kind -> kind.word.equals(word)).findFirst();
    }

    /** Whether {@code pictures} (resource names) are what this kind shows. */
    boolean accepts(List<String> pictures) {
        return switch (this) {
            case CIRCUIT -> pictures.size() == 1 && isCircuit(pictures.get(0));
            case BEFORE_AFTER -> pictures.size() == 2 && pictures.stream().allMatch(TipKind::isPicture);
            case IMAGE -> pictures.size() == 1 && isImage(pictures.get(0));
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
