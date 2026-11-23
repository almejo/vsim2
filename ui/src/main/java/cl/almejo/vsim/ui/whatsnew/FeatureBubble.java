package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.app.Animations;
import cl.almejo.vsim.ui.app.KeyDispatchers;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorView;

import java.util.List;
import java.util.Optional;

/**
 * A bubble for one feature, outside a release's news: the same bubble as «Qué hay de nuevo» ({@link BubbleTour}: over
 * the dimmed window, pointing at its target, the arrows, Enter and Esc, «Listo», sliding only with «Animaciones»), but
 * alone, for what a feature wants to explain the first time (a machine's test mode). The target is a component
 * registered with {@link NewsTargets#register} inside the view. The caller decides when it is due and remembers that
 * the user saw it, from {@code ended}.
 */
public final class FeatureBubble {

    private static final int FRAME_MILLISECONDS = 16;
    private static final String ID = "feature";

    private FeatureBubble() {
    }

    /**
     * Opens the bubble over {@code view}'s window, pointing at {@code targetId}, with the texts of the bundle keys
     * {@code titleKey} and {@code textKey}. {@code ended} runs when the user ends it (not when it is taken away).
     * Returns what takes it away; nothing is shown (and {@code ended} never runs) when the window is not showing or
     * the target is not.
     */
    public static Runnable open(EditorView view, String targetId, String titleKey, String textKey, Runnable ended) {
        BubbleTours tours = new BubbleTours(new BubbleTours.Seams(KeyDispatchers.focusManager(),
                () -> FrameTicker.swing(FRAME_MILLISECONDS), System::currentTimeMillis, Animations::enabled));
        return start(tours, view, targetId, titleKey, textKey, ended)::close;
    }

    /** As {@link #open}, with the tours' seams given (tests), returning the tour. */
    static BubbleTour start(BubbleTours tours, EditorView view, String targetId, String titleKey, String textKey,
                            Runnable ended) {
        NewsStep step = new NewsStep(targetId, titleKey, textKey, Optional.empty());
        return tours.start(view, new NewsFile(ID, List.of(step)), ended);
    }
}
