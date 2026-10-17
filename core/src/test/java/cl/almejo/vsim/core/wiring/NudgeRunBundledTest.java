package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.bundled.BundledExamples;
import cl.almejo.vsim.core.command.History;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Random runs of arrow nudges in every direction on the bundled examples (C1 of the M6.7 review): a run never fails,
 * ends equal to one move by the total, and one undo puts the document back exactly.
 */
class NudgeRunBundledTest {

    private static final int RUNS_PER_EXAMPLE = 12;
    private static final int[][] ARROWS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    static Stream<String> examples() {
        return BundledExamples.ids().stream();
    }

    @ParameterizedTest
    @MethodSource("examples")
    void aRunOfArrowsInAnyDirectionsEqualsOneMoveAndUndoesExactly(String exampleId) throws CircuitFormatException {
        Random random = new Random(exampleId.hashCode());
        CircuitDocument document = BundledExamples.open(exampleId, StandardComponentTypes.registry());
        ComponentTypeRegistry registry = StandardComponentTypes.registry()
                .extendedWith(SubcircuitTypes.resolverFor(document));
        List<ElementId> candidates = new ArrayList<>();
        document.components().forEach(component -> candidates.add(component.id()));
        document.wires().forEach(wire -> candidates.add(wire.id()));
        Map<DocumentElement, Long> original = snapshot(document);
        for (int run = 0; run < RUNS_PER_EXAMPLE; run++) {
            Set<ElementId> moved = new LinkedHashSet<>();
            int count = 1 + random.nextInt(3);
            for (int index = 0; index < count; index++) {
                moved.add(candidates.get(random.nextInt(candidates.size())));
            }
            int steps = 2 + random.nextInt(6);
            int totalX = 0;
            int totalY = 0;
            History history = new History(document);
            for (int step = 0; step < steps; step++) {
                int[] arrow = ARROWS[random.nextInt(ARROWS.length)];
                totalX += arrow[0];
                totalY += arrow[1];
                history.execute(MoveWithWiresCommand.nudge(registry, moved, arrow[0], arrow[1]));
            }
            String description = exampleId + ": nudging " + moved + " by (" + totalX + "," + totalY + ")";
            CircuitDocument single = BundledExamples.open(exampleId, StandardComponentTypes.registry());
            MoveWithWiresCommand.of(single, registry, moved, totalX, totalY).apply(single);
            assertThat(segments(document)).as(description).isEqualTo(segments(single));
            assertThat(notWires(document)).as(description).isEqualTo(notWires(single));

            history.undo();
            assertThat(history.canUndo()).as(description).isFalse();
            assertThat(snapshot(document)).as(description).containsExactlyEntriesOf(original);
        }
    }

    /** The wires as segments, ignoring their ids (a run and one move may number new wires differently). */
    private static List<String> segments(CircuitDocument document) {
        return document.wires().stream().map(wire -> wire.start() + "-" + wire.end()).sorted().toList();
    }

    private static Set<DocumentElement> notWires(CircuitDocument document) {
        Set<DocumentElement> elements = new LinkedHashSet<>(document.elements());
        elements.removeIf(element -> element instanceof Wire);
        return elements;
    }

    private static Map<DocumentElement, Long> snapshot(CircuitDocument document) {
        Map<DocumentElement, Long> snapshot = new LinkedHashMap<>();
        document.elements().forEach(element -> snapshot.put(element, document.orderOf(element.id())));
        return snapshot;
    }
}
