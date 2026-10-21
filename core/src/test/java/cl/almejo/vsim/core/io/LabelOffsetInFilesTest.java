package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.LabelOffset;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LabelOffsetInFilesTest {

    private static final LabelOffset OFFSET = new LabelOffset(-3, 5);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);

    private static Component gate(String id, Optional<LabelOffset> offset) {
        return new Component(new ElementId(id), StandardComponentTypes.NOT, new GridPoint(4, 6), Rotation.R90,
                Map.of("delay", 1), "U1", offset);
    }

    @Test
    void aCircuitFileKeepsTheOffset() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(gate("c1", Optional.of(OFFSET)));
        document.add(gate("c2", Optional.empty()));
        String json = format.write(document);
        assertThat(json).contains("\"labelOffset\"");
        CircuitDocument loaded = format.read(json);
        assertThat(loaded.component(new ElementId("c1")).orElseThrow().labelOffset()).contains(OFFSET);
        assertThat(loaded.component(new ElementId("c2")).orElseThrow().labelOffset()).isEmpty();
    }

    @Test
    void aComponentWithoutOffsetWritesNoLabelOffsetKey() throws CircuitFormatException {
        CircuitDocument document = new CircuitDocument();
        document.add(gate("c1", Optional.empty()));
        assertThat(format.write(document)).doesNotContain("labelOffset");
    }

    @Test
    void aFileWrittenBeforeOffsetsExistedIsWrittenBackByteForByte() throws Exception {
        String committed;
        try (InputStream stream = getClass().getResourceAsStream(
                "/cl/almejo/vsim/core/bundled/examples/full-adder.vsim")) {
            committed = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        // the bundled resources are written with a final newline (BundledCircuits)
        assertThat(SavedFiles.withoutSavedWith(format.write(format.read(committed)) + "\n"))
                .isEqualTo(SavedFiles.withoutSavedWith(committed));
    }

    @Test
    void aHandWrittenOffsetIsRead() throws CircuitFormatException {
        CircuitDocument loaded = format.read("""
                {"formatVersion":2,"kind":"circuit","components":[{"id":"c1","type":"not","x":1,"y":1,
                 "labelOffset":{"x":-3,"y":5}}]}
                """);
        assertThat(loaded.components().getFirst().labelOffset()).contains(OFFSET);
    }

    @Test
    void aFragmentKeepsTheOffset() throws CircuitFormatException {
        FragmentFormat fragmentFormat = new FragmentFormat(registry);
        CircuitFragment fragment = new CircuitFragment(
                List.of(new Component(new ElementId("c1"), StandardComponentTypes.NOT, GridPoint.ORIGIN, Rotation.R0,
                        Map.of("delay", 1), "", Optional.of(OFFSET))), List.of(), List.of());
        assertThat(fragmentFormat.read(fragmentFormat.write(fragment))).isEqualTo(fragment);
    }

    @Test
    void aLibraryFileKeepsTheOffsetOfABodyComponent() throws CircuitFormatException {
        DefinitionFormat definitionFormat = new DefinitionFormat(registry);
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        CircuitFragment body = inverter.body();
        List<Component> components = body.components().stream()
                .map(component -> component.withLabelOffset(Optional.of(OFFSET))).toList();
        ComponentDefinition moved = new ComponentDefinition(inverter.id(), inverter.name(), inverter.description(),
                inverter.size(), inverter.pinout(), new CircuitFragment(components, body.wires(), body.labels()));
        DefinitionBundle bundle = new DefinitionBundle(moved, List.of());
        assertThat(definitionFormat.read(definitionFormat.write(bundle))).isEqualTo(bundle);
    }
}
