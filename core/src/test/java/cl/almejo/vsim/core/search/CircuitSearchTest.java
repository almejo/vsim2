package cl.almejo.vsim.core.search;

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
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static cl.almejo.vsim.core.DocumentFixtures.place;
import static org.assertj.core.api.Assertions.assertThat;

class CircuitSearchTest {

    private static final String HALF_ADDER = "half-adder";
    private static final String FULL_ADDER = "full-adder";

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry()
            .extendedWith(SubcircuitTypes.resolverFor(document));

    /** What the UI would show as type names: gates by their family, user components by their definition name. */
    private final Function<String, String> typeDisplayName = typeId -> switch (typeId) {
        case StandardComponentTypes.AND2 -> "And";
        case StandardComponentTypes.OR2 -> "Or";
        case StandardComponentTypes.XOR2 -> "Xor";
        case StandardComponentTypes.LED -> "LED";
        case InterfacePins.INPUT_PIN -> "Pin de entrada";
        case InterfacePins.OUTPUT_PIN -> "Pin de salida";
        default -> SubcircuitTypes.definitionIdOf(typeId).flatMap(document::definition)
                .map(ComponentDefinition::name).orElse(typeId);
    };

    private List<SearchHit> find(String text) {
        return CircuitSearch.find(document, registry, new SearchQuery(text, false, false).compile(), typeDisplayName);
    }

    private TextLabel text(int x, int y, String text) {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), text);
        document.add(label);
        return label;
    }

    private Frame frame(int x, int y, String title) {
        Frame frame = new Frame(document.newFrameId(), new GridPoint(x, y), 10, 10, title, FrameTint.NONE, false);
        document.add(frame);
        return frame;
    }

    private static List<ElementId> ids(String... values) {
        return java.util.Arrays.stream(values).map(ElementId::new).toList();
    }

    private static Component part(String typeId, String id, int x, int y, String label) {
        return new Component(new ElementId(id), typeId, new GridPoint(x, y), Rotation.R0, Map.of(), label);
    }

    /** «Medio sumador»: a xor labeled X1 and an unlabeled and. «Sumador completo»: half adders H1, H2 and an or. */
    private void defineAdders() {
        document.putDefinition(new ComponentDefinition(HALF_ADDER, "Medio sumador", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(part(StandardComponentTypes.XOR2, "c1", 0, 0, "X1"),
                        part(StandardComponentTypes.AND2, "c2", 0, 6, "")), List.of(), List.of())));
        String halfAdderType = SubcircuitTypes.typeIdOf(HALF_ADDER);
        document.putDefinition(new ComponentDefinition(FULL_ADDER, "Sumador completo", "", new GridSize(4, 6),
                List.of(), new CircuitFragment(List.of(part(halfAdderType, "c1", 0, 0, "H1"),
                part(halfAdderType, "c2", 0, 8, "H2"),
                part(StandardComponentTypes.OR2, "c3", 8, 4, "")), List.of(), List.of())));
    }

    @Test
    void findsComponentLabels() {
        Component gate = place(registry, document, StandardComponentTypes.AND2, 0, 0, "Acarreo");

        assertThat(find("acarreo"))
                .containsExactly(new SearchHit(gate.id(), List.of(), List.of(), SearchHit.Field.LABEL, "Acarreo"));
    }

    @Test
    void findsTypeNamesThroughTheGivenDisplayName() {
        Component gate = place(registry, document, StandardComponentTypes.AND2, 0, 0, "");

        assertThat(find("and")).containsExactly(new SearchHit(gate.id(), List.of(), List.of(), SearchHit.Field.TYPE, "And"));
    }

    @Test
    void findsFreeTextsAndFrameTitles() {
        TextLabel note = text(0, 0, "Sumador de prueba");
        Frame frame = frame(0, 20, "Etapa de prueba");

        assertThat(find("prueba")).containsExactly(
                new SearchHit(note.id(), List.of(), List.of(), SearchHit.Field.TEXT, "Sumador de prueba"),
                new SearchHit(frame.id(), List.of(), List.of(), SearchHit.Field.FRAME_TITLE, "Etapa de prueba"));
    }

    @Test
    void findsAWordInATextStickyNoteAndInAChecklistItem() {
        StickyNote textNote = new StickyNote(document.newStickyNoteId(), new GridPoint(0, 0), 8, 6,
                StickyColor.YELLOW, new StickyText("Falta probar Cin"), false);
        StickyNote listNote = new StickyNote(document.newStickyNoteId(), new GridPoint(0, 20), 8, 6,
                StickyColor.PINK, new StickyChecklist(List.of(new StickyItem("poner reloj", false),
                new StickyItem("probar Cout", true))), false);
        document.add(textNote);
        document.add(listNote);

        assertThat(find("cin")).extracting(SearchHit::elementId).containsExactly(textNote.id());
        assertThat(find("cout")).extracting(SearchHit::elementId).containsExactly(listNote.id());
        assertThat(find("probar")).extracting(SearchHit::elementId).containsExactly(textNote.id(), listNote.id());
    }

    @Test
    void theLabelOfAnInterfacePinIsItsPinName() {
        Component input = place(registry, document, InterfacePins.INPUT_PIN, 0, 0, "Cin");
        Component output = place(registry, document, InterfacePins.OUTPUT_PIN, 0, 10, "Cout");

        assertThat(find("cin"))
                .containsExactly(new SearchHit(input.id(), List.of(), List.of(), SearchHit.Field.PIN_NAME, "Cin"));
        assertThat(find("cout"))
                .containsExactly(new SearchHit(output.id(), List.of(), List.of(), SearchHit.Field.PIN_NAME, "Cout"));
    }

    @Test
    void findsTunnelsByTheirName() {
        Component entrance = registry.require(StandardComponentTypes.TUNNEL_ENTRANCE).newComponent(
                document.newComponentId(), new GridPoint(0, 0), Rotation.R0, Map.of(Tunnels.NAME, "CLK"), "");
        Component exit = registry.require(StandardComponentTypes.TUNNEL_EXIT).newComponent(
                document.newComponentId(), new GridPoint(0, 10), Rotation.R0, Map.of(Tunnels.NAME, "CLK"), "");
        document.add(entrance);
        document.add(exit);
        place(registry, document, StandardComponentTypes.AND2, 0, 20, "");

        assertThat(find("clk")).containsExactly(
                new SearchHit(entrance.id(), List.of(), List.of(), SearchHit.Field.TUNNEL_NAME, "CLK"),
                new SearchHit(exit.id(), List.of(), List.of(), SearchHit.Field.TUNNEL_NAME, "CLK"));
    }

    @Test
    void anElementMatchingOnLabelAndTypeGivesOneHitWithItsLabel() {
        Component gate = place(registry, document, StandardComponentTypes.AND2, 0, 0, "Banda");

        assertThat(find("and"))
                .containsExactly(new SearchHit(gate.id(), List.of(), List.of(), SearchHit.Field.LABEL, "Banda"));
    }

    @Test
    void wiresNeverMatch() {
        document.add(new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(10, 0)));

        assertThat(find("w")).isEmpty();
    }

    @Test
    void anEmptyOrInvalidQueryFindsNothing() {
        place(registry, document, StandardComponentTypes.AND2, 0, 0, "(a");

        assertThat(find("")).isEmpty();
        assertThat(CircuitSearch.find(document, registry, new SearchQuery("(a", false, true).compile(),
                typeDisplayName)).isEmpty();
    }

    @Test
    void hitsFollowTheReadingOrderTopToBottomThenLeftToRight() {
        Component right = place(registry, document, StandardComponentTypes.LED, 20, 0, "x3");
        Component below = place(registry, document, StandardComponentTypes.LED, 0, 10, "x4");
        Component left = place(registry, document, StandardComponentTypes.LED, 0, 0, "x1");
        TextLabel middle = text(10, 0, "x2");

        assertThat(find("x").stream().map(SearchHit::elementId))
                .containsExactly(left.id(), middle.id(), right.id(), below.id());
    }

    @Test
    void elementsAtTheSameTopLeftKeepTheDocumentOrder() {
        TextLabel first = text(5, 5, "uno");
        Component second = place(registry, document, StandardComponentTypes.LED, 5, 5, "uno");

        assertThat(find("uno").stream().map(SearchHit::elementId)).containsExactly(first.id(), second.id());
    }

    @Test
    void matchesInsideUserComponentsAreReachedThroughTheirTopLevelInstance() {
        defineAdders();
        Component adder = place(registry, document, SubcircuitTypes.typeIdOf(FULL_ADDER), 0, 0, "U3");

        assertThat(find("x1")).containsExactly(
                new SearchHit(adder.id(), ids("c1", "c1"), List.of("H1", "X1"), SearchHit.Field.LABEL, "X1"),
                new SearchHit(adder.id(), ids("c2", "c1"), List.of("H2", "X1"), SearchHit.Field.LABEL, "X1"));
    }

    @Test
    void aNestedPartWithoutLabelIsNamedByItsType() {
        defineAdders();
        Component adder = place(registry, document, SubcircuitTypes.typeIdOf(FULL_ADDER), 0, 0, "U3");

        List<SearchHit> hits = CircuitSearch.find(document, registry, new SearchQuery("Or", true, false).compile(),
                typeDisplayName);

        assertThat(hits).containsExactly(
                new SearchHit(adder.id(), ids("c3"), List.of("Or"), SearchHit.Field.TYPE, "Or"));
    }

    @Test
    void theInstanceComesFirstThenItsNestedHitsThenTheNextElement() {
        defineAdders();
        Component adder = place(registry, document, SubcircuitTypes.typeIdOf(FULL_ADDER), 0, 0, "U3");
        TextLabel note = text(0, 30, "Un sumador");

        assertThat(find("sumador")).containsExactly(
                new SearchHit(adder.id(), List.of(), List.of(), SearchHit.Field.TYPE, "Sumador completo"),
                new SearchHit(adder.id(), ids("c1"), List.of("H1"), SearchHit.Field.TYPE, "Medio sumador"),
                new SearchHit(adder.id(), ids("c2"), List.of("H2"), SearchHit.Field.TYPE, "Medio sumador"),
                new SearchHit(note.id(), List.of(), List.of(), SearchHit.Field.TEXT, "Un sumador"));
    }

    @Test
    void twoUnlabeledPartsOfOneTypeAreTellApartByTheirIds() {
        document.putDefinition(new ComponentDefinition("two-ors", "Dos o", "", new GridSize(4, 4), List.of(),
                new CircuitFragment(List.of(part(StandardComponentTypes.OR2, "c1", 0, 0, ""),
                        part(StandardComponentTypes.OR2, "c2", 0, 6, "")), List.of(), List.of())));
        Component instance = place(registry, document, SubcircuitTypes.typeIdOf("two-ors"), 0, 0, "");

        List<SearchHit> hits = CircuitSearch.find(document, registry, new SearchQuery("Or", true, false).compile(),
                typeDisplayName);

        assertThat(hits).containsExactly(
                new SearchHit(instance.id(), ids("c1"), List.of("Or"), SearchHit.Field.TYPE, "Or"),
                new SearchHit(instance.id(), ids("c2"), List.of("Or"), SearchHit.Field.TYPE, "Or"));
        assertThat(hits.get(0)).isNotEqualTo(hits.get(1));
    }

    @Test
    void aQueryThatGivesUpFindsNothingAtAll() {
        // The first labels match cheaply; the long ones exhaust the shared budget: no partial results.
        place(registry, document, StandardComponentTypes.LED, 0, 0, "aa");
        for (int index = 0; index < 400; index++) {
            place(registry, document, StandardComponentTypes.LED, 0, 10 + index, "a".repeat(20) + "!");
        }
        CompiledQuery query = new SearchQuery("^(a+)+\\1$", true, true).compile();

        List<SearchHit> hits = CircuitSearch.find(document, registry, query, typeDisplayName);

        assertThat(query.problem()).contains(new SearchProblem(SearchProblem.TOO_EXPENSIVE, List.of()));
        assertThat(hits).isEmpty();
    }

    @Test
    void anUnknownTypeStillMatchesItsLabel() {
        Component unknown = new Component(document.newComponentId(), "no-such-type", new GridPoint(3, 3),
                Rotation.R0, Map.of(), "misterio");
        document.add(unknown);

        assertThat(find("miste"))
                .containsExactly(new SearchHit(unknown.id(), List.of(), List.of(), SearchHit.Field.LABEL, "misterio"));
    }
}
