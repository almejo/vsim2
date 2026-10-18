package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.BlockHarness;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MuxBehaviorTest {

    private static Map<String, Object> parameters(int width, String inputs) {
        return Map.of(StandardComponentTypes.WIDTH, width, "inputs", inputs);
    }

    private static ComponentType mux() {
        return StandardComponentTypes.registry().require(StandardComponentTypes.MUX);
    }

    @Test
    void theMuxHasItsDataOnTheLeftTheSelectAtTheBottomAndGrowsWithItsInputs() {
        ComponentType two = mux();
        assertThat(two.category()).isEqualTo(ComponentCategory.BLOCKS);
        assertThat(two.parameters()).extracting(parameter -> parameter.name())
                .containsExactly("width", "inputs", "delay");
        assertThat(two.parameters().get(1).choices()).containsExactly("2", "4");
        assertThat(two.parameters().get(1).defaultValue()).isEqualTo("2");
        assertThat(two.pins()).extracting(PinSpec::name).containsExactly("D0", "D1", "S", "Y");
        assertThat(two.pins()).extracting(PinSpec::direction).containsExactly(PinDirection.INPUT,
                PinDirection.INPUT, PinDirection.INPUT, PinDirection.OUTPUT);
        assertThat(two.pins()).extracting(PinSpec::width).containsExactly(8, 8, 1, 8);

        ComponentType four = two.specializedFor(parameters(8, "4"));
        assertThat(four.pins()).extracting(PinSpec::name).containsExactly("D0", "D1", "D2", "D3", "S", "Y");
        assertThat(four.pins()).extracting(PinSpec::width).containsExactly(8, 8, 8, 8, 2, 8);
        assertThat(four.size().height()).isGreaterThan(two.size().height());
        for (ComponentType type : List.of(two, four)) {
            int inputs = type.pinCount() - 2;
            for (int index = 0; index < inputs; index++) {
                assertThat(type.pin(index).position().x()).isZero();
            }
            assertThat(type.pin(inputs).position().y()).isEqualTo(type.size().height());
            assertThat(type.pin(inputs + 1).position().x()).isEqualTo(type.size().width());
        }
        ComponentType wide = two.specializedFor(parameters(32, "2"));
        assertThat(wide.size()).isEqualTo(two.size());
        // M7 fix round: growing to 4 inputs keeps D0, D1 and Y where they were (only S, on the bottom edge, moves)
        assertThat(four.pin(0).position()).isEqualTo(two.pin(0).position());
        assertThat(four.pin(1).position()).isEqualTo(two.pin(1).position());
        assertThat(four.pin(5).position()).isEqualTo(two.pin(3).position());
    }

    @Test
    void everySelectPicksItsInputWithTwoAndFourInputs() {
        BlockHarness two = new BlockHarness(StandardComponentTypes.MUX, parameters(4, "2")).settle();
        two.set(0, 0x3).set(1, 0xC).settle();
        assertThat(two.output(3)).isEqualTo(Value.of(4, 0x3));
        two.set(2, 1).settle();
        assertThat(two.output(3)).isEqualTo(Value.of(4, 0xC));

        BlockHarness four = new BlockHarness(StandardComponentTypes.MUX, parameters(4, "4")).settle();
        for (int index = 0; index < 4; index++) {
            four.set(index, 0x5 + index * 3);
        }
        for (int select = 0; select < 4; select++) {
            four.set(4, select).settle();
            assertThat(four.output(5)).as("S = " + select).isEqualTo(Value.of(4, 0x5 + select * 3));
        }
    }

    @Test
    void anUndefinedUnselectedInputDoesNotMatterButAnUndefinedSelectedOneOrSelectGivesZ() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.MUX, parameters(4, "4")).settle();
        harness.set(0, 9).release(1).conflict(2).set(3, new Value(4, 0b0001, 0b0010, false)).set(4, 0).settle();
        assertThat(harness.output(5)).isEqualTo(Value.of(4, 9));
        for (int select = 1; select < 4; select++) {
            harness.set(4, select).settle();
            assertThat(harness.output(5)).as("S = " + select).isEqualTo(Value.highImpedance(4));
        }

        harness.set(4, 0).settle();
        assertThat(harness.output(5)).isEqualTo(Value.of(4, 9));
        harness.release(4).settle();
        assertThat(harness.output(5)).isEqualTo(Value.highImpedance(4));
        harness.conflict(4).settle();
        assertThat(harness.output(5)).isEqualTo(Value.highImpedance(4));
        harness.set(4, new Value(2, 0b01, 0b10, false)).settle();
        assertThat(harness.output(5)).isEqualTo(Value.highImpedance(4));
        harness.set(4, 0).settle();
        assertThat(harness.output(5)).isEqualTo(Value.of(4, 9));
    }

    @Test
    void oneAndThirtyTwoBitMuxesPassTheirValueUntouched() {
        BlockHarness single = new BlockHarness(StandardComponentTypes.MUX, parameters(1, "2")).settle();
        single.set(1, 1).set(2, 1).settle();
        assertThat(single.output(3)).isEqualTo(Value.ONE);

        BlockHarness wide = new BlockHarness(StandardComponentTypes.MUX, parameters(32, "2")).settle();
        wide.set(0, 0xFFFF_FFFFL).set(1, 0x8000_0001L).settle();
        assertThat(wide.output(3)).isEqualTo(Value.of(32, 0xFFFF_FFFFL));
        wide.set(2, 1).settle();
        assertThat(wide.output(3)).isEqualTo(Value.of(32, 0x8000_0001L));
    }

    @Test
    void theOutputChangesExactlyTheDelayAfterAnInputAndOnInitialization() {
        BlockHarness harness = new BlockHarness(StandardComponentTypes.MUX,
                Map.of(StandardComponentTypes.WIDTH, 4, "delay", 5)).advance(5);
        assertThat(harness.output(3)).isEqualTo(Value.of(4, 0));
        assertThat(harness.state()).isEqualTo(VisibleState.NONE);
        harness.set(0, 7).advance(4);
        assertThat(harness.output(3)).isEqualTo(Value.of(4, 0));
        harness.advance(1);
        assertThat(harness.output(3)).isEqualTo(Value.of(4, 7));
    }

    @Test
    void changingTheInputsOfAPlacedMuxKeepsItsPinsOnTheGridAndItsFirstInputsInPlace() {
        CircuitDocument document = new CircuitDocument();
        for (Rotation rotation : Rotation.values()) {
            Component placed = mux().newComponent(document.newComponentId(), new GridPoint(10, 20), rotation,
                    parameters(4, "2"), "M");
            document.add(placed);
            List<GridPoint> before = locations(placed);

            new ChangeParametersCommand(placed.id(), mux(), parameters(4, "4")).apply(document);
            Component changed = document.component(placed.id()).orElseThrow();
            ComponentType shaped = mux().forComponent(changed);
            assertThat(shaped.pinCount()).isEqualTo(6);
            List<GridPoint> after = locations(changed);
            assertThat(after.subList(0, 2)).as(rotation.name()).isEqualTo(before.subList(0, 2));
            for (PinSpec pin : shaped.pins()) {
                GridPoint position = pin.position();
                boolean onAnEdge = position.x() == 0 || position.x() == shaped.size().width()
                        || position.y() == 0 || position.y() == shaped.size().height();
                assertThat(onAnEdge).as(rotation + " " + pin.name()).isTrue();
                assertThat(position.x()).isBetween(0, shaped.size().width());
                assertThat(position.y()).isBetween(0, shaped.size().height());
            }
            assertThat(after).doesNotHaveDuplicates();
        }
    }

    private static List<GridPoint> locations(Component component) {
        ComponentType type = mux().forComponent(component);
        List<GridPoint> locations = new ArrayList<>();
        for (int index = 0; index < type.pinCount(); index++) {
            locations.add(mux().pinLocation(component, index));
        }
        return locations;
    }
}
