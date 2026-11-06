package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class LayoutItemsTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private LayoutItems items(CircuitDocument document) {
        return LayoutItems.of(document, registry, everything(document), TextBoxes.NONE);
    }

    @Test
    void aGateIsOneMovableBodyWithItsLabelAndAGapOfOne() {
        CircuitDocument document = new CircuitDocument();
        Component labelled = gate(registry, document, 0, 0).withLabel("A");
        document.replace(labelled);
        TextBoxes labelBelow = new TextBoxes() {
            @Override
            public Optional<Bounds> componentLabel(Component component) {
                return Optional.of(new Bounds(0, 5, 3, 1));
            }

            @Override
            public Bounds freeText(TextLabel label) {
                return TextBoxes.NONE.freeText(label);
            }
        };

        LayoutItems items = LayoutItems.of(document, registry, everything(document), labelBelow);

        LayoutItems.Item item = items.item(labelled.id()).orElseThrow();
        assertThat(item.bounds()).isEqualTo(new Bounds(0, 0, 4, 6));
        assertThat(item.gap()).isEqualTo(LayoutItems.BODY_GAP);
        assertThat(item.movable()).isTrue();
        assertThat(item.parts()).isEqualTo(1);
        assertThat(item.texts()).isZero();
    }

    @Test
    void gatesJoinedPinOnPinAreOneItemNamedByTheSmallestId() {
        CircuitDocument document = new CircuitDocument();
        for (int index = 0; index < 9; index++) {
            gate(registry, document, 100 + 10 * index, 100);
        }
        Component tenth = gate(registry, document, 0, 0);
        Component second = document.components().get(1);
        document.replace(second.withPosition(new GridPoint(4, 0)));

        LayoutItems items = items(document);

        LayoutItems.Item cluster = items.item(second.id()).orElseThrow();
        assertThat(cluster.members()).containsExactly(second.id(), tenth.id());
        assertThat(cluster.bounds()).isEqualTo(new Bounds(0, 0, 8, 4));
        assertThat(cluster.parts()).isEqualTo(2);
        assertThat(items.item(tenth.id())).isEmpty();
    }

    @Test
    void theIdOrderIsNatural() {
        assertThat(LayoutItems.ID_ORDER.compare(new ElementId("c2"), new ElementId("c10"))).isNegative();
        assertThat(LayoutItems.ID_ORDER.compare(new ElementId("c10"), new ElementId("l1"))).isNegative();
        assertThat(LayoutItems.ID_ORDER.compare(new ElementId("c3"), new ElementId("c3"))).isZero();
    }

    @Test
    void whatIsOutOfScopeIsFixedAndSoIsAClusterWithAMemberOutOfScope() {
        CircuitDocument document = new CircuitDocument();
        Component selected = gate(registry, document, 0, 0);
        Component joined = gate(registry, document, 4, 0);
        Component alone = gate(registry, document, 20, 0);
        Component other = gate(registry, document, 30, 0);

        LayoutItems items = LayoutItems.of(document, registry, Set.of(selected.id(), other.id()), TextBoxes.NONE);

        assertThat(items.item(selected.id()).orElseThrow().movable()).isFalse();
        assertThat(items.item(alone.id()).orElseThrow().movable()).isFalse();
        assertThat(items.item(other.id()).orElseThrow().movable()).isTrue();
        assertThat(items.item(selected.id()).orElseThrow().members()).contains(joined.id());
    }

    @Test
    void freeTextsAndStickyNotesAreTextsWithNoGap() {
        CircuitDocument document = new CircuitDocument();
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(3, 4), "hola");
        document.add(note);
        StickyNote sticky = new StickyNote(document.newStickyNoteId(), new GridPoint(10, 10), 8, 6, StickyColor.YELLOW,
                new StickyText("recordar"), false);
        document.add(sticky);

        TextBoxes fiveWide = new TextBoxes() {
            @Override
            public Optional<Bounds> componentLabel(Component component) {
                return Optional.empty();
            }

            @Override
            public Bounds freeText(TextLabel label) {
                return new Bounds(label.position().x(), label.position().y(), 5, 1);
            }
        };

        LayoutItems items = LayoutItems.of(document, registry, everything(document), fiveWide);

        assertThat(items(document).item(note.id())).as("a text with no size is no item").isEmpty();
        assertThat(items.item(note.id()).orElseThrow().bounds()).isEqualTo(new Bounds(3, 4, 5, 1));
        assertThat(items.item(note.id()).orElseThrow().gap()).isEqualTo(LayoutItems.TEXT_GAP);
        assertThat(items.item(note.id()).orElseThrow().texts()).isEqualTo(1);
        assertThat(items.item(sticky.id()).orElseThrow().bounds()).isEqualTo(new Bounds(10, 10, 8, 6));
        assertThat(items.item(sticky.id()).orElseThrow().gap()).isEqualTo(LayoutItems.TEXT_GAP);
    }

    @Test
    void anItemAcrossAFrameBorderIsFixedAndOneInsideBelongsToTheFrame() {
        CircuitDocument document = new CircuitDocument();
        Frame block = frame(document, 0, 0, 12, 8);
        Component across = gate(registry, document, 10, 2);
        Component inside = gate(registry, document, 2, 2);

        LayoutItems items = items(document);

        assertThat(items.item(across.id()).orElseThrow().movable()).isFalse();
        assertThat(items.item(inside.id()).orElseThrow().movable()).isTrue();
        assertThat(items.item(inside.id()).orElseThrow().region()).contains(block.id());
        assertThat(items.item(across.id()).orElseThrow().region()).isEmpty();
    }

    @Test
    void eachFrameIsSeparatedApartWithWallsInsideAndAnObstacleOutside() {
        CircuitDocument document = new CircuitDocument();
        frame(document, 0, 0, 12, 8);
        Component inside = gate(registry, document, 2, 2);
        Component outside = gate(registry, document, 20, 2);
        gate(registry, document, 3, 3);
        gate(registry, document, 21, 3);

        List<List<LayoutBox>> groups = items(document).separationGroups();

        assertThat(groups).hasSize(2);
        List<LayoutBox> insideGroup = groups.stream()
                .filter(group -> group.stream().anyMatch(box -> box.id().equals(inside.id()))).findFirst()
                .orElseThrow();
        List<LayoutBox> outsideGroup = groups.stream()
                .filter(group -> group.stream().anyMatch(box -> box.id().equals(outside.id()))).findFirst()
                .orElseThrow();
        assertThat(insideGroup).noneMatch(box -> box.id().equals(outside.id()));
        Bounds insideBody = new Bounds(2, 2, 4, 4);
        Bounds leftOfTheFrame = new Bounds(-5, 2, 4, 4);
        assertThat(insideGroup).filteredOn(box -> !box.movable())
                .anyMatch(wall -> GreedySeparator.tooClose(leftOfTheFrame, wall.bounds(), LayoutItems.BODY_GAP))
                .noneMatch(wall -> GreedySeparator.tooClose(insideBody.translate(-2, -2), wall.bounds(),
                        LayoutItems.BODY_GAP));
        assertThat(outsideGroup).filteredOn(box -> !box.movable())
                .anyMatch(obstacle -> GreedySeparator.tooClose(insideBody, obstacle.bounds(), LayoutItems.BODY_GAP))
                .noneMatch(obstacle -> GreedySeparator.tooClose(new Bounds(12, 2, 4, 4), obstacle.bounds(),
                        LayoutItems.BODY_GAP));
    }

    @Test
    void aWireThroughAForeignBodyIsAnObstacleAndOneEndingOnItsPinIsNot() {
        CircuitDocument document = new CircuitDocument();
        Component crossed = gate(registry, document, 0, 0);
        wire(document, -6, 1, 10, 1);
        wire(document, 0, 2, 9, 2);

        LayoutItems items = items(document);
        LayoutItems.Item item = items.item(crossed.id()).orElseThrow();

        assertThat(items.obstacles(item)).hasSize(1);
        assertThat(LayoutItems.crossedByForeignWire(document, registry, List.of(crossed.id()))).isTrue();
        MoveWithWiresCommand.of(document, registry, Set.of(crossed.id()), 0, 2).apply(document);
        assertThat(LayoutItems.crossedByForeignWire(document, registry, List.of(crossed.id()))).isFalse();
    }

    @Test
    void itemsCoverEachOtherOnlyWhereABodyOrLabelIs() {
        CircuitDocument document = new CircuitDocument();
        Component labelled = gate(registry, document, 0, 0).withLabel("Habilitar");
        document.replace(labelled);
        Component neighbour = gate(registry, document, 6, 2);
        Component covering = gate(registry, document, 7, -1);
        TextBoxes labelToTheRight = new TextBoxes() {
            @Override
            public Optional<Bounds> componentLabel(Component component) {
                return component.label().isEmpty() ? Optional.empty()
                        : Optional.of(new Bounds(component.position().x() + 5, component.position().y() + 1, 6, 2));
            }

            @Override
            public Bounds freeText(TextLabel label) {
                return TextBoxes.NONE.freeText(label);
            }
        };

        LayoutItems items = LayoutItems.of(document, registry, everything(document), labelToTheRight);
        LayoutItems.Item item = items.item(labelled.id()).orElseThrow();

        assertThat(LayoutItems.overlaps(item.bounds(), items.item(neighbour.id()).orElseThrow().bounds(), 0))
                .isTrue();
        assertThat(items.coveredItems(item, Map.of())).containsExactly(covering.id());
    }

    @Test
    void overlapsCountsTouchingOnlyWithAGap() {
        Bounds left = new Bounds(0, 0, 4, 4);

        assertThat(LayoutItems.overlaps(left, new Bounds(4, 0, 4, 4), 0)).isFalse();
        assertThat(LayoutItems.overlaps(left, new Bounds(4, 0, 4, 4), 1)).isTrue();
        assertThat(LayoutItems.overlaps(left, new Bounds(5, 0, 4, 4), 1)).isFalse();
        assertThat(LayoutItems.overlaps(left, new Bounds(3, 3, 4, 4), 0)).isTrue();
        assertThat(LayoutItems.overlaps(left, new Bounds(-2, 2, 10, 0), 0)).isFalse();
    }

    @Test
    void aPlaceIsFreeOnlyAwayFromOthersAndInsideItsFrame() {
        CircuitDocument document = new CircuitDocument();
        frame(document, 0, 0, 12, 8);
        Component first = gate(registry, document, 1, 2);
        Component second = gate(registry, document, 2, 2);
        LayoutItems items = items(document);
        LayoutItems.Item item = items.item(first.id()).orElseThrow();
        Map<ElementId, Bounds> placed = Map.of(first.id(), new Bounds(1, 2, 4, 4), second.id(),
                new Bounds(2, 2, 4, 4));

        assertThat(items.isFree(item, new Bounds(1, 2, 4, 4), placed)).isFalse();
        assertThat(items.isFree(item, new Bounds(7, 2, 4, 4), placed)).isTrue();
        assertThat(items.isFree(item, new Bounds(-30, 2, 4, 4), placed)).isFalse();
    }
}
