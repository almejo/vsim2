package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTest {

    private final Selection selection = new Selection();
    private final AtomicInteger changes = new AtomicInteger();

    @Test
    void selectReplacesAndNotifiesOnlyOnRealChanges() {
        selection.addListener(changes::incrementAndGet);
        selection.select(new ElementId("c1"));
        selection.select(new ElementId("c1"));
        selection.select(new ElementId("c2"));
        selection.clear();
        selection.clear();
        assertThat(changes).hasValue(3);
        assertThat(selection.isEmpty()).isTrue();
    }

    @Test
    void retainDropsOnlyRemovedElements() {
        ElementId selected = new ElementId("l1");
        selection.select(selected);
        selection.addListener(changes::incrementAndGet);
        selection.retain(new DocumentChange(Set.of(new ElementId("c2")), Set.of(new ElementId("w3")), Set.of(selected)));
        assertThat(selection.contains(selected)).isTrue();
        assertThat(changes).hasValue(0);
        selection.retain(new DocumentChange(Set.of(), Set.of(selected), Set.of()));
        assertThat(selection.isEmpty()).isTrue();
        assertThat(changes).hasValue(1);
    }

    @Test
    void addRemoveToggleAndSetNotifyOnlyOnRealChanges() {
        ElementId first = new ElementId("c1");
        ElementId second = new ElementId("w2");
        selection.addListener(changes::incrementAndGet);
        selection.add(first);
        selection.add(first);
        selection.add(second);
        selection.toggle(first);
        assertThat(selection.ids()).containsExactly(second);
        selection.toggle(first);
        selection.remove(new ElementId("c9"));
        selection.set(List.of(second, first));
        selection.set(List.of(first));
        selection.select(first);
        assertThat(selection.ids()).containsExactly(first);
        assertThat(selection.size()).isEqualTo(1);
        assertThat(changes).hasValue(5);
    }

    @Test
    void selectReplacesAMultiSelection() {
        selection.set(List.of(new ElementId("c1"), new ElementId("c2")));
        selection.select(new ElementId("c1"));
        assertThat(selection.ids()).containsExactly(new ElementId("c1"));
    }

    @Test
    void selectAllTakesEveryElementOfTheDocument() {
        CircuitDocument document = new CircuitDocument();
        TextLabel label = new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "x");
        Wire wire = new Wire(document.newWireId(), GridPoint.ORIGIN, new GridPoint(3, 0));
        document.add(label);
        document.add(wire);
        selection.selectAll(document);
        assertThat(selection.ids()).containsExactlyInAnyOrder(label.id(), wire.id());
    }

    @Test
    void retainDropsOnlyTheRemovedIdsOfAMultiSelection() {
        ElementId kept = new ElementId("c1");
        ElementId gone = new ElementId("w2");
        selection.set(List.of(kept, gone));
        selection.addListener(changes::incrementAndGet);
        selection.retain(new DocumentChange(Set.of(), Set.of(gone), Set.of(kept)));
        assertThat(selection.ids()).containsExactly(kept);
        assertThat(changes).hasValue(1);
    }
}
