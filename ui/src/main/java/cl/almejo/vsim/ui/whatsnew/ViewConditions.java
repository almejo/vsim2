package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.window.EditorView;

import java.util.Objects;

/**
 * Presentation mode of a view and the AI's work, as a {@link TourGate} reads them: what holds back the bubbles and the
 * tip of the day until the window is free.
 */
final class ViewConditions implements TourGate.Conditions {

    private final EditorView view;
    private final WhatsNewExtension.AiActivity ai;

    ViewConditions(EditorView view, WhatsNewExtension.AiActivity ai) {
        this.view = Objects.requireNonNull(view, "view");
        this.ai = Objects.requireNonNull(ai, "ai");
    }

    @Override
    public boolean presenting() {
        return view.fullScreenStage().isPresent();
    }

    @Override
    public boolean aiWorking() {
        return ai.working();
    }

    @Override
    public Runnable addChangeListener(Runnable listener) {
        Runnable removeLayoutListener = view.addFullScreenLayoutListener(listener);
        Runnable removeAiListener = ai.addListener(listener);
        return () -> {
            removeAiListener.run();
            removeLayoutListener.run();
        };
    }
}
