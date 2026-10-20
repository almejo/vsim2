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
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
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
    public static final String ADDER = "adder";
    public static final String ALU = "alu";
    public static final String COMPARATOR = "comparator";
    /**
     * The comparator's «Números» parameter: {@link #SIGNEDNESS_UNSIGNED} («Sin signo») or {@link #SIGNEDNESS_SIGNED}
     * («Con signo»). Not to be confused with the «Mostrar como» format {@link ConstantValue#SIGNED}.
     */
    public static final String SIGNEDNESS = "signedness";
    public static final String SIGNEDNESS_UNSIGNED = "unsigned";
    public static final String SIGNEDNESS_SIGNED = "signed";
    public static final String MUX = "mux";
    public static final String DECODER = "decoder";
    public static final String REGISTER = "register";
    public static final String COUNTER = "counter";
    /** «Túnel (entrada)»: gives its wire's signal a name (see {@link Tunnels}). */
    public static final String TUNNEL_ENTRANCE = "tunnel-in";
    /** «Túnel (salida)»: puts the signal of its name on its wire. */
    public static final String TUNNEL_EXIT = "tunnel-out";
    /** The name of a 7-segment display's bus input. */
    public static final String DISPLAY_BUS_PIN = "bus";
    /** The parameter that sets a bus component's width in bits. */
    public static final String WIDTH = "width";

    private static final GridSize GATE_SIZE = new GridSize(4, 4);
    /** «Retardo» of gates and blocks: simulation units from an input change to the outputs. */
    static final ParameterSpec DELAY = ParameterSpec.integer("delay", 1, 1, 1_000_000);
    private static final ParameterSpec BUS_WIDTH = ParameterSpec.integer(WIDTH, 8, 2, 32);
    /** «Entradas» of the mux: 2 or 4 data inputs. */
    private static final ParameterSpec MUX_INPUTS = ParameterSpec.choice("inputs", "2", List.of("2", "4"));
    /** «Bits de entrada» of the decoder: 1..4 address bits, so 2..16 outputs. */
    private static final ParameterSpec DECODER_INPUT_BITS = ParameterSpec.integer("inputBits", 2, 1, 4);
    /**
     * Tunnels only join nets: the flattener leaves them out, so this behaviour never runs; it exists because every
     * type has one.
     */
    private static final Behavior TUNNEL_BEHAVIOR = (context, pinIndex) -> {
    };

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
                List.of(ParameterSpec.integer(WIDTH, 8, 1, ConstantValue.MAXIMUM_WIDTH),
                        ParameterSpec.text("value", "0", ConstantValue::normalize),
                        ConstantValue.FORMAT_PARAMETER),
                parameters -> constantShape((Integer) parameters.get(WIDTH), (String) parameters.get("format")),
                StandardComponentTypes::checkConstant,
                parameters -> {
                    int width = (Integer) parameters.get(WIDTH);
                    return new ConstantBehavior(Value.of(width,
                            ConstantValue.bitsOf((String) parameters.get("value"), width).orElseThrow()));
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
                List.of(ParameterSpec.choice("value", "0", List.of("1", "0", "Z")), InterfacePins.WIDTH_PARAMETER,
                        ConstantValue.FORMAT_PARAMETER),
                parameters -> new ComponentShape(new GridSize(2, 2),
                        List.of(PinSpec.output("pin", 2, 1, (Integer) parameters.get(InterfacePins.WIDTH)))),
                parameters -> new SwitchBehavior(SwitchBehavior.parse((String) parameters.get("value"),
                        (Integer) parameters.get(InterfacePins.WIDTH)))));
        registry.register(ComponentType.parameterized(OUTPUT_PIN, ComponentCategory.INTERFACE,
                List.of(InterfacePins.WIDTH_PARAMETER, ConstantValue.FORMAT_PARAMETER),
                parameters -> new ComponentShape(new GridSize(2, 2),
                        List.of(PinSpec.input("pin", 0, 1, (Integer) parameters.get(InterfacePins.WIDTH)))),
                parameters -> newValueProbe()));
        registry.register(ComponentType.parameterized(BUS_SPLITTER, ComponentCategory.BASIC, List.of(BUS_WIDTH),
                parameters -> busShape((Integer) parameters.get(WIDTH), false),
                parameters -> new BusSplitterBehavior((Integer) parameters.get(WIDTH))));
        registry.register(ComponentType.parameterized(BUS_JOINER, ComponentCategory.BASIC, List.of(BUS_WIDTH),
                parameters -> busShape((Integer) parameters.get(WIDTH), true),
                parameters -> new BusJoinerBehavior((Integer) parameters.get(WIDTH))));
        for (TunnelKind kind : TunnelKind.values()) {
            registry.register(ComponentType.parameterized(kind.typeId(), ComponentCategory.INPUT_OUTPUT,
                    Tunnels.parameters(kind), Tunnels::shape, parameters -> TUNNEL_BEHAVIOR));
        }
        registerBlocks(registry);
    }

    /**
     * The «Bloques» (M7): N-bit parts with «Ancho (bits)» ({@link BlockSignals#WIDTH}) and «Retardo»; data in on the
     * left, results out on the right, control on the top and bottom edges. Their size never depends on the width.
     */
    private static void registerBlocks(ComponentTypeRegistry registry) {
        registry.register(ComponentType.parameterized(ADDER, ComponentCategory.BLOCKS,
                List.of(BlockSignals.WIDTH, DELAY),
                parameters -> adderShape((Integer) parameters.get(WIDTH)),
                parameters -> new AdderBehavior((Integer) parameters.get(WIDTH), (Integer) parameters.get("delay"))));
        registry.register(ComponentType.parameterized(ALU, ComponentCategory.BLOCKS,
                List.of(BlockSignals.WIDTH, DELAY),
                parameters -> aluShape((Integer) parameters.get(WIDTH)),
                parameters -> new AluBehavior((Integer) parameters.get(WIDTH), (Integer) parameters.get("delay"))));
        registry.register(ComponentType.parameterized(COMPARATOR, ComponentCategory.BLOCKS,
                List.of(BlockSignals.WIDTH, ParameterSpec.choice(SIGNEDNESS, SIGNEDNESS_UNSIGNED,
                        List.of(SIGNEDNESS_UNSIGNED, SIGNEDNESS_SIGNED)), DELAY),
                parameters -> comparatorShape((Integer) parameters.get(WIDTH)),
                parameters -> new ComparatorBehavior(SIGNEDNESS_SIGNED.equals(parameters.get(SIGNEDNESS)),
                        (Integer) parameters.get("delay"))));
        registry.register(ComponentType.parameterized(MUX, ComponentCategory.BLOCKS,
                List.of(BlockSignals.WIDTH, MUX_INPUTS, DELAY),
                parameters -> muxShape(Integer.parseInt((String) parameters.get("inputs")),
                        (Integer) parameters.get(WIDTH)),
                parameters -> new MuxBehavior(Integer.parseInt((String) parameters.get("inputs")),
                        (Integer) parameters.get(WIDTH), (Integer) parameters.get("delay"))));
        registry.register(ComponentType.parameterized(DECODER, ComponentCategory.BLOCKS,
                List.of(DECODER_INPUT_BITS, DELAY),
                parameters -> decoderShape((Integer) parameters.get("inputBits")),
                parameters -> new DecoderBehavior((Integer) parameters.get("inputBits"),
                        (Integer) parameters.get("delay"))));
        registry.register(ComponentType.parameterized(REGISTER, ComponentCategory.BLOCKS,
                List.of(BlockSignals.WIDTH, DELAY, ConstantValue.FORMAT_PARAMETER),
                parameters -> registerShape((Integer) parameters.get(WIDTH)),
                parameters -> new RegisterBehavior((Integer) parameters.get(WIDTH),
                        (Integer) parameters.get("delay"))));
        registry.register(ComponentType.parameterized(COUNTER, ComponentCategory.BLOCKS,
                List.of(BlockSignals.WIDTH, DELAY, ConstantValue.FORMAT_PARAMETER),
                parameters -> counterShape((Integer) parameters.get(WIDTH)),
                parameters -> new CounterBehavior((Integer) parameters.get(WIDTH),
                        (Integer) parameters.get("delay"))));
    }

    /**
     * A 6 x (2k + 4) trapezoid: D0..D(k-1)[N] on the left at y = 3, 5, ..., S[log2 k] in the middle of the bottom edge
     * and Y[N] at (6, 4), the middle of the right edge with 2 inputs. D0, D1 and Y stay put when the inputs go from 2
     * to 4 (S, on the bottom edge, has to move), so their wires stay connected.
     */
    private static ComponentShape muxShape(int inputs, int width) {
        int height = 2 * inputs + 4;
        List<PinSpec> pins = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            pins.add(PinSpec.input("D" + index, 0, 3 + 2 * index, width));
        }
        pins.add(PinSpec.input("S", 3, height, Integer.numberOfTrailingZeros(inputs)));
        pins.add(PinSpec.output("Y", 6, 4, width));
        return new ComponentShape(new GridSize(6, height), pins);
    }

    /**
     * A 6 x (2 * 2^k + 2) rectangle: A[k] at (0, 3) on the left edge, EN in the middle of the bottom edge and
     * Y0..Y(2^k-1) on the right edge at y = 2, 4, ... A and the first outputs stay put when «Bits de entrada» changes
     * (EN, on the bottom edge, has to move).
     */
    private static ComponentShape decoderShape(int inputBits) {
        int outputs = 1 << inputBits;
        int height = 2 * outputs + 2;
        List<PinSpec> pins = new ArrayList<>();
        pins.add(PinSpec.input("A", 0, 3, inputBits));
        pins.add(PinSpec.input("EN", 3, height));
        for (int output = 0; output < outputs; output++) {
            pins.add(PinSpec.output("Y" + output, 6, 2 + 2 * output));
        }
        return new ComponentShape(new GridSize(6, height), pins);
    }

    /** A 4 x 8 block: A[N] at (0, 3), B[N] at (0, 5), Cin at (2, 0), S[N] at (4, 4), Cout at (2, 8). */
    private static ComponentShape adderShape(int width) {
        return new ComponentShape(new GridSize(4, 8), List.of(
                PinSpec.input("A", 0, 3, width),
                PinSpec.input("B", 0, 5, width),
                PinSpec.input("Cin", 2, 0),
                PinSpec.output("S", 4, 4, width),
                PinSpec.output("Cout", 2, 8)));
    }

    /**
     * A 6 x 10 chevron: A[N] at (0, 2) and B[N] at (0, 8), clear of the notch; OP[3] at (3, 10) on the bottom; Y[N], Z,
     * N, C, V at (6, 3..7), on the short right edge.
     */
    private static ComponentShape aluShape(int width) {
        return new ComponentShape(new GridSize(6, 10), List.of(
                PinSpec.input("A", 0, 2, width),
                PinSpec.input("B", 0, 8, width),
                PinSpec.input("OP", 3, 10, 3),
                PinSpec.output("Y", 6, 3, width),
                PinSpec.output("Z", 6, 4),
                PinSpec.output("N", 6, 5),
                PinSpec.output("C", 6, 6),
                PinSpec.output("V", 6, 7)));
    }

    /** An 8 x 8 box: A[N] at (0, 2), B[N] at (0, 6); A&lt;B, A=B, A&gt;B at (8, 2), (8, 4), (8, 6). */
    private static ComponentShape comparatorShape(int width) {
        return new ComponentShape(new GridSize(8, 8), List.of(
                PinSpec.input("A", 0, 2, width),
                PinSpec.input("B", 0, 6, width),
                PinSpec.output("A<B", 8, 2),
                PinSpec.output("A=B", 8, 4),
                PinSpec.output("A>B", 8, 6)));
    }

    /**
     * A 12 x 7 register: D[N] at (0, 2), Q[N] at (12, 2), EN, CLR and CLK on the bottom edge at x = 6, 8, 10. Wide
     * enough for its value in every format at 32 bits (32 binary digits get a smaller text).
     */
    private static ComponentShape registerShape(int width) {
        return new ComponentShape(new GridSize(12, 7), List.of(
                PinSpec.input("D", 0, 2, width),
                PinSpec.input("EN", 6, 7),
                PinSpec.input("CLR", 8, 7),
                PinSpec.input("CLK", 10, 7),
                PinSpec.output("Q", 12, 2, width)));
    }

    /** The register's 12 x 7 box with LD at (4, 7) on the bottom edge and C at (12, 5) on the right. */
    private static ComponentShape counterShape(int width) {
        return new ComponentShape(new GridSize(12, 7), List.of(
                PinSpec.input("D", 0, 2, width),
                PinSpec.input("LD", 4, 7),
                PinSpec.input("EN", 6, 7),
                PinSpec.input("CLR", 8, 7),
                PinSpec.input("CLK", 10, 7),
                PinSpec.output("Q", 12, 2, width),
                PinSpec.output("C", 12, 5)));
    }

    /**
     * The box of a constant: height 2, as wide as its longest possible text needs (so the shape never depends on the
     * value), the bus pin on the right edge, in the middle.
     */
    private static ComponentShape constantShape(int width, String format) {
        // About one grid unit per character (a bold 9 px digit is under 8 px wide), plus room for margins and the lead.
        int boxWidth = Math.max(4, ConstantValue.longestDisplay(width, format) + 2);
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

    /**
     * A display of {@code bitCount} one-bit inputs in0.. on the top edge (in0, bit 0, on the right) and, appended after
     * them so their indices never change, a {@code bitCount}-bit bus input on the bottom edge, centred.
     */
    private static ComponentType sevenSegments(String id, int bitCount) {
        List<PinSpec> pins = new ArrayList<>();
        for (int index = 0; index < bitCount; index++) {
            pins.add(PinSpec.input("in" + index, bitCount - index, 0));
        }
        pins.add(PinSpec.input(DISPLAY_BUS_PIN, bitCount / 2, 6, bitCount));
        return new ComponentType(id, ComponentCategory.INPUT_OUTPUT, new GridSize(bitCount, 6), pins, List.of(),
                parameters -> new SevenSegmentsDisplayBehavior(bitCount));
    }

    /** True for both 7-segment displays. */
    public static boolean isSevenSegmentsDisplay(String typeId) {
        return SEVEN_SEGMENTS_DISPLAY.equals(typeId) || SEVEN_SEGMENTS_DISPLAY_DOUBLE.equals(typeId);
    }

    /** The index of a 7-segment display's bus input: the last pin, after the one-bit inputs 0..index-1. */
    public static int sevenSegmentsBusPin(ComponentType display) {
        return display.pinCount() - 1;
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
