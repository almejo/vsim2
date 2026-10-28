package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.GateEvaluation;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.value.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Files written before the gates had parameters (format 3 and older) name a gate by operation and inputs
 * ({@code nand3}) and the inverter {@code not}: they open as the parameterized types, with the same pins in the same
 * places, and save as format 4.
 */
class LegacyGateMigrationTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    @ParameterizedTest(name = "{0} → {1}, {2} inputs, negated {3}")
    @CsvSource({
            "and2,and,2,false", "and3,and,3,false", "and4,and,4,false",
            "or2,or,2,false", "or3,or,3,false", "or4,or,4,false",
            "xor2,xor,2,false", "xor3,xor,3,false", "xor4,xor,4,false",
            "nand2,and,2,true", "nand3,and,3,true", "nand4,and,4,true",
            "nor2,or,2,true", "nor3,or,3,true", "nor4,or,4,true"})
    void anOldGateOpensAsTheParameterizedTypeWithItsPinsInPlace(String oldId, String typeId, int inputs,
                                                                 boolean negated) throws Exception {
        CircuitDocument document = format.read(circuitWith(oldId, 3));

        Component gate = document.components().getFirst();
        assertThat(gate.typeId()).isEqualTo(typeId);
        assertThat(gate.parameters()).containsEntry("inputs", inputs).containsEntry("negate", negated)
                .containsEntry("width", 1).containsEntry("delay", 7);
        ComponentType type = registry.typeOf(gate);
        assertThat(type.forComponent(gate).pinCount()).isEqualTo(inputs + 1);
        for (int pin = 0; pin <= inputs; pin++) {
            assertThat(type.pinLocation(gate, pin)).as("pin %d", pin).isEqualTo(oldPinLocation(inputs, pin));
        }
        assertThat(document.probes().getFirst().target())
                .isEqualTo(new ProbeTarget.PinTarget(gate.id(), inputs));
        assertSameTruthTable(oldId, inputs, type, gate.parameters());
    }

    @Test
    void anOldNotOpensAsANegatedBuffer() throws Exception {
        CircuitDocument document = format.read(circuitWith("not", 3));

        Component gate = document.components().getFirst();
        assertThat(gate.typeId()).isEqualTo("buffer");
        assertThat(gate.parameters()).isEqualTo(Map.of("width", 1, "negate", true, "delay", 7));
        ComponentType type = registry.typeOf(gate);
        assertThat(type.pinLocation(gate, 0)).isEqualTo(rotated(new GridPoint(0, 2)));
        assertThat(type.pinLocation(gate, 1)).isEqualTo(rotated(new GridPoint(4, 2)));
        assertThat(GateEvaluation.output(type, gate.parameters(), List.of(Value.ONE))).isEqualTo(Value.ZERO);
    }

    @Test
    void aMigratedCircuitSavesAsTheCurrentFormatAndReadsBackTheSame() throws Exception {
        CircuitDocument migrated = format.read(circuitWith("nor3", 3));

        String saved = format.write(migrated);

        assertThat(saved).contains("\"formatVersion\" : " + CircuitFormat.FORMAT_VERSION)
                .contains("\"type\" : \"or\"").doesNotContain("nor3");
        assertThat(format.write(format.read(saved))).isEqualTo(saved);
    }

    @Test
    void aCommittedFormatThreeFileOpensAsTheSameCircuitAsItsCurrentRewrite() throws Exception {
        String legacy = Files.readString(Path.of("src/test/resources/legacy-formats/gates-version-3.vsim"));
        String current = Files.readString(Path.of("src/test/resources/fixtures/m3/rotated-gates.vsim"));

        assertThat(legacy).contains("\"formatVersion\" : 3").contains("\"not\"");
        assertThat(current).contains("\"formatVersion\" : " + CircuitFormat.FORMAT_VERSION);
        assertThat(format.write(format.read(legacy)))
                .isEqualTo(format.write(format.read(current)));
    }

    @Test
    void aFormatFourFileIsNotMigrated() {
        assertThat(LegacyGateMigration.needsMigration(4)).isFalse();
        assertThat(LegacyGateMigration.needsMigration(3)).isTrue();
        assertThat(LegacyGateMigration.needsMigration(2)).isTrue();
        ObjectNode root = (ObjectNode) parse(circuitWith("and2", 4));

        LegacyGateMigration.migrate(root);

        assertThat(root.path("components").get(0).path("type").asText()).isEqualTo("and2");
    }

    @Test
    void theMigrationIsIdempotent() {
        ObjectNode root = (ObjectNode) parse(circuitWith("nand4", 3));
        LegacyGateMigration.migrate(root);
        String once = root.toString();

        LegacyGateMigration.migrate(root);

        assertThat(root.toString()).isEqualTo(once);
    }

    @Test
    void aFormatTwoFileIsMigratedToo() throws Exception {
        String formatTwo = circuitWith("xor2", 3).replace("\"formatVersion\": 3", "\"formatVersion\": 2");

        assertThat(format.read(formatTwo).components().getFirst().typeId()).isEqualTo("xor");
    }

    @Test
    void theBodiesOfACircuitsDefinitionsAreMigrated() throws Exception {
        CircuitDocument document = format.read(circuitWithDefinitions(3));

        ComponentDefinition inverter = document.definition("old-inverter").orElseThrow();
        ComponentDefinition pair = document.definition("old-pair").orElseThrow();
        assertThat(typeOf(inverter, "g1")).isEqualTo("buffer");
        assertThat(typeOf(pair, "g2")).isEqualTo("and");
        assertThat(parametersOf(pair, "g2")).containsEntry("negate", true).containsEntry("inputs", 2);
        assertThat(typeOf(pair, "s1")).isEqualTo("subcircuit:old-inverter");
    }

    @Test
    void aLibraryFileIsMigrated() throws Exception {
        DefinitionBundle bundle = new DefinitionFormat(registry).read(componentFile());

        assertThat(typeOf(bundle.root(), "g2")).isEqualTo("and");
        assertThat(typeOf(bundle.dependencies().getFirst(), "g1")).isEqualTo("buffer");
        assertThat(new DefinitionFormat(registry).write(bundle))
                .contains("\"formatVersion\" : " + CircuitFormat.FORMAT_VERSION)
                .doesNotContain("nand2").doesNotContain("\"not\"");
    }

    @Test
    void aClipboardFragmentIsMigrated() throws Exception {
        String fragment = """
                {"format": "vsim2", "formatVersion": 3, "kind": "fragment",
                 "components": [{"id": "c1", "type": "or4", "x": 0, "y": 0, "rotation": 0, "label": "",
                                 "parameters": {"delay": 1}}],
                 "wires": [], "labels": []}
                """;

        Component gate = new FragmentFormat(registry).read(fragment).components().getFirst();

        assertThat(gate.typeId()).isEqualTo("or");
        assertThat(gate.parameters()).containsEntry("inputs", 4).containsEntry("negate", false);
    }

    @Test
    void thePaletteIdOfAnOldIdIsItsTypeOrPreset() {
        assertThat(LegacyGateMigration.paletteIdFor("and3")).contains("and");
        assertThat(LegacyGateMigration.paletteIdFor("nand2")).contains("nand");
        assertThat(LegacyGateMigration.paletteIdFor("nor4")).contains("nor");
        assertThat(LegacyGateMigration.paletteIdFor("xor2")).contains("xor");
        assertThat(LegacyGateMigration.paletteIdFor("not")).contains("not");
        assertThat(LegacyGateMigration.paletteIdFor("led")).isEqualTo(Optional.empty());
    }

    /** The same output as the old gate for every row of its 1-bit table. */
    private static void assertSameTruthTable(String oldId, int inputs, ComponentType type,
                                             Map<String, Object> parameters) {
        for (int row = 0; row < 1 << inputs; row++) {
            List<Value> values = new ArrayList<>();
            int ones = 0;
            for (int input = 0; input < inputs; input++) {
                boolean bit = (row >> input & 1) == 1;
                values.add(Value.of(bit));
                ones += bit ? 1 : 0;
            }
            boolean expected = switch (oldId.replaceAll("\\d", "")) {
                case "and" -> ones == inputs;
                case "nand" -> ones != inputs;
                case "or" -> ones > 0;
                case "nor" -> ones == 0;
                default -> ones % 2 == 1;
            };
            assertThat(GateEvaluation.output(type, parameters, values)).as("%s row %d", oldId, row)
                    .isEqualTo(Value.of(expected));
        }
    }

    /** Where pin {@code pin} of the old gate of {@code inputs} inputs was, for the gate the test places. */
    private static GridPoint oldPinLocation(int inputs, int pin) {
        if (pin == inputs) {
            return rotated(new GridPoint(4, 2));
        }
        // VSim's AssociativePin: inputs spread over 32 pixels, rounded down to the 8-pixel grid.
        int separationInPixels = 32 / (inputs - 1);
        return rotated(new GridPoint(0, Math.floorDiv(pin * separationInPixels, 8)));
    }

    private static GridPoint rotated(GridPoint local) {
        return new GridPoint(10, 20).plus(Rotation.fromQuarterTurns(1).apply(local));
    }

    private static String circuitWith(String typeId, int version) {
        return """
                {"format": "vsim2", "savedWith": "0.9", "formatVersion": %d, "kind": "circuit", "name": "old",
                 "components": [{"id": "c1", "type": "%s", "x": 10, "y": 20, "rotation": 1, "label": "U1",
                                 "parameters": {"delay": 7}}],
                 "wires": [], "labels": [],
                 "probes": [{"id": "p1", "component": "c1", "pin": %d}]}
                """.formatted(version, typeId, typeId.equals("not") ? 1 : Integer.parseInt(typeId.replaceAll("\\D", "")));
    }

    private static String inverterDefinition() {
        return """
                {"id": "old-inverter", "name": "Inversor", "description": "", "width": 4, "height": 4,
                 "pinout": [{"name": "a", "x": 0, "y": 2, "direction": "INPUT", "width": 1, "source": "i1"},
                            {"name": "y", "x": 4, "y": 2, "direction": "OUTPUT", "width": 1, "source": "o1"}],
                 "components": [{"id": "i1", "type": "input-pin", "x": 0, "y": 1, "rotation": 0, "label": "a",
                                 "parameters": {}},
                                {"id": "g1", "type": "not", "x": 2, "y": 0, "rotation": 0, "label": "",
                                 "parameters": {"delay": 1}},
                                {"id": "o1", "type": "output-pin", "x": 6, "y": 1, "rotation": 0, "label": "y",
                                 "parameters": {}}],
                 "wires": [], "labels": []}
                """;
    }

    private static String pairBody() {
        return """
                 "pinout": [{"name": "a", "x": 0, "y": 2, "direction": "INPUT", "width": 1, "source": "i1"},
                            {"name": "y", "x": 4, "y": 2, "direction": "OUTPUT", "width": 1, "source": "o1"}],
                 "components": [{"id": "i1", "type": "input-pin", "x": 0, "y": 1, "rotation": 0, "label": "a",
                                 "parameters": {}},
                                {"id": "g2", "type": "nand2", "x": 2, "y": 0, "rotation": 0, "label": "",
                                 "parameters": {"delay": 1}},
                                {"id": "s1", "type": "subcircuit:old-inverter", "x": 8, "y": 0, "rotation": 0,
                                 "label": "", "parameters": {}},
                                {"id": "o1", "type": "output-pin", "x": 14, "y": 1, "rotation": 0, "label": "y",
                                 "parameters": {}}],
                 "wires": [], "labels": []
                """;
    }

    private static String circuitWithDefinitions(int version) {
        return """
                {"format": "vsim2", "formatVersion": %d, "kind": "circuit", "name": "old",
                 "components": [], "wires": [], "labels": [],
                 "definitions": [%s, {"id": "old-pair", "name": "Par", "description": "", "width": 4, "height": 4,
                 %s}]}
                """.formatted(version, inverterDefinition(), pairBody());
    }

    private static String componentFile() {
        return """
                {"format": "vsim2", "formatVersion": 3, "kind": "component",
                 "id": "old-pair", "name": "Par", "description": "", "width": 4, "height": 4,
                 %s,
                 "definitions": [%s]}
                """.formatted(pairBody(), inverterDefinition());
    }

    private static String typeOf(ComponentDefinition definition, String componentId) {
        return componentOf(definition, componentId).typeId();
    }

    private static Map<String, Object> parametersOf(ComponentDefinition definition, String componentId) {
        return componentOf(definition, componentId).parameters();
    }

    private static Component componentOf(ComponentDefinition definition, String componentId) {
        return definition.body().components().stream()
                .filter(component -> component.id().value().equals(componentId)).findFirst().orElseThrow();
    }

    private static JsonNode parse(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
