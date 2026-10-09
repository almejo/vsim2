package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasteCommandTest {

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument source = new CircuitDocument();
    private final CircuitDocument target = new CircuitDocument();

    /** NOT gate (0,2), wire (4,4)-(8,4) on its output, label "nota" at (0,0). */
    private CircuitFragment fragment() {
        Component gate = registry.require(StandardComponentTypes.NOT).newComponent(source.newComponentId(),
                new GridPoint(10, 10), Rotation.R0, Map.of(), "U1");
        Wire wire = new Wire(source.newWireId(), new GridPoint(14, 12), new GridPoint(18, 12));
        TextLabel label = new TextLabel(source.newLabelId(), new GridPoint(10, 8), "nota");
        source.add(gate);
        source.add(wire);
        source.add(label);
        return CircuitFragment.of(source, List.of(gate.id(), wire.id(), label.id()), registry);
    }

    @Test
    void pastesTheFragmentAtTheTargetWithFreshIds() {
        Component resident = registry.require(StandardComponentTypes.NOT).newComponent(target.newComponentId(),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        target.add(resident);
        PasteCommand command = new PasteCommand(fragment(), new GridPoint(20, 30));
        command.apply(target);
        assertThat(command.pastedIds()).hasSize(3).doesNotContain(resident.id());
        assertThat(command.pastedIds()).allMatch(id -> target.element(id).isPresent());
        Component pasted = target.components().get(1);
        assertThat(pasted.position()).isEqualTo(new GridPoint(20, 32));
        assertThat(pasted.label()).isEqualTo("U1");
        assertThat(target.wires()).singleElement().satisfies(wire -> {
            assertThat(wire.start()).isEqualTo(new GridPoint(24, 34));
            assertThat(wire.end()).isEqualTo(new GridPoint(28, 34));
        });
        assertThat(target.labels()).singleElement().satisfies(label -> {
            assertThat(label.position()).isEqualTo(new GridPoint(20, 30));
            assertThat(label.text()).isEqualTo("nota");
        });
        assertThat(command.description()).isEqualTo("command.paste");
    }

    @Test
    void undoRemovesEverythingAndRedoReusesTheSameIds() {
        History history = new History(target);
        PasteCommand command = new PasteCommand(fragment(), new GridPoint(5, 5));
        history.execute(command);
        List<ElementId> first = command.pastedIds();
        Set<DocumentElement> pasted = Set.copyOf(target.elements());
        history.undo();
        assertThat(target.isEmpty()).isTrue();
        history.redo();
        assertThat(command.pastedIds()).isEqualTo(first);
        assertThat(Set.copyOf(target.elements())).isEqualTo(pasted);
    }

    @Test
    void pastedWireMergesAndUndoRestoresTheOriginalWire() {
        Wire existing = new Wire(target.newWireId(), new GridPoint(24, 34), new GridPoint(30, 34));
        Wire later = new Wire(target.newWireId(), new GridPoint(0, 50), new GridPoint(0, 54));
        target.add(existing);
        target.add(later);
        History history = new History(target);
        PasteCommand command = new PasteCommand(fragment(), new GridPoint(20, 30));
        history.execute(command);
        assertThat(target.wires()).hasSize(2).anySatisfy(wire -> {
            assertThat(wire.start()).isEqualTo(new GridPoint(24, 34));
            assertThat(wire.end()).isEqualTo(new GridPoint(30, 34));
            assertThat(command.pastedIds()).contains(wire.id());
        });
        assertThat(target.wire(existing.id())).isEmpty();
        history.undo();
        assertThat(target.wires()).containsExactly(existing, later);
        assertThat(target.components()).isEmpty();
    }

    @Test
    void emptyFragmentChangesNothing() {
        PasteCommand command = new PasteCommand(new CircuitFragment(List.of(), List.of(), List.of()), GridPoint.ORIGIN);
        command.apply(target);
        assertThat(target.isEmpty()).isTrue();
        assertThat(command.pastedIds()).isEmpty();
    }
}
