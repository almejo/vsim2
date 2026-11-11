package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationSpeedInFilesTest {

    private final CircuitFormat format = new CircuitFormat(StandardComponentTypes.registry());

    @Test
    void aNonDefaultSpeedRoundTrips() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.setSimulationSpeed(1000.0);
        String json = format.write(document);
        assertThat(json).contains("\"simulationSpeed\" : 1000");
        assertThat(format.read(json).simulationSpeed()).isEqualTo(1000.0);
    }

    @Test
    void theMaximumSpeedRoundTrips() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.setSimulationSpeed(Double.POSITIVE_INFINITY);
        String json = format.write(document);
        assertThat(json).contains("\"simulationSpeed\" : \"max\"");
        assertThat(format.read(json).simulationSpeed()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void theDefaultSpeedWritesNoKey() {
        assertThat(format.write(new CircuitDocument())).doesNotContain("simulationSpeed");
    }

    @Test
    void aFileWithoutTheKeyOpensAtTheDefaultSpeed() throws CircuitFormatException {
        assertThat(format.read("{\"formatVersion\":2,\"kind\":\"circuit\"}").simulationSpeed()).isEqualTo(1.0);
    }

    @Test
    void anInvalidSpeedLoadsWithTheDefault() throws CircuitFormatException {
        for (String invalid : new String[]{"\"fast\"", "-5", "0", "7", "null", "true", "[1]"}) {
            assertThat(format.read("{\"formatVersion\":2,\"kind\":\"circuit\",\"simulationSpeed\":" + invalid + "}")
                    .simulationSpeed()).as(invalid).isEqualTo(1.0);
        }
    }
}
