package cl.almejo.vsim.automation.check;

import cl.almejo.vsim.automation.check.LayoutCheck.Fault;
import cl.almejo.vsim.automation.check.LayoutCheck.FaultKind;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.StickyColor;
import cl.almejo.vsim.core.document.StickyNote;
import cl.almejo.vsim.core.document.StickyText;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LayoutCheckTest {

    private final CircuitDocument document = new CircuitDocument();
    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();

    @Test
    void twoPartsWhoseBodiesShareAreaOverlap() {
        Component first = place(StandardComponentTypes.NAND2, 0, 0, "");
        Component second = place(StandardComponentTypes.NAND2, 2, 1, "");

        assertThat(faults()).extracting(Fault::kind, Fault::elementIds)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(FaultKind.PART_OVERLAP,
                        List.of(first.id().value(), second.id().value())));
    }

    @Test
    void partsSideBySideOnlyTouchingDoNotOverlap() {
        Component first = place(StandardComponentTypes.NAND2, 0, 0, "");
        Bounds body = bounds(first);
        place(StandardComponentTypes.NAND2, body.maximumX(), 0, "");

        assertThat(faults()).isEmpty();
    }

    @Test
    void aStickyNoteOverPartsAndWiresReportsNothing() {
        Component gate = place(StandardComponentTypes.NAND2, 0, 0, "");
        Component other = place(StandardComponentTypes.NAND2, 12, 0, "");
        connect(gate, 2, other, 0);
        Bounds body = bounds(gate);
        document.add(new StickyNote(document.newStickyNoteId(), new GridPoint(body.x(), body.y()), 20, 10,
                StickyColor.YELLOW, new StickyText("Falta: probar"), false));
        document.add(new StickyNote(document.newStickyNoteId(), new GridPoint(body.x(), body.y()), 20, 10,
                StickyColor.BLUE, new StickyText("otra encima"), false));

        assertThat(faults()).isEmpty();
    }

    @Test
    void aNoteOverAPartCoversItAndTwoNotesInOnePlaceCoverEachOther() {
        Component gate = place(StandardComponentTypes.NAND2, 0, 0, "");
        Bounds body = bounds(gate);
        TextLabel onTheGate = note("Prueba: 1 y 1 dan 0", body.x() + 1, body.y() + 1);
        TextLabel first = note("Uno", 40, 40);
        TextLabel second = note("Dos", 40, 40);

        List<Fault> faults = faults();

        assertThat(faults).filteredOn(fault -> fault.kind() == FaultKind.TEXT_OVER_PART)
                .extracting(Fault::elementIds).containsExactly(List.of(onTheGate.id().value(), gate.id().value()));
        assertThat(faults).filteredOn(fault -> fault.kind() == FaultKind.TEXT_OVERLAP)
                .extracting(Fault::elementIds).containsExactly(List.of(first.id().value(), second.id().value()));
    }

    @Test
    void aLabelThatRunsIntoTheNextPartCoversIt() {
        Component left = place(StandardComponentTypes.NAND2, 0, 4, "una etiqueta muy pero muy larga");
        Component right = place(StandardComponentTypes.NAND2, bounds(left).maximumX() + 1, 0, "");

        assertThat(faults()).extracting(Fault::kind, Fault::elementIds)
                .contains(org.assertj.core.groups.Tuple.tuple(FaultKind.TEXT_OVER_PART,
                        List.of(left.id().value(), right.id().value())));
    }

    @Test
    void aWireAcrossABodyRunsThroughItButOneEndingAtItsEdgeDoesNot() {
        Component gate = place(StandardComponentTypes.NAND2, 10, 10, "");
        Bounds body = bounds(gate);
        int middle = body.y() + body.height() / 2;
        Wire across = wire(body.x() - 4, middle, body.maximumX() + 4, middle);
        wire(body.x() - 4, body.y() - 2, body.x(), body.y() - 2);

        assertThat(faults()).extracting(Fault::kind, Fault::elementIds)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(FaultKind.WIRE_THROUGH_BODY,
                        List.of(across.id().value(), gate.id().value())));
    }

    @Test
    void aWiredCircuitLaidOutLeftToRightHasNoFault() {
        Component input = place(StandardComponentTypes.SWITCH, 0, 0, "A");
        Component gate = place(StandardComponentTypes.NOT, 10, 0, "");
        Component output = place(StandardComponentTypes.LED, 20, 0, "Y");
        connect(input, 0, gate, 0);
        connect(gate, 1, output, 0);
        note("Prueba: A = 1 apaga Y", 0, 12);

        assertThat(faults()).isEmpty();
    }

    @Test
    void theScopeKeepsOnlyTheFaultsOfItsElements() {
        place(StandardComponentTypes.NAND2, 0, 0, "");
        place(StandardComponentTypes.NAND2, 2, 1, "");
        Component apart = place(StandardComponentTypes.NAND2, 40, 40, "");

        assertThat(LayoutCheck.faults(document, registry, TextMeasure.APPROXIMATE, Set.of(apart.id().value())))
                .isEmpty();
        assertThat(LayoutCheck.faults(document, registry, TextMeasure.APPROXIMATE, Set.of("c1"))).hasSize(1);
    }

    private List<Fault> faults() {
        return LayoutCheck.faults(document, registry, TextMeasure.APPROXIMATE, Set.of());
    }

    private Component place(String typeId, int x, int y, String label) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, Map.of(), label);
        document.add(component);
        return component;
    }

    private Bounds bounds(Component component) {
        return registry.typeOf(component).bounds(component);
    }

    private TextLabel note(String text, int x, int y) {
        TextLabel label = new TextLabel(document.newLabelId(), new GridPoint(x, y), text);
        document.add(label);
        return label;
    }

    private Wire wire(int startX, int startY, int endX, int endY) {
        Wire wire = new Wire(document.newWireId(), new GridPoint(startX, startY), new GridPoint(endX, endY));
        document.add(wire);
        return wire;
    }

    /** An L from one pin to the other: across, then down. */
    private void connect(Component from, int fromPin, Component to, int toPin) {
        GridPoint start = registry.typeOf(from).pinLocation(from, fromPin);
        GridPoint end = registry.typeOf(to).pinLocation(to, toPin);
        if (start.y() == end.y()) {
            wire(start.x(), start.y(), end.x(), end.y());
            return;
        }
        int middle = (start.x() + end.x()) / 2;
        wire(start.x(), start.y(), middle, start.y());
        wire(middle, start.y(), middle, end.y());
        wire(middle, end.y(), end.x(), end.y());
    }
}
