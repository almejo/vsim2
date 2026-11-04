package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.standard.ArithmeticTypes;
import cl.almejo.vsim.core.component.standard.BitGroups;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.SequentialTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.StandardParameters;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.component.standard.WiringTypes;
import cl.almejo.vsim.core.memory.LogisimRawFormat;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.memory.MemoryFile;
import cl.almejo.vsim.core.memory.MemoryHeader;
import cl.almejo.vsim.core.terminal.KeyEncoding;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The inverse of {@link LogisimPartRules}: each VSim part and the Logisim 2.7 part it becomes, one rule per type id.
 * A type missing here has no Logisim equivalent (the ALU, the UART, the time diagram, the double display) and is left
 * out, noted. The rules write the attributes Logisim needs (the writer leaves out those equal to Logisim's default),
 * the side the VSim part faces at rotation 0 where the Logisim part has a facing (the same table as the import's), and
 * note what Logisim cannot express. Delays are not written: Logisim's parts have their own.
 */
final class LogisimExportRules {

    /** Logisim's TTY: up to 48 rows of 120 columns; its keyboard keeps up to 256 characters. */
    private static final int MAXIMUM_TTY_ROWS = 48;
    private static final int MAXIMUM_TTY_COLUMNS = 120;
    private static final int MAXIMUM_KEYBOARD_BUFFER = 256;
    /** VSim's pins whose loose or wired state makes the Logisim part act otherwise. */
    private static final String WRITE_ENABLE = "WE";
    private static final String COUNTER_LOAD = "LD";
    private static final String TRI_STATE_CONTROL = "enable";
    /** A contents attribute with no words still has one, as Logisim writes an empty memory. */
    private static final String NO_WORDS = "0\n";

    private static final Map<String, Consumer<ExportDraft>> RULES = rules();

    private LogisimExportRules() {
    }

    /** Fills {@code draft} with the Logisim part of its VSim part, or leaves it out with a note. */
    static void apply(ExportDraft draft) {
        Consumer<ExportDraft> rule = RULES.get(draft.component().typeId());
        if (rule == null) {
            draft.leaveOut(LogisimNotes.EXPORT_UNSUPPORTED_PART);
            return;
        }
        rule.accept(draft);
    }

    private static Map<String, Consumer<ExportDraft>> rules() {
        Map<String, Consumer<ExportDraft>> rules = new HashMap<>();
        wiring(rules);
        gates(rules);
        plexers(rules);
        arithmetic(rules);
        memory(rules);
        inputOutput(rules);
        return rules;
    }

    // --- Wiring ---

    private static void wiring(Map<String, Consumer<ExportDraft>> rules) {
        rules.put(StandardComponentTypes.INPUT_PIN, draft -> pin(draft, false));
        rules.put(StandardComponentTypes.OUTPUT_PIN, draft -> pin(draft, true));
        rules.put(StandardComponentTypes.TUNNEL_ENTRANCE, draft -> tunnel(draft, LogisimFacing.WEST));
        rules.put(StandardComponentTypes.TUNNEL_EXIT, draft -> tunnel(draft, LogisimFacing.EAST));
        rules.put(StandardComponentTypes.BUS_SPLITTER, draft -> splitter(draft, false));
        rules.put(StandardComponentTypes.BUS_JOINER, draft -> splitter(draft, true));
        rules.put(StandardComponentTypes.CONSTANT, draft -> constant(draft, draft.integer(StandardComponentTypes.WIDTH),
                ConstantValue.bitsOf(draft.text(StandardComponentTypes.VALUE),
                        draft.integer(StandardComponentTypes.WIDTH)).orElse(0)));
        rules.put(StandardComponentTypes.CLOCK, LogisimExportRules::clock);
        rules.put(WiringTypes.BIT_EXTENDER, draft -> draft.part(LogisimLibraries.WIRING, "Bit Extender")
                .attribute("in_width", draft.integer(WiringTypes.INPUT_WIDTH))
                .attribute("out_width", draft.integer(WiringTypes.OUTPUT_WIDTH))
                .attribute("type", draft.text(WiringTypes.FILL).equals(WiringTypes.FILL_SIGN) ? "sign" : "zero"));
        rules.put(WiringTypes.BIT_SELECTOR, draft -> draft.part(LogisimLibraries.PLEXERS, "BitSelector")
                .attribute("width", draft.integer(WiringTypes.INPUT_WIDTH))
                .attribute("group", draft.integer(WiringTypes.OUTPUT_WIDTH))
                .facing(LogisimFacing.EAST));
    }

    /** A Logisim pin: an input one starts two-valued, as Logisim's toolbar places it. */
    private static void pin(ExportDraft draft, boolean output) {
        draft.part(LogisimLibraries.WIRING, "Pin")
                .facing(output ? LogisimFacing.WEST : LogisimFacing.EAST)
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .labelled();
        if (output) {
            draft.attribute("output", true).attribute("labelloc", "east");
        } else {
            draft.attribute("tristate", false);
        }
    }

    /** A Logisim tunnel has no direction; it is as wide as the net its name joins. */
    private static void tunnel(ExportDraft draft, LogisimFacing natural) {
        draft.part(LogisimLibraries.WIRING, "Tunnel")
                .facing(natural)
                .attribute("width", Math.max(1, draft.context().netWidth().applyAsInt(0)))
                .attribute("label", Tunnels.nameOf(draft.component()));
    }

    /**
     * A «Separador de bus» or «Unión de bus»: one Logisim end per pin, each bit sent to its pin's end ({@code none}
     * for a bit in no pin). The ends come out on the side and in the order VSim's pins do: a splitter's ends go
     * east below the bus ({@code appear=right}), a joiner's come from the west below it ({@code appear=left}).
     */
    private static void splitter(ExportDraft draft, boolean joiner) {
        int width = draft.integer(StandardComponentTypes.WIDTH);
        String groupsText = draft.text(StandardComponentTypes.GROUPS);
        BitGroups groups = groupsText.isBlank() ? BitGroups.onePerPin(width) : BitGroups.parse(groupsText, width);
        draft.part(LogisimLibraries.WIRING, "Splitter")
                .facing(joiner ? LogisimFacing.WEST : LogisimFacing.EAST)
                .attribute("fanout", groups.pinCount())
                .attribute("incoming", width)
                .attribute("appear", joiner ? "left" : "right")
                .endsByPosition()
                .end("bus", 0);
        String[] ends = new String[width];
        Arrays.fill(ends, LogisimDefaults.NO_END);
        for (int pin = 0; pin < groups.pinCount(); pin++) {
            draft.end("end" + pin, pin + 1);
            for (int bit : groups.bitsOf(pin)) {
                ends[bit] = String.valueOf(pin);
            }
        }
        for (int bit = 0; bit < width; bit++) {
            draft.attribute("bit" + bit, ends[bit]);
        }
    }

    private static ExportDraft constant(ExportDraft draft, int width, long value) {
        return draft.part(LogisimLibraries.WIRING, "Constant")
                .facing(LogisimFacing.EAST)
                .attribute("width", width)
                .attribute("value", "0x" + Long.toHexString(value));
    }

    /**
     * Logisim's clock counts ticks, not time: each half takes {@link LogisimTiming#HALF_PERIOD} VSim units per tick
     * (what the import reads), at least one; times that are not whole ticks are noted.
     */
    private static void clock(ExportDraft draft) {
        int timeUp = draft.integer("time-up");
        int timeDown = draft.integer("time-down");
        int ticksUp = Math.max(1, Math.round((float) timeUp / LogisimTiming.HALF_PERIOD));
        int ticksDown = Math.max(1, Math.round((float) timeDown / LogisimTiming.HALF_PERIOD));
        draft.part(LogisimLibraries.WIRING, "Clock")
                .facing(LogisimFacing.EAST)
                .attribute("highDuration", ticksUp)
                .attribute("lowDuration", ticksDown)
                .labelled();
        if (timeUp != ticksUp * LogisimTiming.HALF_PERIOD || timeDown != ticksDown * LogisimTiming.HALF_PERIOD) {
            draft.approximated(LogisimNotes.EXPORT_CLOCK_TICKS, String.valueOf(ticksUp), String.valueOf(ticksDown));
        }
    }

    // --- Gates ---

    private static void gates(Map<String, Consumer<ExportDraft>> rules) {
        rules.put(StandardComponentTypes.AND, draft -> gate(draft, "AND Gate", "NAND Gate"));
        rules.put(StandardComponentTypes.OR, draft -> gate(draft, "OR Gate", "NOR Gate"));
        rules.put(StandardComponentTypes.XOR, draft -> gate(draft, "XOR Gate", "XNOR Gate"));
        rules.put(StandardComponentTypes.BUFFER, draft -> draft.part(LogisimLibraries.GATES,
                        draft.bool(StandardComponentTypes.NEGATE) ? "NOT Gate" : "Buffer")
                .facing(LogisimFacing.EAST)
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .labelled());
        rules.put(StandardComponentTypes.TRI_STATE, LogisimExportRules::triState);
    }

    /**
     * A loose control leaves VSim's tri-state buffer off (Z) and gives an error in Logisim's
     * (in Logisim): a real difference, noted.
     */
    private static void triState(ExportDraft draft) {
        draft.part(LogisimLibraries.GATES,
                        draft.bool(StandardComponentTypes.NEGATE) ? "Controlled Inverter" : "Controlled Buffer")
                .facing(LogisimFacing.EAST)
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .labelled();
        if (!draft.isConnected(TRI_STATE_CONTROL)) {
            draft.different(LogisimNotes.EXPORT_LOOSE_BUFFER_CONTROL);
        }
    }

    /** VSim's XOR of more than two inputs is odd parity, Logisim's «odd» option ({@code xor}). */
    private static void gate(ExportDraft draft, String plain, String negated) {
        int inputs = draft.integer(StandardComponentTypes.GATE_INPUTS);
        draft.part(LogisimLibraries.GATES, draft.bool(StandardComponentTypes.NEGATE) ? negated : plain)
                .facing(LogisimFacing.EAST)
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .attribute("inputs", inputs)
                .labelled();
        if (draft.component().typeId().equals(StandardComponentTypes.XOR) && inputs > 2) {
            draft.attribute("xor", "odd");
        }
    }

    // --- Plexers ---

    private static void plexers(Map<String, Consumer<ExportDraft>> rules) {
        rules.put(StandardComponentTypes.MUX, draft -> draft.part(LogisimLibraries.PLEXERS, "Multiplexer")
                .facing(LogisimFacing.EAST)
                .attribute("select", log2(draft.integer("inputs")))
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .attribute("enable", false));
        rules.put(LogisimPartRules.DEMUX, draft -> draft.part(LogisimLibraries.PLEXERS, "Demultiplexer")
                .facing(LogisimFacing.EAST)
                .attribute("select", log2(draft.integer("outputs")))
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .attribute("enable", false));
        rules.put(StandardComponentTypes.DECODER, draft -> draft.part(LogisimLibraries.PLEXERS, "Decoder")
                .facing(LogisimFacing.EAST)
                .attribute("select", draft.integer("inputBits"))
                .attribute("enable", true));
        rules.put(LogisimPartRules.PRIORITY_ENCODER, draft -> draft.part(LogisimLibraries.PLEXERS,
                        "Priority Encoder")
                .facing(LogisimFacing.EAST)
                .attribute("select", log2(draft.integer("inputs"))));
    }

    // --- Arithmetic ---

    private static void arithmetic(Map<String, Consumer<ExportDraft>> rules) {
        rules.put(StandardComponentTypes.ADDER, draft -> block(draft, "Adder"));
        rules.put(ArithmeticTypes.SUBTRACTOR, draft -> block(draft, "Subtractor"));
        rules.put(ArithmeticTypes.NEGATOR, draft -> block(draft, "Negator"));
        rules.put(StandardComponentTypes.COMPARATOR, draft -> block(draft, "Comparator")
                .attribute("mode", draft.text(StandardComponentTypes.SIGNEDNESS)
                        .equals(StandardComponentTypes.SIGNEDNESS_UNSIGNED) ? "unsigned" : "twosComplement"));
        rules.put(ArithmeticTypes.MULTIPLIER, draft -> {
            block(draft, "Multiplier");
            boolean signed = draft.text(StandardComponentTypes.SIGNEDNESS)
                    .equals(StandardComponentTypes.SIGNEDNESS_SIGNED);
            // Logisim multiplies Java ints: signed at 32 bits, unsigned below (LogisimPartRules.multiplier).
            if (signed != (draft.integer(StandardComponentTypes.WIDTH) == Integer.SIZE)) {
                draft.approximated(LogisimNotes.EXPORT_SIGNEDNESS);
            }
        });
        rules.put(ArithmeticTypes.DIVIDER, draft -> {
            block(draft, "Divider");
            if (draft.text(StandardComponentTypes.SIGNEDNESS).equals(StandardComponentTypes.SIGNEDNESS_SIGNED)) {
                draft.approximated(LogisimNotes.EXPORT_SIGNEDNESS);
            }
        });
        rules.put(ArithmeticTypes.SHIFTER, draft -> block(draft, "Shifter")
                .attribute("shift", switch (draft.text(ArithmeticTypes.SHIFT_TYPE)) {
                    case ArithmeticTypes.LOGICAL_RIGHT -> "lr";
                    case ArithmeticTypes.ARITHMETIC_RIGHT -> "ar";
                    case ArithmeticTypes.ROTATE_LEFT -> "rl";
                    case ArithmeticTypes.ROTATE_RIGHT -> "rr";
                    default -> "ll";
                }));
        rules.put(ArithmeticTypes.BIT_COUNTER, draft -> block(draft, "BitAdder").attribute("inputs", 1));
    }

    private static ExportDraft block(ExportDraft draft, String name) {
        return draft.part(LogisimLibraries.ARITHMETIC, name)
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH));
    }

    // --- Memory ---

    private static void memory(Map<String, Consumer<ExportDraft>> rules) {
        rules.put(StandardComponentTypes.FLIP_FLOP_DATA, draft -> flipFlop(draft, "D Flip-Flop"));
        rules.put(SequentialTypes.FLIP_FLOP_T, draft -> flipFlop(draft, "T Flip-Flop"));
        rules.put(SequentialTypes.FLIP_FLOP_JK, draft -> flipFlop(draft, "J-K Flip-Flop"));
        rules.put(SequentialTypes.FLIP_FLOP_SR, draft -> flipFlop(draft, "S-R Flip-Flop"));
        rules.put(StandardComponentTypes.REGISTER, draft -> draft.part(LogisimLibraries.MEMORY, "Register")
                .attribute("width", draft.integer(StandardComponentTypes.WIDTH))
                .attribute("trigger", draft.text(StandardComponentTypes.TRIGGER))
                .labelled());
        rules.put(StandardComponentTypes.COUNTER, LogisimExportRules::counter);
        rules.put(StandardComponentTypes.RAM, LogisimExportRules::ram);
        rules.put(StandardComponentTypes.ROM, LogisimExportRules::rom);
    }

    /**
     * VSim's counter loads whenever LD = 1; Logisim's counts down with LD = 1 unless CT
     * (VSim's EN, which a loose pin leaves at 1) is 0, and loads only with CT = 0: a wired LD is a real difference,
     * noted.
     */
    private static void counter(ExportDraft draft) {
        int width = draft.integer(StandardComponentTypes.WIDTH);
        draft.part(LogisimLibraries.MEMORY, "Counter")
                .attribute("width", width)
                .attribute("max", "0x" + Long.toHexString(width >= Long.SIZE ? -1L : (1L << width) - 1))
                .attribute("trigger", StandardParameters.FALLING)
                .labelled();
        if (draft.isConnected(COUNTER_LOAD)) {
            draft.different(LogisimNotes.EXPORT_COUNTER_LOAD);
        }
    }

    /** VSim's flip-flops change on the falling edge, one of Logisim's triggers. */
    private static void flipFlop(ExportDraft draft, String name) {
        draft.part(LogisimLibraries.MEMORY, name).attribute("trigger", StandardParameters.FALLING).labelled();
    }

    /**
     * Logisim's RAM writes on the rising edge and keeps no contents in the file: another edge, a level write and
     * initial contents are noted. What it writes with differs too: with separate data, when
     * WE ≠ 0, so a loose WE (never a write in VSim) writes on every edge; with combined data it has no WE and writes
     * while OE = 0. Both are real differences, noted. A RAM with one pin per address bit has no Logisim equivalent.
     */
    private static void ram(ExportDraft draft) {
        if (!memoryOnABus(draft)) {
            return;
        }
        boolean separate = draft.text(MemoryTypes.DATA).equals(MemoryTypes.DATA_SEPARATE);
        memoryPart(draft, "RAM");
        draft.attribute("bus", separate ? "separate" : "combined");
        if (!separate) {
            draft.different(LogisimNotes.EXPORT_RAM_WITHOUT_WRITE_ENABLE);
        } else if (!draft.isConnected(WRITE_ENABLE)) {
            draft.different(LogisimNotes.EXPORT_LOOSE_WRITE_ENABLE);
        }
        if (!draft.text(StandardParameters.EDGE.name()).equals(StandardParameters.RISING)) {
            draft.approximated(LogisimNotes.EXPORT_RAM_EDGE);
        }
        if (!draft.text(MemoryTypes.WRITE).equals(MemoryTypes.WRITE_CLOCK)) {
            draft.approximated(LogisimNotes.EXPORT_RAM_WRITE);
        }
        if (!draft.text(MemoryTypes.CONTENTS).isBlank() || !draft.text(MemoryTypes.LINK).isBlank()) {
            draft.approximated(LogisimNotes.EXPORT_MEMORY_CONTENTS);
        }
    }

    /** The ROM's words as Logisim's {@code contents}: a header and the body of a {@code v2.0 raw} image. */
    private static void rom(ExportDraft draft) {
        if (!memoryOnABus(draft)) {
            return;
        }
        int width = draft.integer(MemoryTypes.WIDTH);
        int addressBits = memoryPart(draft, "ROM");
        String raw = new LogisimRawFormat().write(new MemoryFile(MemoryHeader.empty(),
                MemoryContents.decode(draft.text(MemoryTypes.CONTENTS))), width);
        String words = raw.substring(raw.indexOf('\n') + 1);
        draft.attribute(MemoryTypes.CONTENTS, "addr/data: " + addressBits + " " + width + "\n"
                + (words.isEmpty() ? NO_WORDS : words));
        if (!draft.text(MemoryTypes.LINK).isBlank()) {
            draft.approximated(LogisimNotes.EXPORT_MEMORY_CONTENTS);
        }
    }

    private static boolean memoryOnABus(ExportDraft draft) {
        if (draft.text(MemoryTypes.CONNECTION).equals(MemoryTypes.CONNECTION_BUS)) {
            return true;
        }
        draft.leaveOut(LogisimNotes.EXPORT_UNSUPPORTED_PART);
        return false;
    }

    /** The memory's part with its widths; words that are not a power of two round up, noted. The address bits. */
    private static int memoryPart(ExportDraft draft, String name) {
        int words = draft.integer(MemoryTypes.WORDS);
        int addressBits = Math.max(1, Integer.SIZE - Integer.numberOfLeadingZeros(words - 1));
        if (1 << addressBits != words) {
            draft.approximated(LogisimNotes.EXPORT_MEMORY_WORDS, String.valueOf(words),
                    String.valueOf(1 << addressBits));
        }
        draft.part(LogisimLibraries.MEMORY, name)
                .attribute("addrWidth", addressBits)
                .attribute("dataWidth", draft.integer(MemoryTypes.WIDTH));
        return addressBits;
    }

    // --- Input / output ---

    private static void inputOutput(Map<String, Consumer<ExportDraft>> rules) {
        rules.put(StandardComponentTypes.SWITCH, LogisimExportRules::switchPart);
        rules.put(StandardComponentTypes.PUSH_BUTTON, draft -> draft.part(LogisimLibraries.INPUT_OUTPUT, "Button")
                .facing(LogisimFacing.EAST)
                .labelled());
        rules.put(StandardComponentTypes.LED, LogisimExportRules::led);
        rules.put(StandardComponentTypes.SEVEN_SEGMENTS_DISPLAY, draft -> draft.part(LogisimLibraries.INPUT_OUTPUT,
                "Hex Digit Display"));
        rules.put(StandardComponentTypes.KEYBOARD, LogisimExportRules::keyboard);
        rules.put(StandardComponentTypes.TERMINAL, LogisimExportRules::terminal);
    }

    /** Logisim's keyboard holds at most {@link #MAXIMUM_KEYBOARD_BUFFER} characters: a bigger one is noted. */
    private static void keyboard(ExportDraft draft) {
        int capacity = draft.integer(KeyboardTypes.CAPACITY);
        int buffer = Math.clamp(capacity, 1, MAXIMUM_KEYBOARD_BUFFER);
        draft.part(LogisimLibraries.INPUT_OUTPUT, "Keyboard")
                .attribute("buflen", buffer)
                .attribute("trigger", draft.text(StandardParameters.EDGE.name()));
        if (buffer != capacity) {
            draft.approximated(LogisimNotes.EXPORT_KEYBOARD_BUFFER, String.valueOf(capacity), String.valueOf(buffer));
        }
        noteCharacterWidth(draft);
    }

    /** Logisim's TTY has at most {@link #MAXIMUM_TTY_ROWS} × {@link #MAXIMUM_TTY_COLUMNS}: a bigger one is noted. */
    private static void terminal(ExportDraft draft) {
        int rows = draft.integer(TerminalTypes.ROWS);
        int columns = draft.integer(TerminalTypes.COLUMNS);
        int ttyRows = Math.clamp(rows, 1, MAXIMUM_TTY_ROWS);
        int ttyColumns = Math.clamp(columns, 1, MAXIMUM_TTY_COLUMNS);
        draft.part(LogisimLibraries.INPUT_OUTPUT, "TTY")
                .attribute("rows", ttyRows)
                .attribute("cols", ttyColumns)
                .attribute("trigger", draft.text(StandardParameters.EDGE.name()));
        if (ttyRows != rows || ttyColumns != columns) {
            draft.approximated(LogisimNotes.EXPORT_TERMINAL_SIZE, String.valueOf(rows), String.valueOf(columns),
                    String.valueOf(ttyRows), String.valueOf(ttyColumns));
        }
        noteCharacterWidth(draft);
    }

    /**
     * A Switch is a Logisim input pin in the main circuit, which starts at 0 (its value is noted when it is not);
     * inside a component a pin would be one more pin of the component, so there it is a constant with the switch's
     * value, noted.
     */
    private static void switchPart(ExportDraft draft) {
        int width = draft.integer(StandardComponentTypes.WIDTH);
        String value = draft.text(StandardComponentTypes.VALUE);
        if (draft.context().main()) {
            draft.part(LogisimLibraries.WIRING, "Pin")
                    .facing(LogisimFacing.EAST)
                    .attribute("width", width)
                    .attribute("tristate", false)
                    .labelled()
                    .endsByPosition()
                    .end("pin", 0);
            if (ConstantValue.bitsOf(value, width).orElse(0) != 0) {
                draft.approximated(LogisimNotes.EXPORT_SWITCH_VALUE, value);
            }
            return;
        }
        constant(draft, width, ConstantValue.bitsOf(value, width).orElse(0))
                .approximated(LogisimNotes.EXPORT_SWITCH_AS_CONSTANT, value);
    }

    /** Logisim's LED is one bit, in one of any colours: the VSim colour's own. */
    private static void led(ExportDraft draft) {
        if (draft.integer(StandardComponentTypes.WIDTH) != 1) {
            draft.leaveOut(LogisimNotes.EXPORT_UNSUPPORTED_PART);
            return;
        }
        int rgb = LogisimPartRules.LED_COLORS.getOrDefault(draft.text("color"), 0xF00000);
        draft.part(LogisimLibraries.INPUT_OUTPUT, "LED")
                .facing(LogisimFacing.SOUTH)
                .attribute("color", String.format("#%06x", rgb))
                .labelled();
    }

    private static void noteCharacterWidth(ExportDraft draft) {
        int bits = draft.integer(KeyEncoding.DATA_WIDTH);
        if (bits != KeyEncoding.SEVEN_BITS) {
            draft.approximated(LogisimNotes.EXPORT_CHARACTER_WIDTH, String.valueOf(bits));
        }
    }

    /** The bits that count {@code lines} lines (a power of two). */
    private static int log2(int lines) {
        return Integer.SIZE - 1 - Integer.numberOfLeadingZeros(lines);
    }
}
