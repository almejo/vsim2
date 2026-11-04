package cl.almejo.vsim.core.io.logisim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Where Logisim 2.7 puts the ends of its parts, as measured on Logisim itself: {@code ends-fixture.ends} is what
 * Logisim 2.7.1 computed for every part of {@code ends-fixture.circ} in each facing and many attribute combinations,
 * and every rule here reproduces it. The ends come in Logisim's order and are named after the VSim pin each becomes
 * ({@link LogisimEnd}). Probes and texts have none (a probe becomes a text and joins nothing); parts VSim does not
 * import at all (a joystick, a shift register, …) have none either.
 */
public final class LogisimEnds {

    /** Logisim's NOT and controlled inverter come narrow (20 px) or wide (30 px). */
    private static final String NARROW_NOT = "20";
    /** Logisim gates measure 30, 50 or 70 px along their axis; past 40 and past 60 a few input counts spread wider. */
    private static final int MEDIUM_GATE = 40;
    private static final int WIDE_GATE = 60;
    /** The grid Logisim lays ends on, and the room a negation bubble takes. */
    private static final int STEP = 10;
    /** A RAM's or ROM's data end, which the RAM's bus option makes drive only or both ways. */
    private static final String MEMORY_DATA = "Q";
    private static final LogisimPoint ORIGIN = new LogisimPoint(0, 0);

    private static final Map<String, Function<Placement, List<LogisimEnd>>> TABLE = table();

    private LogisimEnds() {
    }

    /** The ends of {@code component}; a subcircuit instance's need {@code project} for its circuit's pins. */
    public static List<LogisimEnd> of(LogisimComponent component, LogisimProject project) {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(project, "project");
        if (component.isSubcircuit()) {
            return project.circuit(component.name())
                    .map(circuit -> SubcircuitEnds.of(component, circuit))
                    .orElse(List.of());
        }
        Function<Placement, List<LogisimEnd>> rule = TABLE.get(
                LogisimPartRules.key(component.library(), component.name()));
        return rule == null ? List.of() : rule.apply(new Placement(component));
    }

    private static Map<String, Function<Placement, List<LogisimEnd>>> table() {
        Map<String, Function<Placement, List<LogisimEnd>>> table = new HashMap<>();
        wiring(table);
        gates(table);
        plexers(table);
        arithmetic(table);
        memory(table);
        inputOutput(table);
        return table;
    }

    private static void wiring(Map<String, Function<Placement, List<LogisimEnd>>> table) {
        String wiring = LogisimLibraries.WIRING;
        table.put(LogisimPartRules.key(wiring, "Pin"), placement -> placement.single("pin",
                placement.bool("output") ? LogisimEnd.Role.INPUT : LogisimEnd.Role.OUTPUT, placement.width()));
        table.put(LogisimPartRules.key(wiring, "Tunnel"), placement -> placement.single("pin", LogisimEnd.Role.ALIAS,
                placement.width()));
        table.put(LogisimPartRules.key(wiring, "Constant"), LogisimEnds::driver);
        table.put(LogisimPartRules.key(wiring, "Power"), LogisimEnds::driver);
        table.put(LogisimPartRules.key(wiring, "Ground"), LogisimEnds::driver);
        table.put(LogisimPartRules.key(wiring, "Clock"), placement -> placement.single("out", LogisimEnd.Role.OUTPUT,
                1));
        table.put(LogisimPartRules.key(wiring, "Bit Extender"), LogisimEnds::bitExtender);
        table.put(LogisimPartRules.key(wiring, "Splitter"), LogisimEnds::splitter);
    }

    private static void gates(Map<String, Function<Placement, List<LogisimEnd>>> table) {
        String gates = LogisimLibraries.GATES;
        table.put(LogisimPartRules.key(gates, "AND Gate"), placement -> gate(placement, 0, false));
        table.put(LogisimPartRules.key(gates, "NAND Gate"), placement -> gate(placement, 0, true));
        table.put(LogisimPartRules.key(gates, "OR Gate"), placement -> gate(placement, 0, false));
        table.put(LogisimPartRules.key(gates, "NOR Gate"), placement -> gate(placement, 0, true));
        table.put(LogisimPartRules.key(gates, "XOR Gate"), placement -> gate(placement, 10, false));
        table.put(LogisimPartRules.key(gates, "XNOR Gate"), placement -> gate(placement, 10, true));
        table.put(LogisimPartRules.key(gates, "Odd Parity"), placement -> gate(placement, 0, false));
        table.put(LogisimPartRules.key(gates, "Even Parity"), placement -> gate(placement, 0, false));
        table.put(LogisimPartRules.key(gates, "NOT Gate"), placement -> inverter(placement,
                placement.text("size").equals(NARROW_NOT) ? 20 : 30));
        table.put(LogisimPartRules.key(gates, "Buffer"), placement -> inverter(placement, 20));
        table.put(LogisimPartRules.key(gates, "Controlled Buffer"), placement -> controlledBuffer(placement, 20));
        table.put(LogisimPartRules.key(gates, "Controlled Inverter"), placement -> controlledBuffer(placement,
                placement.text("size").equals(NARROW_NOT) ? 20 : 30));
    }

    private static void plexers(Map<String, Function<Placement, List<LogisimEnd>>> table) {
        String plexers = LogisimLibraries.PLEXERS;
        table.put(LogisimPartRules.key(plexers, "Multiplexer"), LogisimEnds::multiplexer);
        table.put(LogisimPartRules.key(plexers, "Demultiplexer"), LogisimEnds::demultiplexer);
        table.put(LogisimPartRules.key(plexers, "Decoder"), LogisimEnds::decoder);
        table.put(LogisimPartRules.key(plexers, "Priority Encoder"), LogisimEnds::priorityEncoder);
        table.put(LogisimPartRules.key(plexers, "BitSelector"), LogisimEnds::bitSelector);
    }

    private static void arithmetic(Map<String, Function<Placement, List<LogisimEnd>>> table) {
        String arithmetic = LogisimLibraries.ARITHMETIC;
        table.put(LogisimPartRules.key(arithmetic, "Adder"), placement -> twoOperands(placement, "S",
                new Extra("Cin", LogisimEnd.Role.INPUT, 1, true), new Extra("Cout", LogisimEnd.Role.OUTPUT, 1, true)));
        table.put(LogisimPartRules.key(arithmetic, "Subtractor"), placement -> twoOperands(placement, "D",
                new Extra("Bin", LogisimEnd.Role.INPUT, 1, true), new Extra("Bout", LogisimEnd.Role.OUTPUT, 1, true)));
        table.put(LogisimPartRules.key(arithmetic, "Multiplier"), placement -> twoOperands(placement, "P",
                new Extra("Cin", LogisimEnd.Role.INPUT, placement.width(), false),
                new Extra("H", LogisimEnd.Role.OUTPUT, placement.width(), true)));
        table.put(LogisimPartRules.key(arithmetic, "Divider"), placement -> twoOperands(placement, "Q",
                new Extra("upper", LogisimEnd.Role.INPUT, placement.width(), false),
                new Extra("R", LogisimEnd.Role.OUTPUT, placement.width(), true)));
        table.put(LogisimPartRules.key(arithmetic, "Negator"), placement -> placement.ends()
                .add("A", -40, 0, LogisimEnd.Role.INPUT, placement.width())
                .add("Y", 0, 0, LogisimEnd.Role.OUTPUT, placement.width()).list());
        table.put(LogisimPartRules.key(arithmetic, "Comparator"), placement -> placement.ends()
                .add("A", -40, -10, LogisimEnd.Role.INPUT, placement.width())
                .add("B", -40, 10, LogisimEnd.Role.INPUT, placement.width())
                .add("A>B", 0, -10, LogisimEnd.Role.OUTPUT, 1)
                .add("A=B", 0, 0, LogisimEnd.Role.OUTPUT, 1)
                .add("A<B", 0, 10, LogisimEnd.Role.OUTPUT, 1).list());
        table.put(LogisimPartRules.key(arithmetic, "Shifter"), LogisimEnds::shifter);
        table.put(LogisimPartRules.key(arithmetic, "BitAdder"), LogisimEnds::bitAdder);
    }

    private static void memory(Map<String, Function<Placement, List<LogisimEnd>>> table) {
        String memory = LogisimLibraries.MEMORY;
        table.put(LogisimPartRules.key(memory, "D Flip-Flop"), placement -> flipFlop(placement, List.of("d")));
        table.put(LogisimPartRules.key(memory, "T Flip-Flop"), placement -> flipFlop(placement, List.of("t")));
        table.put(LogisimPartRules.key(memory, "J-K Flip-Flop"), placement -> flipFlop(placement, List.of("j", "k")));
        table.put(LogisimPartRules.key(memory, "S-R Flip-Flop"), placement -> flipFlop(placement, List.of("s", "r")));
        table.put(LogisimPartRules.key(memory, "Register"), placement -> placement.ends()
                .add("Q", 0, 0, LogisimEnd.Role.OUTPUT, placement.width())
                .add("D", -30, 0, LogisimEnd.Role.INPUT, placement.width())
                .add("CLK", -20, 20, LogisimEnd.Role.INPUT, 1)
                .add("CLR", -10, 20, LogisimEnd.Role.INPUT, 1)
                .add("EN", -30, 10, LogisimEnd.Role.INPUT, 1).list());
        table.put(LogisimPartRules.key(memory, "Counter"), placement -> placement.ends()
                .add("Q", 0, 0, LogisimEnd.Role.OUTPUT, placement.width())
                .add("D", -30, 0, LogisimEnd.Role.INPUT, placement.width())
                .add("CLK", -20, 20, LogisimEnd.Role.INPUT, 1)
                .add("CLR", -10, 20, LogisimEnd.Role.INPUT, 1)
                .add("LD", -30, -10, LogisimEnd.Role.INPUT, 1)
                .add("EN", -30, 10, LogisimEnd.Role.INPUT, 1)
                .add("C", 0, 10, LogisimEnd.Role.OUTPUT, 1).list());
        table.put(LogisimPartRules.key(memory, "RAM"), LogisimEnds::ram);
        table.put(LogisimPartRules.key(memory, "ROM"), placement -> memoryEnds(placement, MEMORY_DATA,
                LogisimEnd.Role.OUTPUT).list());
    }

    private static void inputOutput(Map<String, Function<Placement, List<LogisimEnd>>> table) {
        String io = LogisimLibraries.INPUT_OUTPUT;
        table.put(LogisimPartRules.key(io, "LED"), placement -> placement.single("in", LogisimEnd.Role.INPUT, 1));
        table.put(LogisimPartRules.key(io, "Button"), placement -> placement.single("out", LogisimEnd.Role.OUTPUT,
                1));
        table.put(LogisimPartRules.key(io, "Hex Digit Display"), placement -> placement.ends()
                .add("bus", 0, 0, LogisimEnd.Role.INPUT, 4)
                .lacking("DP", 10, 0, LogisimEnd.Role.INPUT, 1).list());
        table.put(LogisimPartRules.key(io, "TTY"), placement -> placement.ends()
                .add("CLR", 20, 10, LogisimEnd.Role.INPUT, 1)
                .add("CLK", 0, 0, LogisimEnd.Role.INPUT, 1)
                .add("WE", 10, 10, LogisimEnd.Role.INPUT, 1)
                .add("D", 0, -10, LogisimEnd.Role.INPUT, 7).list());
        table.put(LogisimPartRules.key(io, "Keyboard"), placement -> placement.ends()
                .add("CLR", 20, 10, LogisimEnd.Role.INPUT, 1)
                .add("CLK", 0, 0, LogisimEnd.Role.INPUT, 1)
                .add("RE", 10, 10, LogisimEnd.Role.INPUT, 1)
                .add("AV", 130, 10, LogisimEnd.Role.OUTPUT, 1)
                .add("D", 140, 10, LogisimEnd.Role.OUTPUT, 7).list());
    }

    // --- Wiring ---

    private static List<LogisimEnd> driver(Placement placement) {
        return placement.single("out", LogisimEnd.Role.OUTPUT, placement.width());
    }

    /** Filling from an input adds a third, one-bit end, which VSim's extender has not. */
    private static List<LogisimEnd> bitExtender(Placement placement) {
        Ends ends = placement.ends()
                .add("out", 0, 0, LogisimEnd.Role.OUTPUT, placement.integer("out_width"))
                .add("in", -40, 0, LogisimEnd.Role.INPUT, placement.integer("in_width"));
        if (placement.text("type").equals("input")) {
            ends.lacking("fill", -20, -20, LogisimEnd.Role.INPUT, 1);
        }
        return ends.list();
    }

    /**
     * The bus at the location, then one end per fanout 20 px out the way the splitter faces, 10 px apart across it.
     * Counting the ends down a splitter facing east or west and leftwards on one facing north or south, {@code appear}
     * puts them all on the facing's left side («left»), all on its right side («right») or around the bus («center»
     * and the older «legacy»). An end is as wide as the bits its {@code bitN} send to it (0 when none).
     */
    private static List<LogisimEnd> splitter(Placement placement) {
        int fanout = placement.integer("fanout");
        int incoming = placement.integer("incoming");
        int[] bitsPerEnd = splitterEndWidths(placement, fanout, incoming);
        LogisimFacing facing = placement.facing();
        int lane = firstSplitterLane(placement.text("appear"), facing, fanout);
        boolean countsDown = facing == LogisimFacing.EAST || facing == LogisimFacing.WEST;
        Ends ends = placement.ends().add("bus", 0, 0, LogisimEnd.Role.ALIAS, incoming);
        for (int end = 0; end < fanout; end++) {
            int across = countsDown ? lane : -lane;
            ends.across("end" + end, 2 * STEP, across, LogisimEnd.Role.ALIAS, bitsPerEnd[end], true);
            lane += STEP;
        }
        return ends.list();
    }

    /** How many bits each end of a splitter carries, from its {@code bitN} (an end number or {@code none}). */
    private static int[] splitterEndWidths(Placement placement, int fanout, int incoming) {
        int[] bitsPerEnd = new int[fanout];
        for (int bit = 0; bit < incoming; bit++) {
            String end = placement.textOr("bit" + bit, LogisimDefaults.NO_END).strip();
            if (!end.isEmpty() && end.chars().allMatch(Character::isDigit)) {
                int index = Integer.parseInt(end);
                if (index < fanout) {
                    bitsPerEnd[index]++;
                }
            }
        }
        return bitsPerEnd;
    }

    /**
     * Where a splitter's end 0 sits, counted along its lanes (down for a splitter facing east or west, leftwards for
     * one facing north or south, so a lane is negative on the left of a splitter facing east or south). Centred, an
     * even fanout has one end more above the bus, or right of it.
     */
    private static int firstSplitterLane(String appear, LogisimFacing facing, int fanout) {
        boolean leftIsNegative = facing == LogisimFacing.EAST || facing == LogisimFacing.SOUTH;
        boolean horizontal = facing == LogisimFacing.EAST || facing == LogisimFacing.WEST;
        return switch (appear) {
            case "left" -> leftIsNegative ? -fanout * STEP : STEP;
            case "right" -> leftIsNegative ? STEP : -fanout * STEP;
            default -> horizontal ? -(fanout / 2) * STEP : -((fanout - 1) / 2) * STEP;
        };
    }

    // --- Gates ---

    /**
     * A gate with inputs: the output at the location and the inputs in a column behind it, as far back as the gate's
     * {@code size}, plus {@code extraLength} (the XOR family's curved back) and a bubble's 10 px for a negated output.
     * A negated input sits 10 px further back still. Across the gate the inputs keep the same order whichever way it
     * faces: top to bottom when it faces east or west, left to right when it faces north or south.
     */
    private static List<LogisimEnd> gate(Placement placement, int extraLength, boolean negatedOutput) {
        int size = placement.integer("size");
        int inputs = placement.integer("inputs");
        int width = placement.width();
        int column = size + extraLength + (negatedOutput ? STEP : 0);
        Ends ends = placement.ends().add("out", 0, 0, LogisimEnd.Role.OUTPUT, width);
        for (int input = 0; input < inputs; input++) {
            int behind = column + (placement.bool("negate" + input) ? STEP : 0);
            ends.across("in" + input, -behind, gateInputAcross(inputs, size, input), LogisimEnd.Role.INPUT, width,
                    true);
        }
        return ends.list();
    }

    /**
     * How far across the output's line input {@code input} sits: 10 px apart, centred on that line and leaving it free
     * when the count is even, except for two to four inputs on a 50 or 70 px gate, which spread wider.
     */
    private static int gateInputAcross(int inputs, int size, int input) {
        int[] spread = widerSpread(inputs, size);
        if (spread.length > 0) {
            return spread[input];
        }
        int middle = inputs / 2;
        int across = (input - middle) * STEP;
        boolean skipsTheMiddle = inputs % 2 == 0 && input >= middle;
        return skipsTheMiddle ? across + STEP : across;
    }

    /** The positions Logisim gives a few inputs on its bigger gates (none: the plain 10 px column). */
    private static int[] widerSpread(int inputs, int size) {
        boolean medium = size > MEDIUM_GATE;
        boolean wide = size > WIDE_GATE;
        return switch (inputs) {
            case 2 -> medium ? new int[]{-20, 20} : new int[0];
            case 3 -> wide ? new int[]{-30, 0, 30} : medium ? new int[]{-20, 0, 20} : new int[0];
            case 4 -> wide ? new int[]{-20, 0, 20, 40} : new int[0];
            default -> new int[0];
        };
    }

    /** A NOT or buffer: the output at the location, the input {@code length} behind it. */
    private static List<LogisimEnd> inverter(Placement placement, int length) {
        return placement.ends()
                .add("out", 0, 0, LogisimEnd.Role.OUTPUT, placement.width())
                .add("in", -length, 0, LogisimEnd.Role.INPUT, placement.width()).list();
    }

    /** Controlled buffer: the control 10 px to the right (or left) of the body's middle. */
    private static List<LogisimEnd> controlledBuffer(Placement placement, int length) {
        int beyond = length - 20;
        int controlSide = placement.text("control").equals("left") ? -10 : 10;
        return placement.ends()
                .add("out", 0, 0, LogisimEnd.Role.OUTPUT, placement.width())
                .add("in", -20 - beyond, 0, LogisimEnd.Role.INPUT, placement.width())
                .add("enable", -10 - beyond, controlSide, LogisimEnd.Role.INPUT, 1).list();
    }

    // --- Plexers ---

    /**
     * The data inputs in a column behind the output (30 px for two, 40 px for more), the select 20 px behind on the
     * {@code selloc} edge and the enable, which VSim's multiplexer has not, 10 px behind on that edge; the output last.
     */
    private static List<LogisimEnd> multiplexer(Placement placement) {
        int select = placement.integer("select");
        int width = placement.width();
        int column = plexerColumnDepth(select);
        Ends ends = placement.ends();
        for (int input = 0; input < 1 << select; input++) {
            ends.across("D" + input, -column, plexerColumnAcross(select, input), LogisimEnd.Role.INPUT, width, true);
        }
        plexerControls(placement, ends, select, -1);
        return ends.add("Y", 0, 0, LogisimEnd.Role.OUTPUT, width).list();
    }

    /** A multiplexer turned around: the outputs in a column ahead of the input, the controls ahead, the input last. */
    private static List<LogisimEnd> demultiplexer(Placement placement) {
        int select = placement.integer("select");
        int width = placement.width();
        int column = plexerColumnDepth(select);
        Ends ends = placement.ends();
        for (int output = 0; output < 1 << select; output++) {
            ends.across("Y" + output, column, plexerColumnAcross(select, output), LogisimEnd.Role.OUTPUT, width,
                    true);
        }
        plexerControls(placement, ends, select, 1);
        return ends.add("D", 0, 0, LogisimEnd.Role.INPUT, width).list();
    }

    /** How far from the location a plexer's column of data ends stands: 30 px with one select bit, else 40. */
    private static int plexerColumnDepth(int select) {
        return select == 1 ? 3 * STEP : 4 * STEP;
    }

    /** Across a plexer's column: two ends 20 px apart, or 10 px apart from half the column above the location. */
    private static int plexerColumnAcross(int select, int index) {
        if (select == 1) {
            return index == 0 ? -STEP : STEP;
        }
        return (index - (1 << select) / 2) * STEP;
    }

    /**
     * The select and the enable of a multiplexer ({@code towards} -1, behind the location) or demultiplexer (+1,
     * ahead), 20 and 10 px away along the part on its {@code selloc} edge: the bottom («bl») or top («tr») one of a
     * part facing east or west, the left or right one of a part facing north or south.
     */
    private static void plexerControls(Placement placement, Ends ends, int select, int towards) {
        int halfHeight = select == 1 ? 2 * STEP : (1 << select) / 2 * STEP;
        boolean bottomOrLeft = placement.text("selloc").equals("bl");
        int edge = bottomOrLeft == placement.horizontal() ? halfHeight : -halfHeight;
        ends.across("S", towards * 2 * STEP, edge, LogisimEnd.Role.INPUT, select, true);
        if (placement.bool("enable")) {
            ends.across("EN", towards * STEP, edge, LogisimEnd.Role.INPUT, 1, false);
        }
    }

    /**
     * The one-bit outputs 10 px ahead (20 with more than one select bit) in a row on the {@code selloc} side of the
     * location, the select at the location and the enable 10 px behind. With one select bit the two outputs are 20 px
     * apart; on the side counted negative across the part the row ends 10 px from the location, on the other it
     * starts at the location (10 px from it with one select bit).
     */
    private static List<LogisimEnd> decoder(Placement placement) {
        int select = placement.integer("select");
        int outputs = 1 << select;
        int spacing = select == 1 ? 2 * STEP : STEP;
        boolean bottomOrLeft = placement.text("selloc").equals("bl");
        boolean negativeSide = bottomOrLeft == placement.horizontal();
        int first = negativeSide ? -STEP - (outputs - 1) * spacing : select == 1 ? STEP : 0;
        int ahead = select == 1 ? STEP : 2 * STEP;
        Ends ends = placement.ends();
        for (int output = 0; output < outputs; output++) {
            ends.across("Y" + output, ahead, first + output * spacing, LogisimEnd.Role.OUTPUT, 1, true);
        }
        ends.add("A", 0, 0, LogisimEnd.Role.INPUT, select);
        if (placement.bool("enable")) {
            ends.add("EN", -STEP, 0, LogisimEnd.Role.INPUT, 1);
        }
        return ends.list();
    }

    /**
     * The inputs in a column 40 px behind the output, 10 px apart from half the column above the location (less 10);
     * the output at the location; the enable in just under the column and the enable out (group select) just over it,
     * 20 px behind (VSim's encoder has neither); and the valid output 10 px under the location.
     */
    private static List<LogisimEnd> priorityEncoder(Placement placement) {
        int select = placement.integer("select");
        int inputs = 1 << select;
        int halfColumn = inputs / 2 * STEP;
        Ends ends = placement.ends();
        for (int input = 0; input < inputs; input++) {
            ends.across("I" + input, -4 * STEP, STEP - halfColumn + input * STEP, LogisimEnd.Role.INPUT, 1, true);
        }
        ends.add("A", 0, 0, LogisimEnd.Role.OUTPUT, select);
        ends.across("EI", -2 * STEP, halfColumn + STEP, LogisimEnd.Role.INPUT, 1, false);
        ends.across("EO", -2 * STEP, -halfColumn, LogisimEnd.Role.OUTPUT, 1, false);
        return ends.across("V", 0, STEP, LogisimEnd.Role.OUTPUT, 1, true).list();
    }

    /**
     * The chosen group out at the location, the data in 30 px behind, and the select 10 px behind on the bottom edge
     * (the left one when facing north or south), as wide as needed to number the groups (at least one bit).
     */
    private static List<LogisimEnd> bitSelector(Placement placement) {
        int width = placement.width();
        int group = placement.integer("group");
        int groups = (width + group - 1) / group;
        int bottomOrLeft = placement.horizontal() ? STEP : -STEP;
        return placement.ends()
                .add("out", 0, 0, LogisimEnd.Role.OUTPUT, group)
                .add("in", -3 * STEP, 0, LogisimEnd.Role.INPUT, width)
                .across("S", -STEP, bottomOrLeft, LogisimEnd.Role.INPUT, Math.max(1, bitsToNumber(groups)), true)
                .list();
    }

    // --- Arithmetic ---

    /** An end beyond the two operands and the result: a carry, a borrow, a remainder. */
    private record Extra(String name, LogisimEnd.Role role, int width, boolean inVsim) {
    }

    /** The arithmetic block: A and B 40 px behind, the result at the location, the extras above and below. */
    private static List<LogisimEnd> twoOperands(Placement placement, String result, Extra above, Extra below) {
        int width = placement.width();
        Ends ends = placement.ends()
                .add("A", -40, -10, LogisimEnd.Role.INPUT, width)
                .add("B", -40, 10, LogisimEnd.Role.INPUT, width)
                .add(result, 0, 0, LogisimEnd.Role.OUTPUT, width);
        ends.add(above.name(), -20, -20, above.role(), above.width(), above.inVsim());
        ends.add(below.name(), -20, 20, below.role(), below.width(), below.inVsim());
        return ends.list();
    }

    /**
     * The data above and the distance below, 40 px behind; the distance is as wide as needed to count to the width
     * (one bit at least: a one-bit shifter, which the oracle does not show, keeps a one-bit distance).
     */
    private static List<LogisimEnd> shifter(Placement placement) {
        int width = placement.width();
        return placement.ends()
                .add("A", -4 * STEP, -STEP, LogisimEnd.Role.INPUT, width)
                .add("S", -4 * STEP, STEP, LogisimEnd.Role.INPUT, Math.max(1, bitsToNumber(width)))
                .add("Y", 0, 0, LogisimEnd.Role.OUTPUT, width).list();
    }

    /**
     * The count at the location, wide enough for every input bit set, and the inputs in a column 40 px behind it:
     * one in line, two 20 px apart, more 10 px apart from half of the rest above the line. VSim's bit counter has one
     * input, so the others have no VSim pin.
     */
    private static List<LogisimEnd> bitAdder(Placement placement) {
        int inputs = placement.integer("inputs");
        int width = placement.width();
        Ends ends = placement.ends().add("Y", 0, 0, LogisimEnd.Role.OUTPUT, bitsToNumber(inputs * width + 1));
        for (int input = 0; input < inputs; input++) {
            int across = inputs == 2 ? (2 * input - 1) * STEP : (input - (inputs - 1) / 2) * STEP;
            ends.add(input == 0 ? "A" : "in" + input, -4 * STEP, across, LogisimEnd.Role.INPUT, width, input == 0);
        }
        return ends.list();
    }

    /** The bits it takes to tell {@code count} values apart (0 for one value). */
    private static int bitsToNumber(int count) {
        return count <= 1 ? 0 : Integer.SIZE - Integer.numberOfLeadingZeros(count - 1);
    }

    // --- Memory ---

    /**
     * {@code AbstractFlipFlop}: the inputs (one at the bottom left, or two at the top and bottom), the clock, Q and
     * Q̅, then reset, preset and enable below, which VSim's flip-flops have not.
     */
    private static List<LogisimEnd> flipFlop(Placement placement, List<String> inputs) {
        Ends ends = placement.ends();
        if (inputs.size() == 1) {
            ends.add(inputs.getFirst(), -40, 20, LogisimEnd.Role.INPUT, 1)
                    .add("clock", -40, 0, LogisimEnd.Role.INPUT, 1);
        } else {
            ends.add(inputs.get(0), -40, 0, LogisimEnd.Role.INPUT, 1)
                    .add(inputs.get(1), -40, 20, LogisimEnd.Role.INPUT, 1)
                    .add("clock", -40, 10, LogisimEnd.Role.INPUT, 1);
        }
        return ends.add("q", 0, 0, LogisimEnd.Role.OUTPUT, 1)
                .add("nQ", 0, 20, LogisimEnd.Role.OUTPUT, 1)
                .lacking("reset", -10, 30, LogisimEnd.Role.INPUT, 1)
                .lacking("preset", -30, 30, LogisimEnd.Role.INPUT, 1)
                .lacking("enable", -20, 30, LogisimEnd.Role.INPUT, 1).list();
    }

    /**
     * RAM ports: data, address and chip select like the ROM, then output enable and clear (VSim's RAM
     * has neither), the clock unless asynchronous, and with separate data the write enable and data in (VSim's D).
     * With a shared bus the data end goes both ways and is VSim's D.
     */
    private static List<LogisimEnd> ram(Placement placement) {
        String bus = placement.text("bus");
        boolean separate = bus.equals("separate");
        Ends ends = separate ? memoryEnds(placement, MEMORY_DATA, LogisimEnd.Role.OUTPUT)
                : memoryEnds(placement, "D", LogisimEnd.Role.BIDIRECTIONAL);
        ends.lacking("OE", -50, 40, LogisimEnd.Role.INPUT, 1)
                .lacking("CLR", -30, 40, LogisimEnd.Role.INPUT, 1);
        if (!bus.equals("asynch")) {
            ends.add("CLK", -70, 40, LogisimEnd.Role.INPUT, 1);
        }
        if (separate) {
            ends.add("WE", -110, 40, LogisimEnd.Role.INPUT, 1)
                    .add("D", -140, 20, LogisimEnd.Role.INPUT, placement.integer("dataWidth"));
        }
        return ends.list();
    }

    private static Ends memoryEnds(Placement placement, String data, LogisimEnd.Role dataRole) {
        return placement.ends()
                .add(data, 0, 0, dataRole, placement.integer("dataWidth"))
                .add("A", -140, 0, LogisimEnd.Role.INPUT, placement.integer("addrWidth"))
                .add("CS", -90, 40, LogisimEnd.Role.INPUT, 1);
    }

    /** A component being measured: its location, attributes and facing. */
    private record Placement(LogisimComponent component) {

        LogisimFacing facing() {
            return LogisimFacing.parse(component.attributes().textOr("facing", "east"));
        }

        String text(String name) {
            return component.attributes().text(name);
        }

        String textOr(String name, String fallback) {
            return component.attributes().textOr(name, fallback);
        }

        int integer(String name) {
            return component.attributes().integer(name);
        }

        boolean bool(String name) {
            return component.attributes().has(name) && component.attributes().bool(name);
        }

        int width() {
            return integer("width");
        }

        /** Whether the part faces east or west, so that across it is up and down. */
        boolean horizontal() {
            LogisimFacing facing = facing();
            return facing == LogisimFacing.EAST || facing == LogisimFacing.WEST;
        }

        Ends ends() {
            return new Ends(component.location(), facing());
        }

        List<LogisimEnd> single(String name, LogisimEnd.Role role, int width) {
            return ends().add(name, 0, 0, role, width).list();
        }
    }

    /**
     * The ends of one component as they are added. {@link #add} takes an offset for the part facing east and turns
     * it with the facing as Logisim does (parts whose ports never turn
     * face east); {@link #across} takes the distance along the facing and a fixed distance across it, for the many
     * parts whose ends keep their order across them whichever way they face.
     */
    private static final class Ends {
        private final LogisimPoint location;
        private final LogisimFacing facing;
        private final List<LogisimEnd> ends = new ArrayList<>();

        Ends(LogisimPoint location, LogisimFacing facing) {
            this.location = location;
            this.facing = facing;
        }

        Ends add(String name, int forward, int right, LogisimEnd.Role role, int width) {
            return add(name, forward, right, role, width, true);
        }

        Ends add(String name, int forward, int right, LogisimEnd.Role role, int width, boolean inVsim) {
            LogisimPoint offset = facing.translate(ORIGIN, forward, right);
            return put(name, offset.x(), offset.y(), role, width, inVsim);
        }

        Ends lacking(String name, int forward, int right, LogisimEnd.Role role, int width) {
            return add(name, forward, right, role, width, false);
        }

        /**
         * An end {@code forward} along the facing and {@code across} on the screen's axis across it: down for a part
         * facing east or west, rightwards for one facing north or south.
         */
        Ends across(String name, int forward, int across, LogisimEnd.Role role, int width, boolean inVsim) {
            LogisimPoint along = facing.translate(ORIGIN, forward, 0);
            boolean horizontal = facing == LogisimFacing.EAST || facing == LogisimFacing.WEST;
            return horizontal ? put(name, along.x(), across, role, width, inVsim)
                    : put(name, across, along.y(), role, width, inVsim);
        }

        private Ends put(String name, int deltaX, int deltaY, LogisimEnd.Role role, int width, boolean inVsim) {
            ends.add(new LogisimEnd(name, new LogisimPoint(location.x() + deltaX, location.y() + deltaY), role, width,
                    inVsim));
            return this;
        }

        List<LogisimEnd> list() {
            return List.copyOf(ends);
        }
    }

    /**
     * A subcircuit instance's ends: one per {@code circ-port} of the circuit's {@code <appear>} (or of the box Logisim
     * draws for a circuit without one, {@link LogisimDefaultAppearance}), relative to its anchor, turned from the anchor's
     * facing to the instance's, in Logisim's order (by x, then y). Each is named by its pin's location in the circuit;
     * a circuit's input pin is an input of the instance.
     */
    private static final class SubcircuitEnds {

        private SubcircuitEnds() {
        }

        static List<LogisimEnd> of(LogisimComponent instance, LogisimCircuit circuit) {
            Map<LogisimPoint, LogisimComponent> pins = new HashMap<>();
            for (LogisimComponent component : circuit.components()) {
                if (component.library().equals(LogisimLibraries.WIRING) && component.name().equals("Pin")) {
                    pins.putIfAbsent(component.location(), component);
                }
            }
            LogisimAppearance appearance = circuit.appearance().shapes().isEmpty()
                    ? LogisimDefaultAppearance.build(firstAtEachPlace(circuit)) : circuit.appearance();
            Shape shape = Shape.of(appearance);
            LogisimFacing facing = LogisimFacing.parse(instance.attributes().textOr("facing", "east"));
            List<LogisimEnd> ends = new ArrayList<>();
            for (Map.Entry<LogisimPoint, LogisimPoint> port : shape.ports().entrySet()) {
                LogisimComponent pin = pins.get(port.getValue());
                if (pin == null) {
                    continue;
                }
                LogisimPoint offset = turn(new LogisimPoint(port.getKey().x() - shape.anchor().x(),
                        port.getKey().y() - shape.anchor().y()), shape.facing(), facing);
                boolean output = pin.attributes().has("output") && pin.attributes().bool("output");
                ends.add(new LogisimEnd(pin.location().x() + "," + pin.location().y(),
                        new LogisimPoint(instance.location().x() + offset.x(), instance.location().y() + offset.y()),
                        output ? LogisimEnd.Role.OUTPUT : LogisimEnd.Role.INPUT,
                        pin.attributes().integer("width"), true));
            }
            ends.sort(Comparator.comparingInt((LogisimEnd end) -> end.point().x())
                    .thenComparingInt(end -> end.point().y()));
            return List.copyOf(ends);
        }

        /** The circuit's pins for its default box, in file order, only the first of several at the same place. */
        private static List<LogisimInterfacePin> firstAtEachPlace(LogisimCircuit circuit) {
            Map<LogisimPoint, LogisimInterfacePin> first = new LinkedHashMap<>();
            LogisimInterfacePin.of(circuit).forEach(pin -> first.putIfAbsent(pin.location(), pin));
            return List.copyOf(first.values());
        }

        /** Counter-clockwise quarter turns from {@code from} to {@code to}. */
        private static LogisimPoint turn(LogisimPoint offset, LogisimFacing from, LogisimFacing to) {
            int quarterTurns = Math.floorMod(from.ordinal() - to.ordinal(), 4);
            return switch (quarterTurns) {
                case 1 -> new LogisimPoint(offset.y(), -offset.x());
                case 2 -> new LogisimPoint(-offset.x(), -offset.y());
                case 3 -> new LogisimPoint(-offset.y(), offset.x());
                default -> offset;
            };
        }
    }

    /** A symbol's anchor, its facing and its ports (connection point → the pin inside the circuit). */
    private record Shape(LogisimPoint anchor, LogisimFacing facing, Map<LogisimPoint, LogisimPoint> ports) {

        static Shape of(LogisimAppearance appearance) {
            LogisimPoint anchor = new LogisimPoint(0, 0);
            LogisimFacing facing = LogisimFacing.EAST;
            Map<LogisimPoint, LogisimPoint> ports = new HashMap<>();
            for (LogisimAppearance.Shape shape : appearance.shapes()) {
                if (shape instanceof LogisimAppearance.Port port) {
                    ports.put(centre(port.x(), port.y(), port.width(), port.height()), port.pin());
                } else if (shape instanceof LogisimAppearance.Anchor found) {
                    anchor = centre(found.x(), found.y(), found.width(), found.height());
                    facing = LogisimFacing.parse(found.facing());
                }
            }
            return new Shape(anchor, facing, ports);
        }

        /** The box's centre, rounded, as Logisim reads a pin's place. */
        private static LogisimPoint centre(int x, int y, int width, int height) {
            return new LogisimPoint((int) Math.round(x + width / 2.0), (int) Math.round(y + height / 2.0));
        }
    }

}
