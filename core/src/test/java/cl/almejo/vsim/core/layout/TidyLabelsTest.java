package cl.almejo.vsim.core.layout;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.TidyOptions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static cl.almejo.vsim.core.DocumentFixtures.gate;
import static cl.almejo.vsim.core.DocumentFixtures.place;
import static cl.almejo.vsim.core.DocumentFixtures.wire;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A part that moves in any stage of «Ordenar circuito» never puts its label over a free text or another part's label
 * it was not over before, nor more of one it was already over (the user's hand tests, 2026-11-06: the LED «Salta»,
 * straightened, ended with its label over «Rs1 = Rs2»; then «Rs1 = Rs2», aligned, slid further over «Salta»).
 */
class TidyLabelsTest {

    private static final CircuitTidyOptions SEPARATE_ONLY = new CircuitTidyOptions(true, false, false,
            TidyOptions.DEFAULTS);
    private static final CircuitTidyOptions ALIGN_ONLY = new CircuitTidyOptions(false, true, false,
            TidyOptions.DEFAULTS);
    private static final CircuitTidyOptions STRAIGHTEN_ONLY = new CircuitTidyOptions(false, false, true,
            TidyOptions.DEFAULTS);

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    /** Each labelled component's label box, relative to its position; free texts one unit per letter, two high. */
    private final Map<ElementId, Bounds> labelOffsets = new HashMap<>();

    private final TextBoxes texts = new TextBoxes() {
        @Override
        public Optional<Bounds> componentLabel(Component component) {
            return Optional.ofNullable(labelOffsets.get(component.id()))
                    .map(offset -> offset.translate(component.position().x(), component.position().y()));
        }

        @Override
        public Bounds freeText(TextLabel label) {
            return new Bounds(label.position().x(), label.position().y(), label.text().length(), 2);
        }
    };

    private static Set<ElementId> everything(CircuitDocument document) {
        Set<ElementId> ids = new LinkedHashSet<>();
        document.elements().forEach(element -> ids.add(element.id()));
        return ids;
    }

    private CircuitTidyPlan tidy(CircuitDocument document, Set<ElementId> scope, CircuitTidyOptions options) {
        return CircuitTidy.plan(document, registry, scope, options, texts, () -> false, CircuitTidyProgress.NONE)
                .orElseThrow();
    }

    private static GridPoint positionOf(CircuitTidyPlan plan, Component component) {
        return plan.after().component(component.id()).orElseThrow().position();
    }

    private TextLabel text(CircuitDocument document, int x, int y, String words) {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), words);
        document.add(label);
        return label;
    }

    /** A labelled gate at (x, y) whose label sits right of its body, at its top, {@code width} wide. */
    private Component labelledGate(CircuitDocument document, int x, int y, int width) {
        Component labelled = place(registry, document, StandardComponentTypes.BUFFER, x, y, "Salta");
        labelOffsets.put(labelled.id(), new Bounds(4, 0, width, 2));
        return labelled;
    }

    /**
     * A fixed gate driving a labelled gate one jog lower: straightening would lift the gate two units and its label
     * from (14, 2) to (14, 0).
     */
    private record Jogged(CircuitDocument document, Component driver, Component labelled) {

        Set<ElementId> scopeWithoutTheDriver() {
            Set<ElementId> scope = everything(document);
            scope.remove(driver.id());
            return scope;
        }
    }

    private Jogged jogged() {
        CircuitDocument document = new CircuitDocument();
        Component driver = gate(registry, document, 0, 0);
        Component labelled = labelledGate(document, 10, 2, 6);
        wire(document, 4, 2, 7, 2);
        wire(document, 7, 2, 7, 4);
        wire(document, 7, 4, 10, 4);
        return new Jogged(document, driver, labelled);
    }

    @Test
    void straighteningLiftsTheGateWhenNothingIsWrittenThere() {
        Jogged fixture = jogged();

        CircuitTidyPlan plan = tidy(fixture.document(), fixture.scopeWithoutTheDriver(), STRAIGHTEN_ONLY);

        assertThat(positionOf(plan, fixture.labelled())).isEqualTo(new GridPoint(10, 0));
    }

    @Test
    void straighteningNeverPutsALabelOverAFreeText() {
        Jogged fixture = jogged();
        text(fixture.document(), 15, 0, "Rs1 = Rs2");

        CircuitTidyPlan plan = tidy(fixture.document(), fixture.scopeWithoutTheDriver(), STRAIGHTEN_ONLY);

        assertThat(positionOf(plan, fixture.labelled())).isEqualTo(fixture.labelled().position());
    }

    @Test
    void straighteningNeverPutsALabelOverAnotherPartsLabel() {
        Jogged fixture = jogged();
        Component other = place(registry, fixture.document(), StandardComponentTypes.LED, 24, 0, "Rs1 = Rs2");
        labelOffsets.put(other.id(), new Bounds(-9, 0, 9, 2));

        CircuitTidyPlan plan = tidy(fixture.document(), fixture.scopeWithoutTheDriver(), STRAIGHTEN_ONLY);

        assertThat(positionOf(plan, fixture.labelled())).isEqualTo(fixture.labelled().position());
        assertThat(positionOf(plan, other)).isEqualTo(other.position());
    }

    /**
     * A column with edges 0 and 1: the gate at 1 would move left and its label from x = 5 to x = 4, over two columns
     * of «abcd» (from one).
     */
    @Test
    void aligningNeverPutsALabelOverAFreeText() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component labelled = labelledGate(document, 1, 8, 6);
        text(document, 2, 8, "abcd");

        CircuitTidyPlan plan = tidy(document, everything(document), ALIGN_ONLY);

        assertThat(positionOf(plan, labelled)).isEqualTo(labelled.position());
    }

    /** The same column with nothing written there: the gate aligns. */
    @Test
    void aligningMovesTheGateWhenNothingIsWrittenThere() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 0, 0);
        Component labelled = labelledGate(document, 1, 8, 6);

        CircuitTidyPlan plan = tidy(document, everything(document), ALIGN_ONLY);

        assertThat(positionOf(plan, labelled)).isEqualTo(new GridPoint(0, 8));
    }

    /**
     * A labelled gate over a fixed one: the nearest free place (four units right) would put its label over the text
     * at x = 13, so it is separated somewhere else, its label over no text.
     */
    @Test
    void separatingNeverPutsALabelOverAFreeText() {
        CircuitDocument document = new CircuitDocument();
        Component fixed = gate(registry, document, 0, 0);
        Component labelled = labelledGate(document, 1, 0, 6);
        TextLabel note = text(document, 13, 0, "abcdef");
        Set<ElementId> scope = everything(document);
        scope.remove(fixed.id());

        CircuitTidyPlan plan = tidy(document, scope, SEPARATE_ONLY);

        Component moved = plan.after().component(labelled.id()).orElseThrow();
        Bounds label = texts.componentLabel(moved).orElseThrow();
        Bounds noteBox = texts.freeText(plan.after().label(note.id()).orElseThrow());
        assertThat(moved.position()).isNotEqualTo(labelled.position());
        assertThat(sharesWriting(label, noteBox)).isFalse();
    }

    /**
     * The user's hand test on riscv-maniqui (2026-11-06): the LEDs «Salta» and «Rs1 = Rs2» side by side, their labels
     * above them already sharing two columns. Aligning would move «Rs1 = Rs2» (a column with the gate above it) one
     * unit left, its label then sharing three columns: an overlap that grows, refused.
     */
    private record SideBySide(CircuitDocument document, Component salta, Component equal) {
    }

    private SideBySide sideBySideLeds() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 20, 0);
        Component equal = place(registry, document, StandardComponentTypes.LED, 21, 8, "Rs1 = Rs2");
        labelOffsets.put(equal.id(), new Bounds(-4, -2, 9, 2));
        Component salta = place(registry, document, StandardComponentTypes.LED, 12, 8, "Salta");
        labelOffsets.put(salta.id(), new Bounds(0, -2, 7, 2));
        return new SideBySide(document, salta, equal);
    }

    @Test
    void aligningNeverMakesAnOverlapOfTwoLabelsGrow() {
        SideBySide fixture = sideBySideLeds();

        CircuitTidyPlan plan = tidy(fixture.document(), everything(fixture.document()), ALIGN_ONLY);

        assertThat(positionOf(plan, fixture.equal())).isEqualTo(fixture.equal().position());
        assertThat(positionOf(plan, fixture.salta())).isEqualTo(fixture.salta().position());
    }

    /**
     * The same two LEDs with every option on: the circuit still tidies (a gate elsewhere aligns), aligning leaves the
     * LEDs where they are, and separating then moves «Rs1 = Rs2» two units right so the labels no longer share a
     * column (spec §6.2).
     */
    @Test
    void everyStageSeparatesTheTouchingLabelsAndTidiesTheRest() {
        SideBySide fixture = sideBySideLeds();
        gate(registry, fixture.document(), 60, 0);
        Component elsewhere = gate(registry, fixture.document(), 61, 8);

        CircuitTidyPlan plan = tidy(fixture.document(), everything(fixture.document()),
                new CircuitTidyOptions(true, true, true, TidyOptions.DEFAULTS));

        assertThat(positionOf(plan, fixture.equal())).isEqualTo(new GridPoint(23, 8));
        assertThat(positionOf(plan, fixture.salta())).isEqualTo(fixture.salta().position());
        assertThat(positionOf(plan, elsewhere)).isEqualTo(new GridPoint(60, 8));
    }

    /**
     * A move that shrinks an overlap of two labels is still allowed: aligning moves «Rs1 = Rs2» right (the column of
     * two gates at 20), away from «Salta».
     */
    @Test
    void aligningMayShrinkAnOverlapOfTwoLabels() {
        CircuitDocument document = new CircuitDocument();
        gate(registry, document, 20, 0);
        gate(registry, document, 20, 30);
        Component equal = place(registry, document, StandardComponentTypes.LED, 19, 8, "Rs1 = Rs2");
        labelOffsets.put(equal.id(), new Bounds(-4, -2, 9, 2));
        Component salta = place(registry, document, StandardComponentTypes.LED, 10, 8, "Salta");
        labelOffsets.put(salta.id(), new Bounds(0, -2, 7, 2));

        CircuitTidyPlan plan = tidy(document, everything(document), ALIGN_ONLY);

        assertThat(positionOf(plan, equal)).isEqualTo(new GridPoint(20, 8));
    }

    /** By the rule of {@link LayoutItems#covers}: at least one row and more than one column. */
    private static boolean sharesWriting(Bounds first, Bounds second) {
        int sharedX = Math.min(first.maximumX(), second.maximumX()) - Math.max(first.x(), second.x());
        int sharedY = Math.min(first.maximumY(), second.maximumY()) - Math.max(first.y(), second.y());
        return sharedX > LayoutItems.TEXT_ROUNDING_COLUMNS && sharedY > 0;
    }
}
