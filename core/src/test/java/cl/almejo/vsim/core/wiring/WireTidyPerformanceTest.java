package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.TestComponentTypes;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tidying a large circuit stays fast (M6.2 final review, I1): before, every candidate change compiled the whole
 * netlist and the merge scan restarted after every merge, so a 1600-wire messy circuit took ~50 s. The bounds are
 * generous (a few times what a slow machine needs) so that only a return to that growth fails.
 */
class WireTidyPerformanceTest {

    private static final ComponentTypeRegistry REGISTRY = TestComponentTypes.registry();
    private static final TidyOptions ALL = new TidyOptions(true, true, true);
    private static final Duration GENEROUS = Duration.ofSeconds(3);

    private enum Mess { NONE, PIECES_AND_STUBS, DETOURS }

    @Test
    void aMessyCircuitOf1600WiresIsTidiedQuickly() {
        CircuitDocument document = circuit(20, Mess.PIECES_AND_STUBS);
        assertThat(document.wires()).hasSize(1600);

        TidyPlan plan = timedPlan(document);

        assertThat(plan.stubsRemoved()).isEqualTo(400);
        assertThat(plan.segmentsMerged()).isEqualTo(800);
    }

    @Test
    void aCircuitOf1600WiresWithDetoursIsTidiedQuickly() {
        CircuitDocument document = circuit(12, Mess.DETOURS);
        assertThat(document.wires()).hasSize(1680);

        TidyPlan plan = timedPlan(document);

        assertThat(plan.detoursFlattened()).isEqualTo(240);
        assertThat(plan.stubsRemoved()).isEqualTo(240);
    }

    @Test
    void aCleanCircuitOf1600WiresIsCheckedQuickly() {
        CircuitDocument document = circuit(20, Mess.NONE);
        assertThat(document.wires()).hasSize(1600);

        TidyPlan plan = timedPlan(document);

        assertThat(plan.isEmpty()).isTrue();
    }

    private static TidyPlan timedPlan(CircuitDocument document) {
        Set<ElementId> scope = new HashSet<>();
        document.wires().forEach(wire -> scope.add(wire.id()));
        long start = System.nanoTime();
        TidyPlan plan = WireTidy.plan(document, REGISTRY, scope, ALL);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).as("WireTidy.plan on %d wires", document.wires().size()).isLessThan(GENEROUS);
        return plan;
    }

    /**
     * Rows of 21 buffers, each output but the last wired to the next buffer's input (20 wired cells per row). {@code PIECES_AND_STUBS}: the wire is cut in
     * three collinear pieces and has a loose T stub. {@code DETOURS}: the wire makes a U below the row, is cut once
     * more and has a loose T stub. {@code NONE}: one straight wire with a closed loop hanging from it (nothing to do).
     */
    private static CircuitDocument circuit(int rows, Mess mess) {
        CircuitDocument document = new CircuitDocument();
        for (int index = 0; index < rows * 21; index++) {
            int x = (index % 21) * 10;
            int y = (index / 21) * 6;
            document.add(new Component(document.newComponentId(), TestComponentTypes.BUFFER, new GridPoint(x, y),
                    Rotation.R0, Map.of(), ""));
            if (index % 21 == 20) {
                continue;
            }
            switch (mess) {
                case NONE -> {
                    wire(document, x + 2, y + 1, x + 10, y + 1);
                    wire(document, x + 6, y + 1, x + 6, y + 4);
                    wire(document, x + 6, y + 4, x + 8, y + 4);
                    wire(document, x + 8, y + 4, x + 8, y + 1);
                }
                case PIECES_AND_STUBS -> {
                    wire(document, x + 2, y + 1, x + 4, y + 1);
                    wire(document, x + 4, y + 1, x + 7, y + 1);
                    wire(document, x + 7, y + 1, x + 10, y + 1);
                    wire(document, x + 5, y + 1, x + 5, y + 3);
                }
                case DETOURS -> {
                    wire(document, x + 2, y + 1, x + 4, y + 1);
                    wire(document, x + 4, y + 1, x + 4, y + 3);
                    wire(document, x + 4, y + 3, x + 6, y + 3);
                    wire(document, x + 6, y + 3, x + 6, y + 1);
                    wire(document, x + 6, y + 1, x + 8, y + 1);
                    wire(document, x + 8, y + 1, x + 10, y + 1);
                    wire(document, x + 8, y + 1, x + 8, y + 3);
                }
            }
        }
        return document;
    }

    private static void wire(CircuitDocument document, int startX, int startY, int endX, int endY) {
        document.add(new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY)));
    }
}
