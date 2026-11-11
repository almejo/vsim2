package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.EditInsideCommand;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Frames and sticky notes inside a component's definition are saved with it, in every file that carries one. */
class DefinitionBodyNotesInFilesTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitFormat circuitFormat = new CircuitFormat(registry);
    private final ObjectMapper mapper = new ObjectMapper();

    private final Frame frame = new Frame(new ElementId("f1"), new GridPoint(1, 2), 12, 7, "Sumador", FrameTint.GREEN,
            true);
    private final StickyNote textNote = new StickyNote(new ElementId("s1"), new GridPoint(20, 3), 9, 5,
            StickyColor.PINK, new StickyText("revisar el acarreo"), false);
    private final StickyNote listNote = new StickyNote(new ElementId("s2"), new GridPoint(20, 12), 8, 6,
            StickyColor.BLUE, new StickyChecklist(List.of(new StickyItem("probar 0+0", true),
            new StickyItem("probar 1+1", false))), true);

    private ComponentDefinition inverterWithNotes() {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        CircuitFragment body = inverter.body();
        return inverter.withBody(new CircuitFragment(body.components(), body.wires(), body.labels(), List.of(),
                List.of(frame), List.of(textNote, listNote)));
    }

    private CircuitDocument documentUsing(ComponentDefinition definition) {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(definition);
        document.add(new Component(new ElementId("c1"), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(4, 4), Rotation.R0, Map.of(), "U1"));
        return document;
    }

    @Test
    void aDefinitionWithAFrameAndStickyNotesRoundTripsThroughACircuitFile() throws CircuitFormatException {
        ComponentDefinition definition = inverterWithNotes();

        CircuitDocument loaded = circuitFormat.read(circuitFormat.write(documentUsing(definition)));

        ComponentDefinition read = loaded.definition(definition.id()).orElseThrow();
        assertThat(read.body().frames()).containsExactly(frame);
        assertThat(read.body().stickyNotes()).containsExactly(textNote, listNote);
        assertThat(read).isEqualTo(definition);
    }

    @Test
    void aFrameAndANotePutInsideAComponentAreStillThereAfterSavingAndOpening() throws CircuitFormatException {
        ComponentDefinition inverter = ExampleDefinitions.inverter();
        CircuitDocument document = documentUsing(inverter);
        ComponentTypeRegistry documentRegistry = registry.copy();
        documentRegistry.addResolver(SubcircuitTypes.resolverFor(document));
        CircuitDocument workingCopy = inverter.bodyDocument(document);
        new History(document).execute(new EditInsideCommand(inverter.id(),
                new AddElementsCommand(List.of(frame, textNote)), workingCopy, documentRegistry));
        ComponentDefinition edited = document.definition(inverter.id()).orElseThrow();
        assertThat(edited.body().frames()).containsExactly(frame);

        CircuitDocument loaded = circuitFormat.read(circuitFormat.write(document));

        ComponentDefinition read = loaded.definition(inverter.id()).orElseThrow();
        assertThat(read.body().frames()).containsExactly(frame);
        assertThat(read.body().stickyNotes()).containsExactly(textNote);
    }

    @Test
    void theEntriesUseTheSameShapeAsTheCircuitsOwnFramesAndNotes() throws Exception {
        CircuitDocument document = documentUsing(inverterWithNotes());
        document.add(frame);
        document.add(textNote);
        JsonNode root = mapper.readTree(circuitFormat.write(document));
        JsonNode definition = root.path("definitions").get(0);

        assertThat(definition.path("frames")).isEqualTo(root.path("frames"));
        assertThat(definition.path("stickyNotes").get(0)).isEqualTo(root.path("stickyNotes").get(0));
    }

    @Test
    void aDefinitionWithoutFramesOrNotesWritesNeitherKey() throws Exception {
        JsonNode definition = mapper.readTree(circuitFormat.write(documentUsing(ExampleDefinitions.inverter())))
                .path("definitions").get(0);
        assertThat(definition.has("frames")).isFalse();
        assertThat(definition.has("stickyNotes")).isFalse();
    }

    @Test
    void aDefinitionWrittenWithoutTheKeysLoadsAsBefore() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(circuitFormat.write(documentUsing(inverterWithNotes())));
        ObjectNode definition = (ObjectNode) root.path("definitions").get(0);
        definition.remove("frames");
        definition.remove("stickyNotes");

        ComponentDefinition read = circuitFormat.read(root.toString()).definition(ExampleDefinitions.INVERTER)
                .orElseThrow();

        assertThat(read).isEqualTo(ExampleDefinitions.inverter());
    }

    @Test
    void aFrameWhoseIdRepeatsAPartOfTheBodyFailsTheFile() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(circuitFormat.write(documentUsing(inverterWithNotes())));
        ObjectNode definition = (ObjectNode) root.path("definitions").get(0);
        String partId = definition.path("components").get(0).path("id").asText();
        ((ObjectNode) ((ArrayNode) definition.path("frames")).get(0)).put("id", partId);

        assertThatThrownBy(() -> circuitFormat.read(root.toString()))
                .isInstanceOfSatisfying(CircuitFormatException.class, exception -> assertThat(exception.reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void aNullFrameEntryInADefinitionFailsTheFile() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree(circuitFormat.write(documentUsing(inverterWithNotes())));
        ((ArrayNode) root.path("definitions").get(0).path("stickyNotes")).addNull();

        assertThatThrownBy(() -> circuitFormat.read(root.toString()))
                .isInstanceOfSatisfying(CircuitFormatException.class, exception -> assertThat(exception.reason())
                        .isEqualTo(CircuitFormatException.Reason.INVALID_ELEMENT));
    }

    @Test
    void aComponentFileKeepsItsFramesAndNotes() throws CircuitFormatException {
        DefinitionFormat format = new DefinitionFormat(registry);
        ComponentDefinition definition = inverterWithNotes();

        DefinitionBundle read = format.read(format.write(new DefinitionBundle(definition, List.of())));

        assertThat(read.root()).isEqualTo(definition);
    }

    @Test
    void aCopiedInstanceCarriesItsDefinitionsFramesAndNotes() throws CircuitFormatException {
        FragmentFormat format = new FragmentFormat(registry);
        CircuitDocument document = documentUsing(inverterWithNotes());
        ComponentTypeRegistry documentRegistry = registry.copy();
        documentRegistry.addResolver(SubcircuitTypes.resolverFor(document));
        CircuitFragment fragment = CircuitFragment.of(document, List.of(new ElementId("c1")), documentRegistry);

        CircuitFragment read = format.read(format.write(fragment));

        assertThat(read.definitions()).containsExactly(inverterWithNotes());
    }
}
