package cl.almejo.vsim.automation;

import cl.almejo.vsim.automation.read.ComponentNames;
import cl.almejo.vsim.automation.read.ReadFilter;
import cl.almejo.vsim.automation.read.ReadSource;
import cl.almejo.vsim.automation.read.RegionPicture;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationApiReadTest {

    private static final DocumentId OPEN = new DocumentId("circuit-1");
    private static final ComponentNames NAMES = new ComponentNames() {
        @Override
        public String spanish(ComponentType type) {
            return type.id();
        }

        @Override
        public String english(ComponentType type) {
            return type.id();
        }
    };

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<Object> pictureRequests = new ArrayList<>();

    private final ReadHost host = new ReadHost() {
        @Override
        public <T> CompletableFuture<T> reading(DocumentId id, Function<ReadSource, T> work) {
            if (!id.equals(OPEN)) {
                return CompletableFuture.failedFuture(
                        new AutomationException(AutomationError.DOCUMENT_CLOSED, "closed"));
            }
            return CompletableFuture.supplyAsync(() -> work.apply(new ReadSource(OPEN, new DocumentVersion(3), "A",
                    document, registry, new NetlistCompiler(registry).compile(document), document.revision(),
                    SimulationSnapshot.EMPTY, NAMES)));
        }

        @Override
        public CompletableFuture<RegionPicture> pictureOf(DocumentId id, Optional<Bounds> region, boolean asOnScreen) {
            pictureRequests.add(List.of(id, region, asOnScreen));
            return CompletableFuture.completedFuture(new RegionPicture(id, new DocumentVersion(3),
                    region.orElse(new Bounds(0, 0, 1, 1)), asOnScreen, 8, 8, new byte[0]));
        }
    };

    private final AutomationApi api = new AutomationApi(new Host(host), Duration.ofSeconds(5));

    @Test
    void theApiReadsTheCircuitTheHostHandsOver() {
        document.add(registry.require(StandardComponentTypes.NOT).newComponent(document.newComponentId(),
                new GridPoint(0, 0), Rotation.R0, Map.of(), "inv"));

        assertThat(api.readCircuit(OPEN, ReadFilter.SUMMARY, Optional.empty()).parts())
                .singleElement().satisfies(part -> assertThat(part.label()).isEqualTo("inv"));
        assertThat(api.describeComponent(OPEN, StandardComponentTypes.NOT, Map.of()).pins()).hasSize(2);
        assertThat(api.describeComponents(OPEN, Optional.of("not")).types()).isNotEmpty();
        assertThat(api.readProblems(OPEN).problems()).isEmpty();
    }

    @Test
    void aClosedCircuitsRefusalReachesTheAiUnchanged() {
        assertThatThrownBy(() -> api.readCircuit(new DocumentId("circuit-9"), ReadFilter.SUMMARY, Optional.empty()))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.DOCUMENT_CLOSED));
    }

    @Test
    void aPictureIsTheExportOneUnlessAskedAsOnScreen() {
        api.pictureOf(OPEN, Optional.empty());
        api.pictureOf(OPEN, Optional.of(new Bounds(0, 0, 10, 10)), true);

        assertThat(pictureRequests).containsExactly(List.of(OPEN, Optional.empty(), false),
                List.of(OPEN, Optional.of(new Bounds(0, 0, 10, 10)), true));
    }

    /** An automation host whose reads are {@code reads}'. */
    private record Host(ReadHost reads) implements AutomationHost {

        @Override
        public <T> CompletableFuture<T> onEditorThread(Function<EditorAccess, T> work) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public <T> CompletableFuture<T> reading(DocumentId document, Function<ReadSource, T> work) {
            return reads.reading(document, work);
        }

        @Override
        public CompletableFuture<RegionPicture> pictureOf(DocumentId document, Optional<Bounds> region,
                                                          boolean asOnScreen) {
            return reads.pictureOf(document, region, asOnScreen);
        }
    }
}
