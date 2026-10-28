package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.bundled.ExampleSimulation;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format 5 gave the D flip-flop a Q̅ pin at (4, 3), right under Q. A wire that leaves Q straight down passes over that
 * point, and a pin joins any wire over it, so an old circuit would short Q with Q̅. Every D flip-flop of a file of
 * format 4 or older therefore opens with «Mostrar Q̅» off: the same three pins it always had.
 */
class FlipFlopNotOutputMigrationTest {

    private static final int OUTPUT = 1;
    private static final int NOT_OUTPUT = 3;
    private static final GridPoint POSITION = new GridPoint(20, 10);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void anOldDFlipFlopWhoseQWireGoesDownIsNotShortedInEveryRotation(Rotation rotation) throws Exception {
        String json = circuit(4, flipFlopEntry("c1", rotation), wire("w2", rotation, new GridPoint(4, 2),
                new GridPoint(4, 10)));

        CircuitDocument document = format.read(json);

        Component flipFlop = document.components().getFirst();
        assertThat(flipFlop.parameters()).containsEntry(StandardComponentTypes.SHOW_INVERTED, false);
        assertThat(registry.typeOf(flipFlop).pins()).hasSize(3);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        assertThat(netlist.netForPin(new PinRef(flipFlop.id(), OUTPUT)).orElseThrow().pins())
                .containsExactly(new PinRef(flipFlop.id(), OUTPUT));
    }

    @Test
    void anOldDFlipFlopWithQWiredDownSimulatesAsBefore() throws Exception {
        // D from a switch, the clock from a switch at 1, Q straight down to an LED: the wire passes over (4, 3).
        String json = circuit(4, String.join(", ", flipFlopEntry("c1", Rotation.R0),
                        switchEntry("c2", "D", "0", new GridPoint(14, 11)),
                        switchEntry("c3", "Reloj", "1", new GridPoint(16, 17)),
                        """
                                {"id": "c4", "type": "led", "x": 23, "y": 17, "rotation": 0, "label": "Q",
                                 "parameters": {}}"""),
                String.join(", ", plainWire("w5", 18, 12, 20, 12), plainWire("w6", 20, 18, 22, 18),
                        plainWire("w7", 22, 18, 22, 14), plainWire("w8", 24, 12, 24, 20)));

        ExampleSimulation simulation = new ExampleSimulation(format.read(json), Map.of()).settle();
        assertThat(simulation.led("Q")).isEqualTo(Value.ZERO);

        simulation.toggle("D").settle().toggle("Reloj").settle();

        assertThat(simulation.led("Q")).as("latched on the falling edge, no short").isEqualTo(Value.ONE);
    }

    @Test
    void aDFlipFlopInsideAnOldDefinitionHasNoNotOutputEither() throws Exception {
        String json = """
                {"format": "vsim2", "formatVersion": 4, "kind": "circuit", "name": "old",
                 "components": [], "wires": [], "labels": [],
                 "definitions": [{"id": "keeper", "name": "Guarda", "description": "", "width": 4, "height": 4,
                   "pinout": [], "components": [%s], "wires": [], "labels": []}]}
                """.formatted(flipFlopEntry("c1", Rotation.R0));

        CircuitDocument document = format.read(json);

        ComponentDefinition definition = document.definition("keeper").orElseThrow();
        assertThat(definition.body().components().getFirst().parameters())
                .containsEntry(StandardComponentTypes.SHOW_INVERTED, false);
    }

    @Test
    void anOldFragmentPastesItsDFlipFlopWithoutNotOutput() throws Exception {
        CircuitFragment fragment = new FragmentFormat(registry).read("""
                {"formatVersion": 4, "kind": "fragment", "components": [%s]}
                """.formatted(flipFlopEntry("c1", Rotation.R0)));

        assertThat(fragment.components().getFirst().parameters())
                .containsEntry(StandardComponentTypes.SHOW_INVERTED, false);
    }

    @Test
    void anOldLibraryComponentFileOpensItsDFlipFlopWithoutNotOutput() throws Exception {
        DefinitionFormat definitionFormat = new DefinitionFormat(registry);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = (ObjectNode) mapper.readTree(definitionFormat.write(
                new DefinitionBundle(ExampleDefinitions.inverter(), List.of())));
        root.put(FileHeader.FORMAT_VERSION_KEY, 4);
        ((ArrayNode) root.path("components")).add(mapper.readTree(flipFlopEntry("c90", Rotation.R0)));

        DefinitionBundle bundle = definitionFormat.read(root.toString());

        assertThat(bundle.root().body().components()).filteredOn(component -> component.id().value().equals("c90"))
                .singleElement().satisfies(flipFlop -> assertThat(flipFlop.parameters())
                        .containsEntry(StandardComponentTypes.SHOW_INVERTED, false));
    }

    @Test
    void aFormatFiveDFlipFlopKeepsItsSavedChoiceAndANewOneShowsNotOutput() throws Exception {
        CircuitDocument document = format.read(circuit(5, flipFlopEntry("c1", Rotation.R0), ""));

        Component flipFlop = document.components().getFirst();
        assertThat(flipFlop.parameters()).containsEntry(StandardComponentTypes.SHOW_INVERTED, true);
        assertThat(registry.typeOf(flipFlop).pins()).hasSize(4);
    }

    @Test
    void theMigrationIsIdempotent() throws Exception {
        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(circuit(4, flipFlopEntry("c1", Rotation.R0), ""));
        FlipFlopNotOutputMigration.migrate(root);
        String once = root.toString();

        FlipFlopNotOutputMigration.migrate(root);

        assertThat(root.toString()).isEqualTo(once);
    }

    @Test
    void theNotOutputIsTheLastPinOnlyWhenShown() {
        Component shown = registry.require(StandardComponentTypes.FLIP_FLOP_DATA).newComponent(new ElementId("c1"),
                POSITION, Rotation.R0, Map.of(), "");
        Component hidden = shown.withParameters(Map.of(StandardComponentTypes.SHOW_INVERTED, false));

        assertThat(registry.typeOf(shown).pins()).hasSize(NOT_OUTPUT + 1);
        assertThat(registry.typeOf(hidden).pins()).extracting(pin -> pin.name())
                .containsExactly("d", "q", "clock");
    }

    private static String circuit(int version, String components, String wires) {
        return """
                {"format": "vsim2", "formatVersion": %d, "kind": "circuit", "name": "old",
                 "components": [%s], "wires": [%s], "labels": []}
                """.formatted(version, components, wires);
    }

    private static String flipFlopEntry(String id, Rotation rotation) {
        return """
                {"id": "%s", "type": "flip-flop-data", "x": %d, "y": %d, "rotation": %d, "label": "",
                 "parameters": {"delay": 1}}""".formatted(id, POSITION.x(), POSITION.y(), rotation.quarterTurns());
    }

    private static String switchEntry(String id, String label, String value, GridPoint position) {
        return """
                {"id": "%s", "type": "switch", "x": %d, "y": %d, "rotation": 0, "label": "%s",
                 "parameters": {"value": "%s"}}""".formatted(id, position.x(), position.y(), label, value);
    }

    private static String wire(String id, Rotation rotation, GridPoint localStart, GridPoint localEnd) {
        GridPoint start = POSITION.plus(rotation.apply(localStart));
        GridPoint end = POSITION.plus(rotation.apply(localEnd));
        return plainWire(id, start.x(), start.y(), end.x(), end.y());
    }

    private static String plainWire(String id, int startX, int startY, int endX, int endY) {
        return """
                {"id": "%s", "x1": %d, "y1": %d, "x2": %d, "y2": %d}""".formatted(id, startX, startY, endX, endY);
    }
}
