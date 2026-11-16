package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Where a machine's pins are in its native type ({@link StateMachineTypes}), found by name and direction, never by
 * position: the behaviour and the waveform channels read them through it. A side shown «Como un bus» has its bus pin
 * ({@code inputBus} / {@code outputBus}, bit k being the k-th name of that side) and no pin per name; otherwise
 * {@code inputPins} / {@code outputPins} give each name its pin. {@code reset}, {@code clock} and the hidden
 * {@code state} pin are always there.
 * <p>
 * {@link #of} fails loudly ({@link IllegalStateException}) when the pinout disagrees with the machine: a missing pin,
 * one too many, the wrong direction or width. A machine's pinout is always generated from its model
 * ({@link MachineBody}), so that is a bug to see, not a state to guess around.
 */
public record MachinePinLayout(OptionalInt inputBus, Map<String, Integer> inputPins, int reset, int clock,
                               OptionalInt outputBus, Map<String, Integer> outputPins, int state) {

    public MachinePinLayout {
        Objects.requireNonNull(inputBus, "inputBus");
        inputPins = Map.copyOf(inputPins);
        Objects.requireNonNull(outputBus, "outputBus");
        outputPins = Map.copyOf(outputPins);
    }

    /**
     * The layout of {@code machine}'s pins in {@code nativeType}.
     *
     * @throws IllegalStateException when the type's pins are not exactly the machine's
     */
    public static MachinePinLayout of(ComponentType nativeType, StateMachine machine) {
        Objects.requireNonNull(nativeType, "nativeType");
        Objects.requireNonNull(machine, "machine");
        Finder finder = new Finder(nativeType, machine);
        OptionalInt inputBus = finder.bus(MachinePinEdits.Side.INPUT, PinDirection.INPUT);
        Map<String, Integer> inputPins = finder.perName(MachinePinEdits.Side.INPUT, PinDirection.INPUT);
        int reset = finder.require(MachineLimits.RESET, PinDirection.INPUT, 1);
        int clock = finder.require(MachineLimits.CLOCK, PinDirection.INPUT, 1);
        OptionalInt outputBus = finder.bus(MachinePinEdits.Side.OUTPUT, PinDirection.OUTPUT);
        Map<String, Integer> outputPins = finder.perName(MachinePinEdits.Side.OUTPUT, PinDirection.OUTPUT);
        int state = finder.require(StateMachineTypes.STATE_PIN_NAME, PinDirection.OUTPUT,
                StateMachineTypes.stateWidth(machine));
        finder.requireNoOthers();
        return new MachinePinLayout(inputBus, inputPins, reset, clock, outputBus, outputPins, state);
    }

    /** Finds the expected pins one by one and remembers which it found. */
    private static final class Finder {

        private final ComponentType type;
        private final StateMachine machine;
        private final Set<Integer> found = new HashSet<>();

        Finder(ComponentType type, StateMachine machine) {
            this.type = type;
            this.machine = machine;
        }

        OptionalInt bus(MachinePinEdits.Side side, PinDirection direction) {
            int names = machine.pins(side).size();
            if (!MachineBody.isBus(machine, side) || names == 0) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(require(MachineBody.busName(machine, side), direction, names));
        }

        Map<String, Integer> perName(MachinePinEdits.Side side, PinDirection direction) {
            if (MachineBody.isBus(machine, side)) {
                return Map.of();
            }
            Map<String, Integer> pins = new LinkedHashMap<>();
            for (MachinePin pin : machine.pins(side)) {
                pins.put(pin.name(), require(pin.name(), direction, 1));
            }
            return pins;
        }

        int require(String name, PinDirection direction, int width) {
            List<PinSpec> pins = type.pins();
            for (int index = 0; index < pins.size(); index++) {
                PinSpec pin = pins.get(index);
                if (pin.name().equals(name) && pin.direction() == direction) {
                    if (pin.width() != width) {
                        throw new IllegalStateException(type.id() + ": the pin " + name + " is " + pin.width()
                                + " bits wide, the machine needs " + width);
                    }
                    found.add(index);
                    return index;
                }
            }
            throw new IllegalStateException(type.id() + " has no " + direction + " pin " + name
                    + ", which the machine needs");
        }

        void requireNoOthers() {
            List<String> others = new ArrayList<>();
            for (int index = 0; index < type.pinCount(); index++) {
                if (!found.contains(index)) {
                    others.add(type.pin(index).name());
                }
            }
            if (!others.isEmpty()) {
                throw new IllegalStateException(type.id() + " has pins the machine does not: " + others);
            }
        }
    }
}
