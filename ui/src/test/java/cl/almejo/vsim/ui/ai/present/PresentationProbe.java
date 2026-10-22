package cl.almejo.vsim.ui.ai.present;

import cl.almejo.vsim.ui.ai.QuestionCard;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.List;
import java.util.Optional;

/**
 * What a «Presentar» window shows, for the end-to-end scenarios of {@code cl.almejo.vsim.ui.ai} (the pieces are
 * package-private). Editor thread only.
 */
public final class PresentationProbe {

    private PresentationProbe() {
    }

    /** The subtitle the window shows now, if any. */
    public static Optional<String> subtitle(PresentationExtension extension, EditorView view) {
        return extension.overlaysOf(view).flatMap(overlays -> overlays.caption().text());
    }

    /** The question card of the floating panel, if a question waits. */
    public static Optional<QuestionCard> questionCard(PresentationExtension extension, EditorView view) {
        return extension.featuresOf(view).flatMap(features -> features.panel().questionCard());
    }

    /** Every line of every stage card of the floating panel, top to bottom. */
    public static List<String> cardLines(PresentationExtension extension, EditorView view) {
        return extension.featuresOf(view).map(features -> features.panel().cards().stream()
                .flatMap(card -> card.lines().stream()).map(StageCard.Line::getText).toList()).orElse(List.of());
    }

    /** The «Presentar» panel's notice line (after «Detener», a silent AI…), if it shows one. */
    public static String notice(PresentationExtension extension, EditorView view) {
        return extension.featuresOf(view).map(features -> features.panel().notice())
                .filter(label -> label.isVisible()).map(label -> label.getText()).orElse("");
    }
}
