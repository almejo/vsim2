package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import org.junit.jupiter.api.Test;

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
    void retainExistingDropsRemovedElements() {
        CircuitDocument document = new CircuitDocument();
        TextLabel label = new TextLabel(document.newLabelId(), GridPoint.ORIGIN, "x");
        document.add(label);
        selection.select(label.id());
        selection.addListener(changes::incrementAndGet);
        selection.retainExisting(document);
        assertThat(changes).hasValue(0);
        document.remove(label.id());
        selection.retainExisting(document);
        assertThat(selection.contains(label.id())).isFalse();
        assertThat(changes).hasValue(1);
    }
}
