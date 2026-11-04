package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.ArithmeticTypes;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.SequentialTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.StandardParameters;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.WiringTypes;
import cl.almejo.vsim.core.memory.MemoryFileProblem;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Logisim 2.7's built-in parts and the VSim part each becomes (gap analysis §1, and the rest of Logisim's libraries for
 * any other file): one {@link Rule} per {@code library/name}. A part missing here has no VSim equivalent and the
 * mapper notes it as unsupported. Another dialect (Logisim Evolution) would add or replace rules in its own table.
 *
 * <p><b>Rotation.</b> VSim turns parts clockwise from rotation 0, Logisim names the side a part faces. Each rule says
 * which side VSim's part faces at rotation 0 ({@link LogisimFacing}): east for gates, plexers, the tri-state, the
 * clock, constants, the input pin, the push button, the tunnel exit and the splitter (its ends go east); west for the
 * output pin, the tunnel entrance and the joiner (its ends come from the west); south for the one-bit LED (its input
 * is below). Ground and Power face away from their connection, so they read the opposite side. Parts without
 * {@code facing} in Logisim (arithmetic, memory, flip-flops, I/O blocks) stay at rotation 0. Where a part's pins sit
 * on its sides (a multiplexer's select, a splitter's ends going north or south) is the geometry step's.
 */
final class LogisimPartRules {

    /** A rule: what one Logisim part becomes. */
    @FunctionalInterface
    interface Rule {
        /**
         * The mapping of the draft's component.
         *
         * @throws IllegalArgumentException when an attribute cannot be read or VSim refuses a value
         */
        LogisimMapping map(PartDraft draft);
    }

    /** VSim's gates take 2..8 inputs. */
    private static final int MAXIMUM_GATE_INPUTS = 8;
    /** VSim's multiplexer and demultiplexer take up to 8 data lines: 3 select bits. */
    private static final int MAXIMUM_MUX_SELECT_BITS = 3;
    /** VSim's decoder and priority encoder take up to 4 address bits (16 lines). */
    private static final int MAXIMUM_DECODER_SELECT_BITS = 4;
    /** {@code PlexerTypes.DEMUX}, package-private there. */
    static final String DEMUX = "demux";
    /** {@code PlexerTypes.PRIORITY_ENCODER}, package-private there. */
    static final String PRIORITY_ENCODER = "priority-encoder";
    /** The Keyboard's and the Terminal's data width for Logisim's 7-bit ASCII. */
    private static final String SEVEN_BITS = String.valueOf(KeyEncoding.SEVEN_BITS);
    /** The Teclado's buffer limits. */
    private static final int MAXIMUM_KEYBOARD_CAPACITY = 4096;
    /** The Terminal's size limits ({@code TerminalTypes}). */
    private static final int MAXIMUM_TERMINAL_ROWS = 60;
    private static final int MAXIMUM_TERMINAL_COLUMNS = 160;
    /** VSim's LED colours with the RGB each paints, to take the nearest to Logisim's colour (and back, on export). */
    static final Map<String, Integer> LED_COLORS = ledColors();

    private LogisimPartRules() {
    }

    /** Logisim 2.7's table, keyed {@code library/name}. */
    static Map<String, Rule> logisim27() {
        Map<String, Rule> rules = new HashMap<>();
        wiring(rules);
        gates(rules);
        plexers(rules);
        arithmetic(rules);
        memory(rules);
        inputOutput(rules);
        rules.put(key(LogisimLibraries.BASE, "Text"), LogisimPartRules::text);
        return rules;
    }

    static String key(String library, String name) {
        return library + "/" + name;
    }

    private static void wiring(Map<String, Rule> rules) {
        String wiring = LogisimLibraries.WIRING;
        rules.put(key(wiring, "Splitter"), LogisimPartRules::splitter);
        rules.put(key(wiring, "Pin"), LogisimPartRules::pin);
        rules.put(key(wiring, "Probe"), LogisimPartRules::probe);
        rules.put(key(wiring, "Tunnel"), LogisimPartRules::tunnel);
        rules.put(key(wiring, "Clock"), LogisimPartRules::clock);
        rules.put(key(wiring, "Constant"), LogisimPartRules::constant);
        rules.put(key(wiring, "Power"), draft -> fixedLevel(draft, true));
        rules.put(key(wiring, "Ground"), draft -> fixedLevel(draft, false));
        rules.put(key(wiring, "Bit Extender"), LogisimPartRules::bitExtender);
    }

    private static void gates(Map<String, Rule> rules) {
        String gates = LogisimLibraries.GATES;
        rules.put(key(gates, "NOT Gate"), draft -> buffer(draft, true));
        rules.put(key(gates, "Buffer"), draft -> buffer(draft, false));
        rules.put(key(gates, "AND Gate"), draft -> gate(draft, StandardComponentTypes.AND, false));
        rules.put(key(gates, "NAND Gate"), draft -> gate(draft, StandardComponentTypes.AND, true));
        rules.put(key(gates, "OR Gate"), draft -> gate(draft, StandardComponentTypes.OR, false));
        rules.put(key(gates, "NOR Gate"), draft -> gate(draft, StandardComponentTypes.OR, true));
        rules.put(key(gates, "XOR Gate"), draft -> gate(draft, StandardComponentTypes.XOR, false));
        rules.put(key(gates, "XNOR Gate"), draft -> gate(draft, StandardComponentTypes.XOR, true));
        rules.put(key(gates, "Odd Parity"), draft -> gate(draft, StandardComponentTypes.XOR, false));
        rules.put(key(gates, "Even Parity"), draft -> gate(draft, StandardComponentTypes.XOR, true));
        rules.put(key(gates, "Controlled Buffer"), draft -> controlledBuffer(draft, false));
        rules.put(key(gates, "Controlled Inverter"), draft -> controlledBuffer(draft, true));
    }

    private static void plexers(Map<String, Rule> rules) {
        String plexers = LogisimLibraries.PLEXERS;
        rules.put(key(plexers, "Multiplexer"), LogisimPartRules::multiplexer);
        rules.put(key(plexers, "Demultiplexer"), LogisimPartRules::demultiplexer);
        rules.put(key(plexers, "Decoder"), LogisimPartRules::decoder);
        rules.put(key(plexers, "Priority Encoder"), LogisimPartRules::priorityEncoder);
        rules.put(key(plexers, "BitSelector"), LogisimPartRules::bitSelector);
    }

    private static void arithmetic(Map<String, Rule> rules) {
        String arithmetic = LogisimLibraries.ARITHMETIC;
        rules.put(key(arithmetic, "Adder"), draft -> block(draft, StandardComponentTypes.ADDER,
                LogisimTiming.adder(draft.integer("width"))));
        rules.put(key(arithmetic, "Subtractor"), draft -> block(draft, ArithmeticTypes.SUBTRACTOR,
                LogisimTiming.subtractor(draft.integer("width"))));
        rules.put(key(arithmetic, "Negator"), draft -> block(draft, ArithmeticTypes.NEGATOR,
                LogisimTiming.comparator(draft.integer("width"))));
        rules.put(key(arithmetic, "Comparator"), LogisimPartRules::comparator);
        rules.put(key(arithmetic, "Multiplier"), LogisimPartRules::multiplier);
        rules.put(key(arithmetic, "Divider"), LogisimPartRules::divider);
        rules.put(key(arithmetic, "Shifter"), LogisimPartRules::shifter);
        rules.put(key(arithmetic, "BitAdder"), LogisimPartRules::bitAdder);
    }

    private static void memory(Map<String, Rule> rules) {
        String memory = LogisimLibraries.MEMORY;
        rules.put(key(memory, "D Flip-Flop"), draft -> flipFlop(draft, StandardComponentTypes.FLIP_FLOP_DATA));
        rules.put(key(memory, "T Flip-Flop"), draft -> flipFlop(draft, SequentialTypes.FLIP_FLOP_T));
        rules.put(key(memory, "J-K Flip-Flop"), draft -> flipFlop(draft, SequentialTypes.FLIP_FLOP_JK));
        rules.put(key(memory, "S-R Flip-Flop"), draft -> flipFlop(draft, SequentialTypes.FLIP_FLOP_SR));
        rules.put(key(memory, "Register"), LogisimPartRules::register);
        rules.put(key(memory, "Counter"), LogisimPartRules::counter);
        rules.put(key(memory, "RAM"), LogisimPartRules::ram);
        rules.put(key(memory, "ROM"), LogisimPartRules::rom);
    }

    private static void inputOutput(Map<String, Rule> rules) {
        String io = LogisimLibraries.INPUT_OUTPUT;
        rules.put(key(io, "Button"), draft -> draft.type(StandardComponentTypes.PUSH_BUTTON)
                .upright().labelled().part());
        rules.put(key(io, "LED"), LogisimPartRules::led);
        rules.put(key(io, "Hex Digit Display"), draft -> draft.type(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY)
                .part());
        rules.put(key(io, "Keyboard"), LogisimPartRules::keyboard);
        rules.put(key(io, "TTY"), LogisimPartRules::terminal);
    }

    // --- Wiring ---

    /**
     * A «Separador de bus» or «Unión de bus» (as the context's direction says) with one grouped pin per end that has
     * bits; {@code none} bits are in no pin. The groups are stored canonical, like the window and the AI store them
     * ({@link BitGroups#canonical}). The extras map each Logisim end to its VSim pin.
     */
    private static LogisimMapping splitter(PartDraft draft) {
        int incoming = draft.integer("incoming");
        if (incoming < 2) {
            return draft.unsupported(LogisimNotes.NARROW_SPLITTER);
        }
        int fanout = draft.integer("fanout");
        List<List<Integer>> bitsPerEnd = new ArrayList<>();
        for (int end = 0; end < fanout; end++) {
            bitsPerEnd.add(new ArrayList<>());
        }
        for (int bit = 0; bit < incoming; bit++) {
            String end = draft.text("bit" + bit);
            if (!end.equals(LogisimDefaults.NO_END)) {
                bitsPerEnd.get(Integer.parseInt(end)).add(bit);
            }
        }
        List<String> groups = new ArrayList<>();
        Map<Integer, Integer> pins = new LinkedHashMap<>();
        for (int end = 0; end < fanout; end++) {
            if (!bitsPerEnd.get(end).isEmpty()) {
                pins.put(end, groups.size());
                groups.add(ranges(bitsPerEnd.get(end)));
            }
        }
        if (groups.isEmpty()) {
            return draft.unsupported(LogisimNotes.EMPTY_SPLITTER);
        }
        boolean joining = draft.context().splitterDirection() == SplitterDirection.JOIN;
        return draft.type(joining ? StandardComponentTypes.BUS_JOINER : StandardComponentTypes.BUS_SPLITTER)
                .parameter(StandardComponentTypes.WIDTH, incoming)
                .parameter(StandardComponentTypes.GROUPS,
                        BitGroups.parse(String.join("|", groups), incoming).canonical())
                .facing(joining ? LogisimFacing.WEST : LogisimFacing.EAST)
                .extras(extras -> extras.withSplitterEnds(pins))
                .part();
    }

    /** Ascending bits as runs: {@code 0-6,25-31}. */
    private static String ranges(List<Integer> bits) {
        List<String> runs = new ArrayList<>();
        int first = bits.getFirst();
        int last = first;
        for (int bit : bits.subList(1, bits.size())) {
            if (bit != last + 1) {
                runs.add(run(first, last));
                first = bit;
            }
            last = bit;
        }
        runs.add(run(first, last));
        return String.join(",", runs);
    }

    private static String run(int first, int last) {
        return first == last ? String.valueOf(first) : first + "-" + last;
    }

    /**
     * «Pin de entrada» / «Pin de salida» named by the label; a blank label asks the subcircuit step for a name. An
     * input starts at 0 (a Logisim root pin is a switch that starts at 0; a subcircuit's unconnected non-tri-state
     * input reads 0 too).
     */
    private static LogisimMapping pin(PartDraft draft) {
        boolean output = draft.bool("output");
        String pull = draft.text("pull");
        if (!pull.equals("none")) {
            draft.approximated(LogisimNotes.PIN_PULL, pull);
        }
        draft.type(output ? StandardComponentTypes.OUTPUT_PIN : StandardComponentTypes.INPUT_PIN)
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .facing(output ? LogisimFacing.WEST : LogisimFacing.EAST)
                .labelled();
        if (!output) {
            draft.parameter(StandardComponentTypes.VALUE, "0");
        }
        boolean unnamed = draft.text("label").isBlank();
        return draft.extras(extras -> extras.withNeedsName(unnamed)).part();
    }

    private static LogisimMapping probe(PartDraft draft) {
        return new MappedText(draft.text("label"), LogisimFont.parse(draft.text("labelfont")),
                draft.text("labelloc"), "", true, draft.notes());
    }

    /**
     * A tunnel «Sin dirección», like every Logisim tunnel (only VSim's have a direction, so none is guessed): an
     * entrada, so each takes its name's colour. Logisim joins only tunnels with a label, so one without does nothing
     * and is left out.
     */
    private static LogisimMapping tunnel(PartDraft draft) {
        String label = draft.text("label");
        if (label.isBlank()) {
            return new NotMapped(draft.notes());
        }
        return draft.type(TunnelKind.ENTRANCE.typeId())
                .parameter(Tunnels.NAME, label)
                .parameter(Tunnels.DIRECTION, Tunnels.UNDIRECTED)
                .facing(LogisimFacing.WEST)
                .extras(extras -> extras.withTunnelLabel(label))
                .part();
    }

    /** Each Logisim tick high or low becomes {@link LogisimTiming#HALF_PERIOD} units, noted. */
    private static LogisimMapping clock(PartDraft draft) {
        return draft.type(StandardComponentTypes.CLOCK)
                .parameter("time-up", draft.integer("highDuration") * LogisimTiming.HALF_PERIOD)
                .parameter("time-down", draft.integer("lowDuration") * LogisimTiming.HALF_PERIOD)
                .upright()
                .labelled()
                .approximated(LogisimNotes.CLOCK_PERIOD, String.valueOf(LogisimTiming.HALF_PERIOD))
                .part();
    }

    /** The value in hex, cut to the width as Logisim keeps it; upright, as Logisim draws its box. */
    private static LogisimMapping constant(PartDraft draft) {
        int width = draft.integer("width");
        long value = draft.attributes().number("value") & mask(width);
        return draft.type(StandardComponentTypes.CONSTANT)
                .parameter(StandardComponentTypes.WIDTH, width)
                .parameter("value", hex(value))
                .upright()
                .part();
    }

    /** Power (all ones) or Ground (all zeros) as a constant, upright like any constant (its value stays readable). */
    private static LogisimMapping fixedLevel(PartDraft draft, boolean ones) {
        int width = draft.integer("width");
        return draft.type(StandardComponentTypes.CONSTANT)
                .parameter(StandardComponentTypes.WIDTH, width)
                .parameter("value", hex(ones ? mask(width) : 0))
                .upright()
                .part();
    }

    private static LogisimMapping bitExtender(PartDraft draft) {
        String type = draft.text("type");
        String fill = switch (type) {
            case "zero" -> WiringTypes.FILL_ZEROS;
            case "sign" -> WiringTypes.FILL_SIGN;
            default -> "";
        };
        if (fill.isEmpty()) {
            return draft.unsupported(LogisimNotes.EXTENDER_FILL, type);
        }
        return draft.type(WiringTypes.BIT_EXTENDER)
                .parameter(WiringTypes.INPUT_WIDTH, draft.integer("in_width"))
                .parameter(WiringTypes.OUTPUT_WIDTH, draft.integer("out_width"))
                .parameter(WiringTypes.FILL, fill)
                .approximated(LogisimNotes.NO_DELAY, draft.component().name(),
                        String.valueOf(LogisimTiming.BIT_EXTENDER))
                .part();
    }

    // --- Gates ---

    private static LogisimMapping buffer(PartDraft draft, boolean negated) {
        noteOutputMode(draft);
        return draft.type(StandardComponentTypes.BUFFER)
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .parameter(StandardComponentTypes.NEGATE, negated)
                .delay(LogisimTiming.GATE)
                .facing(LogisimFacing.EAST)
                .labelled()
                .part();
    }

    /**
     * An AND/OR/XOR family gate. Negated inputs: on an XOR each one only flips the output; on an AND or OR with every
     * input negated De Morgan swaps it (AND ↔ OR, output negation flipped) with the same delay; with only some
     * negated, each gets a NOT in front ({@link MappedPart.Extras#notInFront}), one more delay unit, noted.
     */
    private static LogisimMapping gate(PartDraft draft, String operation, boolean negatedOutput) {
        int inputs = draft.integer("inputs");
        if (inputs > MAXIMUM_GATE_INPUTS) {
            return draft.unsupported(LogisimNotes.TOO_MANY_INPUTS, draft.component().name(), String.valueOf(inputs),
                    String.valueOf(MAXIMUM_GATE_INPUTS));
        }
        List<Integer> negatedInputs = IntStream.range(0, inputs).filter(input -> draft.bool("negate" + input))
                .boxed().toList();
        String type = operation;
        boolean negate = negatedOutput;
        if (operation.equals(StandardComponentTypes.XOR)) {
            negate ^= negatedInputs.size() % 2 == 1;
            if (draft.attributes().textOr("xor", "odd").equals("1") && inputs > 2) {
                draft.approximated(LogisimNotes.ONE_HOT_XOR, String.valueOf(inputs));
            }
        } else if (negatedInputs.size() == inputs) {
            type = operation.equals(StandardComponentTypes.AND) ? StandardComponentTypes.OR : StandardComponentTypes.AND;
            negate = !negate;
        } else if (!negatedInputs.isEmpty()) {
            draft.extras(extras -> extras.withNotInFront(negatedInputs))
                    .approximated(LogisimNotes.NEGATED_INPUT, String.valueOf(negatedInputs.size()));
        }
        noteOutputMode(draft);
        return draft.type(type)
                .parameter(StandardComponentTypes.GATE_INPUTS, inputs)
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .parameter(StandardComponentTypes.NEGATE, negate)
                .delay(LogisimTiming.GATE)
                .facing(LogisimFacing.EAST)
                .labelled()
                .part();
    }

    private static void noteOutputMode(PartDraft draft) {
        String output = draft.text("out");
        if (!output.equals("01")) {
            draft.approximated(LogisimNotes.GATE_OUTPUT, output);
        }
    }

    /** The control side ({@code control}) is only where Logisim draws the control pin. */
    private static LogisimMapping controlledBuffer(PartDraft draft, boolean inverting) {
        return draft.type(StandardComponentTypes.TRI_STATE)
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .parameter(StandardComponentTypes.NEGATE, inverting)
                .delay(LogisimTiming.GATE)
                .facing(LogisimFacing.EAST)
                .labelled()
                .part();
    }

    // --- Plexers ---

    private static LogisimMapping multiplexer(PartDraft draft) {
        int select = draft.integer("select");
        if (select > MAXIMUM_MUX_SELECT_BITS) {
            return tooManySelectBits(draft, select, MAXIMUM_MUX_SELECT_BITS);
        }
        noteEnable(draft);
        return draft.type(StandardComponentTypes.MUX)
                .parameter("inputs", String.valueOf(1 << select))
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .delay(LogisimTiming.PLEXER)
                .facing(LogisimFacing.EAST)
                .part();
    }

    private static LogisimMapping demultiplexer(PartDraft draft) {
        int select = draft.integer("select");
        if (select > MAXIMUM_MUX_SELECT_BITS) {
            return tooManySelectBits(draft, select, MAXIMUM_MUX_SELECT_BITS);
        }
        noteEnable(draft);
        noteFloatingOutputs(draft);
        return draft.type(DEMUX)
                .parameter("outputs", String.valueOf(1 << select))
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .delay(LogisimTiming.PLEXER)
                .facing(LogisimFacing.EAST)
                .part();
    }

    /** VSim's decoder has an EN input like Logisim's, so the enable needs no note. */
    private static LogisimMapping decoder(PartDraft draft) {
        int select = draft.integer("select");
        if (select > MAXIMUM_DECODER_SELECT_BITS) {
            return tooManySelectBits(draft, select, MAXIMUM_DECODER_SELECT_BITS);
        }
        noteFloatingOutputs(draft);
        return draft.type(StandardComponentTypes.DECODER)
                .parameter("inputBits", select)
                .delay(LogisimTiming.PLEXER)
                .facing(LogisimFacing.EAST)
                .part();
    }

    private static LogisimMapping priorityEncoder(PartDraft draft) {
        int select = draft.integer("select");
        if (select > MAXIMUM_DECODER_SELECT_BITS) {
            return tooManySelectBits(draft, select, MAXIMUM_DECODER_SELECT_BITS);
        }
        return draft.type(PRIORITY_ENCODER)
                .parameter("inputs", String.valueOf(1 << select))
                .delay(LogisimTiming.PLEXER)
                .facing(LogisimFacing.EAST)
                .approximated(LogisimNotes.PRIORITY_ENCODER)
                .part();
    }

    /** Logisim's {@code width} is the data and {@code group} the bits taken; VSim numbers the groups the same way. */
    private static LogisimMapping bitSelector(PartDraft draft) {
        return draft.type(WiringTypes.BIT_SELECTOR)
                .parameter(WiringTypes.INPUT_WIDTH, draft.integer("width"))
                .parameter(WiringTypes.OUTPUT_WIDTH, draft.integer("group"))
                .delay(LogisimTiming.PLEXER)
                .facing(LogisimFacing.EAST)
                .part();
    }

    private static NotMapped tooManySelectBits(PartDraft draft, int select, int maximum) {
        return draft.unsupported(LogisimNotes.TOO_MANY_SELECT_BITS, draft.component().name(), String.valueOf(select),
                String.valueOf(maximum));
    }

    private static void noteEnable(PartDraft draft) {
        if (draft.bool("enable")) {
            draft.approximated(LogisimNotes.ENABLE_INPUT, draft.component().name());
        }
    }

    private static void noteFloatingOutputs(PartDraft draft) {
        if (draft.bool("tristate")) {
            draft.approximated(LogisimNotes.FLOATING_OUTPUTS, draft.component().name());
        }
    }

    // --- Arithmetic ---

    private static LogisimMapping block(PartDraft draft, String typeId, int delay) {
        return draft.type(typeId)
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .delay(delay)
                .part();
    }

    /** {@code twosComplement} (Logisim's default) compares signed numbers. */
    private static LogisimMapping comparator(PartDraft draft) {
        int width = draft.integer("width");
        boolean unsigned = draft.text("mode").equals("unsigned");
        return draft.type(StandardComponentTypes.COMPARATOR)
                .parameter(StandardComponentTypes.WIDTH, width)
                .parameter(StandardComponentTypes.SIGNEDNESS, unsigned ? StandardComponentTypes.SIGNEDNESS_UNSIGNED
                        : StandardComponentTypes.SIGNEDNESS_SIGNED)
                .delay(LogisimTiming.comparator(width))
                .part();
    }

    /**
     * Logisim multiplies the Java {@code int}s of its inputs : at 32 bits they carry
     * the sign, so the high word is the signed product's; narrower, the bits are positive and the product unsigned.
     */
    private static LogisimMapping multiplier(PartDraft draft) {
        int width = draft.integer("width");
        return draft.type(ArithmeticTypes.MULTIPLIER)
                .parameter(StandardComponentTypes.WIDTH, width)
                .parameter(StandardComponentTypes.SIGNEDNESS, width == Integer.SIZE
                        ? StandardComponentTypes.SIGNEDNESS_SIGNED : StandardComponentTypes.SIGNEDNESS_UNSIGNED)
                .delay(LogisimTiming.multiplier(width))
                .part();
    }

    /** Logisim divides without sign and treats B = 0 as 1 : noted (gap A5). */
    private static LogisimMapping divider(PartDraft draft) {
        int width = draft.integer("width");
        return draft.type(ArithmeticTypes.DIVIDER)
                .parameter(StandardComponentTypes.WIDTH, width)
                .parameter(StandardComponentTypes.SIGNEDNESS, StandardComponentTypes.SIGNEDNESS_UNSIGNED)
                .delay(LogisimTiming.multiplier(width))
                .approximated(LogisimNotes.DIVISION_BY_ZERO)
                .part();
    }

    private static LogisimMapping shifter(PartDraft draft) {
        int width = draft.integer("width");
        String shift = switch (draft.text("shift")) {
            case "ll" -> ArithmeticTypes.LOGICAL_LEFT;
            case "lr" -> ArithmeticTypes.LOGICAL_RIGHT;
            case "ar" -> ArithmeticTypes.ARITHMETIC_RIGHT;
            case "rl" -> ArithmeticTypes.ROTATE_LEFT;
            case "rr" -> ArithmeticTypes.ROTATE_RIGHT;
            default -> throw new IllegalArgumentException("unknown shift " + draft.text("shift"));
        };
        return draft.type(ArithmeticTypes.SHIFTER)
                .parameter(StandardComponentTypes.WIDTH, width)
                .parameter(ArithmeticTypes.SHIFT_TYPE, shift)
                .delay(LogisimTiming.shifter(width))
                .part();
    }

    /** A bit adder of one input counts its 1 bits: VSim's «Contador de unos». More inputs have no equivalent. */
    private static LogisimMapping bitAdder(PartDraft draft) {
        if (draft.integer("inputs") != 1) {
            return draft.unsupported(LogisimNotes.UNSUPPORTED_PART, draft.component().name());
        }
        int width = draft.integer("width");
        int outputWidth = Integer.SIZE - Integer.numberOfLeadingZeros(width);
        return draft.type(ArithmeticTypes.BIT_COUNTER)
                .parameter(StandardComponentTypes.WIDTH, width)
                .delay(LogisimTiming.bitAdder(outputWidth))
                .part();
    }

    // --- Memory ---

    /** VSim's flip-flops act on the falling edge only: another trigger is noted. */
    private static LogisimMapping flipFlop(PartDraft draft, String typeId) {
        noteEdge(draft);
        draft.type(typeId).delay(LogisimTiming.FLIP_FLOP).labelled();
        if (typeId.equals(StandardComponentTypes.FLIP_FLOP_DATA)) {
            draft.parameter(StandardComponentTypes.SHOW_INVERTED, true);
        }
        return draft.part();
    }

    /** VSim's register has Logisim's four triggers (E1). */
    private static LogisimMapping register(PartDraft draft) {
        return draft.type(StandardComponentTypes.REGISTER)
                .parameter(StandardComponentTypes.WIDTH, draft.integer("width"))
                .parameter(StandardComponentTypes.TRIGGER, draft.text("trigger"))
                .delay(LogisimTiming.REGISTER)
                .labelled()
                .part();
    }

    /** VSim's counter counts on the falling edge and wraps at all ones: anything else is noted. */
    private static LogisimMapping counter(PartDraft draft) {
        int width = draft.integer("width");
        noteEdge(draft);
        if (draft.attributes().number("max") != mask(width) || !draft.text("ongoal").equals("wrap")) {
            draft.approximated(LogisimNotes.COUNTER_GOAL);
        }
        return draft.type(StandardComponentTypes.COUNTER)
                .parameter(StandardComponentTypes.WIDTH, width)
                .delay(LogisimTiming.REGISTER)
                .labelled()
                .part();
    }

    private static void noteEdge(PartDraft draft) {
        String trigger = draft.text("trigger");
        if (!trigger.equals(StandardParameters.FALLING)) {
            draft.approximated(LogisimNotes.EDGE, draft.component().name(), trigger);
        }
    }

    /**
     * Logisim's RAM writes on the clock's rising edge ; {@code separate} has D in and out apart like
     * VSim's default. A combined or asynchronous bus is one shared D pin in VSim, noted; and as Logisim's then writes
     * with OE = 0 while VSim's writes only with its WE, which has no Logisim end, it never writes in VSim: a real
     * difference, noted too.
     */
    private static LogisimMapping ram(PartDraft draft) {
        String bus = draft.text("bus");
        if (!bus.equals("separate")) {
            draft.approximated(LogisimNotes.MEMORY_BUS, bus)
                    .note(ImportNote.Kind.DIFFERENT, LogisimNotes.RAM_WITHOUT_WRITE_ENABLE, bus);
        }
        return draft.type(StandardComponentTypes.RAM)
                .parameter(MemoryTypes.WORDS, words(draft))
                .parameter(MemoryTypes.WIDTH, draft.integer("dataWidth"))
                .parameter(MemoryTypes.DATA, bus.equals("separate") ? MemoryTypes.DATA_SEPARATE
                        : MemoryTypes.DATA_SHARED)
                .parameter(MemoryTypes.WRITE, MemoryTypes.WRITE_CLOCK)
                .parameter(StandardParameters.EDGE.name(), StandardParameters.RISING)
                .delay(LogisimTiming.MEMORY)
                .part();
    }

    /** The ROM with its contents; unreadable contents leave it empty with a note. */
    private static LogisimMapping rom(PartDraft draft) {
        String contents;
        try {
            contents = LogisimMemoryContents.encoded(draft.text("contents"));
        } catch (MemoryFileProblem problem) {
            draft.note(ImportNote.Kind.INVALID, LogisimNotes.MEMORY_CONTENTS, String.valueOf(problem.line()),
                    problem.messageKey());
            contents = "";
        }
        return draft.type(StandardComponentTypes.ROM)
                .parameter(MemoryTypes.WORDS, words(draft))
                .parameter(MemoryTypes.WIDTH, draft.integer("dataWidth"))
                .parameter(MemoryTypes.CONTENTS, contents)
                .delay(LogisimTiming.MEMORY)
                .part();
    }

    private static String words(PartDraft draft) {
        return String.valueOf(1L << draft.integer("addrWidth"));
    }

    // --- Input / output ---

    /** The nearest of VSim's LED colours; Logisim's LED faces its input. */
    private static LogisimMapping led(PartDraft draft) {
        if (!draft.bool("active")) {
            draft.approximated(LogisimNotes.ACTIVE_LOW);
        }
        return draft.type(StandardComponentTypes.LED)
                .parameter("color", nearestLedColor(draft.text("color")))
                .facing(LogisimFacing.SOUTH)
                .labelled()
                .part();
    }

    /** 7-bit ASCII on Logisim's edge; the buffer's size is VSim's capacity. */
    private static LogisimMapping keyboard(PartDraft draft) {
        return draft.type(StandardComponentTypes.KEYBOARD)
                .parameter(KeyEncoding.DATA_WIDTH, SEVEN_BITS)
                .parameter(StandardParameters.EDGE.name(), draft.text("trigger"))
                .parameter(KeyboardTypes.CAPACITY, Math.clamp(draft.integer("buflen"), 1, MAXIMUM_KEYBOARD_CAPACITY))
                .part();
    }

    /** The Terminal with every value of its «Logisim (LRV32IM)» profile, Logisim's size and edge. */
    private static LogisimMapping terminal(PartDraft draft) {
        int rows = draft.integer("rows");
        int columns = draft.integer("cols");
        int vsimRows = Math.clamp(rows, 1, MAXIMUM_TERMINAL_ROWS);
        int vsimColumns = Math.clamp(columns, 1, MAXIMUM_TERMINAL_COLUMNS);
        if (vsimRows != rows || vsimColumns != columns) {
            draft.approximated(LogisimNotes.TERMINAL_SIZE, String.valueOf(rows), String.valueOf(columns),
                    String.valueOf(vsimRows), String.valueOf(vsimColumns));
        }
        draft.context().registry().require(StandardComponentTypes.TERMINAL).profile(TerminalTypes.PROFILE_LOGISIM)
                .orElseThrow().values().forEach(draft::parameter);
        return draft.type(StandardComponentTypes.TERMINAL)
                .parameter(TerminalTypes.PROFILE, TerminalTypes.PROFILE_LOGISIM)
                .parameter(TerminalTypes.ROWS, vsimRows)
                .parameter(TerminalTypes.COLUMNS, vsimColumns)
                .parameter(StandardParameters.EDGE.name(), draft.text("trigger"))
                .part();
    }

    private static String nearestLedColor(String logisimColor) {
        int rgb = Integer.parseInt(logisimColor.substring(1, 7), 16);
        return LED_COLORS.entrySet().stream()
                .min((first, second) -> Integer.compare(distance(first.getValue(), rgb),
                        distance(second.getValue(), rgb)))
                .orElseThrow().getKey();
    }

    private static int distance(int first, int second) {
        int red = (first >> 16 & 0xFF) - (second >> 16 & 0xFF);
        int green = (first >> 8 & 0xFF) - (second >> 8 & 0xFF);
        int blue = (first & 0xFF) - (second & 0xFF);
        return red * red + green * green + blue * blue;
    }

    private static Map<String, Integer> ledColors() {
        return Map.of("red", 0xF00000, "green", 0x00C000, "yellow", 0xFFFF00, "blue", 0x0000FF,
                "orange", 0xFF8000, "white", 0xFFFFFF, "purple", 0x8000FF, "cyan", 0x00FFFF);
    }

    // --- Base ---

    private static LogisimMapping text(PartDraft draft) {
        return new MappedText(draft.text("text"), LogisimFont.parse(draft.text("font")), draft.text("halign"),
                draft.text("valign"), false, draft.notes());
    }

    private static long mask(int width) {
        return width >= Long.SIZE ? -1L : (1L << width) - 1;
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }
}
