package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.DocumentChange;
import cl.almejo.vsim.core.document.ElementId;
import org.junit.jupiter.api.Test;

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
}
