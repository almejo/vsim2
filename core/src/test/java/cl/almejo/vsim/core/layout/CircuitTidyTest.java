package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.command.ReplaceContentCommand;
import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.wiring.Connectivity;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

class CircuitTidyTest {

    private static final BooleanSupplier NEVER = () -> false;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    private CircuitTidyPlan tidy(CircuitDocument document, Set<ElementId> scope, CircuitTidyOptions options,
                                 TextBoxes texts, CircuitTidy.Separator separator) {
        return CircuitTidy.plan(document, registry, scope, options, texts, NEVER, round -> {
        }, separator).orElseThrow();
    }

    private CircuitTidyPlan tidy(CircuitDocument document, CircuitTidy.Separator separator) {
        return tidy(document, everything(document), CircuitTidyOptions.DEFAULTS, TextBoxes.NONE, separator);
    }

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private Set<Set<PinRef>> nets(CircuitDocument document) {
        return Connectivity.of(document.wires(), document, registry).pinNets();
    }

    private Bounds body(CircuitDocument document, ElementId id) {
        return ElementBounds.of(document.component(id).orElseThrow(), registry);
    }

    /** True when two component bodies share an area or are closer than the 1-unit gap. */
    private boolean anyBodiesTooClose(CircuitDocument document) {
        List<Component> components = document.components();
        for (int first = 0; first < components.size(); first++) {
            for (int second = first + 1; second < components.size(); second++) {
                if (GreedySeparator.tooClose(ElementBounds.of(components.get(first), registry),
                        ElementBounds.of(components.get(second), registry), LayoutItems.BODY_GAP)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<DocumentElement, Long> snapshot(CircuitDocument document) {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }

    /** A separator that gives each listed box the delta given, whatever the boxes say. */
    private static CircuitTidy.Separator fixed(Map<ElementId, GridPoint> deltas) {
        return (boxes, cancelled) -> {
            Map<ElementId, GridPoint> given = new LinkedHashMap<>();
            boxes.forEach(box -> Optional.ofNullable(deltas.get(box.id())).ifPresent(
                    delta -> given.put(box.id(), delta)));
            return new Separation(given, Set.of());
        };
    }

    /** Two overlapping AND gates with wires on their inputs and outputs. */
    private CircuitDocument stackedAndGates() {
        CircuitDocument document = new CircuitDocument();
        place(registry, document, StandardComponentTypes.AND, 10, 10, "");
        place(registry, document, StandardComponentTypes.AND, 12, 12, "");
        wire(document, 4, 10, 10, 10);
        wire(document, 4, 14, 10, 14);
        wire(document, 4, 16, 12, 16);
        wire(document, 14, 12, 20, 12);
        wire(document, 16, 14, 22, 14);
        return document;
    }

    @Test
    void twoStackedAndGatesWithWiresEndSeparatedAndStillConnected() {
        CircuitDocument document = stackedAndGates();

        CircuitTidyPlan plan = tidy(document, new GreedySeparator());

        assertThat(anyBodiesTooClose(plan.after())).isFalse();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
        assertThat(plan.partsMoved()).isBetween(1, 2);
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void gatesJoinedPinOnPinMoveTogether() {
        CircuitDocument document = new CircuitDocument();
        Component left = gate(registry, document, 0, 0);
        Component right = gate(registry, document, 4, 0);
        Component third = gate(registry, document, 6, 1);

        CircuitTidyPlan plan = tidy(document, new GreedySeparator());

        GridPoint leftAfter = plan.after().component(left.id()).orElseThrow().position();
        GridPoint rightAfter = plan.after().component(right.id()).orElseThrow().position();
        assertThat(rightAfter.x() - leftAfter.x()).isEqualTo(4);
        assertThat(rightAfter.y() - leftAfter.y()).isZero();
        assertThat(leftAfter).isNotEqualTo(left.position());
        assertThat(plan.after().component(third.id()).orElseThrow().position()).isEqualTo(third.position());
        assertThat(plan.partsMoved()).isEqualTo(2);
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    @Test
    void aMoveThatWouldJoinAForeignWireTriesAnotherPlace() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component moved = gate(registry, document, 1, 0);
        gate(registry, document, 10, -7);
        wire(document, 10, -5, 10, 5);

        CircuitTidyPlan plan = tidy(document, fixed(Map.of(moved.id(), new GridPoint(5, 0))));

        assertThat(plan.after().component(moved.id()).orElseThrow().position()).isEqualTo(new GridPoint(1, 5));
        assertThat(nets(plan.after())).isEqualTo(nets(document));
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aGateWithNoGoodPlaceStaysAndIsCounted() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component boxedIn = gate(registry, document, 1, 0);
        gate(registry, document, 20, 0);
        wire(document, 6, 2, 20, 2);
        gate(registry, document, -14, 5);
        wire(document, -10, 7, 1, 7);
        gate(registry, document, -16, 0);
        wire(document, -12, 2, -5, 2);

        CircuitTidyPlan plan = tidy(document, fixed(Map.of(boxedIn.id(), new GridPoint(5, 0))));

        assertThat(plan.after().component(boxedIn.id()).orElseThrow().position()).isEqualTo(boxedIn.position());
        assertThat(plan.notSeparated()).containsExactly(boxedIn.id());
        assertThat(plan.partsMoved()).isZero();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    @Test
    void aBodyOverAForeignWireMovesOffIt() {
        CircuitDocument document = new CircuitDocument();
        Component crossed = gate(registry, document, 0, 0);
        gate(registry, document, -10, -1);
        gate(registry, document, 10, -1);
        wire(document, -6, 1, 10, 1);

        CircuitTidyPlan plan = tidy(document, new GreedySeparator());

        assertThat(plan.after().component(crossed.id()).orElseThrow().position()).isNotEqualTo(crossed.position());
        assertThat(LayoutItems.crossedByForeignWire(plan.after(), registry, List.of(crossed.id()))).isFalse();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
    }

    /** Through the public entry point, with the real {@link OverlapSeparation}, on everything. */
    private CircuitTidyPlan tidyForReal(CircuitDocument document) {
        return CircuitTidy.plan(document, registry, everything(document), CircuitTidyOptions.DEFAULTS,
                TextBoxes.NONE, NEVER, round -> {
                }).orElseThrow();
    }

    @Test
    void partsThatOnlyTouchAreNeitherMovedNorCounted() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        gate(registry, document, 0, 4);

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aWireOfThePartsOwnNetOverItsBodyIsNotAnOverlap() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        wire(document, 4, 2, 6, 2);
        wire(document, 6, 2, 6, -2);
        wire(document, 6, -2, 2, -2);
        wire(document, 2, -2, 2, 6);

        CircuitTidyPlan plan = tidy(document, everything(document),
                new CircuitTidyOptions(true, new TidyOptions(false, false, false)), TextBoxes.NONE,
                OverlapSeparation::separate);

        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aPartThatOverlapsNothingIsNotPushed() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        gate(registry, document, 2, 0);
        Component bystander = gate(registry, document, 7, 0);

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.after().component(bystander.id()).orElseThrow()).isEqualTo(bystander);
        assertThat(anyBodiesTooClose(plan.after())).isFalse();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aPartNeverMakesWayForAMuchSimplerOne() {
        CircuitDocument document = new CircuitDocument();
        Component crossedLamp = place(registry, document, StandardComponentTypes.LED, 0, 0, "");
        wire(document, -10, 1, 10, 1);
        Component gateWithThreePins = place(registry, document, StandardComponentTypes.AND, 0, 4, "");
        place(registry, document, StandardComponentTypes.LED, 3, 6, "");

        CircuitTidyPlan plan = tidy(document, everything(document),
                new CircuitTidyOptions(true, new TidyOptions(false, false, false)), TextBoxes.NONE,
                OverlapSeparation::separate);

        assertThat(plan.after().component(gateWithThreePins.id()).orElseThrow()).isEqualTo(gateWithThreePins);
        assertThat(LayoutItems.crossedByForeignWire(plan.after(), registry, List.of(crossedLamp.id()))).isFalse();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void theSimplerPartMovesAndTheOneWithMorePinsStays() {
        CircuitDocument document = new CircuitDocument();
        Component gateWithThreePins = place(registry, document, StandardComponentTypes.AND, 0, 0, "");
        Component lamp = place(registry, document, StandardComponentTypes.LED, 2, 1, "");

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.after().component(gateWithThreePins.id()).orElseThrow()).isEqualTo(gateWithThreePins);
        assertThat(plan.after().component(lamp.id()).orElseThrow().position()).isNotEqualTo(lamp.position());
        assertThat(plan.partsMoved()).isEqualTo(1);
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aTextWithoutSizeCoversNothing() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        document.add(new TextLabel(document.newLabelId(), new GridPoint(2, 2), "hola"));

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.textsMoved()).isZero();
        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aWireThatEntersALampToReachItsPinIsNotAnOverlap() {
        CircuitDocument document = new CircuitDocument();
        place(registry, document, StandardComponentTypes.SWITCH, 0, 0, "");
        place(registry, document, StandardComponentTypes.LED, 30, 0, "");
        wire(document, 4, 1, 31, 1);
        wire(document, 31, 1, 31, 3);

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aWireThatRunsThroughALampAndEndsOnItsPinIsNotAnOverlap() {
        CircuitDocument document = new CircuitDocument();
        place(registry, document, StandardComponentTypes.SWITCH, 0, 0, "");
        place(registry, document, StandardComponentTypes.LED, 30, 0, "");
        wire(document, 4, 1, 20, 1);
        wire(document, 20, 1, 20, -5);
        wire(document, 20, -5, 31, -5);
        wire(document, 31, -5, 31, 3);

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    /** Component labels 6 x 2 right of the body, free texts 6 x 2 from their position (a line is 2 units high). */
    private static final TextBoxes WIDE_TEXTS = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            if (component.label().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Bounds(component.position().x() + 5, component.position().y() + 1, 6, 2));
        }

        @Override
        public Bounds freeText(TextLabel label) {
            return new Bounds(label.position().x(), label.position().y(), 6, 2);
        }
    };

    @Test
    void labelsAndNotesCountWithTheTextBoxesGiven() {
        CircuitDocument document = new CircuitDocument();
        Component labelled = place(registry, document, StandardComponentTypes.BUFFER, 0, 0, "Habilitar");
        Component neighbour = gate(registry, document, 8, 0);

        CircuitTidyPlan withoutTexts = tidy(document, everything(document), CircuitTidyOptions.DEFAULTS,
                TextBoxes.NONE, new GreedySeparator());
        CircuitTidyPlan withTexts = tidy(document, everything(document), CircuitTidyOptions.DEFAULTS, WIDE_TEXTS,
                new GreedySeparator());

        assertThat(withoutTexts.partsMoved()).isZero();
        assertThat(withTexts.partsMoved()).isEqualTo(1);
        Bounds label = WIDE_TEXTS.componentLabel(withTexts.after().component(labelled.id()).orElseThrow())
                .orElseThrow();
        assertThat(GreedySeparator.tooClose(label, body(withTexts.after(), neighbour.id()), LayoutItems.BODY_GAP))
                .isFalse();
    }

    /**
     * A gate in a corridor of foreign wires, one every two units: wherever it goes, a wire runs through its body or
     * lands on a pin, so it can never move.
     */
    private Component stuckGate(CircuitDocument document) {
        Component stuck = gate(registry, document, 0, 0);
        for (int y = -99; y <= 99; y += 2) {
            wire(document, -100, y, 100, y);
        }
        return stuck;
    }

    @Test
    void nothingMovesOntoANeighbourThatCouldNotLeave() {
        CircuitDocument document = new CircuitDocument();
        Component stuck = stuckGate(document);
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(30, 30), "hola");
        document.add(note);

        CircuitTidyPlan plan = tidy(document, everything(document), CircuitTidyOptions.DEFAULTS, WIDE_TEXTS,
                fixed(Map.of(stuck.id(), new GridPoint(0, 20), note.id(), new GridPoint(-30, -29))));

        assertThat(plan.after().component(stuck.id()).orElseThrow()).isEqualTo(stuck);
        assertThat(LayoutItems.overlaps(WIDE_TEXTS.freeText(plan.after().label(note.id()).orElseThrow()),
                body(plan.after(), stuck.id()), 0)).isFalse();
    }

    @Test
    void aFreeTextOverABodyMovesAway() {
        CircuitDocument document = new CircuitDocument();
        Component covered = gate(registry, document, 0, 0);
        TextLabel note = new TextLabel(document.newLabelId(), new GridPoint(1, 1), "hola");
        document.add(note);

        CircuitTidyPlan plan = tidy(document, Set.of(note.id()), CircuitTidyOptions.DEFAULTS, WIDE_TEXTS,
                new GreedySeparator());

        TextLabel moved = plan.after().label(note.id()).orElseThrow();
        assertThat(plan.textsMoved()).isEqualTo(1);
        assertThat(plan.partsMoved()).isZero();
        assertThat(GreedySeparator.tooClose(WIDE_TEXTS.freeText(moved), body(plan.after(), covered.id()),
                LayoutItems.BODY_GAP)).isFalse();
    }

    private TextLabel text(CircuitDocument document, int x, int y, String words) {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), words);
        document.add(label);
        return label;
    }

    private CircuitTidyPlan tidyWithWideTexts(CircuitDocument document) {
        return CircuitTidy.plan(document, registry, everything(document), CircuitTidyOptions.DEFAULTS, WIDE_TEXTS,
                NEVER, round -> {
                }).orElseThrow();
    }

    @Test
    void textsOverTextsAreLeftAsTheyAre() {
        CircuitDocument document = new CircuitDocument();
        text(document, 0, 0, "desde el bus");
        text(document, 3, 0, "de datos");

        CircuitTidyPlan plan = tidyWithWideTexts(document);

        assertThat(plan.textsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    /** The text stays; the part moves its label off it (spec §6.2, {@link LabelsApartTest}). */
    @Test
    void aTextOverAPartsLabelStaysAndThePartMovesOffIt() {
        CircuitDocument document = new CircuitDocument();
        place(registry, document, StandardComponentTypes.BUFFER, 0, 0, "Habilitar");
        text(document, 7, 1, "nota");

        CircuitTidyPlan plan = tidyWithWideTexts(document);

        assertThat(plan.textsMoved()).isZero();
        assertThat(plan.partsMoved()).isEqualTo(1);
        assertThat(plan.notSeparated()).isEmpty();
    }

    /** Free texts one unit per character wide and two high; component labels 6 x 2 right of the body. */
    private static final TextBoxes LETTER_TEXTS = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            return WIDE_TEXTS.componentLabel(component);
        }

        @Override
        public Bounds freeText(TextLabel label) {
            return new Bounds(label.position().x(), label.position().y(), label.text().length(), 2);
        }
    };

    private static boolean shareAnArea(Bounds first, Bounds second) {
        return LayoutItems.overlaps(first, second, 0);
    }

    private CircuitTidyPlan tidyWithLetterTexts(CircuitDocument document, Set<ElementId> scope) {
        return CircuitTidy.plan(document, registry, scope, CircuitTidyOptions.DEFAULTS, LETTER_TEXTS, NEVER,
                round -> {
                }).orElseThrow();
    }

    /** The final review's probe: the text moved off the body landed on the text one unit right of it. */
    @Test
    void aTextMovedOffABodyNeverLandsOnAnotherText() {
        CircuitDocument document = new CircuitDocument();
        Component covered = gate(registry, document, 0, 0);
        Bounds body = body(document, covered.id());
        TextLabel over = text(document, body.x() + 1, body.y() + 1, "abcd");
        TextLabel neighbour = text(document, body.maximumX() + 2, body.y() + 1, "WXYZWXYZ");

        CircuitTidyPlan plan = tidyWithLetterTexts(document, everything(document));

        Bounds moved = LETTER_TEXTS.freeText(plan.after().label(over.id()).orElseThrow());
        assertThat(plan.after().label(neighbour.id()).orElseThrow()).isEqualTo(neighbour);
        assertThat(shareAnArea(moved, LETTER_TEXTS.freeText(neighbour))).isFalse();
        assertThat(shareAnArea(moved, body(plan.after(), covered.id()))).isFalse();
        assertThat(plan.textsMoved()).isEqualTo(1);
    }

    @Test
    void aTextMovedOffABodyNeverLandsOnAPartsLabel() {
        CircuitDocument document = new CircuitDocument();
        Component labelled = place(registry, document, StandardComponentTypes.BUFFER, 0, 0, "Habilitar");
        Bounds body = body(document, labelled.id());
        TextLabel over = text(document, body.x() + 1, body.y() + 1, "abcd");

        CircuitTidyPlan plan = tidyWithLetterTexts(document, Set.of(over.id()));

        Bounds moved = LETTER_TEXTS.freeText(plan.after().label(over.id()).orElseThrow());
        assertThat(shareAnArea(moved, LETTER_TEXTS.componentLabel(labelled).orElseThrow())).isFalse();
        assertThat(shareAnArea(moved, body)).isFalse();
    }

    /** With no room clear of bodies and of other writing, the text stays and is counted, never hidden. */
    @Test
    void aTextWithNoPlaceOffOtherWritingStaysAndIsCounted() {
        CircuitDocument document = new CircuitDocument();
        Component covered = gate(registry, document, 0, 0);
        Bounds body = body(document, covered.id());
        TextLabel over = text(document, body.x() + 1, body.y() + 1, "abcd");
        Bounds overBox = LETTER_TEXTS.freeText(over);
        // fixed three-letter texts everywhere around, none touching the text (or it would join their block)
        for (int x = body.x() - 20; x <= body.maximumX() + 20; x++) {
            for (int y = body.y() - 20; y <= body.maximumY() + 20; y++) {
                if (!LayoutItems.overlaps(new Bounds(x, y, 3, 2), overBox, 1)) {
                    text(document, x, y, "XXX");
                }
            }
        }

        CircuitTidyPlan plan = tidyWithLetterTexts(document, Set.of(over.id()));

        assertThat(plan.after().label(over.id()).orElseThrow()).isEqualTo(over);
        assertThat(plan.notSeparated()).containsExactly(over.id());
    }

    /**
     * The hand test on lrv32im-importado: a column of notes half a row off a column of tunnels (each line 2 units high,
     * sharing one row with a tunnel) stayed on top of them although the right side was empty.
     */
    @Test
    void aColumnOfTextsHalfOverAColumnOfTunnelsMovesOffThemKeepingItsOrder() {
        CircuitDocument document = new CircuitDocument();
        List<Component> tunnels = List.of(place(registry, document, StandardComponentTypes.TUNNEL_EXIT, 20, 0, ""),
                place(registry, document, StandardComponentTypes.TUNNEL_EXIT, 20, 3, ""),
                place(registry, document, StandardComponentTypes.TUNNEL_EXIT, 20, 6, ""));
        List<TextLabel> column = List.of(text(document, 21, 1, "Simple microprogrammed"),
                text(document, 21, 3, "physical architecture"), text(document, 21, 5, "64 MB RAM"),
                text(document, 21, 7, "GPL Licence"));

        CircuitTidyPlan plan = tidyWithWideTexts(document);

        for (Component tunnel : tunnels) {
            assertThat(plan.after().component(tunnel.id()).orElseThrow()).isEqualTo(tunnel);
            for (TextLabel label : column) {
                Bounds box = WIDE_TEXTS.freeText(plan.after().label(label.id()).orElseThrow());
                assertThat(LayoutItems.overlaps(box, body(plan.after(), tunnel.id()), 0))
                        .as("%s over %s", label.text(), tunnel.id()).isFalse();
            }
        }
        GridPoint first = plan.after().label(column.get(0).id()).orElseThrow().position();
        GridPoint shift = new GridPoint(first.x() - 21, first.y() - 1);
        for (int index = 0; index < column.size(); index++) {
            assertThat(plan.after().label(column.get(index).id()).orElseThrow().position())
                    .isEqualTo(column.get(index).position().plus(shift));
        }
        assertThat(shift.y()).as("it moves along its row").isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aColumnOfTextsMovesTogetherAndKeepsItsOrder() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        List<TextLabel> column = List.of(text(document, 2, 1, "000 itype"), text(document, 2, 2, "001 stype"),
                text(document, 2, 3, "010 utype"));

        CircuitTidyPlan plan = tidyWithWideTexts(document);

        List<GridPoint> after = column.stream()
                .map(label -> plan.after().label(label.id()).orElseThrow().position()).toList();
        GridPoint shift = new GridPoint(after.get(0).x() - 2, after.get(0).y() - 1);
        assertThat(shift).isNotEqualTo(GridPoint.ORIGIN);
        assertThat(after).containsExactly(new GridPoint(2, 1).plus(shift), new GridPoint(2, 2).plus(shift),
                new GridPoint(2, 3).plus(shift));
        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    @Test
    void aPartNeverJumpsFarToFindRoom() {
        CircuitDocument document = new CircuitDocument();
        Component stuck = gate(registry, document, 0, 0);
        for (int y = -9; y <= 13; y += 2) {
            wire(document, -100, y, 100, y);
        }

        CircuitTidyPlan plan = tidyForReal(document);

        assertThat(plan.after().component(stuck.id()).orElseThrow()).isEqualTo(stuck);
        assertThat(plan.notSeparated()).containsExactly(stuck.id());
    }

    @Test
    void everyStageIsReportedInOrder() {
        CircuitDocument document = stackedAndGates();
        List<CircuitTidyProgress.Stage> stages = new ArrayList<>();

        CircuitTidy.plan(document, registry, everything(document), CircuitTidyOptions.DEFAULTS, TextBoxes.NONE, NEVER,
                (stage, done, total) -> {
                    if (stages.isEmpty() || stages.getLast() != stage) {
                        stages.add(stage);
                    }
                    assertThat(done).isPositive();
                    if (stage != CircuitTidyProgress.Stage.WIRES) {
                        assertThat(done).isLessThanOrEqualTo(total);
                    }
                }).orElseThrow();

        assertThat(stages).startsWith(CircuitTidyProgress.Stage.SEPARATING, CircuitTidyProgress.Stage.MOVING)
                .endsWith(CircuitTidyProgress.Stage.WIRES);
    }

    @Test
    void textsReportTheirOwnStageAndSeparatingCountsThePartRounds() {
        CircuitDocument document = stackedAndGates();
        text(document, 11, 11, "nota");
        List<String> reports = new ArrayList<>();

        CircuitTidy.plan(document, registry, everything(document), CircuitTidyOptions.DEFAULTS, WIDE_TEXTS, NEVER,
                (stage, done, total) -> reports.add(stage + " " + done + "/" + total)).orElseThrow();

        assertThat(reports).first().isEqualTo("SEPARATING 1/3");
        assertThat(reports).filteredOn(report -> report.startsWith("SEPARATING"))
                .allMatch(report -> report.endsWith("/3")).doesNotHaveDuplicates();
        assertThat(reports).anyMatch(report -> report.startsWith("MOVING_TEXTS 1/"));
    }

    @Test
    void lockedFrameContentsStayInside() {
        CircuitDocument document = new CircuitDocument();
        Frame frame = new Frame(document.newFrameId(), new GridPoint(0, 0), 12, 8, "Bloque", FrameTint.NONE, true);
        document.add(frame);
        Component first = gate(registry, document, 1, 2);
        Component second = gate(registry, document, 2, 2);

        CircuitTidyPlan plan = tidy(document, new GreedySeparator());

        for (ElementId id : List.of(first.id(), second.id())) {
            Bounds bounds = body(plan.after(), id);
            assertThat(frame.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY())).isTrue();
        }
        assertThat(anyBodiesTooClose(plan.after())).isFalse();
        assertThat(plan.partsMoved()).isEqualTo(1);
    }

    @Test
    void nothingCrossesAFrameBorder() {
        CircuitDocument document = new CircuitDocument();
        Frame frame = new Frame(document.newFrameId(), new GridPoint(0, 0), 12, 8, "Bloque", FrameTint.NONE, false);
        document.add(frame);
        Component inside = gate(registry, document, 7, 2);
        Component insideToo = gate(registry, document, 8, 3);
        Component outside = gate(registry, document, 14, 2);
        Component outsideToo = gate(registry, document, 15, 3);

        CircuitTidyPlan plan = tidy(document, new GreedySeparator());

        for (Component component : List.of(inside, insideToo)) {
            Bounds bounds = body(plan.after(), component.id());
            assertThat(frame.encloses(bounds.x(), bounds.y(), bounds.maximumX(), bounds.maximumY())).isTrue();
        }
        Bounds frameBox = new Bounds(0, 0, 12, 8);
        for (Component component : List.of(outside, outsideToo)) {
            assertThat(GreedySeparator.tooClose(body(plan.after(), component.id()), frameBox, 0)).isFalse();
        }
        assertThat(anyBodiesTooClose(plan.after())).isFalse();
    }

    @Test
    void withAScopeOnlyScopedItemsMove() {
        CircuitDocument document = new CircuitDocument();
        Component fixedGate = gate(registry, document, 0, 0);
        Component selected = gate(registry, document, 1, 1);

        CircuitTidyPlan plan = tidy(document, Set.of(selected.id()), CircuitTidyOptions.DEFAULTS, TextBoxes.NONE,
                new GreedySeparator());

        assertThat(plan.after().component(fixedGate.id()).orElseThrow()).isEqualTo(fixedGate);
        assertThat(plan.after().component(selected.id()).orElseThrow().position())
                .isNotEqualTo(selected.position());
        assertThat(anyBodiesTooClose(plan.after())).isFalse();
    }

    @Test
    void aClusterWithAPartOutOfScopeStaysAndIsCounted() {
        CircuitDocument document = new CircuitDocument();
        Component selected = gate(registry, document, 0, 0);
        gate(registry, document, 4, 0);
        gate(registry, document, 1, 1);

        CircuitTidyPlan plan = tidy(document, Set.of(selected.id()), CircuitTidyOptions.DEFAULTS, TextBoxes.NONE,
                new GreedySeparator());

        assertThat(plan.after().component(selected.id()).orElseThrow()).isEqualTo(selected);
        assertThat(plan.notSeparated()).containsExactly(selected.id());
    }

    @Test
    void separateOverlapsOffRunsOnlyTheWireRules() {
        CircuitDocument document = stackedAndGates();
        wire(document, 20, 12, 24, 12);
        wire(document, 22, 14, 22, 18);
        Set<ElementId> scope = everything(document);
        CircuitTidyOptions wiresOnly = new CircuitTidyOptions(false, TidyOptions.DEFAULTS);

        CircuitTidyPlan plan = tidy(document, scope, wiresOnly, TextBoxes.NONE, new GreedySeparator());

        TidyPlan expected = WireTidy.plan(document, registry, scope, TidyOptions.DEFAULTS);
        CircuitDocument expectedAfter = document.copy();
        new TidyWiresCommand(expected).apply(expectedAfter);
        assertThat(plan.wires()).isEqualTo(expected);
        assertThat(expected.isEmpty()).isFalse();
        assertThat(Set.copyOf(plan.after().elements())).isEqualTo(Set.copyOf(expectedAfter.elements()));
        assertThat(plan.partsMoved()).isZero();
    }

    @Test
    void theOriginalDocumentIsUntouched() {
        CircuitDocument document = stackedAndGates();
        Map<DocumentElement, Long> before = snapshot(document);
        long revision = document.revision();

        tidy(document, new GreedySeparator());

        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
        assertThat(document.revision()).isEqualTo(revision);
    }

    @Test
    void sameInputSamePlan() {
        CircuitTidyPlan first = tidy(stackedAndGates(), new GreedySeparator());
        CircuitTidyPlan second = tidy(stackedAndGates(), new GreedySeparator());

        assertThat(snapshot(second.after())).containsExactlyEntriesOf(snapshot(first.after()));
        assertThat(second.partsMoved()).isEqualTo(first.partsMoved());
    }

    @Test
    void cancelledReturnsEmpty() {
        CircuitDocument document = stackedAndGates();

        assertThat(CircuitTidy.plan(document, registry, everything(document), CircuitTidyOptions.DEFAULTS,
                TextBoxes.NONE, () -> true, round -> {
                }, new GreedySeparator())).isEmpty();
    }

    @Test
    void cancelledWhileSeparatingReturnsEmpty() {
        CircuitDocument document = stackedAndGates();
        CircuitTidy.Separator cancelling = (boxes, cancelled) -> {
            throw new CancellationException();
        };

        assertThat(CircuitTidy.plan(document, registry, everything(document), CircuitTidyOptions.DEFAULTS,
                TextBoxes.NONE, NEVER, round -> {
                }, cancelling)).isEmpty();
    }

    @Test
    void cancelledBetweenMovesReturnsEmpty() {
        CircuitDocument document = stackedAndGates();
        AtomicInteger polls = new AtomicInteger();

        Optional<CircuitTidyPlan> plan = CircuitTidy.plan(document, registry, everything(document),
                CircuitTidyOptions.DEFAULTS, TextBoxes.NONE, () -> polls.incrementAndGet() > 2, round -> {
                }, fixed(Map.of(document.components().get(1).id(), new GridPoint(6, 0))));

        assertThat(plan).isEmpty();
    }

    @Test
    void oneUndoStep() {
        CircuitDocument document = stackedAndGates();
        ElementId probed = document.wires().get(3).id();
        document.setProbes(List.of(new Probe(document.newProbeId(), new ProbeTarget.WireTarget(probed), "y")));
        Map<DocumentElement, Long> before = snapshot(document);
        List<Probe> probesBefore = document.probes();
        CircuitTidyPlan plan = tidy(document, new GreedySeparator());

        ReplaceContentCommand command = ReplaceContentCommand.between(document, plan.after(), "command.tidyCircuit");
        command.apply(document);
        assertThat(Set.copyOf(document.elements())).isEqualTo(Set.copyOf(plan.after().elements()));
        command.revert(document);

        assertThat(snapshot(document)).containsExactlyEntriesOf(before);
        assertThat(document.probes()).isEqualTo(probesBefore);
        assertThat(command.description()).isEqualTo("command.tidyCircuit");
    }

    @Test
    void componentDefinitionsAreNeverTouched() {
        CircuitDocument document = new CircuitDocument();
        document.putDefinition(ExampleDefinitions.inverter());
        ComponentTypeRegistry withDefinitions = registry.extendedWith(SubcircuitTypes.resolverFor(document));
        Component instance = new Component(document.newComponentId(),
                SubcircuitTypes.typeIdOf(ExampleDefinitions.INVERTER), new GridPoint(0, 0), Rotation.R0, Map.of(),
                "");
        document.add(instance);
        gate(registry, document, 1, 1);

        CircuitTidyPlan plan = CircuitTidy.plan(document, withDefinitions, everything(document),
                CircuitTidyOptions.DEFAULTS, TextBoxes.NONE, NEVER, round -> {
                }, new GreedySeparator()).orElseThrow();

        assertThat(plan.after().definitions()).isEqualTo(document.definitions());
        assertThat(plan.partsMoved()).isEqualTo(1);
        assertThat(plan.notSeparated()).isEmpty();
    }

    /**
     * Through the public entry point, with the real {@link OverlapSeparation}. Skipped until it exists (it is written
     * in parallel with this class); it runs as soon as {@code separate} stops throwing.
     */
    @Test
    void thePublicPlanSeparatesStackedGatesWithTheRealGeometry() {
        Assumptions.assumeTrue(realSeparationExists(), "OverlapSeparation.separate is not written yet");
        CircuitDocument document = stackedAndGates();

        CircuitTidyPlan plan = CircuitTidy.plan(document, registry, everything(document),
                CircuitTidyOptions.DEFAULTS, TextBoxes.NONE, NEVER, round -> {
                }).orElseThrow();

        assertThat(anyBodiesTooClose(plan.after())).isFalse();
        assertThat(nets(plan.after())).isEqualTo(nets(document));
        assertThat(plan.notSeparated()).isEmpty();
    }

    private static boolean realSeparationExists() {
        try {
            OverlapSeparation.separate(List.of(), NEVER);
            return true;
        } catch (UnsupportedOperationException notWritten) {
            return false;
        }
    }
}
