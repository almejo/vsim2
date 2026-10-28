package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.bundled.ExampleSimulation;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.value.Value;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format 5 made the register thinner (12 x 7 → 12 x 4, 12 x 5 over 16 bits): EN, CLR and CLK moved up from y = 7.
 * A file of format 4 or older opens with a short wire from each moved pin to where it was, when something was
 * connected there, so the circuit stays wired and behaves the same.
 */
class RegisterShapeMigrationTest {

    private static final int ENABLE = 1;
    private static final int CLEAR = 2;
    private static final int CLOCK = 3;
    private static final int OUTPUT = 4;
    private static final int OLD_BOTTOM = 7;
    private static final GridPoint POSITION = new GridPoint(40, 40);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    @Test
    void theRegisterIsTwelveByFourUpToSixteenBitsAndTwelveByFiveAbove() {
        ComponentType register = registry.require(StandardComponentTypes.REGISTER);
        for (int width : new int[]{1, 8, 16}) {
            ComponentType shaped = register.specializedFor(Map.of(StandardComponentTypes.WIDTH, width));
            assertThat(shaped.size().width()).isEqualTo(12);
            assertThat(shaped.size().height()).as("%d bits", width).isEqualTo(4);
            assertThat(shaped.pins()).extracting(pin -> pin.position()).containsExactly(new GridPoint(0, 2),
                    new GridPoint(6, 4), new GridPoint(8, 4), new GridPoint(10, 4), new GridPoint(12, 2));
        }
        for (int width : new int[]{17, 32}) {
            ComponentType shaped = register.specializedFor(Map.of(StandardComponentTypes.WIDTH, width));
            assertThat(shaped.size().height()).as("%d bits", width).isEqualTo(5);
            assertThat(shaped.pins()).extracting(pin -> pin.position()).containsExactly(new GridPoint(0, 2),
                    new GridPoint(6, 5), new GridPoint(8, 5), new GridPoint(10, 5), new GridPoint(12, 2));
        }
        assertThat(registry.require(StandardComponentTypes.COUNTER).size().height()).as("the counter keeps 12 x 7")
                .isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(Rotation.class)
    void anOldWiredRegisterOpensConnectedInEveryRotation(Rotation rotation) throws Exception {
        CircuitDocument document = format.read(circuit(4, rotation, 8, true));

        Component register = register(document);
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        for (int pin : new int[]{ENABLE, CLEAR, CLOCK}) {
            assertThat(netlist.netForPin(new PinRef(register.id(), pin)).orElseThrow().wires())
                    .as("pin %d in %s", pin, rotation).contains(new ElementId("w" + pin));
        }
        assertThat(netlist.netForPin(new PinRef(register.id(), OUTPUT)).orElseThrow().wires())
                .contains(new ElementId("w" + OUTPUT));
        assertThat(document.wires()).hasSize(4 + 3);
        for (Wire stub : stubs(document)) {
            assertThat(length(stub)).isEqualTo(3);
        }
    }

    @Test
    void anOldUnconnectedRegisterGetsNoStubs() throws Exception {
        CircuitDocument document = format.read(circuit(4, Rotation.R90, 8, false));

        assertThat(document.wires()).isEmpty();
    }

    @Test
    void aPinOfAnotherPartOnTheOldPositionGetsAStubToo() throws Exception {
        // A switch's output (4, 1) right on the old CLK (10, 7): before format 5 they touched with no wire.
        GridPoint switchPosition = POSITION.plus(new GridPoint(10 - 4, OLD_BOTTOM - 1));
        String json = """
                {"format": "vsim2", "formatVersion": 4, "kind": "circuit", "name": "old",
                 "components": [%s,
                   {"id": "c2", "type": "switch", "x": %d, "y": %d, "rotation": 0, "label": "", "parameters": {}}],
                 "wires": [], "labels": []}
                """.formatted(registerEntry("c1", Rotation.R0, 8), switchPosition.x(), switchPosition.y());

        CircuitDocument document = format.read(json);

        Netlist netlist = new NetlistCompiler(registry).compile(document);
        assertThat(document.wires()).hasSize(1);
        assertThat(netlist.netForPin(new PinRef(new ElementId("c1"), CLOCK)).orElseThrow().pins())
                .contains(new PinRef(new ElementId("c2"), 0));
    }

    @Test
    void aWideRegisterGetsStubsFromItsBottomRowAtFive() throws Exception {
        CircuitDocument document = format.read(circuit(4, Rotation.R0, 32, true));

        assertThat(stubs(document)).hasSize(3).allSatisfy(stub -> assertThat(length(stub)).isEqualTo(2));
    }

    @Test
    void aFormatFiveFileIsLeftAlone() throws Exception {
        CircuitDocument document = format.read(circuit(5, Rotation.R0, 8, true));

        assertThat(document.wires()).hasSize(4);
    }

    @Test
    void theMigrationIsIdempotentAndKeepsIdsUnique() throws Exception {
        ObjectNode root = (ObjectNode) new ObjectMapper().readTree(circuit(4, Rotation.R0, 8, true));
        RegisterShapeMigration.migrate(root);
        String once = root.toString();

        RegisterShapeMigration.migrate(root);

        assertThat(root.toString()).isEqualTo(once);
        assertThat(root.path("wires").findValuesAsText("id")).doesNotHaveDuplicates()
                .doesNotContain("c1");
    }

    @Test
    void aRegisterInsideAUserComponentIsMigratedToo() throws Exception {
        String json = """
                {"format": "vsim2", "formatVersion": 4, "kind": "circuit", "name": "old",
                 "components": [], "wires": [], "labels": [],
                 "definitions": [{"id": "keeper", "name": "Guarda", "description": "", "width": 4, "height": 4,
                   "pinout": [], "components": [%s], "wires": [%s], "labels": []}]}
                """.formatted(registerEntry("c1", Rotation.R180, 8), controlWires(Rotation.R180));

        CircuitDocument document = format.read(json);

        ComponentDefinition definition = document.definition("keeper").orElseThrow();
        assertThat(definition.body().wires()).hasSize(3 + 3);
    }

    @Test
    void aCommittedFormatFourCircuitWithARotatedRegisterOpensWiredAndLoadsOnTheFallingEdge() throws Exception {
        String legacy = Files.readString(Path.of("src/test/resources/legacy-formats/register-version-4.vsim"));
        assertThat(legacy).contains("\"formatVersion\" : 4");
        CircuitDocument document = format.read(legacy);

        ExampleSimulation simulation = new ExampleSimulation(document, Map.of()).settle()
                .setInput("Dato", Value.of(8, 37)).settle();
        assertThat(simulation.outputPin("Q")).isEqualTo(Value.of(8, 0));

        simulation.toggle("Reloj").settle();

        assertThat(simulation.outputPin("Q")).isEqualTo(Value.of(8, 37));
        simulation.toggle("Borrar").settle();
        assertThat(simulation.outputPin("Q")).as("CLR is connected too").isEqualTo(Value.of(8, 0));
    }

    private static Component register(CircuitDocument document) {
        return document.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.REGISTER)).findFirst()
                .orElseThrow();
    }

    /** The wires the migration added: every id that is not one of the file's {@code w1} … {@code w4}. */
    private static List<Wire> stubs(CircuitDocument document) {
        List<String> original = List.of("w1", "w2", "w3", "w4");
        return document.wires().stream().filter(wire -> !original.contains(wire.id().value())).toList();
    }

    private static int length(Wire wire) {
        return Math.abs(wire.end().x() - wire.start().x()) + Math.abs(wire.end().y() - wire.start().y());
    }

    /**
     * A circuit of format {@code version} with one register at {@link #POSITION}; when {@code wired}, a wire leaves
     * each old bottom pin (ids w1, w2, w3 for EN, CLR, CLK) and Q (w4), three units outwards.
     */
    private static String circuit(int version, Rotation rotation, int width, boolean wired) {
        String wires = wired ? controlWires(rotation) + ", " + wire("w4", rotation, new GridPoint(12, 2),
                new GridPoint(15, 2)) : "";
        return """
                {"format": "vsim2", "formatVersion": %d, "kind": "circuit", "name": "old",
                 "components": [%s], "wires": [%s], "labels": []}
                """.formatted(version, registerEntry("c1", rotation, width), wires);
    }

    private static String registerEntry(String id, Rotation rotation, int width) {
        return """
                {"id": "%s", "type": "register", "x": %d, "y": %d, "rotation": %d, "label": "",
                 "parameters": {"width": %d}}""".formatted(id, POSITION.x(), POSITION.y(), rotation.quarterTurns(),
                width);
    }

    private static String controlWires(Rotation rotation) {
        return String.join(", ",
                wire("w1", rotation, new GridPoint(6, OLD_BOTTOM), new GridPoint(6, OLD_BOTTOM + 3)),
                wire("w2", rotation, new GridPoint(8, OLD_BOTTOM), new GridPoint(8, OLD_BOTTOM + 3)),
                wire("w3", rotation, new GridPoint(10, OLD_BOTTOM), new GridPoint(10, OLD_BOTTOM + 3)));
    }

    private static String wire(String id, Rotation rotation, GridPoint localStart, GridPoint localEnd) {
        GridPoint start = POSITION.plus(rotation.apply(localStart));
        GridPoint end = POSITION.plus(rotation.apply(localEnd));
        return """
                {"id": "%s", "x1": %d, "y1": %d, "x2": %d, "y2": %d}""".formatted(id, start.x(), start.y(), end.x(),
                end.y());
    }
}
