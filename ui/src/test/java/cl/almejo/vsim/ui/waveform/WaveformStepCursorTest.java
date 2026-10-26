package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.ChannelTrace;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.actions.TextPrompt;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** «Avanzar» moves the waveform cursor (M6.1-B). */
class WaveformStepCursorTest {

    private static final TextPrompt NO_ANSWER = (parent, title, message, initialText) -> Optional.empty();

    private OpenDocument openDocument;
    private ProbeActions actions;
    private WaveformPanel panel;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        actions = new ProbeActions(openDocument);
        panel = new WaveformPanel(openDocument, actions, () -> ColorScheme.LIGHT, NO_ANSWER, probeId -> { },
                () -> { });
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private static Trace trace(Object... timesAndValues) {
        ChannelTrace channel = new ChannelTrace(1);
        for (int index = 0; index < timesAndValues.length; index += 2) {
            channel.append(((Number) timesAndValues[index]).longValue(), (Value) timesAndValues[index + 1]);
        }
        return channel.snapshot();
    }

    /** Switch → NOT with both outputs as channels, showing a recording up to t = 20 with edges at 10. */
    private void chainRecordedUntilTwenty() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component gate = place(openDocument, StandardComponentTypes.NOT, 4, -1, Map.of());
        List<String> ids = actions.addSelection(List.of(toggle.id(), gate.id())).addedIds();
        panel.probesChanged();
        panel.display(new WaveformSnapshot(1, 20, Map.of(ids.get(0), trace(0, Value.ONE, 10, Value.ZERO),
                ids.get(1), trace(0, Value.ZERO, 10, Value.ONE))));
    }

    @Test
    void placingTheCursorAtATimeMakesTheNameColumnReadThere() {
        chainRecordedUntilTwenty();
        int repaints = panel.repaintCount();

        panel.placeCursorAtTime(5);

        assertThat(panel.axis().cursor()).hasValue(5);
        assertThat(panel.names().values()).containsExactly("1", "0");
        assertThat(panel.repaintCount()).isGreaterThan(repaints);
        panel.placeCursorAtTime(10);
        assertThat(panel.names().values()).containsExactly("0", "1");
        assertThat(panel.axis().isFollowing()).as("following the current time goes on").isTrue();
    }

    @Test
    void withFollowOffATimeOutsideThePlotIsCentered() {
        chainRecordedUntilTwenty();
        panel.axis().setWidth(400);
        panel.axis().showDelays(1_000);

        panel.placeCursorAtTime(5);

        assertThat(panel.axis().startTime()).isCloseTo(-5, within(1e-9));
        assertThat(panel.followButton().isSelected()).isFalse();
    }

    @Test
    void followStepMovesTheCursorOnlyWhileThePanelIsShowing() {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(),
                List.of(new WaveformsExtension(NO_ANSWER, (parent, message) -> { })));
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            WaveformPanel viewPanel = (WaveformPanel) view.bottomDock().content().orElseThrow();
            view.bottomDock().hideDock();
            WaveformFeatures.followStep(view, 7);
            assertThat(viewPanel.axis().cursor()).isEmpty();

            view.bottomDock().showDock();
            WaveformFeatures.followStep(view, 7);
            assertThat(viewPanel.axis().cursor()).hasValue(7);
        } finally {
            view.dispose();
        }
    }

    @Test
    void followStepIgnoresAViewWithoutWaveforms() {
        EditorView view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        try {
            view.bottomDock().showDock();
            WaveformFeatures.followStep(view, 7);
            assertThat(view.bottomDock().content()).isEmpty();
        } finally {
            view.dispose();
        }
    }
}
