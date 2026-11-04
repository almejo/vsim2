package cl.almejo.vsim.core.io.logisim;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * The attributes a Logisim dialect gives a placed component that the file leaves out. Logisim only writes the
 * attributes that differ from the part's default (it leaves out the ones equal to the default), so a reader must fill the
 * rest the way Logisim's own reader does (with the part's defaults): a component
 * starts from its factory's fresh attributes, never from the {@code <lib><tool>} attributes of the file, which only
 * shape the next placement in Logisim's toolbar.
 *
 * <p>{@link #logisim27()} is Logisim 2.7's table, taken from its source ({@code std/**}, {@code SplitterAttributes})
 * and checked by loading a fixture with every built-in part in Logisim 2.7.1 itself. A second dialect (Logisim
 * Evolution) would be another instance of this class with its own table and rules, handed to {@link LogisimReader}.
 *
 * <p>Three defaults are not a fixed text: a gate has a {@code negateN} for each of its inputs, a splitter a
 * {@code bitN} for each bit ({@link #splitterBits}), and a counter's {@code max} follows its width.
 */
public final class LogisimDefaults {

    /** The version from which a multiplexer, demultiplexer and decoder have an enable input by default. */
    private static final LogisimVersion ENABLE_BY_DEFAULT = new LogisimVersion(2, 6, 3, 220);
    /** The version from which a splitter is drawn «left» by default (before it was «legacy»). */
    private static final LogisimVersion SPLITTER_LEFT = new LogisimVersion(2, 6, 3, 202);
    /** Logisim's default label font. */
    private static final String LABEL_FONT = "SansSerif plain 12";
    /** A splitter end: no end. */
    static final String NO_END = "none";
    private static final Set<String> GATES_WITH_INPUTS = Set.of("AND Gate", "OR Gate", "NAND Gate", "NOR Gate",
            "XOR Gate", "XNOR Gate", "Odd Parity", "Even Parity");
    private static final Set<String> PLEXERS_WITH_ENABLE = Set.of("Multiplexer", "Demultiplexer", "Decoder");

    private final Map<String, Map<String, Map<String, String>>> parts;

    private LogisimDefaults(Map<String, Map<String, Map<String, String>>> parts) {
        this.parts = parts;
    }

    /** Logisim 2.7.x's defaults. */
    public static LogisimDefaults logisim27() {
        Table table = new Table();
        wiring(table);
        gates(table);
        plexers(table);
        arithmetic(table);
        memory(table);
        inputOutput(table);
        table.part(LogisimLibraries.BASE, "Text", "text", "", "font", LABEL_FONT, "halign", "center", "valign", "base");
        table.part(LogisimLibraries.SUBCIRCUIT, "", "facing", "east", "label", "", "labelloc", "north", "labelfont",
                LABEL_FONT);
        return new LogisimDefaults(table.parts);
    }

    /** Whether {@code library} is one of this dialect's built-in libraries. */
    public boolean knowsLibrary(String library) {
        return !library.isEmpty() && parts.containsKey(library);
    }

    /** Whether {@code library} has a built-in part called {@code name} (any subcircuit instance has one). */
    public boolean knowsPart(String library, String name) {
        Map<String, Map<String, String>> libraryParts = parts.get(library);
        return libraryParts != null && (library.isEmpty() || libraryParts.containsKey(name));
    }

    /**
     * The attributes of a component of {@code library}/{@code name} that the file writes as {@code written}, saved by
     * {@code version}: the part's defaults in Logisim's attribute order with the written values over them, then the
     * written ones Logisim does not list. A part this dialect does not know keeps only what is written.
     */
    public LogisimAttributes resolve(String library, String name, Map<String, String> written,
                                     LogisimVersion version) {
        if (!knowsPart(library, name)) {
            return LogisimAttributes.of(written);
        }
        Map<String, String> fixed = parts.get(library).get(library.isEmpty() ? "" : name);
        Map<String, String> values = new LinkedHashMap<>(fixed);
        versionDefaults(library, name, version, values);
        values.putAll(written);
        if (library.equals(LogisimLibraries.GATES) && GATES_WITH_INPUTS.contains(name)) {
            addNegatedInputs(values);
        } else if (library.equals(LogisimLibraries.WIRING) && name.equals("Splitter")) {
            addSplitterBits(values, written);
        } else if (library.equals(LogisimLibraries.MEMORY) && name.equals("Counter") && !written.containsKey("max")) {
            values.put("max", "0x" + Long.toHexString(mask(widthOf(values, "width"))));
        } else if (library.equals(LogisimLibraries.MEMORY) && name.equals("ROM") && !written.containsKey("contents")) {
            values.put("contents", "addr/data: " + widthOf(values, "addrWidth") + " " + widthOf(values, "dataWidth")
                    + "\n0\n");
        }
        return LogisimAttributes.of(values);
    }

    /**
     * The end of each bit of a splitter with {@code fanout} ends and {@code incoming} bits whose file writes
     * {@code written} ({@code bitN} → end number or {@code none}), as Logisim's reader leaves it: first the even split
     * ({@link #evenSplit}); then each written {@code bitN} that names an existing end (or
     * {@code none}); and a bit the file leaves out gets the attribute's own default, end N, when that end exists
     * (Logisim's default for that attribute): with 4 ends and 32 bits, bits 0..3 go to ends 0..3 and bits 4-7, 8-15,
     * 16-23, 24-31 to ends 0, 1, 2, 3. Each entry is the end's number as text or {@code none}.
     */
    static List<String> splitterBits(int fanout, int incoming, Map<String, String> written) {
        int[] distribution = evenSplit(fanout, incoming);
        String[] ends = new String[incoming];
        for (int bit = 0; bit < incoming; bit++) {
            String text = written.get("bit" + bit);
            ends[bit] = String.valueOf(distribution[bit]);
            if (text == null) {
                if (bit < fanout) {
                    ends[bit] = String.valueOf(bit);
                }
            } else if (text.strip().equals(NO_END)) {
                ends[bit] = NO_END;
            } else {
                OptionalInt end = writtenEnd(text, fanout);
                if (end.isPresent()) {
                    ends[bit] = String.valueOf(end.getAsInt());
                }
            }
        }
        return List.of(ends);
    }

    private static OptionalInt writtenEnd(String text, int fanout) {
        try {
            int end = Integer.parseInt(text.strip());
            return end >= 0 && end < fanout ? OptionalInt.of(end) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /**
     * The bit-to-end table Logisim starts a splitter from: the bits in order, in runs as even as they can be, the
     * longer runs (one bit more) first. {@code ends-fixture.ends} shows it: 8 bits over 3 ends go 3, 3, 2 and 32 bits
     * over 5 ends go 7, 7, 6, 6, 6; with more ends than bits, the first ends take one bit each.
     */
    private static int[] evenSplit(int fanout, int bits) {
        int[] endOfBit = new int[bits];
        int shortRun = bits / fanout;
        int longerRuns = bits % fanout;
        int bit = 0;
        for (int end = 0; end < fanout && bit < bits; end++) {
            int run = end < longerRuns ? shortRun + 1 : shortRun;
            for (int taken = 0; taken < run; taken++) {
                endOfBit[bit++] = end;
            }
        }
        return endOfBit;
    }

    private static void versionDefaults(String library, String name, LogisimVersion version,
                                        Map<String, String> values) {
        if (library.equals(LogisimLibraries.PLEXERS) && PLEXERS_WITH_ENABLE.contains(name)) {
            values.put("enable", String.valueOf(version.isAtLeast(ENABLE_BY_DEFAULT)));
        } else if (library.equals(LogisimLibraries.WIRING) && name.equals("Splitter")) {
            values.put("appear", version.isAtLeast(SPLITTER_LEFT) ? "left" : "legacy");
        }
    }

    /** {@code negate0} … {@code negate(inputs-1)}, false unless written. */
    private static void addNegatedInputs(Map<String, String> values) {
        int inputs = widthOf(values, "inputs");
        for (int input = 0; input < inputs; input++) {
            values.putIfAbsent("negate" + input, "false");
        }
    }

    private static void addSplitterBits(Map<String, String> values, Map<String, String> written) {
        int fanout = widthOf(values, "fanout");
        int incoming = widthOf(values, "incoming");
        List<String> ends = splitterBits(fanout, incoming, written);
        for (int bit = 0; bit < incoming; bit++) {
            values.put("bit" + bit, ends.get(bit));
        }
    }

    /** A width or count attribute; an unreadable one counts as 1 (the mapper reports the value itself). */
    private static int widthOf(Map<String, String> values, String name) {
        try {
            return Math.max(1, Integer.parseInt(values.get(name).strip().toLowerCase(Locale.ROOT)));
        } catch (NumberFormatException | NullPointerException exception) {
            return 1;
        }
    }

    private static long mask(int width) {
        return width >= Long.SIZE ? -1L : (1L << width) - 1;
    }

    private static void wiring(Table table) {
        String wiring = LogisimLibraries.WIRING;
        table.part(wiring, "Splitter", "facing", "east", "fanout", "2", "incoming", "2", "appear", "left");
        table.part(wiring, "Pin", "facing", "east", "output", "false", "width", "1", "tristate", "true",
                "pull", "none", "label", "", "labelloc", "west", "labelfont", LABEL_FONT);
        table.part(wiring, "Probe", "facing", "east", "radix", "2", "label", "", "labelloc", "west",
                "labelfont", LABEL_FONT);
        table.part(wiring, "Tunnel", "facing", "west", "width", "1", "label", "", "labelfont", LABEL_FONT);
        table.part(wiring, "Pull Resistor", "facing", "south", "pull", "0");
        table.part(wiring, "Clock", "facing", "east", "highDuration", "1", "lowDuration", "1", "label", "",
                "labelloc", "west", "labelfont", LABEL_FONT);
        table.part(wiring, "Constant", "facing", "east", "width", "1", "value", "0x1");
        table.part(wiring, "Power", "facing", "north", "width", "1");
        table.part(wiring, "Ground", "facing", "south", "width", "1");
        table.part(wiring, "Transistor", "type", "p", "facing", "east", "gate", "tl", "width", "1");
        table.part(wiring, "Transmission Gate", "facing", "east", "gate", "tl", "width", "1");
        table.part(wiring, "Bit Extender", "in_width", "8", "out_width", "16", "type", "zero");
    }

    private static void gates(Table table) {
        String gates = LogisimLibraries.GATES;
        table.part(gates, "NOT Gate", "facing", "east", "width", "1", "size", "30", "out", "01", "label", "",
                "labelfont", LABEL_FONT);
        table.part(gates, "Buffer", "facing", "east", "width", "1", "out", "01", "label", "", "labelfont",
                LABEL_FONT);
        for (String name : List.of("AND Gate", "OR Gate", "NAND Gate", "NOR Gate", "Odd Parity", "Even Parity")) {
            table.part(gates, name, "facing", "east", "width", "1", "size", "50", "inputs", "5", "out", "01",
                    "label", "", "labelfont", LABEL_FONT);
        }
        for (String name : List.of("XOR Gate", "XNOR Gate")) {
            table.part(gates, name, "facing", "east", "width", "1", "size", "50", "inputs", "5", "out", "01",
                    "label", "", "labelfont", LABEL_FONT, "xor", "1");
        }
        table.part(gates, "Controlled Buffer", "facing", "east", "width", "1", "control", "right", "label", "",
                "labelfont", LABEL_FONT);
        table.part(gates, "Controlled Inverter", "facing", "east", "width", "1", "size", "30", "control", "right",
                "label", "", "labelfont", LABEL_FONT);
    }

    private static void plexers(Table table) {
        String plexers = LogisimLibraries.PLEXERS;
        table.part(plexers, "Multiplexer", "facing", "east", "selloc", "bl", "select", "1", "width", "1",
                "disabled", "Z", "enable", "true");
        table.part(plexers, "Demultiplexer", "facing", "east", "selloc", "bl", "select", "1", "width", "1",
                "tristate", "false", "disabled", "Z", "enable", "true");
        table.part(plexers, "Decoder", "facing", "east", "selloc", "bl", "select", "1", "tristate", "false",
                "disabled", "Z", "enable", "true");
        table.part(plexers, "Priority Encoder", "facing", "east", "select", "3", "disabled", "Z");
        table.part(plexers, "BitSelector", "facing", "east", "width", "8", "group", "1");
    }

    private static void arithmetic(Table table) {
        String arithmetic = LogisimLibraries.ARITHMETIC;
        for (String name : List.of("Adder", "Subtractor", "Multiplier", "Divider", "Negator")) {
            table.part(arithmetic, name, "width", "8");
        }
        table.part(arithmetic, "Comparator", "width", "8", "mode", "twosComplement");
        table.part(arithmetic, "Shifter", "width", "8", "shift", "ll");
        table.part(arithmetic, "BitAdder", "width", "8", "inputs", "1");
        table.part(arithmetic, "BitFinder", "width", "8", "type", "low1");
    }

    private static void memory(Table table) {
        String memory = LogisimLibraries.MEMORY;
        for (String name : List.of("D Flip-Flop", "T Flip-Flop", "J-K Flip-Flop", "S-R Flip-Flop")) {
            table.part(memory, name, "trigger", "rising", "label", "", "labelfont", LABEL_FONT);
        }
        table.part(memory, "Register", "width", "8", "trigger", "rising", "label", "", "labelfont", LABEL_FONT);
        table.part(memory, "Counter", "width", "8", "max", "0xff", "ongoal", "wrap", "trigger", "rising",
                "label", "", "labelfont", LABEL_FONT);
        table.part(memory, "Shift Register", "width", "1", "length", "8", "parallel", "true", "trigger", "rising",
                "label", "", "labelfont", LABEL_FONT);
        table.part(memory, "Random", "width", "8", "seed", "0", "trigger", "rising", "label", "", "labelfont",
                LABEL_FONT);
        table.part(memory, "RAM", "addrWidth", "8", "dataWidth", "8", "bus", "combined");
        table.part(memory, "ROM", "addrWidth", "8", "dataWidth", "8", "contents", "addr/data: 8 8\n0\n");
    }

    private static void inputOutput(Table table) {
        String io = LogisimLibraries.INPUT_OUTPUT;
        table.part(io, "Button", "facing", "east", "color", "#ffffff", "label", "", "labelloc", "center",
                "labelfont", LABEL_FONT, "labelcolor", "#000000");
        table.part(io, "Joystick", "bits", "4", "color", "#ff0000");
        table.part(io, "Keyboard", "buflen", "32", "trigger", "rising");
        table.part(io, "LED", "facing", "west", "color", "#f00000", "offcolor", "#404040", "active", "true",
                "label", "", "labelloc", "center", "labelfont", LABEL_FONT, "labelcolor", "#000000");
        table.part(io, "7-Segment Display", "color", "#f00000", "offcolor", "#dcdcdc", "bg", "#ffffff00",
                "active", "true");
        table.part(io, "Hex Digit Display", "color", "#f00000", "offcolor", "#dcdcdc", "bg", "#ffffff00");
        table.part(io, "DotMatrix", "inputtype", "column", "matrixcols", "5", "matrixrows", "7", "color",
                "#00ff00", "offcolor", "#404040", "persist", "0", "dotshape", "square");
        table.part(io, "TTY", "rows", "8", "cols", "32", "trigger", "rising", "color", "#000000", "bg",
                "#00000040");
    }

    /** The defaults being written: library → part → attribute → text, all in order. */
    private static final class Table {
        private final Map<String, Map<String, Map<String, String>>> parts = new HashMap<>();

        void part(String library, String name, String... namesAndValues) {
            Map<String, String> values = new LinkedHashMap<>();
            for (int index = 0; index < namesAndValues.length; index += 2) {
                values.put(namesAndValues[index], namesAndValues[index + 1]);
            }
            parts.computeIfAbsent(library, ignored -> new HashMap<>()).put(name, Collections.unmodifiableMap(values));
        }
    }
}
