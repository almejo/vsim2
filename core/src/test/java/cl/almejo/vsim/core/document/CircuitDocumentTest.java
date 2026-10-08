package cl.almejo.vsim.core.document;

import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitDocumentTest {

    private static Component andGate(ElementId id) {
        return new Component(id, "and2", new GridPoint(1, 1), Rotation.R0, Map.of("delay", 1), null);
    }

    @Test
    void componentLabelIsNeverNullAndParametersAreCopied() {
        Component component = andGate(new ElementId("c1"));
        assertThat(component.label()).isEmpty();
        assertThatThrownBy(() -> component.parameters().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void addFindReplaceAndRemove() {
        CircuitDocument document = new CircuitDocument();
        ElementId id = document.newComponentId();
        document.add(andGate(id));
        assertThat(document.component(id)).isPresent();

        document.replace(andGate(id).withLabel("U1"));
        assertThat(document.component(id).orElseThrow().label()).isEqualTo("U1");

        assertThat(document.remove(id)).isPresent();
        assertThat(document.isEmpty()).isTrue();
        assertThat(document.remove(id)).isEmpty();
    }

    @Test
    void duplicateIdIsRejected() {
        CircuitDocument document = new CircuitDocument();
        document.add(andGate(new ElementId("c1")));
        assertThatThrownBy(() -> document.add(andGate(new ElementId("c1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replacingMissingElementIsRejected() {
        CircuitDocument document = new CircuitDocument();
        assertThatThrownBy(() -> document.replace(andGate(new ElementId("c9"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newIdsSkipLoadedIds() {
        CircuitDocument document = new CircuitDocument();
        document.add(andGate(new ElementId("c41")));
        assertThat(document.newComponentId()).isEqualTo(new ElementId("c42"));
        assertThat(document.newWireId()).isEqualTo(new ElementId("w43"));
    }

    @Test
    void elementsAreListedByKindInInsertionOrder() {
        CircuitDocument document = new CircuitDocument();
        Wire wire = new Wire(document.newWireId(), new GridPoint(0, 0), new GridPoint(3, 0));
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(2, 2), "hola");
        Component component = andGate(document.newComponentId());
        document.add(wire);
        document.add(label);
        document.add(component);
        assertThat(document.wires()).containsExactly(wire);
        assertThat(document.labels()).containsExactly(label);
        assertThat(document.components()).containsExactly(component);
        assertThat(document.elements()).containsExactlyInAnyOrder(wire, label, component);
    }

    @Test
    void batchNotifiesOnceAndNestedBatchesToo() {
        CircuitDocument document = new CircuitDocument();
        AtomicInteger notifications = new AtomicInteger();
        document.addListener(changed -> notifications.incrementAndGet());
        document.batch(() -> {
            document.add(andGate(document.newComponentId()));
            document.batch(() -> document.add(andGate(document.newComponentId())));
        });
        assertThat(notifications).hasValue(1);
        document.add(andGate(document.newComponentId()));
        assertThat(notifications).hasValue(2);
    }
}
