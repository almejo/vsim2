package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.BusLabels;
import cl.almejo.vsim.ui.canvas.RenderState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cl.almejo.vsim.ui.UiTestSupport.waitUntil;
import static cl.almejo.vsim.ui.library.TemplateFixture.perform;
import static org.assertj.core.api.Assertions.assertThat;

class BusEndToEndTest {

    private static final VisibleState LED_ZERO = new VisibleState.LedState(Value.ZERO);
    private static final VisibleState LED_ONE = new VisibleState.LedState(Value.ONE);
    private static final VisibleState LED_FLOATING = new VisibleState.LedState(Value.HIGH_IMPEDANCE);
    private static final Map<String, Object> EIGHT_BITS = Map.of(InterfacePins.WIDTH, 8);

    @TempDir
    Path directory;

    private static List<String> busLabels(TemplateFixture fixture) {
        OpenDocument openDocument = fixture.openDocument;
        return BusLabels.of(new RenderState(openDocument.document(), openDocument.netlist(),
                        openDocument.netlistToken(), openDocument.latestSnapshot(), Set.of(), 0))
                .stream().map(BusLabels.BusLabel::text).toList();
    }

    /** Waits for a snapshot of the current netlist and hands it to the Problems controller, as a repaint would. */
    private static SimulationSnapshot showCurrentSnapshot(TemplateFixture fixture) {
        waitUntil(() -> fixture.openDocument.latestSnapshot().token() == fixture.openDocument.netlistToken());
        SimulationSnapshot snapshot = fixture.openDocument.latestSnapshot();
        fixture.view.problems().showSnapshot(snapshot);
        return snapshot;
    }

    private static MouseEvent hoverAt(TemplateFixture fixture, int gridX, int gridY) {
        return new MouseEvent(fixture.view.canvas(), MouseEvent.MOUSE_MOVED, 0, 0, gridX * 8, gridY * 8, 0, false);
    }

    @Test
    void aBusRunsThroughAWideComponentIntoASplitterAndAMismatchIsReportedUntilUndone() throws Exception {
        Path file = directory.resolve("bus.vsim");
        try (TemplateFixture fixture = new TemplateFixture(directory.resolve("library"))) {
            // an 8-bit pass-through made through the create dialog; it replaces the selection at (0, 1)
            Component input = fixture.place(InterfacePins.INPUT_PIN, 0, 1, EIGHT_BITS);
            Component output = fixture.place(InterfacePins.OUTPUT_PIN, 2, 1, EIGHT_BITS);
            fixture.select(input, output);
            fixture.dialogs.acceptForm(form -> form.setName("Copia"));
            perform(fixture.features.actions().createComponent());
            Component copy = fixture.single(SubcircuitTypes.typeIdOf("copia"));
            assertThat(fixture.openDocument.registry().typeOf(copy).pins()).extracting(PinSpec::width)
                    .containsExactly(8, 8);

            // source (pin 0,3) → copy (a 0,3; y 6,3) → wire → 4.5 px bus → splitter (bus 16,3; b0 18,4) → LED on b0
            Component source = fixture.place(InterfacePins.INPUT_PIN, -2, 2, EIGHT_BITS);
            fixture.openDocument.execute(new AddWireCommand(new Wire(fixture.openDocument.document().newWireId(),
                    new GridPoint(6, 3), new GridPoint(16, 3))));
            fixture.place(StandardComponentTypes.BUS_SPLITTER, 16, 3, Map.of(StandardComponentTypes.WIDTH, 8));
            Component led = fixture.place(StandardComponentTypes.LED, 17, 1, Map.of());
            waitUntil(() -> fixture.stateOf(led).equals(LED_ZERO));

            fixture.openDocument.interact(source.id());
            waitUntil(() -> fixture.stateOf(led).equals(LED_ONE));
            waitUntil(() -> busLabels(fixture).equals(List.of("0xFF")));
            showCurrentSnapshot(fixture);
            assertThat(fixture.view.statusBar().text()).doesNotContain("⚠");

            // a 1-bit LED touching the bus: mismatch everywhere, the splitter reads Z
            fixture.place(StandardComponentTypes.LED, 9, 0, Map.of()); // its pin (10, 3) is on the bus wire
            waitUntil(() -> fixture.stateOf(led).equals(LED_FLOATING));
            assertThat(showCurrentSnapshot(fixture).widthMismatches()).hasSize(1);
            assertThat(fixture.view.statusBar().text()).contains("⚠ 1 ancho incompatible");
            assertThat(fixture.view.canvas().getToolTipText(hoverAt(fixture, 13, 3)))
                    .startsWith("⚠ Anchos incompatibles");
            assertThat(busLabels(fixture)).isEmpty();

            fixture.openDocument.history().undo();
            waitUntil(() -> fixture.stateOf(led).equals(LED_ONE));
            assertThat(showCurrentSnapshot(fixture).widthMismatches()).isEmpty();
            assertThat(fixture.view.statusBar().text()).doesNotContain("⚠");

            fixture.openDocument.save(file);
        }

        try (OpenDocument reopened = OpenDocument.open(file, StandardComponentTypes.registry())) {
            Component splitter = reopened.document().components().stream()
                    .filter(component -> component.typeId().equals(StandardComponentTypes.BUS_SPLITTER))
                    .findFirst().orElseThrow();
            assertThat(reopened.registry().typeOf(splitter).pinCount()).isEqualTo(9);
            Component copy = reopened.document().components().stream()
                    .filter(component -> component.typeId().equals(SubcircuitTypes.typeIdOf("copia")))
                    .findFirst().orElseThrow();
            assertThat(reopened.registry().typeOf(copy).pins()).extracting(PinSpec::width).containsExactly(8, 8);
        }
    }
}
