package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.paint.ColorScheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Ver estados y salidas en formas de onda» on a machine piece (hito 2, task 7): one state row, written with the
 * state names, and one row per output pin, in one undo step, saved with the circuit like any channel.
 */
class MachineStateProbesTest {

    private final List<ProbeActions.AddResult> results = new ArrayList<>();
    private OpenDocument openDocument;
    private ProbeActions actions;
    private WaveformPanel panel;
    private ElementId piece;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = MachineFixtures.circuitWithGeneratedMachines(1);
        piece = new ElementId(MachineFixtures.copies(openDocument).get(0));
        actions = new ProbeActions(openDocument);
        panel = new WaveformPanel(openDocument, actions, () -> ColorScheme.LIGHT,
                (parent, title, message, initialText) -> Optional.empty(), probeId -> { }, () -> { });
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private ComponentType typeOfPiece() {
        return openDocument.registry().typeOf(openDocument.document().component(piece).orElseThrow());
    }

    @Test
    void itAddsTheStateAndEveryOutputInOneUndoStep() {
        ProbeActions.AddResult result = actions.addMachineStatesAndOutputs(piece);
        panel.probesChanged();

        int outputZ = typeOfPiece().pinIndexOf("Z").orElseThrow();
        assertThat(openDocument.document().probes()).extracting(Probe::target).containsExactly(
                new ProbeTarget.MachineStateTarget(piece), new ProbeTarget.PinTarget(piece, outputZ));
        assertThat(result.addedIds()).hasSize(2);
        assertThat(panel.channelNames()).containsExactly(piece.value() + " · estado", piece.value() + ".Z");

        openDocument.history().undo();
        assertThat(openDocument.document().probes()).isEmpty();
    }

    @Test
    void aSecondTimeTheStateRowBlinks() {
        String stateRow = actions.addMachineStatesAndOutputs(piece).addedIds().getFirst();

        ProbeActions.AddResult again = actions.addMachineStatesAndOutputs(piece);

        assertThat(again.addedAny()).isFalse();
        assertThat(again.existingId()).contains(stateRow);
    }

    @Test
    void theStateRowIsWrittenWithTheStateNamesAndHasNoFormatToChoose() {
        List<String> ids = actions.addMachineStatesAndOutputs(piece).addedIds();
        panel.probesChanged();

        UiTestSupport.awaitSimulation(openDocument);
        UiTestSupport.waitUntil(() -> openDocument.latestWaveforms().trace(ids.getFirst())
                .filter(trace -> trace.lastTime().isPresent()
                        && trace.valueAt(trace.lastTime().getAsLong()).filter(value -> value.isFullyDefined())
                        .isPresent())
                .isPresent());
        panel.display(openDocument.latestWaveforms());

        assertThat(panel.names().values()).containsExactly("S0", "0");
        assertThat(texts(panel.menuOfRow(ids.getFirst()).orElseThrow()))
                .contains(Messages.text("waveform.rename"))
                .doesNotContain(Messages.text("waveform.showAs"));
    }

    @Test
    void aDeletedPieceLeavesItsRowsWithoutAConnection() {
        actions.addMachineStatesAndOutputs(piece);
        panel.probesChanged();

        openDocument.execute(new RemoveElementsCommand(List.of(piece)));

        assertThat(panel.names().values()).containsExactly("(sin conexión)", "(sin conexión)");
        openDocument.history().undo();
        assertThat(panel.names().values()).doesNotContain("(sin conexión)");
    }

    @Test
    void theContextMenuOffersItRightAfterThePins() {
        WaveformMenuContributor contributor = new WaveformMenuContributor(actions, results::add);
        Component machine = openDocument.document().component(piece).orElseThrow();
        Component toggle = place(openDocument, StandardComponentTypes.SWITCH, -20, 0, Map.of());

        JPopupMenu machineMenu = new JPopupMenu();
        contributor.contribute(machineMenu, Optional.of(machine), new Selection(), new Point2D.Double(1, 1));
        JPopupMenu switchMenu = new JPopupMenu();
        contributor.contribute(switchMenu, Optional.of(toggle), new Selection(), new Point2D.Double(1, 1));

        assertThat(texts(machineMenu)).containsExactly("Ver pines en formas de onda",
                "Ver estados y salidas en formas de onda");
        assertThat(texts(switchMenu)).containsExactly("Ver pines en formas de onda");
        ((JMenuItem) machineMenu.getComponent(1)).doClick(0);
        assertThat(results).singleElement().satisfies(result -> assertThat(result.addedIds()).hasSize(2));
    }

    @Test
    void theStateRowsMarkerSitsOnThePiece() {
        actions.addMachineStatesAndOutputs(piece);
        Component machine = openDocument.document().component(piece).orElseThrow();
        Bounds box = typeOfPiece().bounds(machine);

        List<ProbeMarker> markers = ProbeMarkers.of(openDocument.document(), openDocument.netlist(),
                openDocument.registry(), probe -> probe.id());

        assertThat(markers).hasSize(2);
        ProbeMarker state = markers.getFirst();
        assertThat(state.side()).isEqualTo(ProbeMarker.Side.TOP);
        assertThat(state.anchor().y()).isEqualTo(box.y());
        assertThat(state.anchor().x()).isBetween(box.x(), box.maximumX());
    }
}
