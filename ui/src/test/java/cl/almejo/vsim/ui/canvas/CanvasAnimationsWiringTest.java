package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.terminal.TerminalScreen;
import cl.almejo.vsim.core.terminal.TerminalSettings;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.canvas.CanvasDragSupport.paint;
import static org.assertj.core.api.Assertions.assertThat;

/** A view's panes ask Preferencias ▸ «Animaciones» (through {@code Animations}) whether their parts may move. */
class CanvasAnimationsWiringTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final ManualFrameTicker ticker = new ManualFrameTicker();
    private EditorView view;
    private AppPreferences preferences;

    @BeforeEach
    void showABlinkingTerminalInAView() {
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        preferences = view.services().preferences();
        CircuitCanvas canvas = view.canvas();
        canvas.overrideOnScreen(() -> true);
        canvas.useTerminalTicker(ticker);
        canvas.setSize(800, 600);
        Component terminal = place(openDocument, StandardComponentTypes.TERMINAL, 2, 2,
                Map.of(TerminalTypes.ROWS, 4, TerminalTypes.COLUMNS, 20, TerminalTypes.CURSOR, "bar",
                        TerminalTypes.BLINK, true));
        TerminalScreen screen = new TerminalScreen(TerminalSettings.vt(4, 20));
        screen.write('a', 0);
        VisibleState state = new VisibleState.TerminalState(1, screen.version(), screen.frame(), false);
        canvas.display(new SimulationSnapshot(1, openDocument.netlistToken(), 0, true, Map.of(),
                Map.of(terminal.id().value(), state), List.of(), Optional.empty()));
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void withAnimationsOnTheTerminalsCursorBlinksAndWithNoItStaysStill() {
        paint(view.canvas());
        assertThat(view.canvas().isAnimatingTerminals()).isTrue();

        preferences.setAnimations(false);
        paint(view.canvas());
        ticker.advance(40);

        assertThat(view.canvas().isAnimatingTerminals()).isFalse();

        preferences.setAnimations(true);
        paint(view.canvas());
        assertThat(view.canvas().isAnimatingTerminals()).isTrue();
    }
}
