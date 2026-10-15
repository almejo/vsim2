package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.Behavior;
import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterProblem;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The built-in components, with VSim's geometry converted to grid units. */
public final class StandardComponentTypes {

    public static final String AND2 = "and2";
    public static final String AND3 = "and3";
    public static final String AND4 = "and4";
    public static final String OR2 = "or2";
    public static final String OR3 = "or3";
    public static final String OR4 = "or4";
    public static final String XOR2 = "xor2";
    public static final String XOR3 = "xor3";
    public static final String XOR4 = "xor4";
    public static final String NAND2 = "nand2";
    public static final String NAND3 = "nand3";
    public static final String NAND4 = "nand4";
    public static final String NOR2 = "nor2";
    public static final String NOR3 = "nor3";
    public static final String NOR4 = "nor4";
    public static final String NOT = "not";
    public static final String FLIP_FLOP_DATA = "flip-flop-data";
    public static final String TRI_STATE = "tri-state";
    public static final String CLOCK = "clock";
    public static final String SWITCH = "switch";
    public static final String CONSTANT = "constant";
    public static final String LED = "led";
    public static final String SEVEN_SEGMENTS_DISPLAY = "seven-segments-display";
    public static final String SEVEN_SEGMENTS_DISPLAY_DOUBLE = "seven-segments-display-double";
    public static final String TIME_DIAGRAM = "time-diagram";
    public static final String INPUT_PIN = InterfacePins.INPUT_PIN;
    public static final String OUTPUT_PIN = InterfacePins.OUTPUT_PIN;
    public static final String BUS_SPLITTER = "bus-splitter";
    public static final String BUS_JOINER = "bus-joiner";
    /** The parameter that sets a bus component's width in bits. */
    public static final String WIDTH = "width";

    private static final GridSize GATE_SIZE = new GridSize(4, 4);
    private static final ParameterSpec DELAY = ParameterSpec.integer("delay", 1, 1, 1_000_000);
    private static final ParameterSpec BUS_WIDTH = ParameterSpec.integer(WIDTH, 8, 2, 32);

    private StandardComponentTypes() {
    }

    public static ComponentTypeRegistry registry() {
        ComponentTypeRegistry registry = new ComponentTypeRegistry();
        registerAll(registry);
        return registry;
    }

    public static void registerAll(ComponentTypeRegistry registry) {
        for (LogicOperation operation : LogicOperation.values()) {
            for (int inputCount = 2; inputCount <= 4; inputCount++) {
                registry.register(logicGate(operation, inputCount));
            }
        }
        registry.register(new ComponentType(NOT, ComponentCategory.BASIC, GATE_SIZE,
                List.of(PinSpec.input("in", 0, 2), PinSpec.output("out", 4, 2)),
                List.of(DELAY),
                parameters -> new NotBehavior((Integer) parameters.get("delay"))));
        registry.register(new ComponentType(FLIP_FLOP_DATA, ComponentCategory.BASIC, GATE_SIZE,
                List.of(PinSpec.input("d", 0, 2), PinSpec.output("q", 4, 2), PinSpec.input("clock", 2, 4)),
                List.of(DELAY),
                parameters -> new FlipFlopDataBehavior((Integer) parameters.get("delay"))));
        registry.register(new ComponentType(TRI_STATE, ComponentCategory.BASIC, GATE_SIZE,
                List.of(PinSpec.input("in", 0, 2), PinSpec.output("out", 4, 2), PinSpec.input("enable", 2, 4)),
                List.of(DELAY),
                parameters -> new TriStateBehavior((Integer) parameters.get("delay"))));
        registry.register(new ComponentType(CLOCK, ComponentCategory.INPUT_OUTPUT, new GridSize(2, 2),
                List.of(PinSpec.output("out", 2, 2)),
                List.of(ParameterSpec.integer("time-up", 1000, 1, 1_000_000_000),
                        ParameterSpec.integer("time-down", 1000, 1, 1_000_000_000)),
                parameters -> new ClockBehavior((Integer) parameters.get("time-up"), (Integer) parameters.get("time-down"))));
        registry.register(new ComponentType(SWITCH, ComponentCategory.INPUT_OUTPUT, new GridSize(4, 2),
                List.of(PinSpec.output("out", 4, 1)),
                List.of(ParameterSpec.choice("value", "1", List.of("1", "0", "Z"))),
                parameters -> new SwitchBehavior(SwitchBehavior.parse((String) parameters.get("value")))));
        registry.register(ComponentType.parameterized(CONSTANT, ComponentCategory.INPUT_OUTPUT,
                List.of(ParameterSpec.integer(WIDTH, 4, 1, ConstantValue.MAXIMUM_WIDTH),
                        ParameterSpec.text("value", "0", ConstantValue::normalize),
                        ParameterSpec.choice("format", ConstantValue.AUTO,
                                List.of(ConstantValue.AUTO, ConstantValue.BINARY, ConstantValue.HEX,
                                        ConstantValue.DECIMAL))),
                parameters -> constantShape((Integer) parameters.get(WIDTH), (String) parameters.get("format")),
                StandardComponentTypes::checkConstant,
                parameters -> {
                    int width = (Integer) parameters.get(WIDTH);
                    return new ConstantBehavior(Value.of(width,
                            ConstantValue.parse((String) parameters.get("value"))));
                }));
        registry.register(new ComponentType(LED, ComponentCategory.INPUT_OUTPUT, new GridSize(2, 3),
                List.of(PinSpec.input("in", 1, 3)),
                List.of(ParameterSpec.choice("color", "red",
                        List.of("red", "green", "yellow", "blue", "orange", "white", "purple", "cyan"))),
                parameters -> new LedBehavior()));
        registry.register(sevenSegments(SEVEN_SEGMENTS_DISPLAY, 4));
        registry.register(sevenSegments(SEVEN_SEGMENTS_DISPLAY_DOUBLE, 8));
        List<PinSpec> diagramPins = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            diagramPins.add(PinSpec.input("in" + index, 0, index * 2));
        }
        registry.register(new ComponentType(TIME_DIAGRAM, ComponentCategory.DEBUG, new GridSize(4, 6),
                diagramPins,
                List.of(ParameterSpec.integer("sample-interval", 150, 1, 1_000_000),
                        ParameterSpec.integer("history", 200, 10, 10_000)),
                parameters -> new TimeDiagramBehavior(4, (Integer) parameters.get("sample-interval"),
                        (Integer) parameters.get("history"))));
        registry.register(ComponentType.parameterized(INPUT_PIN, ComponentCategory.INTERFACE,
                List.of(ParameterSpec.choice("value", "0", List.of("1", "0", "Z")), InterfacePins.WIDTH_PARAMETER),
                parameters -> new ComponentShape(new GridSize(2, 2),
                        List.of(PinSpec.output("pin", 2, 1, (Integer) parameters.get(InterfacePins.WIDTH)))),
                parameters -> new SwitchBehavior(SwitchBehavior.parse((String) parameters.get("value"),
                        (Integer) parameters.get(InterfacePins.WIDTH)))));
        registry.register(ComponentType.parameterized(OUTPUT_PIN, ComponentCategory.INTERFACE,
                List.of(InterfacePins.WIDTH_PARAMETER),
                parameters -> new ComponentShape(new GridSize(2, 2),
                        List.of(PinSpec.input("pin", 0, 1, (Integer) parameters.get(InterfacePins.WIDTH)))),
                parameters -> newValueProbe()));
        registry.register(ComponentType.parameterized(BUS_SPLITTER, ComponentCategory.BASIC, List.of(BUS_WIDTH),
                parameters -> busShape((Integer) parameters.get(WIDTH), false),
                parameters -> new BusSplitterBehavior((Integer) parameters.get(WIDTH))));
        registry.register(ComponentType.parameterized(BUS_JOINER, ComponentCategory.BASIC, List.of(BUS_WIDTH),
                parameters -> busShape((Integer) parameters.get(WIDTH), true),
                parameters -> new BusJoinerBehavior((Integer) parameters.get(WIDTH))));
    }

    /**
     * The box of a constant: height 2, as wide as its longest possible text needs (so the shape never depends on the
     * value), the bus pin on the right edge, in the middle.
     */
    private static ComponentShape constantShape(int width, String format) {
        int boxWidth = Math.max(4, (ConstantValue.longestDisplay(width, format) * 3 + 3) / 4 + 2);
        return new ComponentShape(new GridSize(boxWidth, 2), List.of(PinSpec.output("out", boxWidth, 1, width)));
    }

    /** The value must be a number and fit the width. A width out of range is left to the width parameter itself. */
    private static Optional<ParameterProblem> checkConstant(Map<String, Object> raw) {
        Object rawWidth = raw == null ? null : raw.get(WIDTH);
        int width;
        try {
            width = rawWidth == null ? 4 : rawWidth instanceof Number number ? number.intValue()
                    : Integer.parseInt(rawWidth.toString().trim());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        if (width < 1 || width > ConstantValue.MAXIMUM_WIDTH) {
            return Optional.empty();
        }
        Object rawValue = raw == null ? null : raw.get("value");
        String text = rawValue == null ? "0" : rawValue.toString();
        if (!ConstantValue.isValidText(text)) {
            return Optional.of(new ParameterProblem("value", "constant.invalidValue", List.of()));
        }
        if (!ConstantValue.fitsText(text, width)) {
            return Optional.of(new ParameterProblem("value", "constant.doesNotFit", List.of(width)));
        }
        return Optional.empty();
    }

    /** A passive behavior that only shows the value at its single input, like an LED or an output pin. */
    public static Behavior newValueProbe() {
        return new LedBehavior();
    }

    private static ComponentType logicGate(LogicOperation operation, int inputCount) {
        List<PinSpec> pins = new ArrayList<>();
        int separationInPixels = 32 / (inputCount - 1);
        for (int index = 0; index < inputCount; index++) {
            pins.add(PinSpec.input("in" + index, 0, Math.floorDiv(index * separationInPixels, 8)));
        }
        pins.add(PinSpec.output("out", 4, 2));
        return new ComponentType(operation.typeId(inputCount), ComponentCategory.BASIC, GATE_SIZE, pins,
                List.of(DELAY),
                parameters -> new LogicGateBehavior(operation, inputCount, (Integer) parameters.get("delay")));
    }

    private static ComponentType sevenSegments(String id, int pinCount) {
        List<PinSpec> pins = new ArrayList<>();
        for (int index = 0; index < pinCount; index++) {
            pins.add(PinSpec.input("in" + index, pinCount - index, 0));
        }
        return new ComponentType(id, ComponentCategory.INPUT_OUTPUT, new GridSize(pinCount, 6), pins, List.of(),
                parameters -> new SevenSegmentsDisplayBehavior(pinCount));
    }

    /**
     * A splitter of {@code width} bits: a 2 x width box, the bus pin at (0, 0) and bits b0..b(width-1) at (2, 1)..
     * (2, width). The joiner mirrors it: bits in at (0, 1)..(0, width), the bus out at (2, 0).
     */
    private static ComponentShape busShape(int width, boolean joining) {
        List<PinSpec> pins = new ArrayList<>();
        pins.add(new PinSpec("bus", new GridPoint(joining ? 2 : 0, 0),
                joining ? PinDirection.OUTPUT : PinDirection.INPUT, width));
        for (int bit = 0; bit < width; bit++) {
            pins.add(new PinSpec("b" + bit, new GridPoint(joining ? 0 : 2, bit + 1),
                    joining ? PinDirection.INPUT : PinDirection.OUTPUT, 1));
        }
        return new ComponentShape(new GridSize(2, width), pins);
    }
}
