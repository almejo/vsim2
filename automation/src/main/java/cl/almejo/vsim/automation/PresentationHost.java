package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.present.PresentationPace;
import cl.almejo.vsim.automation.present.PresentationSettings;
import cl.almejo.vsim.automation.present.StepPacer;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.time.Duration;
import java.util.Optional;

/**
 * The editor-side services for «Presentar» (IA.2, spec §4, §5): the presentation mode of the window that shows a
 * circuit and the timer that paces its steps. The defaults are the working mode, so a host that offers no
 * presentation (a test's fake) needs to write nothing.
 */
public interface PresentationHost {

    /**
     * Editor thread: the presentation mode of the window the AI works in for {@code document} (its first window), as
     * the user chose it; the working mode when it has none.
     */
    default PresentationSettings presentationSettings(DocumentId document) {
        return PresentationSettings.WORKING;
    }

    /**
     * Editor thread: switches «Presentar» on or off for the window of {@code document} (the AI asks for it when the
     * user wants to see how it builds, spec §4), remembered like the user's toggle.
     */
    default void setPresenting(DocumentId document, boolean presenting) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor cannot present the AI's work");
    }

    /**
     * Editor thread: shows {@code pace} and {@code pause} (when given) in the temporary layer of the window of
     * {@code document}, as the AI's editor settings (spec §7): never saved, never seen by the other windows, given
     * back when the task ends; the user's own choice in that window replaces them.
     */
    default void layerPresentation(DocumentId document, Optional<PresentationPace> pace, Optional<Duration> pause) {
        throw new AutomationException(AutomationError.NOT_ALLOWED, "This editor has no presentation pace to change");
    }

    /**
     * Editor thread: the windows of {@code document} travel smoothly to {@code origin}, where the AI builds the stage
     * that begins (spec §6; reduced motion respected). Nothing by default.
     */
    default void travelToStageOrigin(DocumentId document, GridPoint origin) {
    }

    /** Any thread: the clock and timer of «De corrido». */
    default StepPacer stepPacer() {
        return StepPacer.system();
    }
}
