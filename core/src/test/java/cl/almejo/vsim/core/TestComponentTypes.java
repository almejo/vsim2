package cl.almejo.vsim.core;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.BehaviorContext;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.value.Value;

import java.util.List;
import java.util.function.UnaryOperator;

/** Minimal component types for engine and netlist tests. */
public final class TestComponentTypes {

    public static final String SOURCE = "test-source";
    public static final String BUFFER = "test-buffer";
    public static final String INVERTER = "test-inverter";
    public static final String WIDE_PROBE = "test-wide-probe";

    private TestComponentTypes() {
    }

    public static ComponentTypeRegistry registry() {
        ComponentTypeRegistry registry = new ComponentTypeRegistry();
        registry.register(new ComponentType(SOURCE, ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 1)),
                List.of(ParameterSpec.choice("value", "0", List.of("0", "1", "Z"))),
                parameters -> new SourceBehavior(parse((String) parameters.get("value")))));
        registry.register(unary(BUFFER, value -> value));
        // Treats Z as 0 (like VSim's NOT) so that a test inverter wired to itself starts oscillating.
        registry.register(unary(INVERTER, value -> value.isFullyHighImpedance() ? Value.ONE : value.not()));
        registry.register(new ComponentType(WIDE_PROBE, ComponentCategory.DEBUG, new GridSize(2, 2),
                List.of(new PinSpec("in", GridPoint.ORIGIN, PinDirection.INPUT, 8)),
                List.of(),
                parameters -> (context, pinIndex) -> {
                }));
        return registry;
    }

    public static Value parse(String text) {
        return switch (text) {
            case "0" -> Value.ZERO;
            case "1" -> Value.ONE;
            case "Z" -> Value.HIGH_IMPEDANCE;
            default -> throw new IllegalArgumentException(text);
        };
    }

    private static ComponentType unary(String id, UnaryOperator<Value> function) {
        return new ComponentType(id, ComponentCategory.BASIC, new GridSize(2, 2),
                List.of(PinSpec.input("in", 0, 1), PinSpec.output("out", 2, 1)),
                List.of(ParameterSpec.integer("delay", 1, 1, 1_000_000)),
                parameters -> new UnaryBehavior(function, (Integer) parameters.get("delay")));
    }

    private record UnaryBehavior(UnaryOperator<Value> function, int delay) implements Behavior {
        @Override
        public void initialize(BehaviorContext context) {
            inputChanged(context, 0);
        }

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
            context.drive(1, function.apply(context.input(0)), delay);
        }
    }

    private static final class SourceBehavior implements Behavior {
        private Value value;

        SourceBehavior(Value value) {
            this.value = value;
        }

        @Override
        public void initialize(BehaviorContext context) {
            context.drive(0, value, 0);
        }

        @Override
        public void inputChanged(BehaviorContext context, int pinIndex) {
        }

        @Override
        public boolean isInteractive() {
            return true;
        }

        @Override
        public void interact(BehaviorContext context) {
            value = value.equals(Value.ONE) ? Value.ZERO : Value.ONE;
            context.drive(0, value, 0);
        }

        @Override
        public VisibleState visibleState() {
            return new VisibleState.SwitchState(value);
        }
    }
}
