package cl.almejo.vsim.core.machine;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.InterfacePins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The generated outside of a machine (spec §2.2): one Pin de entrada per input, then {@code RST}, then {@code CLK}
 * on the left, one Pin de salida per output on the right, and a body holding only those interface pins, so the
 * pinout, the library, «Actualizar componente» and the painters work as for any user component. A pure function of
 * the model: the ids of the body's pins come from the {@link MachinePin#id() pin ids}, so a pin keeps its
 * {@link DefinitionPin#source()} when others are added, removed or moved and when it is renamed, and the placed
 * pieces' wires follow it.
 * <p>
 * «Como un bus» (hito 3) makes a side one pin, {@link #busName in} or {@link #busName out}, as wide as its names, bit
 * k being the k-th name; a bus side without names has no pin.
 */
public record MachineBody(CircuitFragment body, List<DefinitionPin> pinout, GridSize size) {

    /**
     * The box is twice a plain component's width (6) so the face's miniature of up to four states fits between the
     * pin names (decision 1 of hito 2, 2026-11-15). It never depends on the number of states.
     */
    public static final int BOX_WIDTH = 12;
    /** The smallest box, also with only {@code RST} and {@code CLK}: the name, the miniature and «S0 → S1». */
    public static final int MINIMUM_HEIGHT = 8;
    /** Pins 2 grid units apart from y = 2, like {@code DefinitionBuilder}'s boxes. */
    public static final int PIN_SPACING = 2;
    public static final int FIRST_PIN_Y = 2;

    /** Where the body's interface pins sit (never shown: the machine's tab shows the diagram). */
    private static final int BODY_OUTPUT_X = 10;
    private static final int BODY_ROW_HEIGHT = 4;

    private static final String INPUT_ID_PREFIX = "machine-input-";
    private static final String OUTPUT_ID_PREFIX = "machine-output-";
    private static final ElementId RESET_ID = new ElementId("machine-reset");
    private static final ElementId CLOCK_ID = new ElementId("machine-clock");
    /**
     * The sources of the bus pins. A pin whose id is {@code bus} makes the same source when not a bus, so its wire
     * follows the bus when the side becomes one (and back): harmless, and the width problem shows it.
     */
    private static final ElementId INPUT_BUS_ID = new ElementId(INPUT_ID_PREFIX + "bus");
    private static final ElementId OUTPUT_BUS_ID = new ElementId(OUTPUT_ID_PREFIX + "bus");

    /** The name of the input bus pin (spec §1 «Como un bus»), unless an output already has it ({@link #busName}). */
    public static final String BUS_INPUT_NAME = "in";
    /** The name of the output bus pin, unless an input already has it ({@link #busName}). */
    public static final String BUS_OUTPUT_NAME = "out";

    /** The interface pin types only, for their default parameters. */
    private static final ComponentTypeRegistry PIN_TYPES = StandardComponentTypes.registry();

    public MachineBody {
        Objects.requireNonNull(body, "body");
        pinout = List.copyOf(pinout);
        Objects.requireNonNull(size, "size");
    }

    /** The body, pinout and size of {@code machine}. */
    public static MachineBody of(StateMachine machine) {
        List<Side> inputs = new ArrayList<>(side(machine, MachinePinEdits.Side.INPUT, INPUT_ID_PREFIX, INPUT_BUS_ID));
        inputs.add(new Side(MachineLimits.RESET, RESET_ID, 1));
        inputs.add(new Side(MachineLimits.CLOCK, CLOCK_ID, 1));
        List<Side> outputs = side(machine, MachinePinEdits.Side.OUTPUT, OUTPUT_ID_PREFIX, OUTPUT_BUS_ID);
        GridSize size = new GridSize(BOX_WIDTH, Math.max(MINIMUM_HEIGHT,
                PIN_SPACING * (Math.max(inputs.size(), outputs.size()) + 1)));
        List<DefinitionPin> pinout = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            Side input = inputs.get(index);
            pinout.add(new DefinitionPin(PinSpec.input(input.name(), 0, pinY(index), input.width()), input.id()));
            components.add(interfacePin(InterfacePins.INPUT_PIN, input, new GridPoint(0, BODY_ROW_HEIGHT * index)));
        }
        for (int index = 0; index < outputs.size(); index++) {
            Side output = outputs.get(index);
            pinout.add(new DefinitionPin(PinSpec.output(output.name(), BOX_WIDTH, pinY(index), output.width()),
                    output.id()));
            components.add(interfacePin(InterfacePins.OUTPUT_PIN, output,
                    new GridPoint(BODY_OUTPUT_X, BODY_ROW_HEIGHT * index)));
        }
        return new MachineBody(new CircuitFragment(components, List.of(), List.of()), pinout, size);
    }

    /**
     * The pins of one side of {@code machine}: one per name, or one bus as wide as the names (none without names).
     */
    private static List<Side> side(StateMachine machine, MachinePinEdits.Side side, String prefix, ElementId busId) {
        List<MachinePin> pins = machine.pins(side);
        if (isBus(machine, side)) {
            return pins.isEmpty() ? List.of() : List.of(new Side(busName(machine, side), busId, pins.size()));
        }
        List<Side> sides = new ArrayList<>();
        for (MachinePin pin : pins) {
            sides.add(new Side(pin.name(), idFor(prefix, pin.id()), 1));
        }
        return sides;
    }

    /** Whether {@code machine} shows the pins of {@code side} as one bus. */
    public static boolean isBus(StateMachine machine, MachinePinEdits.Side side) {
        return side == MachinePinEdits.Side.INPUT ? machine.inputsAsBus() : machine.outputsAsBus();
    }

    /**
     * The name of the bus pin of {@code side}: {@link #BUS_INPUT_NAME} or {@link #BUS_OUTPUT_NAME}, or, when a pin of
     * the other side (not a bus itself) already has that name, the first of {@code in2}, {@code in3}… that none has,
     * so the pinout's names stay unique.
     */
    public static String busName(StateMachine machine, MachinePinEdits.Side side) {
        String base = side == MachinePinEdits.Side.INPUT ? BUS_INPUT_NAME : BUS_OUTPUT_NAME;
        MachinePinEdits.Side other = side == MachinePinEdits.Side.INPUT ? MachinePinEdits.Side.OUTPUT
                : MachinePinEdits.Side.INPUT;
        if (isBus(machine, other)) {
            return base;
        }
        String name = base;
        for (int suffix = 2; machine.pin(other, name).isPresent(); suffix++) {
            name = base + suffix;
        }
        return name;
    }

    private static int pinY(int index) {
        return FIRST_PIN_Y + PIN_SPACING * index;
    }

    private static Component interfacePin(String typeId, Side side, GridPoint position) {
        Map<String, Object> parameters = side.width() == 1 ? Map.of() : Map.of(InterfacePins.WIDTH, side.width());
        return PIN_TYPES.require(typeId).newComponent(side.id(), position, Rotation.R0, parameters, side.name());
    }

    /**
     * A body id from a pin id, one-to-one: an element id cannot hold {@code /} (it separates instance paths), so
     * {@code %} and {@code /} are escaped.
     */
    private static ElementId idFor(String prefix, String name) {
        return new ElementId(prefix + name.replace("%", "%25").replace("/", "%2F"));
    }

    /** A pin of one side: its name, the id of its interface pin in the body and its width. */
    private record Side(String name, ElementId id, int width) {
    }
}
