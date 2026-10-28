package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.read.CircuitView.NetView;
import cl.almejo.vsim.automation.read.CircuitView.PartView;
import cl.almejo.vsim.automation.read.CircuitView.PinView;
import cl.almejo.vsim.automation.read.ReadFilter.ElementKind;
import cl.almejo.vsim.core.component.appearance.ComponentAppearance;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.document.NoteStyle;
import cl.almejo.vsim.core.document.StickyChecklist;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyItem;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitReaderTest {

    private final ReadFixture fixture = new ReadFixture();

    @Test
    void aFlipFlopCircuitComesBackWithEveryPinsNameWidthAndPlace() {
        Component input = fixture.place(StandardComponentTypes.SWITCH, 4, 11);
        Component flipFlop = fixture.place(StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Rotation.R0, Map.of(),
                "FF1");
        Component clock = fixture.place(StandardComponentTypes.CLOCK, 10, 16);
        Wire data = fixture.wire(fixture.pin(input, 0), fixture.pin(flipFlop, 0));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        PartView part = part(view, flipFlop.id().value());
        assertThat(part.typeId()).isEqualTo(StandardComponentTypes.FLIP_FLOP_DATA);
        assertThat(part.label()).isEqualTo("FF1");
        assertThat(part.position()).isEqualTo(new GridPoint(10, 10));
        assertThat(part.rotation()).isEqualTo("R0");
        assertThat(part.parameters()).containsKey("delay");
        assertThat(part.bounds()).isEqualTo(fixture.registry.typeOf(flipFlop).bounds(flipFlop));
        assertThat(part.pins()).extracting(PinView::pin).containsExactly(
                new PinReference(flipFlop.id().value(), "d", 0),
                new PinReference(flipFlop.id().value(), "q", 0),
                new PinReference(flipFlop.id().value(), "clock", 0),
                new PinReference(flipFlop.id().value(), "nQ", 0));
        assertThat(part.pins()).extracting(PinView::position).containsExactly(new GridPoint(10, 12),
                new GridPoint(14, 12), new GridPoint(12, 14), new GridPoint(14, 13));
        assertThat(part.pins()).extracting(PinView::direction).containsExactly("INPUT", "OUTPUT", "INPUT", "OUTPUT");
        assertThat(part.pins()).extracting(PinView::width).containsOnly(1);
        assertThat(part.pins()).extracting(PinView::connected).containsExactly(true, false, false, false);
        assertThat(view.wires()).singleElement().satisfies(wire -> {
            assertThat(wire.id()).isEqualTo(data.id().value());
            assertThat(wire.netId()).isEqualTo(part.pins().get(0).netId());
        });
        assertThat(part(view, clock.id().value()).pins()).singleElement()
                .satisfies(pin -> assertThat(pin.position()).isEqualTo(new GridPoint(12, 18)));
    }

    @Test
    void theNetsAreListsOfPinReferences() {
        Component input = fixture.place(StandardComponentTypes.SWITCH, 4, 11);
        Component flipFlop = fixture.place(StandardComponentTypes.FLIP_FLOP_DATA, 10, 10);
        Component led = fixture.place(StandardComponentTypes.LED, 20, 9);
        Wire data = fixture.wire(fixture.pin(input, 0), fixture.pin(flipFlop, 0));
        fixture.wire(fixture.pin(flipFlop, 1), new GridPoint(21, 12));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        NetView dataNet = net(view, new PinReference(flipFlop.id().value(), "d", 0));
        assertThat(dataNet.pins()).containsExactlyInAnyOrder(new PinReference(input.id().value(), "out", 0),
                new PinReference(flipFlop.id().value(), "d", 0));
        assertThat(dataNet.wires()).containsExactly(data.id().value());
        assertThat(dataNet.width()).isEqualTo(1);
        assertThat(net(view, new PinReference(led.id().value(), "in", 0)).pins())
                .contains(new PinReference(flipFlop.id().value(), "q", 0));
    }

    @Test
    void aRotatedPartsPinsAreWhereTheyAreDrawn() {
        Component flipFlop = fixture.place(StandardComponentTypes.FLIP_FLOP_DATA, 10, 10, Rotation.R90, Map.of(),
                "");

        PartView part = part(CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty()),
                flipFlop.id().value());

        assertThat(part.rotation()).isEqualTo("R90");
        assertThat(part.pins()).extracting(PinView::position)
                .containsExactly(new GridPoint(8, 10), new GridPoint(8, 14), new GridPoint(6, 12),
                        new GridPoint(7, 14));
    }

    @Test
    void anEightBitPartHasItsEightBitPins() {
        Component splitter = fixture.place(StandardComponentTypes.BUS_SPLITTER, 0, 0, Rotation.R0,
                Map.of("width", 4), "");

        PartView part = part(CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty()),
                splitter.id().value());

        assertThat(part.parameters()).containsEntry("width", 4);
        assertThat(part.pins()).hasSize(5);
        assertThat(part.pins().get(0).width()).isEqualTo(4);
        assertThat(part.pins()).extracting(pin -> pin.pin().pinName()).containsExactly("bus", "b0", "b1", "b2", "b3");
    }

    @Test
    void aUserComponentsInstanceHasThePinsOfItsDefinition() {
        fixture.document.putDefinition(ExampleDefinitions.byteBuffer());
        Component buffer = fixture.place(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), 10, 10);

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        assertThat(part(view, buffer.id().value()).pins())
                .extracting(PinView::pin, PinView::width, PinView::position)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(new PinReference(buffer.id().value(), "a", 0),
                                8, new GridPoint(10, 12)),
                        org.assertj.core.groups.Tuple.tuple(new PinReference(buffer.id().value(), "y", 0),
                                8, new GridPoint(14, 12)));
        assertThat(view.summary().userComponents()).containsExactly("Búfer de 8 bits");
    }

    @Test
    void anInstanceWithItsOwnShapeSaysSoAndOthersDoNot() {
        fixture.document.putDefinition(ExampleDefinitions.byteBuffer());
        Component plain = fixture.place(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), 10, 10);
        Component shaped = fixture.place(SubcircuitTypes.typeIdOf(ExampleDefinitions.BYTE_BUFFER), 30, 10);
        Component gate = fixture.place(StandardComponentTypes.BUFFER, 50, 10);
        fixture.document.replace(shaped.withAppearance(Optional.of(ComponentAppearance.of(
                ExampleDefinitions.byteBuffer()))));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());

        assertThat(part(view, plain.id().value()).ownShape()).contains(false);
        assertThat(part(view, shaped.id().value()).ownShape()).contains(true);
        assertThat(part(view, gate.id().value()).ownShape()).isEmpty();
    }

    @Test
    void aNotesStyleShowsOnlyWhenItIsNotPlainAndOnlyItsColours() {
        fixture.document.add(new TextLabel(fixture.document.newLabelId(), new GridPoint(0, 0), "simple"));
        fixture.document.add(new TextLabel(fixture.document.newLabelId(), new GridPoint(0, 4), "ojo",
                new NoteStyle(NoteColor.YELLOW, NoteColor.NONE, NoteColor.RED)));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());

        assertThat(view.notes()).extracting(CircuitView.NoteView::style).containsExactly(Optional.empty(),
                Optional.of(new CircuitView.NoteStyleView(Optional.of(NoteColor.YELLOW), Optional.empty(),
                        Optional.of(NoteColor.RED))));
    }

    @Test
    void stickyNotesAreListedWithTheirFieldsAndTheirItemsDoneState() {
        fixture.document.add(new StickyNote(fixture.document.newStickyNoteId(), new GridPoint(3, 4), 8, 6,
                StickyColor.PINK, new StickyText("Falta: probar con Cin = 1"), false));
        fixture.document.add(new StickyNote(fixture.document.newStickyNoteId(), new GridPoint(20, 4), 10, 7,
                StickyColor.GREEN, new StickyChecklist(List.of(new StickyItem("uno", true),
                new StickyItem("dos", false))), true));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());

        assertThat(view.stickyNotes()).extracting(CircuitView.StickyNoteView::position, CircuitView.StickyNoteView::width,
                CircuitView.StickyNoteView::height, CircuitView.StickyNoteView::color, CircuitView.StickyNoteView::kind,
                CircuitView.StickyNoteView::text, CircuitView.StickyNoteView::items,
                CircuitView.StickyNoteView::folded).containsExactly(
                org.assertj.core.groups.Tuple.tuple(new GridPoint(3, 4), 8, 6, "PINK", "TEXT",
                        Optional.of("Falta: probar con Cin = 1"), Optional.empty(), false),
                org.assertj.core.groups.Tuple.tuple(new GridPoint(20, 4), 10, 7, "GREEN", "CHECKLIST",
                        Optional.empty(), Optional.of(List.of(new CircuitView.StickyItemView("uno", true),
                        new CircuitView.StickyItemView("dos", false))), true));
        assertThat(view.stickyNotes()).extracting(CircuitView.StickyNoteView::id).doesNotContainNull();
        assertThat(view.summary().stickyNotes()).isEqualTo(2);
        assertThat(view.matchingElements()).isEqualTo(2);
        assertThat(view.notes()).isEmpty();
    }

    @Test
    void theStickyNoteKindNarrowsTheReadingAndOtherKindsLeaveThemOut() {
        fixture.place(StandardComponentTypes.BUFFER, 50, 10);
        fixture.document.add(new StickyNote(fixture.document.newStickyNoteId(), new GridPoint(3, 4), 8, 6,
                StickyColor.YELLOW, new StickyText("x"), false));

        CircuitView onlyNotes = CircuitReader.read(fixture.source(), fullFilter(Set.of(ElementKind.STICKY_NOTE),
                Set.of(), Optional.empty()), Optional.empty());
        CircuitView onlyParts = CircuitReader.read(fixture.source(), fullFilter(Set.of(ElementKind.PART),
                Set.of(), Optional.empty()), Optional.empty());

        assertThat(onlyNotes.stickyNotes()).hasSize(1);
        assertThat(onlyNotes.parts()).isEmpty();
        assertThat(onlyParts.stickyNotes()).isEmpty();
        assertThat(onlyParts.parts()).hasSize(1);
    }

    @Test
    void theSummaryCountsANandAsANandAndNotAsAnAnd() {
        fixture.place(StandardComponentTypes.AND, 0, 0);
        fixture.place(StandardComponentTypes.AND, 10, 0, Rotation.R0, Map.of(StandardComponentTypes.NEGATE, true), "");
        fixture.place(StandardComponentTypes.BUFFER, 20, 0, Rotation.R0,
                Map.of(StandardComponentTypes.NEGATE, true), "");

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());

        assertThat(view.summary().partsByType()).containsEntry("and", 1).containsEntry("nand", 1)
                .containsEntry("not", 1);
    }

    @Test
    void theSummaryListsPartsFramesNotesAndGuidesAndOnlyCountsWiresAndNets() {
        Component input = fixture.place(StandardComponentTypes.SWITCH, 4, 11);
        Component flipFlop = fixture.place(StandardComponentTypes.FLIP_FLOP_DATA, 10, 10);
        fixture.wire(fixture.pin(input, 0), fixture.pin(flipFlop, 0));
        fixture.document.add(new Frame(fixture.document.newFrameId(), new GridPoint(0, 0), 30, 20, "Etapa 1",
                FrameTint.BLUE, false));
        fixture.document.add(new TextLabel(fixture.document.newLabelId(), new GridPoint(2, 22), "Prueba: d=1"));
        fixture.document.setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 10)));

        CircuitView view = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());

        assertThat(view.name()).isEqualTo("Prueba");
        assertThat(view.parts()).hasSize(2).allSatisfy(part -> assertThat(part.pins()).isEmpty());
        assertThat(view.wires()).isEmpty();
        assertThat(view.nets()).isEmpty();
        assertThat(view.frames()).singleElement().satisfies(frame -> {
            assertThat(frame.title()).isEqualTo("Etapa 1");
            assertThat(frame.bounds()).isEqualTo(new Bounds(0, 0, 30, 20));
            assertThat(frame.tint()).isEqualTo("BLUE");
        });
        assertThat(view.notes()).singleElement().satisfies(note -> assertThat(note.text()).isEqualTo("Prueba: d=1"));
        assertThat(view.guides()).singleElement()
                .satisfies(guide -> assertThat(guide).isEqualTo(new CircuitView.GuideView("VERTICAL", 10)));
        assertThat(view.summary().parts()).isEqualTo(2);
        assertThat(view.summary().wires()).isEqualTo(1);
        assertThat(view.summary().frames()).isEqualTo(1);
        assertThat(view.summary().notes()).isEqualTo(1);
        assertThat(view.summary().guides()).isEqualTo(1);
        assertThat(view.summary().nets()).isPositive();
        assertThat(view.summary().partsByType()).containsEntry(StandardComponentTypes.SWITCH, 1)
                .containsEntry(StandardComponentTypes.FLIP_FLOP_DATA, 1);
        assertThat(view.summary().bounds()).contains(new Bounds(0, 0, 30, 22));
        assertThat(view.matchingElements()).isEqualTo(4);
        assertThat(view.nextCursor()).isEmpty();
    }

    @Test
    void aBigCircuitComesInPagesOfAtMostTwoHundred() {
        for (int index = 0; index < 250; index++) {
            fixture.place(StandardComponentTypes.BUFFER, (index % 25) * 6, (index / 25) * 6);
        }

        CircuitView first = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());
        CircuitView second = CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, first.nextCursor());

        assertThat(first.parts()).hasSize(ReadFilter.MAXIMUM_PAGE_SIZE);
        assertThat(first.matchingElements()).isEqualTo(250);
        assertThat(first.nextCursor()).isPresent();
        assertThat(second.parts()).hasSize(50);
        assertThat(second.nextCursor()).isEmpty();
        assertThat(second.guides()).isEmpty();
        assertThat(first.parts().stream().map(PartView::id).toList())
                .doesNotContainAnyElementsOf(second.parts().stream().map(PartView::id).toList());
        assertThat(summaryOfPageSize(1000).pageSize()).isEqualTo(ReadFilter.MAXIMUM_PAGE_SIZE);
    }

    @Test
    void aCursorOfAnOlderVersionIsStaleAndABrokenOneIsRefused() {
        for (int index = 0; index < 5; index++) {
            fixture.place(StandardComponentTypes.BUFFER, index * 6, 0);
        }
        CircuitView first = CircuitReader.read(fixture.source(), summaryOfPageSize(2), Optional.empty());
        fixture.useVersion(8);

        assertThatThrownBy(() -> CircuitReader.read(fixture.source(), summaryOfPageSize(2),
                first.nextCursor()))
                .isInstanceOfSatisfying(AutomationException.class, exception -> {
                    assertThat(exception.error()).isEqualTo(AutomationError.STALE_DOCUMENT);
                    assertThat(exception.recovery()).containsEntry("currentVersion", 8L);
                });
        assertThatThrownBy(() -> CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.of("nonsense")))
                .isInstanceOfSatisfying(AutomationException.class,
                        exception -> assertThat(exception.error()).isEqualTo(AutomationError.INVALID_REFERENCE));
    }

    @Test
    void theFilterKeepsTheAskedKindsIdsAndRegion() {
        Component left = fixture.place(StandardComponentTypes.BUFFER, 0, 0);
        Component right = fixture.place(StandardComponentTypes.BUFFER, 40, 0);
        fixture.wire(fixture.pin(left, 1), new GridPoint(10, 2));

        assertThat(CircuitReader.read(fixture.source(),
                fullFilter(Set.of(ElementKind.WIRE), Set.of(), Optional.empty()), Optional.empty()).parts()).isEmpty();
        assertThat(CircuitReader.read(fixture.source(),
                fullFilter(Set.of(), Set.of(right.id().value()), Optional.empty()), Optional.empty()).parts())
                .extracting(PartView::id).containsExactly(right.id().value());
        CircuitView region = CircuitReader.read(fixture.source(),
                fullFilter(Set.of(), Set.of(), Optional.of(new Bounds(-1, -1, 20, 10))), Optional.empty());
        assertThat(region.parts()).extracting(PartView::id).containsExactly(left.id().value());
        assertThat(region.wires()).hasSize(1);
        assertThat(region.matchingElements()).isEqualTo(2);
    }

    @Test
    void readingChangesNothing() {
        Component input = fixture.place(StandardComponentTypes.SWITCH, 4, 11);
        Component flipFlop = fixture.place(StandardComponentTypes.FLIP_FLOP_DATA, 10, 10);
        fixture.wire(fixture.pin(input, 0), fixture.pin(flipFlop, 0));
        long revision = fixture.document.revision();
        String serializedBefore = fixture.document.elements().toString();

        CircuitReader.read(fixture.source(), ReadFilter.FULL, Optional.empty());
        CircuitReader.read(fixture.source(), ReadFilter.SUMMARY, Optional.empty());

        assertThat(fixture.document.revision()).isEqualTo(revision);
        assertThat(fixture.document.elements().toString()).isEqualTo(serializedBefore);
    }

    private static ReadFilter summaryOfPageSize(int pageSize) {
        return new ReadFilter(ReadFilter.Detail.SUMMARY, Set.of(), Set.of(), Optional.empty(), pageSize);
    }

    private static ReadFilter fullFilter(Set<ElementKind> kinds, Set<String> elementIds, Optional<Bounds> region) {
        return new ReadFilter(ReadFilter.Detail.FULL, kinds, elementIds, region, ReadFilter.MAXIMUM_PAGE_SIZE);
    }

    private static PartView part(CircuitView view, String id) {
        return view.parts().stream().filter(part -> part.id().equals(id)).findFirst().orElseThrow();
    }

    private static NetView net(CircuitView view, PinReference pin) {
        return view.nets().stream().filter(net -> net.pins().contains(pin)).findFirst().orElseThrow();
    }
}
