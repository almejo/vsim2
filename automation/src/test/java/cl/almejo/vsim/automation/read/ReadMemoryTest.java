package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.ReadHost;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code vsim_read_memory}: initial contents from the circuit, current contents from the simulation. */
class ReadMemoryTest {

    private final ReadFixture fixture = new ReadFixture();
    private MemoryContents current = MemoryContents.empty();
    private boolean simulating = true;

    private final ReadHost host = new ReadHost() {
        @Override
        public <T> CompletableFuture<T> reading(DocumentId document, Function<ReadSource, T> work) {
            return CompletableFuture.completedFuture(work.apply(fixture.source()));
        }

        @Override
        public CompletableFuture<Optional<MemoryContents>> currentMemory(DocumentId document, String component) {
            return CompletableFuture.completedFuture(simulating ? Optional.of(current) : Optional.empty());
        }
    };
    private final Reads reads = new Reads(host, Duration.ofSeconds(5));

    private Component memory(Map<String, Object> parameters) {
        return fixture.place(StandardComponentTypes.RAM, 0, 0, Rotation.R0, parameters, "");
    }

    @Test
    void initialAndCurrentContentsDifferAfterASimulatedWrite() {
        Component ram = memory(Map.of("words", "16", "width", 8, "contents", "@1 AA BB"));
        current = MemoryContents.decode("@1 AA BB @3 7F");

        MemoryView initial = reads.readMemory(ReadFixture.ID, ram.id().value(), false, 0, 5);
        MemoryView now = reads.readMemory(ReadFixture.ID, ram.id().value(), true, 0, 5);

        assertThat(initial.words()).containsExactly("00", "AA", "BB", "00", "00");
        assertThat(initial.nonZeroWords()).isEqualTo(2);
        assertThat(now.words()).containsExactly("00", "AA", "BB", "7F", "00");
        assertThat(now.nonZeroWords()).isEqualTo(3);
        assertThat(now.source()).isEqualTo("current");
        assertThat(now.width()).isEqualTo(8);
        assertThat(now.totalWords()).isEqualTo(16);
        assertThat(now.linked()).isFalse();
    }

    @Test
    void theWindowStopsAtTheLastAddressAndIsCapped() {
        Component ram = memory(Map.of("words", "512", "width", 12));

        assertThat(reads.readMemory(ReadFixture.ID, ram.id().value(), false, 510, 100).words()).hasSize(2);
        assertThat(reads.readMemory(ReadFixture.ID, ram.id().value(), false, 0, 256).words()).hasSize(256);
        assertThatThrownBy(() -> reads.readMemory(ReadFixture.ID, ram.id().value(), false, 0, 257))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        assertThatThrownBy(() -> reads.readMemory(ReadFixture.ID, ram.id().value(), false, 512, 1))
                .isInstanceOf(AutomationException.class);
    }

    @Test
    void aLinkedMemoryIsReportedAsLinkedAndANonMemoryIsRefused() {
        Component rom = fixture.place(StandardComponentTypes.ROM, 0, 20, Rotation.R0,
                Map.of("link", "rom.hex", "contents", "@0 5"), "");
        Component gate = fixture.place("nand2", 30, 30);

        assertThat(reads.readMemory(ReadFixture.ID, rom.id().value(), false, 0, 1).linked()).isTrue();
        assertThatThrownBy(() -> reads.readMemory(ReadFixture.ID, gate.id().value(), false, 0, 1))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void currentWithoutASimulationIsRefused() {
        Component ram = memory(Map.of());
        simulating = false;

        assertThatThrownBy(() -> reads.readMemory(ReadFixture.ID, ram.id().value(), true, 0, 1))
                .isInstanceOfSatisfying(AutomationException.class,
                        refusal -> assertThat(refusal.error()).isEqualTo(AutomationError.NOT_ALLOWED));
    }

    @Test
    void readingTheCircuitWithASixteenMegaWordMemoryStaysSmall() {
        Component huge = memory(Map.of("words", "16777216", "width", 64, "contents", "@FFFFFF FFFFFFFFFFFFFFFF"));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        Map<String, Object> parameters = view.parts().stream()
                .filter(part -> part.id().equals(huge.id().value())).findFirst().orElseThrow().parameters();
        assertThat(parameters).containsEntry("contents", "1 word set").containsKey("link");
        assertThat(parameters.toString().length()).isLessThan(400);
    }
}
