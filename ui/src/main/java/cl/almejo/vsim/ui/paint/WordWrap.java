package cl.almejo.vsim.ui.paint;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Greedy word wrapping for text painted by hand (the canvas notices, the step callout and the subtitle of
 * «Presentar»): each line takes words while they fit the width; a word wider than the width stays whole on a line of
 * its own. How the text is cut into words is the caller's (some collapse runs of spaces, some keep them).
 */
public final class WordWrap {

    private WordWrap() {
    }

    /** {@code words} joined by single spaces into lines at most {@code width} wide, measured with {@code metrics}. */
    public static List<String> lines(String[] words, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && metrics.stringWidth(candidate) > width) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
