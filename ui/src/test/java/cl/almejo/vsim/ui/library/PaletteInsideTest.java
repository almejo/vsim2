package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.panels.PalettePanelAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The palette of a component tab (spec §6.5, §6.2 c): the definition itself and every definition that contains it
 * cannot be placed inside it, nor the interface pin parts while its interface is locked; each disabled entry says why.
 */
class PaletteInsideTest {

    private static final String INVERTER = SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER);
    private static final String DOUBLE_INVERTER = SubcircuitTypes.typeIdOf(ExampleDefinitions.DOUBLE_INVERTER);

    @TempDir
    Path directory;

    private final OpenDocument circuit = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private TemplateFixture fixture;

    @AfterEach
    void close() {
        if (fixture != null) {
            fixture.close();
        }
        circuit.close();
    }

    @Test
    void greysTheDefinitionItselfAndItsAncestors() {
        withADoubleInverterPlaced();
        fixture = new TemplateFixture(directory.resolve("library"),
                circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c1/c2")));
        PalettePanel palette = fixture.view.palette();

        assertThat(palette.disabledReason(INVERTER)).contains("No se puede usar dentro de sí mismo");
        assertThat(palette.disabledReason(DOUBLE_INVERTER)).contains("Contiene al componente que estás editando");
        assertThat(palette.disabledReason(StandardComponentTypes.AND)).isEmpty();
        assertThat(PalettePanelAccess.toolTip(palette, INVERTER)).contains("No se puede usar dentro de sí mismo");

        palette.choose(INVERTER);
        palette.choose(DOUBLE_INVERTER);

        assertThat(fixture.view.canvas().tool()).isSameAs(fixture.view.canvas().defaultTool());
        assertThat(PalettePanelAccess.hasSelection(palette)).isFalse();
        palette.choose(StandardComponentTypes.AND);
        assertThat(fixture.view.canvas().tool()).isNotSameAs(fixture.view.canvas().defaultTool());
    }

    @Test
    void greysThePinPartsWhileLocked() {
        withADoubleInverterPlaced();
        fixture = new TemplateFixture(directory.resolve("library"),
                circuit.openBody(ExampleDefinitions.INVERTER, Optional.of("c1/c2")));
        PalettePanel palette = fixture.view.palette();

        String locked = "Este componente se usa dentro de Doble inversor: sus pines no se pueden cambiar todavía";
        assertThat(palette.disabledReason(InterfacePins.INPUT_PIN)).contains(locked);
        assertThat(palette.disabledReason(InterfacePins.OUTPUT_PIN)).contains(locked);
    }

    @Test
    void aComponentUsedOnlyOnTheCanvasKeepsItsPinParts() {
        withADoubleInverterPlaced();
        fixture = new TemplateFixture(directory.resolve("library"),
                circuit.openBody(ExampleDefinitions.DOUBLE_INVERTER, Optional.of("c1")));
        PalettePanel palette = fixture.view.palette();

        assertThat(palette.disabledReason(InterfacePins.INPUT_PIN)).isEmpty();
        assertThat(palette.disabledReason(INVERTER)).isEmpty();
        assertThat(palette.disabledReason(DOUBLE_INVERTER)).contains("No se puede usar dentro de sí mismo");
    }

    @Test
    void theCircuitsOwnTabGreysNothing() {
        withADoubleInverterPlaced();
        fixture = new TemplateFixture(directory.resolve("library"), circuit);
        PalettePanel palette = fixture.view.palette();

        assertThat(List.of(INVERTER, DOUBLE_INVERTER, InterfacePins.INPUT_PIN))
                .allSatisfy(id -> assertThat(palette.disabledReason(id)).isEmpty());
    }

    /** The inverter and the double inverter (two inverters c2, c3 inside) in the circuit; a double inverter c1. */
    private void withADoubleInverterPlaced() {
        circuit.execute(new PutDefinitionsCommand(List.of(ExampleDefinitions.inverter(),
                ExampleDefinitions.doubleInverter())));
        circuit.execute(new AddElementsCommand(List.of(new Component(new ElementId("c1"), DOUBLE_INVERTER,
                new GridPoint(0, 0), Rotation.R0, Map.of(), ""))));
    }
}
