package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.core.waveform.ChannelTrace;
import cl.almejo.vsim.core.waveform.Trace;
import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The waveform panel shows a {@link WaveformChannels} source (hito 2 of «Máquinas de estado»): the document's probes,
 * or a machine copy's own channels in its tab, with a title, rows that cannot be edited and state names; the general
 * panel's rows, cursor, fit and history notice use only its own channels, never the machines' {@code machine:} ones.
 */
class WaveformChannelsTest {

    private static final String STATE = "machine:c9:state";
    private static final String INPUT = "machine:c9:in:x";
    private static final String OUTPUT = "machine:c9:out:Z";

    private OpenDocument openDocument;
    private ProbeActions actions;
    private WaveformPanel panel;
    private WaveformSnapshot latest = WaveformSnapshot.EMPTY;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        actions = new ProbeActions(openDocument);
        panel = new WaveformPanel(openDocument, actions, () -> ColorScheme.LIGHT,
                (parent, title, message, initialText) -> Optional.empty(), probeId -> { }, () -> { });
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    /** A machine copy's three rows, named states on the state row, read-only, with a title. */
    private final class Strip implements WaveformChannels {

        @Override
        public List<ChannelRows.ChannelRow> rows() {
            return List.of(new ChannelRows.ChannelRow(INPUT, "x", true, Probe.FORMAT_HEX),
                    new ChannelRows.ChannelRow(STATE, "estado", true, Probe.FORMAT_HEX),
                    new ChannelRows.ChannelRow(OUTPUT, "Z", true, Probe.FORMAT_HEX));
        }

        @Override
        public Object rowsKey() {
            return "strip";
        }

        @Override
        public WaveformSnapshot latest() {
            return latest;
        }

        @Override
        public boolean hasChannels() {
            return true;
        }

        @Override
        public Optional<LongFunction<String>> valueNames(String channelId) {
            return channelId.equals(STATE) ? Optional.of(code -> "S" + code) : Optional.empty();
        }

        @Override
        public boolean editable() {
            return false;
        }

        @Override
        public Optional<String> title() {
            return Optional.of("Onda de Detector 11");
        }

        @Override
        public List<Action> headerActions() {
            return List.of();
        }
    }

    /** Switch c1 → NOT c2: probes on c1.out and c2.out. */
    private List<String> probes() {
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 4, -1,
                Map.of(StandardComponentTypes.NEGATE, true));
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

    @Test
    void anotherSourceShowsItsRowsAndTitleAndItsRestorerBringsTheProbeRowsBack() {
        probes();
        assertThat(panel.title()).isEmpty();

        Runnable restorer = panel.showChannels(new Strip());

        assertThat(panel.channelNames()).containsExactly("x", "estado", "Z");
        assertThat(panel.title()).contains("Onda de Detector 11");
        restorer.run();
        assertThat(panel.channelNames()).containsExactly("c1.out", "c2.out");
        assertThat(panel.title()).isEmpty();
    }

    @Test
    void aStateRowReadsTheStateNameAtTheCursorAndADashForNoState() {
        panel.showChannels(new Strip());
        panel.display(new WaveformSnapshot(1, 30, Map.of(INPUT, trace(0, Value.ONE),
                STATE, trace(0, Value.of(2, 0), 10, Value.of(2, 2), 20, Value.highImpedance(2)),
                OUTPUT, trace(0, Value.ZERO, 10, Value.ONE))));

        panel.axis().setCursor(15);
        assertThat(panel.names().values()).containsExactly("1", "S2", "1");
        panel.axis().setCursor(25);
        assertThat(panel.names().values()).containsExactly("1", "—", "1");
    }

    @Test
    void rowsThatCannotBeEditedHaveNoMenu() {
        List<String> ids = probes();
        assertThat(panel.menuOfRow(ids.get(0))).isPresent();

        panel.showChannels(new Strip());

        assertThat(panel.menuOfRow(STATE)).as("no rename, move, remove or «Mostrar como»").isEmpty();
    }

    @Test
    void theTimerRunsForAStripWithoutAnyProbe() {
        panel.overrideShowing(() -> true);
        assertThat(panel.isTimerRunning()).as("no probes").isFalse();

        Runnable restorer = panel.showChannels(new Strip());

        assertThat(panel.isTimerRunning()).isTrue();
        latest = new WaveformSnapshot(7, 40, Map.of(STATE, trace(0, Value.of(2, 1))));
        panel.timerTick();
        assertThat(panel.displayed()).isSameAs(latest);
        restorer.run();
        assertThat(panel.isTimerRunning()).isFalse();
    }

    @Test
    void theGeneralPanelsFitAndHistoryIgnoreTheMachinesOwnChannels() {
        List<String> ids = probes();
        panel.axis().setWidth(800);
        ChannelTrace longHistory = new ChannelTrace(1);
        for (int index = 0; index <= ChannelTrace.CHUNK_SIZE * ChannelTrace.MAXIMUM_CHUNKS; index++) {
            longHistory.append(index, Value.of(index % 2 == 1));
        }
        latest = new WaveformSnapshot(1, 70_000, Map.of(ids.get(0), trace(50_000, Value.ONE),
                STATE, longHistory.snapshot()));
        panel.display(latest);

        panel.button(Messages.text("waveform.fitAll")).doClick(0);

        assertThat(panel.axis().startTime()).as("from the probe's first event, not the machine's")
                .isGreaterThan(40_000);
        assertThat(panel.rulerToolTip()).as("the machine's truncated history is not the panel's")
                .doesNotContain("historial");

        panel.showChannels(new Strip());
        panel.button(Messages.text("waveform.fitAll")).doClick(0);
        assertThat(panel.axis().startTime()).as("the strip's own state row").isLessThan(2_000);
        assertThat(panel.rulerToolTip()).contains("historial");
    }
}
