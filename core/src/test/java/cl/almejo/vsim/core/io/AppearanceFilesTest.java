package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.appearance.SymbolDrawing;
import cl.almejo.vsim.core.component.appearance.SymbolElement;
import cl.almejo.vsim.core.component.appearance.SymbolNode;
import cl.almejo.vsim.core.component.appearance.SymbolPath;
import cl.almejo.vsim.core.component.appearance.SymbolPoint;
import cl.almejo.vsim.core.component.appearance.SymbolText;
import cl.almejo.vsim.core.component.appearance.TextAlignment;
import cl.almejo.vsim.core.component.appearance.TextSize;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.command.PasteCommand;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppearanceFilesTest {
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat format = new CircuitFormat(registry);
    private final ComponentDefinition original = ExampleDefinitions.inverter();

    @Test
    void circuitDefinitionAndOverrideRoundTripMixedOrderAndRevision() throws Exception {
        ComponentDefinition definition = original.withAppearance(Optional.of(drawing()));
        CircuitDocument document = document(definition);
        Component instance = document.components().getFirst();
        ComponentAppearance override = new ComponentAppearance(original.size(), Optional.of(drawing()),
                ComponentAppearance.of(original).pins());
        document.replace(instance.withAppearance(Optional.of(override)));
        String json = format.write(document);
        assertThat(new ObjectMapper().readTree(json).path("formatVersion").asInt()).isEqualTo(3);
        CircuitDocument loaded = format.read(json);
        assertThat(loaded.definitions()).containsExactly(definition);
        assertThat(loaded.components()).isEqualTo(document.components());
        assertThat(format.write(loaded)).isEqualTo(json);
    }

    @Test
    void libraryRootAndNestedDefinitionsRetainTheirIndependentRevisions() throws Exception {
        ComponentDefinition first = original.withAppearance(Optional.of(drawing()));
        ComponentDefinition parent = ExampleDefinitions.doubleInverter().withName("Dos");
        DefinitionBundle bundle = new DefinitionBundle(parent, List.of(first));
        DefinitionFormat definitionFormat = new DefinitionFormat(registry);
        assertThat(definitionFormat.read(definitionFormat.write(bundle))).isEqualTo(bundle);
    }

    @Test
    void restoringTheDefaultBoxDoesNotBecomeAnEmptySymbolOnDisk() throws Exception {
        CircuitDocument document = document(original);
        Component instance = document.components().getFirst();
        document.replace(instance.withAppearance(Optional.of(ComponentAppearance.of(original))));
        Component loaded = format.read(format.write(document)).components().getFirst();
        assertThat(loaded.appearance()).contains(ComponentAppearance.of(original));
        assertThat(loaded.appearance().orElseThrow().drawing()).isEmpty();
        document.replace(instance.withAppearance(Optional.of(new ComponentAppearance(original.size(),
                Optional.of(SymbolDrawing.empty(original.size())), ComponentAppearance.of(original).pins()))));
        assertThat(format.read(format.write(document)).components().getFirst().appearance().orElseThrow().drawing())
                .contains(SymbolDrawing.empty(original.size()));
    }

    @Test
    void appearanceSurvivesClipboardNormalizationAndPastingWithANewInstanceId() throws Exception {
        CircuitDocument document = document(original);
        Component instance = document.components().getFirst().withAppearance(Optional.of(
                new ComponentAppearance(original.size(), Optional.of(drawing()), ComponentAppearance.of(original).pins())));
        document.replace(instance);
        CircuitFragment fragment = new CircuitFragment(document.components(), List.of(), List.of(), List.of(original));
        FragmentFormat fragments = new FragmentFormat(registry);
        CircuitFragment loaded = fragments.read(fragments.write(fragment));
        CircuitDocument target = new CircuitDocument();
        new PasteCommand(loaded, new GridPoint(5, 5)).apply(target);
        assertThat(target.components().getFirst().appearance()).isEqualTo(instance.appearance());
    }

    @Test
    void legacyDefinitionWithoutARevisionReadsAsTheFirstRevision() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(format.write(document(original)));
        tree.put("formatVersion", 2);
        ((ObjectNode) tree.path("definitions").get(0)).remove("revision");
        assertThat(format.read(tree.toString()).definitions().getFirst().revision()).isEqualTo(1);
    }

    @Test
    void malformedAppearanceFailsWithAnElementErrorInsteadOfChangingPinsSilently() throws Exception {
        CircuitDocument document = document(original.withAppearance(Optional.of(drawing())));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(format.write(document));
        ObjectNode path = (ObjectNode) tree.path("definitions").get(0).path("appearance").path("elements").get(0);
        path.put("d", "M 0 0 C 1 1 2 2");
        assertThatThrownBy(() -> format.read(tree.toString())).isInstanceOfSatisfying(CircuitFormatException.class,
                exception -> assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void persistedOverrideSourcesMustMatchTheDefinitionAndCannotShortPins() throws Exception {
        CircuitDocument document = document(original);
        Component instance = document.components().getFirst();
        document.replace(instance.withAppearance(Optional.of(ComponentAppearance.of(original))));
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode unknown = (ObjectNode) mapper.readTree(format.write(document));
        ((ObjectNode) unknown.path("components").get(0).path("appearance").path("pins").get(0))
                .put("source", "missing");
        assertThatThrownBy(() -> format.read(unknown.toString())).isInstanceOfSatisfying(CircuitFormatException.class,
                exception -> assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
        ObjectNode coincident = (ObjectNode) mapper.readTree(format.write(document));
        ((ObjectNode) coincident.path("components").get(0).path("appearance").path("pins").get(1)).put("x", 0);
        assertThatThrownBy(() -> format.read(coincident.toString()))
                .isInstanceOfSatisfying(CircuitFormatException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void coloursAndAMultilineTextRoundTripAndDefaultsStayOutOfTheFile() throws Exception {
        SymbolPath path = new SymbolPath(List.of(new SymbolNode(new SymbolPoint(0, 0), Optional.empty()),
                new SymbolNode(new SymbolPoint(32, 0), Optional.empty()),
                new SymbolNode(new SymbolPoint(16, 32), Optional.empty())), true, true, NoteColor.GREEN,
                NoteColor.PURPLE);
        SymbolText text = new SymbolText(new SymbolPoint(16, 16), Optional.of("uno\ndos"), TextSize.MEDIUM,
                TextAlignment.RIGHT, NoteColor.BLUE, NoteColor.GRAY);
        SymbolDrawing coloured = new SymbolDrawing(new GridSize(4, 4), List.<SymbolElement>of(path, text), true);
        ComponentDefinition definition = original.withAppearance(Optional.of(coloured));

        String json = format.write(document(definition));

        assertThat(format.read(json).definitions()).containsExactly(definition);
        JsonNode elements = new ObjectMapper().readTree(json).path("definitions").get(0).path("appearance")
                .path("elements");
        assertThat(elements.get(0).path("fill").asText()).isEqualTo("green");
        assertThat(elements.get(0).path("line").asText()).isEqualTo("purple");
        assertThat(elements.get(1).path("color").asText()).isEqualTo("blue");
        assertThat(elements.get(1).path("border").asText()).isEqualTo("gray");
        assertThat(elements.get(1).path("text").asText()).isEqualTo("uno\ndos");
        String plain = format.write(document(original.withAppearance(Optional.of(drawing()))));
        assertThat(plain).doesNotContain("\"fill\"", "\"line\"", "\"color\"", "\"border\"");
    }

    @Test
    void aRedFillInAFileIsAnElementError() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode tree = (ObjectNode) mapper.readTree(format.write(document(original.withAppearance(
                Optional.of(drawing())))));
        ObjectNode path = (ObjectNode) tree.path("definitions").get(0).path("appearance").path("elements").get(0);
        path.put("fill", "red");
        assertThatThrownBy(() -> format.read(tree.toString())).isInstanceOfSatisfying(CircuitFormatException.class,
                exception -> assertThat(exception.reason()).isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    private CircuitDocument document(ComponentDefinition definition) {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(definition);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(2, 2), Rotation.R0, Map.of(), "U1"));
        return document;
    }

    private SymbolDrawing drawing() {
        SymbolPath path = new SymbolPath(List.of(new SymbolNode(new SymbolPoint(0, 0), Optional.empty()),
                new SymbolNode(new SymbolPoint(16, 32), Optional.empty())), false, false);
        SymbolText text = new SymbolText(new SymbolPoint(16, 16), Optional.empty(), TextSize.MEDIUM,
                TextAlignment.CENTER);
        return new SymbolDrawing(new GridSize(4, 4), List.<SymbolElement>of(path, text, path), true);
    }
}
