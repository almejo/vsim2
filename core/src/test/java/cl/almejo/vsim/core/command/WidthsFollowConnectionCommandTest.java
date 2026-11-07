package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * «Anchos que siguen lo conectado»: an input pin, output pin, Constante or Registro whose pin is connected to a net of
 * another width takes that width, in the same undo step as the connection; two widths at once, or a width the part
 * cannot take, change nothing and the width mismatch stays.
 */
class WidthsFollowConnectionCommandTest {

    private static final String WIDTH = StandardComponentTypes.WIDTH;

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final CircuitDocument document = new CircuitDocument();
    private final History history = new History(document);

    private Component add(String typeId, int x, int y, Map<String, Object> parameters) {
        Component component = registry.require(typeId).newComponent(document.newComponentId(), new GridPoint(x, y),
                Rotation.R0, parameters, "");
        document.add(component);
        return component;
    }

    private Component constant(int x, int y, int width) {
        return add(StandardComponentTypes.CONSTANT, x, y, Map.of(WIDTH, width));
    }

    private Component adder(int x, int y, int width) {
        return add(StandardComponentTypes.ADDER, x, y, Map.of(WIDTH, width));
    }

    private Component current(Component component) {
        return document.component(component.id()).orElseThrow();
    }

    private int widthOf(Component component) {
        return (Integer) current(component).parameters().get(WIDTH);
    }

    private GridPoint pin(Component component, String name) {
        Component now = current(component);
        ComponentType type = registry.typeOf(now);
        return type.pinLocation(now, type.pinIndexOf(name).orElseThrow());
    }

    private PinRef pinRef(Component component, String name) {
        return new PinRef(component.id(), registry.typeOf(current(component)).pinIndexOf(name).orElseThrow());
    }

    private Net netOf(Component component, String name) {
        return new NetlistCompiler(registry).compile(document).netForPin(pinRef(component, name)).orElseThrow();
    }

    private boolean joined(Component first, String firstPin, Component second, String secondPin) {
        return netOf(first, firstPin).pins().contains(pinRef(second, secondPin));
    }

    private Wire newWire(GridPoint start, GridPoint end) {
        return new Wire(document.newWireId(), start, end);
    }

    private void draw(GridPoint start, GridPoint end) {
        history.execute(WidthsFollowConnectionCommand.wire(new AddWireCommand(newWire(start, end)), registry));
    }

    @Test
    void aWireFromAOneBitConstantToAnEightBitAdderMakesTheConstantEightBitsAndUndoRestoresBoth() {
        Component constant = constant(0, 2, 1);
        Component adder = adder(20, 0, 8);
        GridPoint constantPin = pin(constant, "out");

        draw(constantPin, pin(adder, "A"));

        assertThat(widthOf(constant)).isEqualTo(8);
        assertThat(joined(constant, "out", adder, "A")).isTrue();
        assertThat(netOf(adder, "A").widthMismatch()).isFalse();

        history.undo();

        assertThat(widthOf(constant)).isEqualTo(1);
        assertThat(document.wires()).isEmpty();
        assertThat(pin(constant, "out")).isEqualTo(constantPin);
    }

    @Test
    void redoingTheWireMakesTheConstantFollowAgain() {
        Component constant = constant(0, 2, 1);
        Component adder = adder(20, 0, 8);

        draw(pin(constant, "out"), pin(adder, "A"));
        history.undo();
        history.redo();

        assertThat(widthOf(constant)).isEqualTo(8);
        assertThat(joined(constant, "out", adder, "A")).isTrue();
    }

    @Test
    void placingAnOutputPinOnTheEndOfABusMakesItTakeTheBusWidth() {
        Component adder = adder(0, 0, 8);
        GridPoint sum = pin(adder, "S");
        document.add(newWire(sum, new GridPoint(sum.x() + 6, sum.y())));
        Component outputPin = registry.require(StandardComponentTypes.OUTPUT_PIN).newComponent(
                document.newComponentId(), new GridPoint(sum.x() + 6, sum.y() - 1), Rotation.R0, Map.of(), "");

        history.execute(WidthsFollowConnectionCommand.placing(new AddElementsCommand(List.of(outputPin)),
                Set.of(outputPin.id()), registry));

        assertThat(widthOf(outputPin)).isEqualTo(8);
        assertThat(joined(outputPin, "pin", adder, "S")).isTrue();

        history.undo();

        assertThat(document.component(outputPin.id())).isEmpty();
    }

    @Test
    void movingAConstantSoItsPinTouchesABusMakesItTakeTheBusWidth() {
        Component adder = adder(20, 0, 8);
        GridPoint input = pin(adder, "A");
        document.add(newWire(new GridPoint(10, input.y()), input));
        Component constant = constant(0, 20, 1);
        GridPoint constantPin = pin(constant, "out");
        int deltaX = 10 - constantPin.x();
        int deltaY = input.y() - constantPin.y();

        history.execute(WidthsFollowConnectionCommand.placing(
                MoveWithWiresCommand.of(document, registry, Set.of(constant.id()), deltaX, deltaY),
                Set.of(constant.id()), registry));

        assertThat(widthOf(constant)).isEqualTo(8);
        assertThat(joined(constant, "out", adder, "A")).isTrue();

        history.undo();

        assertThat(widthOf(constant)).isEqualTo(1);
        assertThat(pin(constant, "out")).isEqualTo(constantPin);
    }

    @Test
    void aMovedPartFollowsAndThePartsItTouchesKeepTheirWidths() {
        Component constant = constant(0, 0, 4);
        document.add(newWire(pin(constant, "out"), new GridPoint(10, pin(constant, "out").y())));
        Component outputPin = registry.require(StandardComponentTypes.OUTPUT_PIN).newComponent(
                document.newComponentId(), new GridPoint(10, 30), Rotation.R0, Map.of(), "");
        document.add(outputPin);
        GridPoint outputPinPlace = pin(outputPin, "pin");

        history.execute(WidthsFollowConnectionCommand.placing(MoveWithWiresCommand.of(document, registry,
                        Set.of(outputPin.id()), 10 - outputPinPlace.x(), pin(constant, "out").y() - outputPinPlace.y()),
                Set.of(outputPin.id()), registry));

        assertThat(widthOf(outputPin)).isEqualTo(4);
        assertThat(widthOf(constant)).isEqualTo(4);
        assertThat(joined(outputPin, "pin", constant, "out")).isTrue();
    }

    @Test
    void aFreshInputPinWiredToAConnectedRegisterTakesTheRegisterWidthAndTheRegisterKeepsIts() {
        Component register = add(StandardComponentTypes.REGISTER, 20, 0, Map.of(WIDTH, 8));
        Component outputPin = add(StandardComponentTypes.OUTPUT_PIN, 40, 1, Map.of(WIDTH, 8));
        document.add(newWire(pin(register, "Q"), pin(outputPin, "pin")));
        Component inputPin = add(StandardComponentTypes.INPUT_PIN, 0, 1, Map.of());

        draw(pin(inputPin, "pin"), pin(register, "D"));

        assertThat(widthOf(inputPin)).isEqualTo(8);
        assertThat(widthOf(register)).isEqualTo(8);
        assertThat(netOf(register, "D").widthMismatch()).isFalse();
    }

    @Test
    void aFreshOutputPinWiredToAFreshConstantTakesTheConstantWidth() {
        Component constant = constant(0, 1, 4);
        Component outputPin = add(StandardComponentTypes.OUTPUT_PIN, 20, pin(constant, "out").y() - 1, Map.of());

        draw(pin(constant, "out"), pin(outputPin, "pin"));

        assertThat(widthOf(constant)).isEqualTo(4);
        assertThat(widthOf(outputPin)).isEqualTo(4);
    }

    @Test
    void twoLoosePartsOfTheSameRankGiveNoWidthToEachOtherAndTheMismatchStays() {
        Component adder = adder(0, 0, 4);
        Component register = add(StandardComponentTypes.REGISTER, 20, pin(adder, "S").y() - 2, Map.of(WIDTH, 8));

        draw(pin(adder, "S"), pin(register, "D"));

        assertThat(widthOf(adder)).isEqualTo(4);
        assertThat(widthOf(register)).isEqualTo(8);
        assertThat(netOf(register, "D").widthMismatch()).isTrue();
    }

    @Test
    void twoDifferentWidthsAtThePinChangeNothingAndTheMismatchStays() {
        Component wide = adder(0, 0, 8);
        Component narrow = adder(30, 0, 4);
        GridPoint sum = pin(wide, "S");
        GridPoint narrowInput = pin(narrow, "A");
        document.add(newWire(sum, new GridPoint(sum.x() + 4, sum.y())));
        document.add(newWire(new GridPoint(sum.x() + 4, sum.y()), new GridPoint(sum.x() + 4, narrowInput.y())));
        document.add(newWire(new GridPoint(sum.x() + 4, narrowInput.y()), narrowInput));
        Component outputPin = add(StandardComponentTypes.OUTPUT_PIN, sum.x() + 4, 20, Map.of());

        draw(new GridPoint(sum.x() + 4, narrowInput.y()), pin(outputPin, "pin"));

        assertThat(widthOf(outputPin)).isEqualTo(1);
        assertThat(netOf(outputPin, "pin").widthMismatch()).isTrue();
    }

    @Test
    void aWidthBeyondThePartsMaximumIsNotClampedAndTheMismatchStays() {
        Component inputPin = add(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(WIDTH, 64));
        Component outputPin = add(StandardComponentTypes.OUTPUT_PIN, 20, 0, Map.of(WIDTH, 64));
        document.add(newWire(pin(inputPin, "pin"), pin(outputPin, "pin")));
        Component constant = constant(0, 10, 1);
        GridPoint constantPin = pin(constant, "out");

        draw(constantPin, new GridPoint(10, constantPin.y()));
        draw(new GridPoint(10, constantPin.y()), new GridPoint(10, pin(inputPin, "pin").y()));

        assertThat(widthOf(constant)).isEqualTo(1);
        assertThat(netOf(constant, "out").widthMismatch()).isTrue();
    }

    @Test
    void aConstantWhoseValueDoesNotFitTheNarrowerWidthKeepsItsWidth() {
        Component constant = add(StandardComponentTypes.CONSTANT, 0, 2, Map.of(WIDTH, 8, "value", "200"));
        Component adder = adder(20, 0, 4);
        GridPoint constantPin = pin(constant, "out");

        draw(constantPin, pin(adder, "A"));

        assertThat(widthOf(constant)).isEqualTo(8);
        assertThat(netOf(adder, "A").widthMismatch()).isTrue();
    }

    @Test
    void aPartWithoutAWidthParameterIsUntouchedAndAConnectedPartKeepsItsWidth() {
        Component flipFlop = add(StandardComponentTypes.FLIP_FLOP_DATA, 0, 10, Map.of());
        GridPoint output = pin(flipFlop, "q");
        Component adder = adder(20, output.y() - 3, 8);
        Component inputPin = add(StandardComponentTypes.INPUT_PIN, 10, 30, Map.of(WIDTH, 8));
        document.add(newWire(pin(inputPin, "pin"), new GridPoint(pin(adder, "B").x(), pin(inputPin, "pin").y())));
        document.add(newWire(new GridPoint(pin(adder, "B").x(), pin(inputPin, "pin").y()), pin(adder, "B")));

        draw(output, pin(adder, "A"));

        assertThat(current(flipFlop).parameters()).isEqualTo(flipFlop.parameters());
        assertThat(widthOf(adder)).isEqualTo(8);
        assertThat(netOf(adder, "A").widthMismatch()).isTrue();
    }

    @Test
    void anAndGateDroppedWithAnInputOnAnEightBitBusTakesEightBits() {
        Component inputPin = add(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(WIDTH, 8));
        document.add(newWire(pin(inputPin, "pin"), new GridPoint(30, pin(inputPin, "pin").y())));

        Component and = dropAnd(new GridPoint(14, pin(inputPin, "pin").y()));

        assertThat(widthOf(and)).isEqualTo(8);
        assertThat(netOf(and, "in0").widthMismatch()).isFalse();
    }

    @Test
    void anAndGateDroppedWithItsInputsOnBusesOfTwoWidthsChangesNothingAndTheMismatchStays() {
        Component narrow = add(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(WIDTH, 4));
        GridPoint narrowEnd = pin(narrow, "pin");
        document.add(newWire(narrowEnd, new GridPoint(30, narrowEnd.y())));
        Component probe = andAt(GridPoint.ORIGIN);
        int rowsApart = pin(probe, "in1").y() - pin(probe, "in0").y();
        document.remove(probe.id());
        Component wide = add(StandardComponentTypes.INPUT_PIN, 0, rowsApart, Map.of(WIDTH, 8));
        document.add(newWire(pin(wide, "pin"), new GridPoint(30, pin(wide, "pin").y())));

        Component and = dropAnd(new GridPoint(14, narrowEnd.y()));

        assertThat(joined(and, "in1", wide, "pin")).isTrue();
        assertThat(widthOf(and)).isEqualTo(1);
        assertThat(netOf(and, "in0").widthMismatch()).isTrue();
        assertThat(netOf(and, "in1").widthMismatch()).isTrue();
    }

    @Test
    void anAndGateWhoseOutputIsOnAOneBitWireKeepsItsWidthWhenMovedOntoAnEightBitBus() {
        Component inputPin = add(StandardComponentTypes.INPUT_PIN, 0, 0, Map.of(WIDTH, 8));
        GridPoint busEnd = pin(inputPin, "pin");
        document.add(newWire(busEnd, new GridPoint(30, busEnd.y())));
        Component and = andAt(new GridPoint(10, 20));
        Component outputPin = add(StandardComponentTypes.OUTPUT_PIN, 40, pin(and, "out").y() - 1, Map.of());
        document.add(newWire(pin(and, "out"), pin(outputPin, "pin")));
        GridPoint input = pin(and, "in0");

        history.execute(WidthsFollowConnectionCommand.placing(MoveWithWiresCommand.of(document, registry,
                Set.of(and.id()), 14 - input.x(), busEnd.y() - input.y()), Set.of(and.id()), registry));

        assertThat(joined(and, "in0", inputPin, "pin")).isTrue();
        assertThat(joined(and, "out", outputPin, "pin")).isTrue();
        assertThat(widthOf(and)).isEqualTo(1);
        assertThat(netOf(and, "in0").widthMismatch()).isTrue();
    }

    private Component andAt(GridPoint position) {
        return add(StandardComponentTypes.AND, position.x(), position.y(), Map.of());
    }

    /** Places a new two-input AND whose {@code in0} lands on {@code input}, as the palette does. */
    private Component dropAnd(GridPoint input) {
        Component atOrigin = registry.require(StandardComponentTypes.AND).newComponent(new ElementId("probe"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        GridPoint offset = registry.typeOf(atOrigin).pinLocation(atOrigin, 0);
        Component and = registry.require(StandardComponentTypes.AND).newComponent(document.newComponentId(),
                new GridPoint(input.x() - offset.x(), input.y() - offset.y()), Rotation.R0, Map.of(), "");
        history.execute(WidthsFollowConnectionCommand.placing(new AddElementsCommand(List.of(and)), Set.of(and.id()),
                registry));
        return and;
    }

    @Test
    void theCommandSaysWhichPartChangedWidth() {
        Component constant = constant(0, 2, 1);
        Component adder = adder(20, 0, 8);
        WidthsFollowConnectionCommand command = WidthsFollowConnectionCommand.wire(
                new AddWireCommand(newWire(pin(constant, "out"), pin(adder, "A"))), registry);

        history.execute(command);

        assertThat(command.widthChanges()).containsExactly(
                new WidthChange(constant.id(), StandardComponentTypes.CONSTANT, 1, 8));
        assertThat(command.description()).isEqualTo("command.wire");
    }

    @Test
    void aRunOfNudgesFollowsTheWidthOnceAndIsOneUndoStep() {
        Component adder = adder(20, 0, 8);
        GridPoint input = pin(adder, "A");
        Component constant = constant(0, 0, 1);
        GridPoint start = pin(constant, "out");
        document.add(newWire(new GridPoint(start.x() + 5, input.y()), input));
        Set<ElementId> moved = Set.of(constant.id());

        nudge(moved, 5, 0);
        for (int step = 0; step < input.y() - start.y() + 1; step++) {
            nudge(moved, 0, 1);
        }
        nudge(moved, 0, -1);

        assertThat(widthOf(constant)).isEqualTo(8);
        assertThat(joined(constant, "out", adder, "A")).isTrue();

        history.undo();

        assertThat(widthOf(constant)).isEqualTo(1);
        assertThat(current(constant).position()).isEqualTo(new GridPoint(0, 0));
        assertThat(history.canUndo()).isFalse();
    }

    private void nudge(Set<ElementId> moved, int deltaX, int deltaY) {
        history.execute(WidthsFollowConnectionCommand.placing(
                MoveWithWiresCommand.nudge(registry, moved, deltaX, deltaY), moved, registry));
    }
}
