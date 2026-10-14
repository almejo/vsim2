package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.TidyPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TidyWiresCommandTest {

    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);
    private final Wire first = wire("w1", 0, 0, 4, 0);
    private final Wire second = wire("w2", 4, 0, 8, 0);
    private final Wire third = wire("w3", 0, 5, 3, 5);
    private final Wire merged = wire("w1", 0, 0, 8, 0);

    private static Wire wire(String id, int startX, int startY, int endX, int endY) {
        return new Wire(new ElementId(id), new GridPoint(startX, startY), new GridPoint(endX, endY));
    }

    private void fill() {
        document.add(first);
        document.add(second);
        document.add(third);
    }

    private TidyPlan mergePlan(Map<ElementId, ElementId> retargets) {
        return new TidyPlan(List.of(first, second), List.of(merged), retargets, 0, 0, 1, 0);
    }

    @Test
    void applyReplacesTheWiresAndKeepsTheDrawOrder() {
        fill();
        history.execute(new TidyWiresCommand(mergePlan(Map.of())));

        List<DocumentElement> elements = document.elements();
        assertThat(elements).containsExactly(merged, third);
        assertThat(document.orderOf(merged.id())).isZero();
    }

    @Test
    void revertRestoresElementsIdsAndOrderAndRedoWorks() {
        fill();
        List<DocumentElement> before = document.elements();
        long orderOfSecond = document.orderOf(second.id());

        history.execute(new TidyWiresCommand(mergePlan(Map.of())));
        List<DocumentElement> after = document.elements();
        history.undo();
        assertThat(document.elements()).isEqualTo(before);
        assertThat(document.orderOf(second.id())).isEqualTo(orderOfSecond);

        history.redo();
        assertThat(document.elements()).isEqualTo(after);
        history.undo();
        assertThat(document.elements()).isEqualTo(before);
    }

    @Test
    void brandNewWiresAreAddedAndRemovedOnRevert() {
        fill();
        Wire fresh = wire("w9", 0, 9, 2, 9);
        TidyPlan plan = new TidyPlan(List.of(third), List.of(fresh), Map.of(), 0, 0, 0, 0);
        history.execute(new TidyWiresCommand(plan));
        assertThat(document.wire(fresh.id())).isPresent();
        assertThat(document.wire(third.id())).isEmpty();
        history.undo();
        assertThat(document.wire(fresh.id())).isEmpty();
        assertThat(document.elements()).containsExactly(first, second, third);
    }

    @Test
    void probeRetargetsAreAppliedAndReverted() {
        fill();
        Probe onSecond = new Probe("p1", new ProbeTarget.WireTarget(second.id()), "cable");
        Probe onThird = new Probe("p2", new ProbeTarget.WireTarget(third.id()), "otro");
        document.setProbes(List.of(onSecond, onThird));

        history.execute(new TidyWiresCommand(mergePlan(Map.of(second.id(), first.id()))));
        assertThat(document.probes()).containsExactly(
                new Probe("p1", new ProbeTarget.WireTarget(first.id()), "cable"), onThird);

        history.undo();
        assertThat(document.probes()).containsExactly(onSecond, onThird);
        history.redo();
        assertThat(document.probes().get(0).target()).isEqualTo(new ProbeTarget.WireTarget(first.id()));
    }

    @Test
    void describesItselfWithItsMessageKey() {
        assertThat(new TidyWiresCommand(TidyPlan.EMPTY).description()).isEqualTo("command.tidyWires");
    }
}
