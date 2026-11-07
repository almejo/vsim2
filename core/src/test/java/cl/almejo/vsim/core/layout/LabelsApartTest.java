package cl.almejo.vsim.core.layout;

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
import cl.almejo.vsim.core.wiring.TidyOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static cl.almejo.vsim.core.DocumentFixtures.frame;
import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * «Separar lo que se tapa» also moves a part whose label covers other writing (spec 2026-11-06 §6.2): the user's LEDs
 * «Salta» and «Rs1 = Rs2» side by side on riscv-maniqui, their labels read «SaltaRs1 = Rs2». The part moves the least
 * it can, along its row first and at most {@value LabelsApart#MAXIMUM_SHIFT} units, with every rule of the tidying
 * stages (no more wire corners); a free text never moves for it, a row or an aligned column is never broken, and when
 * there is no place it stays uncounted.
 */
class LabelsApartTest {

    private static final CircuitTidyOptions EVERY_STAGE = new CircuitTidyOptions(true, true, true,
            TidyOptions.DEFAULTS);
    private static final CircuitTidyOptions SEPARATE_ONLY = new CircuitTidyOptions(true, false, false,
            TidyOptions.DEFAULTS);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    /** Each labelled component's label box, relative to its position. */
    private final Map<ElementId, Bounds> labelOffsets = new HashMap<>();

    /** Free texts one unit per letter of their longest line, two units per line. */
    private final TextBoxes texts = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            return Optional.ofNullable(labelOffsets.get(component.id()))
                    .map(offset -> offset.translate(component.position().x(), component.position().y()));
        }

        @Override
        public Bounds freeText(TextLabel label) {
            String[] lines = label.text().split("\n");
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, line.length());
            }
            return new Bounds(label.position().x(), label.position().y(), width, 2 * lines.length);
        }
    };

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private CircuitTidyPlan tidy(CircuitDocument document, CircuitTidyOptions options) {
        return tidy(document, everything(document), options);
    }

    private CircuitTidyPlan tidy(CircuitDocument document, Set<ElementId> scope, CircuitTidyOptions options) {
        return CircuitTidy.plan(document, registry, scope, options, texts, () -> false,
                CircuitTidyProgress.NONE).orElseThrow();
    }

    private static GridPoint positionOf(CircuitTidyPlan plan, Component component) {
        return plan.after().component(component.id()).orElseThrow().position();
    }

    private Bounds labelIn(CircuitTidyPlan plan, Component component) {
        return texts.componentLabel(plan.after().component(component.id()).orElseThrow()).orElseThrow();
    }

    private TextLabel text(CircuitDocument document, int x, int y, String words) {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), words);
        document.add(label);
        return label;
    }

    /** An LED at (x, y) with its label above it, {@code labelOffset} wide units from its left edge. */
    private Component led(CircuitDocument document, int x, int y, String label, Bounds labelOffset) {
        Component led = place(registry, document, StandardComponentTypes.LED, x, y, label);
        labelOffsets.put(led.id(), labelOffset);
        return led;
    }

    /**
     * True when the two boxes share a column and a row: the first shared column, which
     * {@link LayoutItems#writingOverlap(List, List)} takes for rounding, counts here, since a label moved apart should
     * end clear of the other writing, not still touching it (with the user's font, «Salta» and «Rs1 = Rs2» sharing one
     * column still read as one word).
     */
    private static boolean sharesWriting(Bounds first, Bounds second) {
        return LayoutItems.writingOverlap(List.of(first), List.of(second), 0) > 0;
    }

    /**
     * The user's case as the probe measures it with the user's font: «Salta» (x 12 to 19) and «Rs1 = Rs2» (x 17 to
     * 26) above two LEDs four units apart, a row, the labels sharing two columns.
     */
    private record SideBySide(CircuitDocument document, Component salta, Component equal) {
    }

    private SideBySide saltaAndEqual() {
        CircuitDocument document = new CircuitDocument();
        Component salta = led(document, 12, 8, "Salta", new Bounds(0, -2, 7, 2));
        Component equal = led(document, 18, 8, "Rs1 = Rs2", new Bounds(-1, -2, 9, 2));
        return new SideBySide(document, salta, equal);
    }

    @Test
    void theSaltaAndRsLabelsEndApart() {
        SideBySide fixture = saltaAndEqual();

        CircuitTidyPlan plan = tidy(fixture.document(), SEPARATE_ONLY);

        GridPoint saltaShift = shift(fixture.salta(), plan);
        GridPoint equalShift = shift(fixture.equal(), plan);
        assertThat(sharesWriting(labelIn(plan, fixture.salta()), labelIn(plan, fixture.equal()))).isFalse();
        assertThat(saltaShift.equals(GridPoint.ORIGIN) || equalShift.equals(GridPoint.ORIGIN)).isTrue();
        GridPoint moved = saltaShift.equals(GridPoint.ORIGIN) ? equalShift : saltaShift;
        assertThat(moved.y()).isZero();
        assertThat(Math.abs(moved.x())).isBetween(1, LabelsApart.MAXIMUM_SHIFT);
        assertThat(plan.partsMoved()).isEqualTo(1);
        assertThat(plan.notSeparated()).isEmpty();
    }

    private static GridPoint shift(Component component, CircuitTidyPlan plan) {
        GridPoint after = positionOf(plan, component);
        return new GridPoint(after.x() - component.position().x(), after.y() - component.position().y());
    }

    @Test
    void aLabelOverAFreeTextMovesThePartNotTheText() {
        CircuitDocument document = new CircuitDocument();
        Component led = led(document, 8, 8, "Habilitar", new Bounds(0, -2, 6, 2));
        TextLabel note = text(document, 10, 5, "abcdef");

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(plan.after().label(note.id()).orElseThrow()).isEqualTo(note);
        assertThat(positionOf(plan, led)).isNotEqualTo(led.position());
        assertThat(sharesWriting(labelIn(plan, led), texts.freeText(note))).isFalse();
        assertThat(plan.textsMoved()).isZero();
    }

    /**
     * The LED's label is under a block of five 50-letter lines above it, and a gate one unit below it: no shift of at
     * most four units frees the label, so the LED stays, and it is not counted.
     */
    @Test
    void whenNoPlaceItStaysAndIsNotCounted() {
        CircuitDocument document = new CircuitDocument();
        Component led = led(document, 0, 8, "Habilitar", new Bounds(0, -2, 6, 2));
        gate(registry, document, 0, 12);
        String line = "x".repeat(50);
        text(document, -25, -2, String.join("\n", line, line, line, line, line));

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(positionOf(plan, led)).isEqualTo(led.position());
        assertThat(plan.partsMoved()).isZero();
        assertThat(plan.textsMoved()).isZero();
        assertThat(plan.notSeparated()).isEmpty();
    }

    /**
     * Three LEDs in a row six units apart (the widest gap that is still a row); the middle one's label is under a
     * free text. Moving it along the row takes it more than six units from a neighbour, moving it down takes it off
     * the row's top edge: either breaks the row, so it stays.
     */
    @Test
    void aRowIsNeverBroken() {
        CircuitDocument document = new CircuitDocument();
        Component left = place(registry, document, StandardComponentTypes.LED, 0, 8, "");
        Component middle = led(document, 8, 8, "Habilitar", new Bounds(0, -2, 6, 2));
        Component right = place(registry, document, StandardComponentTypes.LED, 16, 8, "");
        text(document, 10, 5, "abcdef");

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(positionOf(plan, left)).isEqualTo(left.position());
        assertThat(positionOf(plan, middle)).isEqualTo(middle.position());
        assertThat(positionOf(plan, right)).isEqualTo(right.position());
        assertThat(plan.notSeparated()).isEmpty();
    }

    /**
     * The middle LED of {@link #aRowIsNeverBroken} alone: one unit down would already free its label, but along x
     * comes first, so it moves four units left, where its label ends clear of the text.
     */
    @Test
    void withoutTheRowTheSameLedMovesAlongXFirst() {
        CircuitDocument document = new CircuitDocument();
        Component middle = led(document, 8, 8, "Habilitar", new Bounds(0, -2, 6, 2));
        text(document, 10, 5, "abcdef");

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(positionOf(plan, middle)).isEqualTo(new GridPoint(4, 8));
    }

    /**
     * The same LED with another LED in its row at x = 2: four units left its body would touch it, and up or down
     * leaves the row, so no place ends clear; three units left, where the label shares only the column taken for
     * rounding with the text, will do.
     */
    @Test
    void withNoClearPlaceOneThatOnlyTouchesWillDo() {
        CircuitDocument document = new CircuitDocument();
        place(registry, document, StandardComponentTypes.LED, 2, 8, "");
        Component middle = led(document, 8, 8, "Habilitar", new Bounds(0, -2, 6, 2));
        text(document, 10, 5, "abcdef");

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(positionOf(plan, middle)).isEqualTo(new GridPoint(5, 8));
    }

    /**
     * An LED at (20, 10) whose label, three units right of it, covers the free text «xyz»; a wire runs straight up
     * from its pin in two pieces and then left to a switch (one corner). A move along x drags only the first piece,
     * so it bends the wire.
     */
    private Component ledWiredFromAbove(CircuitDocument document) {
        Component led = led(document, 20, 10, "abc", new Bounds(3, 0, 3, 2));
        GridPoint pin = registry.typeOf(led).pinLocation(led, 0);
        Component source = place(registry, document, StandardComponentTypes.SWITCH, pin.x() - 20, pin.y() - 32, "");
        GridPoint sourcePin = registry.typeOf(source).pinLocation(source, 0);
        wire(document, pin.x(), pin.y(), pin.x(), pin.y() - 30);
        wire(document, pin.x(), pin.y() - 30, pin.x(), sourcePin.y());
        wire(document, sourcePin.x(), sourcePin.y(), pin.x(), sourcePin.y());
        text(document, 23, 10, "xyz");
        return led;
    }

    /**
     * Freeing a label is a tidying move: it never leaves the wires with more corners than before (the final
     * review's probe: 1 corner became 2).
     */
    @Test
    void freeingALabelNeverAddsWireCorners() {
        CircuitDocument document = new CircuitDocument();
        ledWiredFromAbove(document);
        int cornersBefore = WireCorners.count(document);

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(WireCorners.count(plan.after())).isLessThanOrEqualTo(cornersBefore);
    }

    /**
     * The LED of {@link #ledWiredFromAbove} in the middle of a column of three LEDs at x = 20, every stage on: freeing
     * its label never takes it out of the column that aligning leaves (the final review's probe: 20 became 17).
     */
    @Test
    void freeingALabelKeepsAnAlignedColumn() {
        CircuitDocument document = new CircuitDocument();
        Component top = led(document, 20, 0, "", null);
        Component middle = ledWiredFromAbove(document);
        Component bottom = led(document, 20, 20, "", null);
        int cornersBefore = WireCorners.count(document);

        CircuitTidyPlan plan = tidy(document, EVERY_STAGE);

        assertThat(positionOf(plan, top).x()).isEqualTo(20);
        assertThat(positionOf(plan, middle).x()).isEqualTo(20);
        assertThat(positionOf(plan, bottom).x()).isEqualTo(20);
        assertThat(WireCorners.count(plan.after())).isLessThanOrEqualTo(cornersBefore);
    }

    /**
     * A column of three LEDs at x = 20 with no wire, so no move bends one: the middle LED, whose label covers a free
     * text, still never leaves the column; it moves up or down instead, where its label ends clear of the text.
     */
    @Test
    void anAlignedColumnIsNeverBrokenEvenWithoutWires() {
        CircuitDocument document = new CircuitDocument();
        Component top = led(document, 20, 0, "", null);
        Component middle = led(document, 20, 10, "abc", new Bounds(3, 0, 3, 2));
        Component bottom = led(document, 20, 20, "", null);
        TextLabel words = text(document, 23, 10, "xyz");

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(positionOf(plan, top)).isEqualTo(top.position());
        assertThat(positionOf(plan, bottom)).isEqualTo(bottom.position());
        assertThat(positionOf(plan, middle).x()).isEqualTo(20);
        assertThat(positionOf(plan, middle)).isNotEqualTo(middle.position());
        assertThat(sharesWriting(labelIn(plan, middle), texts.freeText(words))).isFalse();
    }

    /**
     * A sticky note is painted on top of everything, so a label under its paper is hidden: the LED «reloj» whose label
     * crosses the note's bottom edge moves clear of the whole paper. The note is 20 wide, so no shift along x frees
     * the label; the LED moves down, and the note stays where it is.
     */
    @Test
    void aLabelUnderAStickyNoteMovesThePartClearOfThePaper() {
        CircuitDocument document = new CircuitDocument();
        Component led = led(document, 20, 10, "reloj", new Bounds(-1, -2, 6, 2));
        StickyNote note = new StickyNote(document.newStickyNoteId(), new GridPoint(10, 4), 20, 6, StickyColor.YELLOW,
                new StickyText("una nota"), false);
        document.add(note);
        Bounds paper = new Bounds(10, 4, note.visibleWidth(), note.visibleHeight());

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        assertThat(plan.after().stickyNote(note.id()).orElseThrow()).isEqualTo(note);
        assertThat(positionOf(plan, led).x()).isEqualTo(led.position().x());
        assertThat(positionOf(plan, led).y()).isGreaterThan(led.position().y());
        assertThat(sharesWriting(labelIn(plan, led), paper)).isFalse();
        assertThat(plan.partsMoved()).isEqualTo(1);
        assertThat(plan.textsMoved()).isZero();
    }

    /**
     * With a selection only the selected part may move: «Salta» and «Rs1 = Rs2» with one of them selected, the other
     * stays where it is and the selected one moves until the labels end apart, whichever of the two it is.
     */
    @Test
    void onlyTheSelectedPartMovesToFreeItsLabel() {
        for (boolean saltaSelected : new boolean[] {true, false}) {
            SideBySide fixture = saltaAndEqual();
            Component selected = saltaSelected ? fixture.salta() : fixture.equal();
            Component left = saltaSelected ? fixture.equal() : fixture.salta();

            CircuitTidyPlan plan = tidy(fixture.document(), Set.of(selected.id()), SEPARATE_ONLY);

            assertThat(positionOf(plan, left)).isEqualTo(left.position());
            assertThat(positionOf(plan, selected)).isNotEqualTo(selected.position());
            assertThat(sharesWriting(labelIn(plan, fixture.salta()), labelIn(plan, fixture.equal()))).isFalse();
        }
    }

    /**
     * The LED of {@link #withoutTheRowTheSameLedMovesAlongXFirst} inside a frame whose left border is two units from
     * it: four units left would take it out of the frame, so it moves down instead, staying inside.
     */
    @Test
    void aPartFreesItsLabelWithoutLeavingItsFrame() {
        CircuitDocument document = new CircuitDocument();
        Frame border = frame(document, 6, 0, 30, 20);
        Component middle = led(document, 8, 8, "Habilitar", new Bounds(0, -2, 6, 2));
        TextLabel words = text(document, 10, 5, "abcdef");

        CircuitTidyPlan plan = tidy(document, SEPARATE_ONLY);

        GridPoint after = positionOf(plan, middle);
        assertThat(after).isNotEqualTo(middle.position());
        assertThat(after.x()).isGreaterThanOrEqualTo(border.topLeft().x());
        assertThat(sharesWriting(labelIn(plan, middle), texts.freeText(words))).isFalse();
        assertThat(plan.after().frame(border.id()).orElseThrow()).isEqualTo(border);
    }

    /** Cancelling stops before the first part is looked at: an exception, and the copy keeps every part in place. */
    @Test
    void cancellingStopsBeforeAnyPartMoves() {
        SideBySide fixture = saltaAndEqual();
        CircuitDocument copy = fixture.document().copy();
        Placement placement = new Placement(copy, registry, LayoutItems.of(copy, registry, everything(copy), texts),
                Placement.netsOf(copy, registry));

        assertThatThrownBy(() -> LabelsApart.separate(placement, () -> true))
                .isInstanceOf(CancellationException.class);
        assertThat(copy.component(fixture.salta().id()).orElseThrow()).isEqualTo(fixture.salta());
        assertThat(copy.component(fixture.equal().id()).orElseThrow()).isEqualTo(fixture.equal());
    }

    @Test
    void offWhenSeparateIsOff() {
        SideBySide fixture = saltaAndEqual();

        CircuitTidyPlan plan = tidy(fixture.document(), new CircuitTidyOptions(false, true, true,
                TidyOptions.DEFAULTS));

        assertThat(positionOf(plan, fixture.salta())).isEqualTo(fixture.salta().position());
        assertThat(positionOf(plan, fixture.equal())).isEqualTo(fixture.equal().position());
    }
}
