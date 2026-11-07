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
 * it was not over before (the user's hand test, 2026-11-06: the LED «Salta», straightened, ended with its label over
 * «Rs1 = Rs2»).
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

    /** By the rule of {@link LayoutItems#covers}: at least one row and more than one column. */
    private static boolean sharesWriting(Bounds first, Bounds second) {
        int sharedX = Math.min(first.maximumX(), second.maximumX()) - Math.max(first.x(), second.x());
        int sharedY = Math.min(first.maximumY(), second.maximumY()) - Math.max(first.y(), second.y());
        return sharedX > LayoutItems.TEXT_ROUNDING_COLUMNS && sharedY > 0;
    }
}
