package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.truthtable.Column;
import cl.almejo.vsim.core.truthtable.Preparation;
import cl.almejo.vsim.core.truthtable.Row;
import cl.almejo.vsim.core.truthtable.TruthTable;
import cl.almejo.vsim.core.truthtable.TruthTableEvaluator;
import cl.almejo.vsim.core.truthtable.TruthTableScope;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The bundled example files must be what {@link BundledCircuits} builds, must open and must run (spec §3.3). */
class BundledCircuitsTest {

    private static final Path RESOURCES = Path.of("src/main/resources", BundledExamples.RESOURCE_DIRECTORY);
    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();

    static Stream<String> exampleIds() {
        return BundledExamples.ids().stream();
    }

    /**
     * Rewrites the resource files from the builders. Run it after changing a builder, from the repository root:
     * {@code VSIM_WRITE_BUNDLED=1 ./gradlew :core:test --tests '*BundledCircuitsTest.writeTheResources'}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "VSIM_WRITE_BUNDLED", matches = "1")
    void writeTheResources() throws IOException {
        BundledCircuits.main(new String[]{RESOURCES.toString()});
    }

    @Test
    void everyResourceHasABuilderAndTheBuildersFollowTheMenuOrder() throws IOException {
        assertThat(BundledCircuits.builders().keySet()).containsExactlyElementsOf(BundledExamples.ids());
        try (Stream<Path> files = Files.list(RESOURCES)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrderElementsOf(BundledExamples.ids().stream().map(id -> id + ".vsim")
                            .toList());
        }
    }

    @ParameterizedTest
    @MethodSource("exampleIds")
    void theCommittedResourceIsWhatTheBuilderProduces(String id) throws Exception {
        CircuitBuilder builder = BundledCircuits.builders().get(id).get();
        CircuitDocument committed = BundledExamples.open(id, REGISTRY);
        assertThat(new CircuitFormat(REGISTRY).write(committed))
                .isEqualTo(new CircuitFormat(builder.registry()).write(builder.document()));
    }

    @ParameterizedTest
    @MethodSource("exampleIds")
    void everyExampleOpensWithItsTitleAndRunsWithoutElectricalProblems(String id) throws Exception {
        CircuitDocument document = BundledExamples.open(id, REGISTRY);
        assertThat(document.name()).isNotBlank()
                .isEqualTo(BundledCircuits.builders().get(id).get().document().name());
        assertThat(document.labels()).anyMatch(label -> label.text().equals(document.name()))
                .anyMatch(label -> label.text().startsWith("Prueba: "));

        ExampleSimulation simulation = new ExampleSimulation(document, Map.of());
        simulation.engine().advance(3000);
        SimulationSnapshot snapshot = simulation.engine().snapshot();
        assertThat(snapshot.failure()).isEmpty();
        assertThat(snapshot.problems()).filteredOn(problem -> !(problem instanceof ConnectivityProblem)).isEmpty();
    }

    /**
     * The combinational examples and their LEDs as a function of their switches (Task 6's expected functions). The bus
     * is left out: its LEDs share their labels with its switches, and every byte is already checked on its LEDs.
     */
    private static final Map<String, Function<Map<String, Integer>, Map<String, Integer>>> FUNCTIONS = Map.of(
            "full-adder", CombinationalExamplesTest::fullAdderOutputs,
            "adder-4-bits", CombinationalExamplesTest::fourBitAdderOutputs,
            "mux-4-1", CombinationalExamplesTest::multiplexerOutputs,
            "decoder-2-4", CombinationalExamplesTest::decoderOutputs,
            "parity-3-bits", CombinationalExamplesTest::parityOutputs,
            "multiplier-2x2", CombinationalExamplesTest::multiplierOutputs);

    static Stream<String> combinationalIds() {
        return Stream.of("full-adder", "adder-4-bits", "mux-4-1", "decoder-2-4", "parity-3-bits", "multiplier-2x2");
    }

    @ParameterizedTest
    @MethodSource("combinationalIds")
    void theTruthTableOfTheWholeCircuitIsItsFunction(String id) throws Exception {
        CircuitDocument document = BundledExamples.open(id, REGISTRY);
        Preparation preparation = TruthTableScope.prepare(document, Set.of(), registryFor(document));
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        TruthTable table = TruthTableEvaluator.evaluate((Preparation.Ready) preparation, percent -> { }, () -> false)
                .orElseThrow();

        assertThat(table.hasFeedback()).isFalse();
        assertThat(table.rows()).hasSize(1 << table.inputs().stream().mapToInt(Column::width).sum());
        for (Row row : table.rows()) {
            assertThat(row.settled()).isTrue();
            Map<String, Integer> inputs = new LinkedHashMap<>();
            for (int index = 0; index < table.inputs().size(); index++) {
                inputs.put(table.inputs().get(index).name(), row.inputs().get(index).equals(Value.ONE) ? 1 : 0);
            }
            Map<String, Value> actual = new LinkedHashMap<>();
            for (int index = 0; index < table.outputs().size(); index++) {
                actual.put(table.outputs().get(index).name(), row.outputs().get(index));
            }
            Map<String, Value> expected = new LinkedHashMap<>();
            FUNCTIONS.get(id).apply(inputs).forEach((name, bit) -> expected.put(name, Value.of(bit == 1)));
            assertThat(actual).as("%s %s", id, inputs).isEqualTo(expected);
        }
    }

    @Test
    void theFullAdderTableHasItsColumnsInPositionOrder() throws Exception {
        CircuitDocument document = BundledExamples.open("full-adder", REGISTRY);
        Preparation.Ready ready = (Preparation.Ready) TruthTableScope.prepare(document, Set.of(),
                registryFor(document));
        assertThat(ready.inputs()).extracting(Column::name).containsExactly("A", "B", "Cin");
        assertThat(ready.outputs()).extracting(Column::name).containsExactly("S", "Cout");
    }

    @ParameterizedTest
    @ValueSource(strings = {"register-4-bits", "shift-register-4-bits", "counter-4-bits", "johnson-counter",
            "accumulator"})
    void theSequentialExamplesRefuseATruthTable(String id) throws Exception {
        CircuitDocument document = BundledExamples.open(id, REGISTRY);
        assertThat(TruthTableScope.prepare(document, Set.of(), registryFor(document)))
                .isInstanceOfSatisfying(Preparation.Refused.class, refused ->
                        assertThat(refused.reason()).isEqualTo(Preparation.Reason.SEQUENTIAL));
    }

    @Test
    void theAluHasMoreInputBitsThanATableComputesAtOnce() throws Exception {
        CircuitDocument document = BundledExamples.open("alu-4-bits", REGISTRY);
        Preparation preparation = TruthTableScope.prepare(document, Set.of(), registryFor(document));
        assertThat(preparation).isInstanceOf(Preparation.Ready.class);
        Preparation.Ready ready = (Preparation.Ready) preparation;
        assertThat(ready.inputBits()).as("A, B, Cin and the two selector bits").isEqualTo(11);
        assertThat(ready.needsConfirmation()).as("above 10 bits the user is asked first").isTrue();
    }

    @Test
    void theMiniAluTableIsRefusedAt8BitsForTooManyInputBits() throws Exception {
        CircuitDocument document = BundledExamples.open("mini-alu", REGISTRY);
        Preparation preparation = TruthTableScope.prepare(document, Set.of(), registryFor(document));
        assertThat(preparation).isInstanceOf(Preparation.Refused.class);
        Preparation.Refused refused = (Preparation.Refused) preparation;
        assertThat(refused.reason()).isEqualTo(Preparation.Reason.TOO_MANY_INPUT_BITS);
        assertThat(refused.inputBits()).as("A (8), B (8) and OP (3)").isEqualTo(19);
    }

    /** The standard types plus the document's own definitions, like {@code OpenDocument.registry()}. */
    private static ComponentTypeRegistry registryFor(CircuitDocument document) {
        return REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));
    }
}
