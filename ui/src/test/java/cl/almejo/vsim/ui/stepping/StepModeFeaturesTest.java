package cl.almejo.vsim.ui.stepping;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.waveform.WaveformPanel;
import cl.almejo.vsim.ui.waveform.WaveformsExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

class StepModeFeaturesTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final TextPrompt NO_ANSWER = (parent, title, message, initialText) -> Optional.empty();

    private final AtomicLong clock = new AtomicLong();
    private OpenDocument openDocument;
    private EditorView view;
    private StepModeFeatures features;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(),
                List.of(new WaveformsExtension(NO_ANSWER, (parent, message) -> { })));
        view = new EditorView(openDocument, services, NO_WINDOW);
        features = new StepModeFeatures(view, clock::get);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
    }

    private Wire addWire(int x1, int y1, int x2, int y2) {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(x1, y1), new GridPoint(x2, y2));
        openDocument.execute(new AddElementsCommand(List.of(wire)));
        return wire;
    }

    /** What the canvas frame timer does on screen: hand the latest snapshot to the listeners. */
    private void showLatestSnapshot() {
        features.snapshotPublished(openDocument.latestSnapshot());
    }

    /** Lets the running simulation settle (pausing at once would leave events pending), then enters the mode. */
    private void settleThenEnterStepMode() {
        waitUntil(() -> openDocument.latestSnapshot().token() == openDocument.netlistToken()
                && openDocument.latestSnapshot().running() && openDocument.latestNextEventTime().isEmpty());
        perform(features.stepModeAction());
        waitUntil(() -> !openDocument.latestSnapshot().running());
        showLatestSnapshot();
    }

    @Test
    void theToggleEntersTheModeAndIniciarLeavesIt() {
        assertThat(features.stepModeAction().getValue(Action.SELECTED_KEY)).isEqualTo(false);
        assertThat(features.advanceButton().isVisible()).isFalse();
        assertThat(features.status().isVisible()).isFalse();

        perform(features.stepModeAction());

        assertThat(openDocument.stepMode()).isTrue();
        assertThat(features.stepModeAction().getValue(Action.SELECTED_KEY)).isEqualTo(true);
        assertThat(features.advanceButton().isVisible()).isTrue();
        assertThat(features.advanceButton().getText()).as("the action name, until Task 7 gives it an icon")
                .isEqualTo("Avanzar al próximo evento");
        assertThat(features.status().isVisible()).isTrue();

        perform(view.actions().start());

        assertThat(openDocument.stepMode()).isFalse();
        assertThat(features.stepModeAction().getValue(Action.SELECTED_KEY)).isEqualTo(false);
        assertThat(features.advanceButton().isVisible()).isFalse();
    }

    @Test
    void avanzarIsEnabledOnlyWhilePausedWithAnEventPending() {
        place(openDocument, StandardComponentTypes.CLOCK, 0, 0, Map.of());
        waitUntil(() -> openDocument.latestNextEventTime().isPresent());

        features.snapshotPublished(SimulationSnapshot.EMPTY.withRunning(true));
        assertThat(features.advanceAction().isEnabled()).as("running").isFalse();
        features.snapshotPublished(SimulationSnapshot.EMPTY);
        assertThat(features.advanceAction().isEnabled()).as("paused with a clock").isTrue();
        assertThat(features.status().getText()).startsWith("t = 0 · próximo evento: t = ");
    }

    @Test
    void aStableCircuitDisablesAvanzarAndSaysSo() {
        settleThenEnterStepMode();

        assertThat(features.advanceAction().isEnabled()).isFalse();
        assertThat(features.status().getText()).isEqualTo("Sin eventos pendientes: el circuito está estable");
    }

    @Test
    void aClickAndEachAdvanceLightTheirWiresAndMoveTheWaveformCursor() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());  // out (4,1)
        Wire input = addWire(4, 1, 8, 1);
        place(openDocument, StandardComponentTypes.NOT, 8, -1, Map.of());                       // (8,1) → (12,1)
        Wire output = addWire(12, 1, 16, 1);
        view.bottomDock().showDock();
        WaveformPanel panel = (WaveformPanel) view.bottomDock().content().orElseThrow();
        settleThenEnterStepMode();
        long before = openDocument.latestSnapshot().time();

        openDocument.interact(toggle.id());
        waitUntil(() -> openDocument.latestStep().isPresent() && openDocument.latestNextEventTime().isPresent());
        clock.set(1_000);
        showLatestSnapshot();
        assertThat(features.overlay().current()).hasValueSatisfying(highlight -> {
            assertThat(highlight.wires()).isEqualTo(Set.of(input.id()));
            assertThat(highlight.startNanos()).isEqualTo(1_000);
        });
        assertThat(features.advanceAction().isEnabled()).isTrue();
        assertThat(panel.cursorTime()).hasValue(before);

        perform(features.advanceAction());
        waitUntil(() -> openDocument.latestStep().orElseThrow().sequence() == 2
                && openDocument.latestSnapshot().time() == before + 1);
        showLatestSnapshot();
        assertThat(features.overlay().current().orElseThrow().wires()).isEqualTo(Set.of(output.id()));
        assertThat(features.status().getText()).isEqualTo("Sin eventos pendientes: el circuito está estable");
        assertThat(panel.cursorTime()).hasValue(before + 1);

        long glowStart = features.overlay().current().orElseThrow().startNanos();
        showLatestSnapshot();
        assertThat(features.overlay().current().orElseThrow().startNanos()).as("the same step glows once")
                .isEqualTo(glowStart);
    }

    @Test
    void aClickWhilePausedOutsideTheModeGlowsButLeavesTheWaveformCursor() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());  // out (4,1)
        Wire input = addWire(4, 1, 8, 1);
        view.bottomDock().showDock();
        WaveformPanel panel = (WaveformPanel) view.bottomDock().content().orElseThrow();
        waitUntil(() -> openDocument.latestSnapshot().token() == openDocument.netlistToken()
                && openDocument.latestSnapshot().running() && openDocument.latestNextEventTime().isEmpty());
        openDocument.pause();
        waitUntil(() -> !openDocument.latestSnapshot().running());
        showLatestSnapshot();
        long reading = openDocument.latestSnapshot().time() + 1_000; // the user reads another part of the trace
        panel.placeCursorAtTime(reading);

        openDocument.interact(toggle.id());
        waitUntil(() -> openDocument.latestStep().isPresent());
        clock.set(1_000);
        showLatestSnapshot();

        assertThat(openDocument.stepMode()).isFalse();
        assertThat(features.overlay().current()).as("the click still glows")
                .hasValueSatisfying(highlight -> assertThat(highlight.wires()).isEqualTo(Set.of(input.id())));
        assertThat(panel.cursorTime()).as("time did not move and the mode is off: the cursor stays")
                .hasValue(reading);
    }

    @Test
    void disposeTakesEverythingBack() {
        int listeners = openDocument.stepModeListenerCount();
        int toolBarItems = view.toolBar().getComponentCount();
        int menuItems = view.menu(MenuId.SIMULATION).getMenuComponentCount();

        features.dispose();
        features.dispose();

        assertThat(openDocument.stepModeListenerCount()).isEqualTo(listeners - 1);
        assertThat(view.toolBar().getComponentCount()).isEqualTo(toolBarItems - 4);
        assertThat(view.menu(MenuId.SIMULATION).getMenuComponentCount())
                .isEqualTo(menuItems - 2);
        assertThat(view.shortcutActions()).doesNotContainKeys("simulation.stepMode", "simulation.advance");
        assertThat(features.overlay().isTimerRunning()).isFalse();
    }
}
