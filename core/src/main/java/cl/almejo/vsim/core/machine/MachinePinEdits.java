package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.logic.ExpressionParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The edits of a machine's pin list (Propiedades of the machine): rename, add, remove, move and «Como un bus». Each
 * is a pure function that returns a new {@link StateMachine}; the UI applies it as one {@code EditMachineCommand}, so
 * it is one undo step and the placed pieces' wires follow the pins by their ids.
 * <p>
 * A rename keeps the pin's id (its wires stay) and rewrites the name everywhere it is written: every condition, also
 * one that does not parse (from its tokens, so a half-typed {@code x +} becomes {@code a +}), and every Moore and
 * Mealy output. Renaming onto a name that is already a pin of the same side is allowed when one of the two is «sin
 * usar» (decision 1 of hito 3, 2026-11-17), and the pin that survives, with its id and so its wire, is always the
 * unused one: it is the wired pin the student kept and wants to use again. Renaming an unused {@code x} onto {@code y}
 * keeps {@code x}'s pin, now called {@code y}; renaming a used {@code a} onto an unused {@code b} keeps {@code b}'s
 * pin, and the conditions that held {@code a} now hold {@code b}. The other pin goes away and its wires come loose
 * (told on the status line, undone in one step). Both used is refused.
 */
public final class MachinePinEdits {

    private MachinePinEdits() {
    }

    /** The two pin lists of a machine. */
    public enum Side {
        INPUT, OUTPUT;

        Side other() {
            return this == INPUT ? OUTPUT : INPUT;
        }

        int limit() {
            return this == INPUT ? MachineLimits.MAXIMUM_INPUTS : MachineLimits.MAXIMUM_OUTPUTS;
        }

        PinNameProblem taken() {
            return this == INPUT ? PinNameProblem.TAKEN_INPUT : PinNameProblem.TAKEN_OUTPUT;
        }
    }

    /** Why a name cannot be given to a pin. */
    public enum PinNameProblem {
        /** Not a letter followed by letters, digits or {@code _}. */
        NOT_A_NAME,
        /** {@code CLK} or {@code RST}, which every machine has. */
        RESERVED,
        /** Already an input (and it cannot be merged). */
        TAKEN_INPUT,
        /** Already an output (and it cannot be merged). */
        TAKEN_OUTPUT,
        /** The side already has {@link MachineLimits its limit} of pins. */
        TOO_MANY
    }

    /**
     * Why {@code newName} cannot be the name of the pin {@code oldName} of {@code side} ({@code ""} for a new pin);
     * empty when it can.
     */
    public static Optional<PinNameProblem> nameProblem(StateMachine machine, Side side, String oldName,
                                                       String newName) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(oldName, "oldName");
        Objects.requireNonNull(newName, "newName");
        boolean adding = oldName.isEmpty();
        if (!ExpressionParser.isName(newName)) {
            return Optional.of(PinNameProblem.NOT_A_NAME);
        }
        if (MachineLimits.isReserved(newName)) {
            return Optional.of(PinNameProblem.RESERVED);
        }
        if (!adding && newName.equals(oldName)) {
            return Optional.empty();
        }
        if (machine.pin(side.other(), newName).isPresent()) {
            return Optional.of(side.other().taken());
        }
        if (machine.pin(side, newName).isPresent() && (adding || !canMerge(machine, oldName, newName))) {
            return Optional.of(side.taken());
        }
        if (adding && machine.pins(side).size() >= side.limit()) {
            return Optional.of(PinNameProblem.TOO_MANY);
        }
        return Optional.empty();
    }

    private static boolean canMerge(StateMachine machine, String oldName, String newName) {
        return MachinePinUses.of(machine, oldName).isUnused() || MachinePinUses.of(machine, newName).isUnused();
    }

    /**
     * {@code machine} with the pin {@code oldName} of {@code side} called {@code newName}, written so in every
     * condition and output; the pin keeps its id. When {@code newName} is another pin of that side (one of the two
     * «sin usar»), the used one of the two goes away and the unused one stays with its id (both unused: the renamed
     * one stays).
     *
     * @throws IllegalArgumentException when there is no such pin or {@link #nameProblem} refuses the name
     */
    public static StateMachine renamed(StateMachine machine, Side side, String oldName, String newName) {
        MachinePin renamedPin = requirePin(machine, side, oldName);
        nameProblem(machine, side, oldName, newName).ifPresent(problem -> {
            throw new IllegalArgumentException("cannot rename " + oldName + " to " + newName + ": " + problem);
        });
        if (newName.equals(oldName)) {
            return machine;
        }
        Optional<MachinePin> target = machine.pin(side, newName);
        boolean targetSurvives = target.isPresent() && !MachinePinUses.of(machine, oldName).isUnused();
        List<MachinePin> pins = new ArrayList<>();
        for (MachinePin pin : machine.pins(side)) {
            if (pin.equals(renamedPin)) {
                if (!targetSurvives) {
                    pins.add(pin.withName(newName));
                }
            } else if (!pin.name().equals(newName) || targetSurvives) {
                pins.add(pin);
            }
        }
        StateMachine rewritten = machine
                .withStates(machine.states().stream()
                        .map(state -> state.withOutputs(renamed(state.outputs(), oldName, newName))).toList())
                .withTransitions(machine.transitions().stream()
                        .map(transition -> transition
                                .withCondition(MachineConditions.renamed(transition.condition(), oldName, newName))
                                .withOutputs(renamed(transition.outputs(), oldName, newName))).toList());
        return withSide(rewritten, side, pins);
    }

    private static List<OutputAssignment> renamed(List<OutputAssignment> assignments, String oldName,
                                                  String newName) {
        return assignments.stream().map(assignment -> assignment.output().equals(oldName)
                ? new OutputAssignment(newName, assignment.value()) : assignment).toList();
    }

    /**
     * {@code machine} with a new pin {@code name} after the others of {@code side} («sin usar» until a condition or an
     * output uses it).
     *
     * @throws IllegalArgumentException when {@link #nameProblem} refuses the name
     */
    public static StateMachine added(StateMachine machine, Side side, String name) {
        nameProblem(machine, side, "", name).ifPresent(problem -> {
            throw new IllegalArgumentException("cannot add the pin " + name + ": " + problem);
        });
        List<MachinePin> pins = new ArrayList<>(machine.pins(side));
        pins.add(MachinePin.minted(name, pins.stream().map(MachinePin::id).toList()));
        return withSide(machine, side, pins);
    }

    /**
     * {@code machine} without the pin {@code name} of {@code side}.
     *
     * @throws IllegalArgumentException when there is no such pin or it is used ({@link MachinePinUses}): removing it
     *                                  would only bring it back on the next edit
     */
    public static StateMachine removed(StateMachine machine, Side side, String name) {
        MachinePin removedPin = requirePin(machine, side, name);
        MachinePinUses.Uses uses = MachinePinUses.of(machine, name);
        if (!uses.isUnused()) {
            throw new IllegalArgumentException("the pin " + name + " is used in " + uses.count() + " places");
        }
        return withSide(machine, side, machine.pins(side).stream().filter(pin -> !pin.equals(removedPin)).toList());
    }

    /**
     * {@code machine} with the pin at {@code fromIndex} of {@code side} moved to {@code toIndex} (the others shift);
     * every pin keeps its id, so the pieces' pins change place with their wires.
     *
     * @throws IndexOutOfBoundsException when an index is outside the side's pins
     */
    public static StateMachine moved(StateMachine machine, Side side, int fromIndex, int toIndex) {
        List<MachinePin> pins = new ArrayList<>(machine.pins(side));
        Objects.checkIndex(fromIndex, pins.size());
        Objects.checkIndex(toIndex, pins.size());
        pins.add(toIndex, pins.remove(fromIndex));
        return withSide(machine, side, pins);
    }

    /** {@code machine} with the pins of {@code side} shown as one bus ({@code asBus}) or one pin per name. */
    public static StateMachine asBus(StateMachine machine, Side side, boolean asBus) {
        return side == Side.INPUT ? machine.withBus(asBus, machine.outputsAsBus())
                : machine.withBus(machine.inputsAsBus(), asBus);
    }

    private static MachinePin requirePin(StateMachine machine, Side side, String name) {
        return machine.pin(side, name).orElseThrow(() -> new IllegalArgumentException(
                "the machine has no " + side + " pin " + name));
    }

    private static StateMachine withSide(StateMachine machine, Side side, List<MachinePin> pins) {
        return side == Side.INPUT ? machine.withPins(pins, machine.outputs()) : machine.withPins(machine.inputs(), pins);
    }
}
