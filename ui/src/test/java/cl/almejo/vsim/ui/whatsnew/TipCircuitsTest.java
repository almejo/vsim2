package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormat;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The tips' circuits are what {@link TipCircuits} builds, and each one runs without a problem. */
class TipCircuitsTest {

    private static final Path RESOURCES = Path.of("src/main/resources/tips");

    static Stream<String> circuitNames() {
        return TipCircuits.builders().keySet().stream();
    }

    /**
     * Rewrites the circuit files from the builders. Run it after changing a builder, from the repository root:
     * {@code VSIM_WRITE_BUNDLED=1 ./gradlew :ui:test --tests '*TipCircuitsTest.writeTheResources'}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "VSIM_WRITE_BUNDLED", matches = "1")
    void writeTheResources() throws IOException {
        Files.createDirectories(RESOURCES);
        for (Map.Entry<String, Supplier<CircuitDocument>> builder : TipCircuits.builders().entrySet()) {
            Files.writeString(RESOURCES.resolve(builder.getKey()), written(builder.getValue().get()),
                    StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest
    @MethodSource("circuitNames")
    void theCommittedCircuitIsWhatTheBuilderMakes(String name) throws Exception {
        assertThat(written(TipCircuit.read(name))).isEqualTo(written(TipCircuits.builders().get(name).get()));
    }

    @ParameterizedTest
    @MethodSource("circuitNames")
    void everyCircuitRunsWithoutAProblem(String name) throws Exception {
        TipCircuit circuit = TipCircuit.load(name);

        assertThat(circuit.snapshot().failure()).isEmpty();
        assertThat(circuit.snapshot().problems()).isEmpty();
    }

    private static String written(CircuitDocument document) {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        return new CircuitFormat(registry).write(document);
    }
}
