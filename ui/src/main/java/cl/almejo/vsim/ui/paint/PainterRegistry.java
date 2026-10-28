package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.VisibleState;
import cl.almejo.vsim.core.component.standard.ArithmeticTypes;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.SequentialTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.WiringTypes;
import cl.almejo.vsim.core.library.InterfacePins;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.paint.LogicGatePainter.GateShape;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PainterRegistry {

    /** What the ALU shows inside for each OP code (M7 spec §2.1). */
    private static final List<String> ALU_OPERATIONS = List.of("A+B", "A−B", "AND", "OR", "XOR", "NOT A", "A<<1",
            "A>>1");

    private final Map<String, ComponentPainter> painters = new HashMap<>();
    private final ComponentPainter fallback = new GenericBoxPainter();
    private final ComponentPainter subcircuit = new SubcircuitPainter();

    public static PainterRegistry standard() {
        PainterRegistry registry = new PainterRegistry();
        registry.register(StandardComponentTypes.AND, new LogicGatePainter(GateShape.AND));
        registry.register(StandardComponentTypes.OR, new LogicGatePainter(GateShape.OR));
        registry.register(StandardComponentTypes.XOR, new LogicGatePainter(GateShape.XOR));
        registry.register(StandardComponentTypes.BUFFER, new BufferPainter());
        registry.register(StandardComponentTypes.TRI_STATE, new TriStatePainter());
        FlipFlopPainter flipFlop = new FlipFlopPainter();
        for (String flipFlopType : SequentialTypes.FLIP_FLOPS) {
            registry.register(flipFlopType, flipFlop);
        }
        registry.register(StandardComponentTypes.CLOCK, new ClockPainter());
        registry.register(StandardComponentTypes.SWITCH, new SwitchPainter());
        registry.register(StandardComponentTypes.CONSTANT, new ConstantPainter());
        registry.register(StandardComponentTypes.LED, new LedPainter());
        SevenSegmentsPainter display = new SevenSegmentsPainter();
        registry.register(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, display);
        registry.register(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY_DOUBLE, display);
        registry.register(StandardComponentTypes.TIME_DIAGRAM, new TimeDiagramPainter());
        BusSplitterPainter bus = new BusSplitterPainter();
        registry.register(StandardComponentTypes.BUS_SPLITTER, bus);
        registry.register(StandardComponentTypes.BUS_JOINER, bus);
        InterfacePinPainter interfacePin = new InterfacePinPainter();
        registry.register(InterfacePins.INPUT_PIN, interfacePin);
        registry.register(InterfacePins.OUTPUT_PIN, interfacePin);
        TunnelPainter tunnel = new TunnelPainter();
        registry.register(StandardComponentTypes.TUNNEL_ENTRANCE, tunnel);
        registry.register(StandardComponentTypes.TUNNEL_EXIT, tunnel);
        registerBlocks(registry);
        registerArithmetic(registry);
        registerWiring(registry);
        return registry;
    }

    /** The bit extender («EXT» with its «N→M» widths) and the bit selector, drawn as blocks. */
    private static void registerWiring(PainterRegistry registry) {
        registry.register(WiringTypes.BIT_EXTENDER, new BlockPainter("EXT", BlockPainter.Body.RECTANGLE)
                .withLiveText((state, parameters) -> Optional.of(parameters.get(WiringTypes.INPUT_WIDTH) + "→"
                        + parameters.get(WiringTypes.OUTPUT_WIDTH))));
        registry.register(WiringTypes.BIT_SELECTOR, new BlockPainter("SEL", BlockPainter.Body.RECTANGLE));
    }

    /** The «Bloques» (M7), all drawn by a {@link BlockPainter}. */
    private static void registerBlocks(PainterRegistry registry) {
        registry.register(StandardComponentTypes.ADDER, new BlockPainter("Σ", BlockPainter.Body.CHEVRON));
        registry.register(StandardComponentTypes.ALU, new BlockPainter("ALU", BlockPainter.Body.CHEVRON)
                .withLiveText(PainterRegistry::aluOperation));
        registry.register(StandardComponentTypes.COMPARATOR, new BlockPainter("CMP", BlockPainter.Body.RECTANGLE));
        registry.register(StandardComponentTypes.MUX, new BlockPainter("MUX", BlockPainter.Body.TRAPEZOID));
        registry.register(StandardComponentTypes.DECODER, new BlockPainter("DEC", BlockPainter.Body.RECTANGLE));
        registry.register("demux", new BlockPainter("DEMUX", BlockPainter.Body.RECTANGLE));
        registry.register("priority-encoder", new BlockPainter("PRI", BlockPainter.Body.RECTANGLE));
        registry.register(StandardComponentTypes.REGISTER, new BlockPainter("REG", BlockPainter.Body.RECTANGLE)
                .withLiveText(PainterRegistry::registerValueText).withCornerSymbol());
        registry.register(StandardComponentTypes.COUNTER, new BlockPainter("CNT", BlockPainter.Body.RECTANGLE)
                .withLiveText(PainterRegistry::storedValueText));
        registry.register(StandardComponentTypes.RAM, new MemoryPainter());
        registry.register(StandardComponentTypes.ROM, new MemoryPainter());
    }

    /** The arithmetic parts after the adder: each shows its operation's symbol; the shifter, its type's. */
    private static void registerArithmetic(PainterRegistry registry) {
        registry.register(ArithmeticTypes.SUBTRACTOR, new BlockPainter("−", BlockPainter.Body.CHEVRON));
        registry.register(ArithmeticTypes.NEGATOR, new BlockPainter("−A", BlockPainter.Body.CHEVRON));
        registry.register(ArithmeticTypes.MULTIPLIER, new BlockPainter("×", BlockPainter.Body.CHEVRON));
        registry.register(ArithmeticTypes.DIVIDER, new BlockPainter("÷", BlockPainter.Body.RECTANGLE));
        registry.register(ArithmeticTypes.SHIFTER, new BlockPainter("SHF", BlockPainter.Body.RECTANGLE)
                .withLiveText((state, parameters) -> Optional.of(shiftSymbol(parameters))));
        registry.register(ArithmeticTypes.BIT_COUNTER, new BlockPainter("#1", BlockPainter.Body.RECTANGLE));
    }

    /** The shifter's symbol by «Tipo»: {@code <<}, {@code >>}, {@code >>>}, {@code ⟲} (rotate left), {@code ⟳}. */
    static String shiftSymbol(Map<String, Object> parameters) {
        return switch (String.valueOf(parameters.get(ArithmeticTypes.SHIFT_TYPE))) {
            case ArithmeticTypes.LOGICAL_RIGHT -> ">>";
            case ArithmeticTypes.ARITHMETIC_RIGHT -> ">>>";
            case ArithmeticTypes.ROTATE_LEFT -> "⟲";
            case ArithmeticTypes.ROTATE_RIGHT -> "⟳";
            default -> "<<";
        };
    }

    /**
     * The operation an ALU shows inside: {@code A+B} ... {@code A>>1} for its OP code, {@code ?} while OP is undefined,
     * and {@code A+B} (OP = 000, the power-on inputs) for the palette icon ({@link VisibleState#NONE}).
     */
    static Optional<String> aluOperation(VisibleState state) {
        if (state instanceof VisibleState.AluState alu) {
            int operation = alu.operation();
            return Optional.of(operation >= 0 && operation < ALU_OPERATIONS.size() ? ALU_OPERATIONS.get(operation) : "?");
        }
        return state instanceof VisibleState.None ? Optional.of(ALU_OPERATIONS.getFirst()) : Optional.empty();
    }

    /**
     * What a register or a counter shows under its name: its stored value in its «Mostrar como» format, written like
     * a pin's value box (Z bits and conflicts included). Without a state ({@link VisibleState#NONE}, the palette icon)
     * it shows the power-on value, 0, like the ALU shows {@code A+B}; another kind of state shows nothing.
     */
    static Optional<String> storedValueText(VisibleState state, Map<String, Object> parameters) {
        Value value;
        if (state instanceof VisibleState.StoredValueState stored) {
            value = stored.value();
        } else if (state instanceof VisibleState.None
                && parameters.get(StandardComponentTypes.WIDTH) instanceof Integer width) {
            value = Value.of(width, 0);
        } else {
            return Optional.empty();
        }
        Object format = parameters.get(ConstantValue.FORMAT);
        return Optional.of(InterfacePinPainter.shownText(value, format == null ? ConstantValue.AUTO : (String) format));
    }

    /**
     * What the thin register shows in its middle: {@link #storedValueText}, with binary digits grouped by four and,
     * over {@value StandardComponentTypes#REGISTER_ONE_LINE_BITS} bits, in two lines ({@code \n} between them): the
     * high bits on top, the low 16 bits below, so the most significant bit is top left (like the multi-bit Switch).
     */
    static Optional<String> registerValueText(VisibleState state, Map<String, Object> parameters) {
        Optional<String> text = storedValueText(state, parameters);
        Object format = parameters.get(ConstantValue.FORMAT);
        if (text.isEmpty() || !(parameters.get(StandardComponentTypes.WIDTH) instanceof Integer width)
                || text.get().length() != width
                || !ConstantValue.BINARY.equals(ConstantValue.resolveFormat(
                format == null ? ConstantValue.AUTO : (String) format, width))) {
            return text;
        }
        String digits = text.get();
        if (width <= StandardComponentTypes.REGISTER_ONE_LINE_BITS) {
            return Optional.of(groupedByFour(digits));
        }
        int split = width - StandardComponentTypes.REGISTER_ONE_LINE_BITS;
        return Optional.of(groupedByFour(digits.substring(0, split)) + "\n" + groupedByFour(digits.substring(split)));
    }

    /** {@code digits} in groups of four from the right: {@code 101010} → {@code 10 1010}. */
    private static String groupedByFour(String digits) {
        StringBuilder grouped = new StringBuilder();
        for (int index = 0; index < digits.length(); index++) {
            if (index > 0 && (digits.length() - index) % 4 == 0) {
                grouped.append(' ');
            }
            grouped.append(digits.charAt(index));
        }
        return grouped.toString();
    }

    public void register(String typeId, ComponentPainter painter) {
        painters.put(typeId, painter);
    }

    /** The painter registered for {@code typeId}; {@link SubcircuitPainter} for any {@code subcircuit:} id; a box otherwise. */
    public ComponentPainter painterFor(String typeId) {
        ComponentPainter painter = painters.get(typeId);
        if (painter != null) {
            return painter;
        }
        return SubcircuitTypes.definitionIdOf(typeId).isPresent() ? subcircuit : fallback;
    }

    public boolean hasDedicatedPainter(String typeId) {
        return painters.containsKey(typeId);
    }
}
