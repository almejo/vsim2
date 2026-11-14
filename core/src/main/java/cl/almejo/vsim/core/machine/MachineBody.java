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
 * the model: the ids of the body's pins come from the pin names, so a pin keeps its {@link DefinitionPin#source()}
 * when others are added or removed and the placed pieces' wires follow it.
 * <p>
 * Hito 1 draws every pin as one bit; «Como un bus» arrives in hito 3.
 */
public record MachineBody(CircuitFragment body, List<DefinitionPin> pinout, GridSize size) {

    /** The box is wider than a plain component's (6) so the current state's name fits between the pin names. */
    public static final int BOX_WIDTH = 8;
    /** The smallest box, also with only {@code RST} and {@code CLK}: room for the state's name and «S0 → S1». */
    public static final int MINIMUM_HEIGHT = 6;
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

    /** The interface pin types only, for their default parameters. */
    private static final ComponentTypeRegistry PIN_TYPES = StandardComponentTypes.registry();

    public MachineBody {
        Objects.requireNonNull(body, "body");
        pinout = List.copyOf(pinout);
        Objects.requireNonNull(size, "size");
    }

    /** The body, pinout and size of {@code machine}. */
    public static MachineBody of(StateMachine machine) {
        List<Side> inputs = new ArrayList<>();
        for (String name : machine.inputNames()) {
            inputs.add(new Side(name, idFor(INPUT_ID_PREFIX, name)));
        }
        inputs.add(new Side(MachineLimits.RESET, RESET_ID));
        inputs.add(new Side(MachineLimits.CLOCK, CLOCK_ID));
        List<Side> outputs = new ArrayList<>();
        for (String name : machine.outputNames()) {
            outputs.add(new Side(name, idFor(OUTPUT_ID_PREFIX, name)));
        }
        GridSize size = new GridSize(BOX_WIDTH, Math.max(MINIMUM_HEIGHT,
                PIN_SPACING * (Math.max(inputs.size(), outputs.size()) + 1)));
        List<DefinitionPin> pinout = new ArrayList<>();
        List<Component> components = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            Side input = inputs.get(index);
            pinout.add(new DefinitionPin(PinSpec.input(input.name(), 0, pinY(index), 1), input.id()));
            components.add(interfacePin(InterfacePins.INPUT_PIN, input, new GridPoint(0, BODY_ROW_HEIGHT * index)));
        }
        for (int index = 0; index < outputs.size(); index++) {
            Side output = outputs.get(index);
            pinout.add(new DefinitionPin(PinSpec.output(output.name(), BOX_WIDTH, pinY(index), 1), output.id()));
            components.add(interfacePin(InterfacePins.OUTPUT_PIN, output,
                    new GridPoint(BODY_OUTPUT_X, BODY_ROW_HEIGHT * index)));
        }
        return new MachineBody(new CircuitFragment(components, List.of(), List.of()), pinout, size);
    }

    private static int pinY(int index) {
        return FIRST_PIN_Y + PIN_SPACING * index;
    }

    private static Component interfacePin(String typeId, Side side, GridPoint position) {
        return PIN_TYPES.require(typeId).newComponent(side.id(), position, Rotation.R0, Map.of(), side.name());
    }

    /**
     * A body id from a pin name, one-to-one: an element id cannot hold {@code /} (it separates instance paths), so
     * {@code %} and {@code /} are escaped.
     */
    private static ElementId idFor(String prefix, String name) {
        return new ElementId(prefix + name.replace("%", "%25").replace("/", "%2F"));
    }

    /** A pin of one side: its name and the id of its interface pin in the body. */
    private record Side(String name, ElementId id) {
    }
}
