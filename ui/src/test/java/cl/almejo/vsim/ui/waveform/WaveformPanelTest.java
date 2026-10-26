package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.ChannelTrace;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.problems.Blink;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WaveformPanelTest {

    private final Deque<Optional<String>> answers = new ArrayDeque<>();
    private final List<String> prefilled = new ArrayList<>();
    private final List<String> shownInCircuit = new ArrayList<>();
    private int hides;
    private OpenDocument openDocument;
    private ProbeActions actions;
    private WaveformPanel panel;
    private Component gate;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        actions = new ProbeActions(openDocument);
        TextPrompt prompt = (parent, title, message, initialText) -> {
            prefilled.add(initialText);
            return answers.isEmpty() ? Optional.empty() : answers.removeFirst();
        };
        panel = new WaveformPanel(openDocument, actions, () -> ColorScheme.LIGHT, prompt, shownInCircuit::add,
                () -> hides++);
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    /** Switch c1 → NOT c2 (the gate): channels c1.out and c2.out. */
    private List<String> chain() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        gate = place(openDocument, StandardComponentTypes.NOT, 4, -1, Map.of());
        List<String> ids = actions.addSelection(List.of(toggle.id(), gate.id())).addedIds();
        panel.probesChanged();
        return ids;
    }

    private static Trace trace(Object... timesAndValues) {
        ChannelTrace channel = new ChannelTrace(1);
        for (int index = 0; index < timesAndValues.length; index += 2) {
            channel.append(((Number) timesAndValues[index]).longValue(), (Value) timesAndValues[index + 1]);
        }
        return channel.snapshot();
    }

    private void pressKey(int keyCode) {
        Object name = panel.plot().getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(keyCode, 0));
        perform(panel.plot().getActionMap().get(name));
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && item.getText().equals(text)) {
                return item;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu));
    }

    /** Runs {@code steps} on the event thread, where the panel's Swing timers fire, so they cannot interleave. */
    private static void onEventThread(Runnable steps) throws Exception {
        try {
            SwingUtilities.invokeAndWait(steps);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof AssertionError error) {
                throw error;
            }
            throw exception;
        }
    }

    @Test
    void theToolbarHasEveryCommandAndFollowStartsOn() {
        assertThat(panel.toolBarTexts()).containsExactly("Seguir", "Acercar", "Alejar", "Ver todo", "Ver retardos",
                "Limpiar trazas", "Ocultar");
        assertThat(panel.followButton().isSelected()).isTrue();
        panel.button("Ocultar").doClick(0);
        assertThat(hides).isEqualTo(1);
    }

    @Test
    void theToolbarButtonsShowOnlyAnIconWithATooltip() {
        List<String> names = panel.toolBarTexts();
        List<String> tooltips = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            AbstractButton button = panel.button(names.get(index));
            assertThat(button.getText()).as(names.get(index)).isNullOrEmpty();
            assertThat(button.getIcon()).as(names.get(index))
                    .isSameAs(Icons.get(WaveformIcons.names().get(index), Icons.MENU_SIZE));
            tooltips.add(button.getToolTipText());
        }
        assertThat(tooltips).containsExactly("Seguir el tiempo actual", "Acercar", "Alejar", "Ver todo lo registrado",
                "Ver retardos: 20 px por unidad", "Limpiar trazas", "Ocultar el panel");
        assertThat(panel.button("Seguir")).isSameAs(panel.followButton());
    }

    @Test
    void seguirLooksPressedWhileFollowingAndLetsGoOnAManualMove() {
        chain();
        panel.axis().setWidth(800);
        panel.display(new WaveformSnapshot(1, 4_000, Map.of()));

        panel.button("Acercar").doClick(0);
        assertThat(panel.axis().isFollowing()).as("zooming keeps the right edge on the current time").isTrue();
        assertThat(panel.followButton().isSelected()).isTrue();

        panel.scrollBy(-50);
        assertThat(panel.axis().isFollowing()).isFalse();
        assertThat(panel.followButton().isSelected()).isFalse();

        panel.followButton().doClick(0);
        assertThat(panel.axis().isFollowing()).isTrue();
        assertThat(panel.followButton().isSelected()).isTrue();
    }

    @Test
    void theNameColumnReadsEveryChannelAtTheCursorAndMarksDisconnectedOnes() {
        List<String> ids = chain();
        panel.display(new WaveformSnapshot(1, 20, Map.of(ids.get(0), trace(0, Value.ONE, 10, Value.ZERO),
                ids.get(1), trace(0, Value.ZERO, 10, Value.ONE))));

        assertThat(panel.channelNames()).containsExactly("c1.out", "c2.out");
        assertThat(panel.names().values()).as("no cursor: the current time").containsExactly("0", "1");
        panel.axis().setCursor(5);
        assertThat(panel.names().values()).containsExactly("1", "0");

        openDocument.execute(new RemoveElementsCommand(List.of(gate.id())));
        assertThat(panel.names().values()).containsExactly("1", "(sin conexión)");
    }

    @Test
    void aClickSnapsTheCursorToANearbyEdgeAndHoverShowsTheDelta() {
        List<String> ids = chain();
        panel.axis().setWidth(800);
        panel.axis().fit(0, 40); // 20 px per unit
        panel.display(new WaveformSnapshot(1, 30, Map.of(ids.get(0), trace(0, Value.ONE, 10, Value.ZERO))));

        panel.placeCursor(203);

        assertThat(panel.axis().cursor()).hasValue(10);
        assertThat(panel.hoverText(260)).isEqualTo("t = 13 · Δt = 3");
        assertThat(panel.ruler().cursorLabel()).contains("cursor t = 10");
        assertThat(panel.followButton().isSelected()).isFalse();
        panel.placeCursor(300);
        assertThat(panel.axis().cursor()).hasValue(15);
    }

    @Test
    void arrowKeysMoveTheCursorAlongTheSelectedRow() {
        List<String> ids = chain();
        panel.axis().setWidth(800);
        panel.axis().fit(0, 40);
        panel.display(new WaveformSnapshot(1, 40,
                Map.of(ids.get(0), trace(0, Value.ONE, 10, Value.ZERO, 25, Value.ONE))));
        panel.selectProbe(ids.get(0));
        panel.axis().setCursor(10);

        pressKey(KeyEvent.VK_RIGHT);
        assertThat(panel.axis().cursor()).hasValue(25);
        pressKey(KeyEvent.VK_LEFT);
        assertThat(panel.axis().cursor()).hasValue(10);
        pressKey(KeyEvent.VK_LEFT);
        assertThat(panel.axis().cursor()).hasValue(0);
        pressKey(KeyEvent.VK_LEFT);
        assertThat(panel.axis().cursor()).as("no earlier edge").hasValue(0);
    }

    @Test
    void theToolbarAndTheWheelDriveTheAxis() {
        List<String> ids = chain();
        panel.axis().setWidth(800);
        panel.display(new WaveformSnapshot(1, 4_000,
                Map.of(ids.get(0), trace(1_000, Value.ONE, 2_000, Value.ZERO))));
        panel.axis().setCursor(1_500);

        panel.button("Ver retardos").doClick(0);
        assertThat(panel.axis().scale()).isEqualTo(TimeAxis.DELAY_SCALE);
        assertThat(panel.axis().timeAt(400)).isCloseTo(1_500, within(1e-9));
        assertThat(panel.followButton().isSelected()).isFalse();

        panel.button("Ver todo").doClick(0);
        assertThat(panel.axis().startTime()).isCloseTo(1_000, within(1e-9));
        assertThat(panel.axis().endTime()).isCloseTo(4_000, within(1e-9));

        panel.followButton().doClick(0);
        assertThat(panel.axis().isFollowing()).isTrue();
        assertThat(panel.axis().endTime()).isCloseTo(4_000, within(1e-9));

        panel.wheel(400, 1, false);
        assertThat(panel.axis().isFollowing()).isFalse();
        assertThat(panel.followButton().isSelected()).isFalse();
        double scale = panel.axis().scale();
        panel.wheel(400, -1, true);
        assertThat(panel.axis().scale()).isCloseTo(scale * WaveformPanel.WHEEL_ZOOM_FACTOR, within(1e-12));
    }

    /** Review M1: a trackpad sends many small rotations; zoom follows how far the wheel turned. */
    @Test
    void ctrlWheelZoomFollowsHowFarTheWheelTurned() {
        chain();
        panel.axis().setWidth(800);
        panel.display(new WaveformSnapshot(1, 4_000, Map.of()));
        panel.wheel(400, 1, false); // stop following: the zoom keeps the time under the pointer
        double scale = panel.axis().scale();
        double timeUnderPointer = panel.axis().timeAt(300);

        panel.wheel(300, -0.5, true);
        panel.wheel(300, -0.5, true);
        assertThat(panel.axis().scale()).isCloseTo(scale * WaveformPanel.WHEEL_ZOOM_FACTOR, within(1e-9));
        assertThat(panel.axis().timeAt(300)).isCloseTo(timeUnderPointer, within(1e-6));

        panel.wheel(300, 2, true);
        assertThat(panel.axis().scale())
                .isCloseTo(scale / WaveformPanel.WHEEL_ZOOM_FACTOR, within(1e-9)); // wheel down zooms out

        double before = panel.axis().scale();
        panel.wheel(300, 0, true);
        assertThat(panel.axis().scale()).as("a rotation of 0 changes nothing").isEqualTo(before);
    }

    @Test
    void aResizeWhileFollowingKeepsTheCurrentTimeAtTheRightEdge() {
        chain();
        panel.axis().setWidth(800);
        panel.display(new WaveformSnapshot(1, 4_000, Map.of()));
        assertThat(panel.axis().endTime()).isCloseTo(4_000, within(1e-9));

        panel.plotResized(400);

        assertThat(panel.axis().width()).isEqualTo(400);
        assertThat(panel.axis().endTime()).isCloseTo(4_000, within(1e-9));
    }

    @Test
    void theRulerLabelsNoTimeBeforeZero() {
        chain();
        panel.axis().setWidth(800);
        panel.display(new WaveformSnapshot(1, 100, Map.of()));
        assertThat(panel.axis().startTime()).as("the view reaches before t = 0").isNegative();

        assertThat(panel.ruler().visibleTicks()).isNotEmpty().allMatch(tick -> tick >= 0);
        assertThat(panel.ruler().visibleTicks()).contains(0L);
    }

    @Test
    void clearTracesAsksTheDocumentToDropTheRecording() {
        List<String> ids = chain();
        waitUntil(() -> openDocument.latestWaveforms().time() > 50
                && openDocument.latestWaveforms().trace(ids.get(0)).isPresent());
        long clearedAfter = openDocument.latestWaveforms().time();

        panel.button("Limpiar trazas").doClick(0);

        waitUntil(() -> openDocument.latestWaveforms().trace(ids.get(0))
                .map(trace -> trace.firstTime().orElse(-1) >= clearedAfter).orElse(false));
        assertThat(panel.channelNames()).hasSize(2);
    }

    @Test
    void theTimerRunsOnlyWhileShowingWithChannelsAndRepaintsOnlyOnChange() throws Exception {
        openDocument.pause();
        waitUntil(() -> !openDocument.latestSnapshot().running());
        panel.overrideShowing(() -> true);
        assertThat(panel.isTimerRunning()).as("no channels").isFalse();

        List<String> ids = chain();
        assertThat(panel.isTimerRunning()).isTrue();
        waitUntil(() -> openDocument.latestWaveforms().traces().keySet().containsAll(ids));

        onEventThread(() -> {
            panel.timerTick();
            assertThat(panel.displayed()).isSameAs(openDocument.latestWaveforms());
            int painted = panel.repaintCount();
            panel.timerTick();
            panel.timerTick();
            assertThat(panel.repaintCount()).isEqualTo(painted);
        });

        panel.overrideShowing(() -> false);
        assertThat(panel.isTimerRunning()).isFalse();
    }

    @Test
    void theRowMenuEditsChannelsOneUndoStepAtATime() {
        List<String> ids = chain();
        assertThat(texts(panel.rowMenu(ids.get(1)))).containsExactly("Renombrar…", "---", "Subir", "Bajar", "---",
                "Quitar", "Quitar todos", "---", "Mostrar en el circuito");

        item(panel.rowMenu(ids.get(1)), "Subir").doClick(0);
        assertThat(panel.channelNames()).containsExactly("c2.out", "c1.out");
        openDocument.history().undo();
        assertThat(panel.channelNames()).containsExactly("c1.out", "c2.out");

        answers.add(Optional.of("salida"));
        item(panel.rowMenu(ids.get(1)), "Renombrar…").doClick(0);
        assertThat(panel.channelNames()).containsExactly("c1.out", "salida");
        answers.add(Optional.of(""));
        item(panel.rowMenu(ids.get(1)), "Renombrar…").doClick(0);
        assertThat(panel.channelNames()).containsExactly("c1.out", "c2.out");
        item(panel.rowMenu(ids.get(1)), "Renombrar…").doClick(0); // cancelled
        assertThat(panel.channelNames()).containsExactly("c1.out", "c2.out");

        item(panel.rowMenu(ids.get(0)), "Mostrar en el circuito").doClick(0);
        assertThat(shownInCircuit).containsExactly(ids.get(0));

        item(panel.rowMenu(ids.get(0)), "Quitar").doClick(0);
        assertThat(panel.channelNames()).containsExactly("c2.out");
        item(panel.rowMenu(ids.get(1)), "Quitar todos").doClick(0);
        assertThat(panel.channelNames()).isEmpty();
    }

    @Test
    void aRowBlinksForAFewTogglesThenStops() throws Exception {
        List<String> ids = chain();
        onEventThread(() -> {
            panel.blinkRow(ids.get(1));
            assertThat(panel.isBlinkLit(ids.get(1))).isTrue();
            assertThat(panel.isBlinkLit(ids.get(0))).isFalse();
            for (int toggle = 1; toggle < Blink.TOGGLES; toggle++) {
                panel.rowBlink().tick();
                assertThat(panel.isBlinkLit(ids.get(1))).isEqualTo(toggle % 2 == 0);
            }
            panel.rowBlink().tick();
            assertThat(panel.isBlinkLit(ids.get(1))).isFalse();
        });
    }

    @Test
    void theRulerTooltipSaysSinceWhenTheHistoryIsComplete() {
        List<String> ids = chain();
        panel.display(new WaveformSnapshot(1, 20, Map.of(ids.get(0), trace(0, Value.ONE))));
        assertThat(panel.rulerToolTip()).isEqualTo("t = 20");

        ChannelTrace longHistory = new ChannelTrace(1);
        for (int index = 0; index <= ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS; index++) {
            longHistory.append(index, Value.of(index % 2 == 1));
        }
        panel.display(new WaveformSnapshot(2, 70_000, Map.of(ids.get(0), longHistory.snapshot())));
        assertThat(panel.rulerToolTip()).isEqualTo("historial desde t = 1 024");
    }

    @Test
    void disposeStopsEverythingForGood() {
        List<String> ids = chain();
        panel.overrideShowing(() -> true);
        assertThat(panel.isTimerRunning()).isTrue();

        panel.dispose();
        assertThat(panel.isTimerRunning()).isFalse();
        panel.probesChanged();
        panel.overrideShowing(() -> true);
        panel.blinkRow(ids.get(0));

        assertThat(panel.isTimerRunning()).isFalse();
        assertThat(panel.isBlinkLit(ids.get(0))).isFalse();
    }

    @Test
    void thePlotShowsACrosshairAndTheRulerAMoveCursor() {
        assertThat(panel.plot().getCursor().getType()).isEqualTo(Cursor.CROSSHAIR_CURSOR);
        assertThat(panel.ruler().getCursor().getType()).isEqualTo(Cursor.MOVE_CURSOR);
    }

    @Test
    void theScaleLabelSitsAtTheRightOfTheToolbarAndFollowsZoom() {
        List<String> ids = chain();
        assertThat(panel.scaleLabel().getToolTipText())
                .isEqualTo("Tiempo de simulación entre dos marcas de la regla");
        panel.plotResized(800); // the first width shows the last 4 000 units: 0.2 px per unit
        assertThat(panel.scaleText()).isEqualTo("1 div = 500 t");

        panel.button("Acercar").doClick(0);
        assertThat(panel.scaleText()).isEqualTo("1 div = 200 t");
        panel.button("Alejar").doClick(0);
        panel.button("Alejar").doClick(0);
        assertThat(panel.scaleText()).isEqualTo("1 div = 1 000 t");

        panel.display(new WaveformSnapshot(1, 41_000, Map.of(ids.get(0), trace(1_000, Value.ONE))));
        panel.button("Ver todo").doClick(0); // 40 000 units in 800 px
        assertThat(panel.scaleText()).isEqualTo("1 div = 5 000 t");

        panel.button("Ver retardos").doClick(0); // 20 px per unit
        assertThat(panel.scaleText()).isEqualTo("1 div = 5 t");
        assertThat(panel.axis().tickSpacing()).isEqualTo(5);

        panel.wheel(400, -1, true);
        assertThat(panel.scaleText()).isEqualTo("1 div = " + TimeAxis.format(panel.axis().tickSpacing()) + " t");
    }

    @Test
    void theScaleLabelIsNotRewrittenWhileTheSpacingStaysTheSame() {
        chain();
        panel.plotResized(800);
        List<String> texts = new ArrayList<>();
        panel.scaleLabel().addPropertyChangeListener("text", event -> texts.add((String) event.getNewValue()));

        panel.display(new WaveformSnapshot(1, 5_000, Map.of()));
        panel.display(new WaveformSnapshot(2, 6_000, Map.of()));
        panel.plotResized(800);
        assertThat(texts).isEmpty();

        panel.plotResized(400); // same scale: still 500 per tick
        panel.button("Acercar").doClick(0);
        assertThat(texts).containsExactly("1 div = 200 t");
    }

    @Test
    void aChannelNeverRenamedFollowsTheLabelUntilItIsRenamed() {
        List<String> ids = chain();
        openDocument.execute(new SetLabelCommand(gate.id(), "A"));
        assertThat(panel.channelNames()).containsExactly("c1.out", "A.out");

        answers.add(Optional.of("suma"));
        item(panel.rowMenu(ids.get(1)), "Renombrar…").doClick(0);
        assertThat(prefilled).containsExactly("A.out");
        openDocument.execute(new SetLabelCommand(gate.id(), "B"));
        assertThat(panel.channelNames()).as("a user name stays").containsExactly("c1.out", "suma");

        answers.add(Optional.of("  "));
        item(panel.rowMenu(ids.get(1)), "Renombrar…").doClick(0);
        assertThat(prefilled).containsExactly("A.out", "suma");
        assertThat(panel.channelNames()).as("blank: live again").containsExactly("c1.out", "B.out");
        assertThat(openDocument.document().probes().get(1).name()).isEmpty();
    }

    @Test
    void acceptingTheLiveNameUnchangedKeepsItLive() {
        List<String> ids = chain();
        int undoSteps = undoDepth();

        answers.add(Optional.of("c2.out"));
        item(panel.rowMenu(ids.get(1)), "Renombrar…").doClick(0);

        assertThat(prefilled).containsExactly("c2.out");
        assertThat(openDocument.document().probes().get(1).name()).isEmpty();
        assertThat(undoDepth()).isEqualTo(undoSteps);
        openDocument.execute(new SetLabelCommand(gate.id(), "A"));
        assertThat(panel.channelNames()).containsExactly("c1.out", "A.out");
    }

    @Test
    void theRowsAreRebuiltOnlyWhenTheCircuitOrTheChannelsChange() {
        List<String> ids = chain();
        List<ChannelRows.ChannelRow> rows = panel.rows();
        assertThat(panel.rows()).isSameAs(rows);

        openDocument.execute(new SetLabelCommand(gate.id(), "A"));
        assertThat(panel.rows()).isNotSameAs(rows);
        rows = panel.rows();
        actions.moveUp(ids.get(1));
        assertThat(panel.rows()).isNotSameAs(rows);
        assertThat(panel.channelNames()).containsExactly("A.out", "c1.out");
    }

    @Test
    void theTimerRepaintsWhenALabelChangedEvenWithoutANewRecording() throws Exception {
        openDocument.pause();
        waitUntil(() -> !openDocument.latestSnapshot().running());
        chain();
        panel.overrideShowing(() -> true);
        onEventThread(() -> {
            panel.timerTick();
            panel.channelNames(); // the rows as shown now
            int painted = panel.repaintCount();
            openDocument.execute(new SetLabelCommand(gate.id(), "A"));
            panel.timerTick();
            assertThat(panel.repaintCount()).isGreaterThan(painted);
            assertThat(panel.channelNames()).containsExactly("c1.out", "A.out");
        });
    }

    private int undoDepth() {
        int depth = 0;
        while (openDocument.history().canUndo()) {
            openDocument.history().undo();
            depth++;
        }
        for (int step = 0; step < depth; step++) {
            openDocument.history().redo();
        }
        return depth;
    }

    @Test
    void selectRowSelectsAKnownChannelOnly() {
        List<String> ids = chain();
        panel.selectRow(ids.get(1));
        assertThat(panel.selectedProbe()).contains(ids.get(1));
        panel.selectRow("p99");
        assertThat(panel.selectedProbe()).contains(ids.get(1));
        panel.dispose();
        panel.selectRow(ids.get(0));
        assertThat(panel.selectedProbe()).contains(ids.get(1));
    }
}
