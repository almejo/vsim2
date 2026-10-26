package cl.almejo.vsim.core.netlist;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the compiled netlists (net ids, order, members, points, pin to net) of the example circuits, so refactors of the
 * compiler cannot change them. Goldens: {@code src/test/resources/netlist-snapshots/*.txt}; regenerate with
 * {@code VSIM_WRITE_NETLISTS=1 ./gradlew :core:test --tests '*NetlistSnapshotTest'}.
 */
class NetlistSnapshotTest {

    private static final ComponentTypeRegistry REGISTRY = StandardComponentTypes.registry();
    private static final Path RESOURCES = Path.of("src/test/resources");
    private static final Path SNAPSHOTS = RESOURCES.resolve("netlist-snapshots");
    private static final List<Path> FILE_DIRECTORIES = List.of(RESOURCES.resolve("fixtures"),
            RESOURCES.resolve("vsim1"));

    /** Where an example comes from: its snapshot name and, for committed files, the file (null: a bundled example). */
    private record Example(String name, Path file) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Example> examples() throws IOException {
        List<Example> examples = new ArrayList<>();
        for (String id : BundledExamples.ids()) {
            examples.add(new Example("bundled-" + id, null));
        }
        for (Path directory : FILE_DIRECTORIES) {
            try (Stream<Path> files = Files.walk(directory)) {
                files.filter(path -> path.toString().endsWith(".vsim") || path.toString().endsWith(".json"))
                        .sorted()
                        .forEach(path -> examples.add(new Example(
                                RESOURCES.relativize(path).toString().replace('/', '-'), path)));
            }
        }
        return examples.stream();
    }

    @ParameterizedTest
    @MethodSource("examples")
    void theCompiledNetlistsAreTheCommittedOnes(Example example) throws Exception {
        CircuitDocument document = example.file() == null
                ? BundledExamples.open(example.name().substring("bundled-".length()), REGISTRY)
                : new CircuitReader(REGISTRY).read(example.file());
        ComponentTypeRegistry registry = REGISTRY.extendedWith(SubcircuitTypes.resolverFor(document));
        NetlistCompiler compiler = new NetlistCompiler(registry);
        StringBuilder digest = new StringBuilder();
        digest.append("== compile\n");
        append(digest, compiler.compile(document), document, registry);
        digest.append("== compileAsDrawn\n");
        append(digest, compiler.compileAsDrawn(document), document, registry);

        Path golden = SNAPSHOTS.resolve(example.name() + ".txt");
        if ("1".equals(System.getenv("VSIM_WRITE_NETLISTS"))) {
            Files.createDirectories(SNAPSHOTS);
            Files.writeString(golden, digest.toString(), StandardCharsets.UTF_8);
        }
        assertThat(golden).exists();
        assertThat(digest.toString()).isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
    }

    private static void append(StringBuilder digest, Netlist netlist, CircuitDocument document,
                               ComponentTypeRegistry registry) {
        for (Net net : netlist.nets()) {
            digest.append("net ").append(net.id().value()).append(" width=").append(net.width())
                    .append(" mismatch=").append(net.widthMismatch()).append('\n');
            digest.append("  wires ").append(net.wires().stream().map(wire -> wire.value()).sorted().toList())
                    .append('\n');
            digest.append("  pins ").append(net.pins().stream()
                    .map(pin -> pin.componentId().value() + "#" + pin.pinIndex()).sorted().toList()).append('\n');
            digest.append("  points ").append(net.points().stream().sorted()
                    .map(GridPoint::toString).toList()).append('\n');
        }
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                digest.append("pin ").append(component.id().value()).append('#').append(pinIndex).append(" -> ")
                        .append(netlist.netForPin(new PinRef(component.id(), pinIndex))
                                .map(net -> net.id().value()).orElse("none")).append('\n');
            }
        }
    }
}
