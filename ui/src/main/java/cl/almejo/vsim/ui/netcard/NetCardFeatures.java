package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.FrameTicker;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.ToolTipManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * «Resaltar la red al pasar» in one editor window: on every pane (split views included, through
 * {@link EditorView#forEachPane}) hovering a wire with the Selección tool idle lights its whole net
 * ({@link PaneNetCard}); the wire's balloon gives way to it where a card can show. Each pane has its own; a
 * net lit in one pane goes out, unless pinned, when the pointer lights one in another.
 */
public final class NetCardFeatures {

    /** One frame every 16 ms while a timeout is pending (the card's delay, the grace). */
    private static final int FRAME_MILLISECONDS = 16;

    /**
     * What the panes need from outside, injectable for tests: a frame ticker per pane, the clock in milliseconds, the
     * balloon delay and the latest snapshot of a document.
     */
    record Setup(Supplier<FrameTicker> tickers, LongSupplier clock, LongSupplier initialDelay,
                 Function<OpenDocument, SimulationSnapshot> snapshots) {

        Setup {
            Objects.requireNonNull(tickers, "tickers");
            Objects.requireNonNull(clock, "clock");
            Objects.requireNonNull(initialDelay, "initialDelay");
            Objects.requireNonNull(snapshots, "snapshots");
        }

        /** Swing timers, the system clock, the balloons' own delay (read each time) and the published snapshot. */
        static Setup editor() {
            return new Setup(() -> FrameTicker.swing(FRAME_MILLISECONDS), () -> System.nanoTime() / 1_000_000,
                    () -> ToolTipManager.sharedInstance().getInitialDelay(), OpenDocument::latestSnapshot);
        }
    }

    private final EditorView view;
    private final AppPreferences preferences;
    private final Setup setup;
    private final Map<CircuitCanvas, PaneNetCard> panes = new LinkedHashMap<>();
    private final Runnable preferenceListener = this::preferenceChanged;
    private final Runnable removeFromPanes;
    private final Runnable removeBalloonVeto;
    private boolean disposed;

    NetCardFeatures(EditorView view, AppPreferences preferences, Setup setup) {
        this.view = Objects.requireNonNull(view, "view");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.setup = Objects.requireNonNull(setup, "setup");
        preferences.addListener(preferenceListener);
        removeBalloonVeto = view.problems().addWireBalloonVeto(this::cardCanShowIn);
        removeFromPanes = view.forEachPane(this::installOn);
    }

    /**
     * Installs the feature on every pane of {@code view}, now and later, gated by
     * {@link AppPreferences#highlightNetOnHover()}; the returned runnable undoes every registration.
     */
    public static Runnable install(EditorView view, AppPreferences preferences) {
        return new NetCardFeatures(view, preferences, Setup.editor())::dispose;
    }

    private Runnable installOn(CircuitCanvas pane) {
        PaneNetCard card = new PaneNetCard(view, pane, setup, preferences::highlightNetOnHover, this::lightingIn);
        panes.put(pane, card);
        return () -> {
            panes.remove(pane);
            card.dispose();
        };
    }

    /**
     * True when hovering a wire of {@code pane} lights its net (the preference is on, the Selección tool is at rest
     * there): only then does the card take the place of the wire's balloon, which otherwise stays with its value and
     * problems.
     */
    private boolean cardCanShowIn(CircuitCanvas pane) {
        return preferences.highlightNetOnHover()
                && Optional.ofNullable(panes.get(pane)).map(PaneNetCard::isIdle).orElse(false);
    }

    /** The pane that lit a net now: an unpinned card of another pane goes. */
    private void lightingIn(PaneNetCard lighting) {
        for (PaneNetCard other : List.copyOf(panes.values())) {
            if (other != lighting) {
                other.closeUnlessPinned();
            }
        }
    }

    private void preferenceChanged() {
        if (!preferences.highlightNetOnHover()) {
            List.copyOf(panes.values()).forEach(PaneNetCard::close);
        }
    }

    /** For tests: what {@code canvas} shows. */
    Optional<PaneNetCard> paneOf(CircuitCanvas canvas) {
        return Optional.ofNullable(panes.get(canvas));
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        removeFromPanes.run();
        removeBalloonVeto.run();
        preferences.removeListener(preferenceListener);
    }
}
