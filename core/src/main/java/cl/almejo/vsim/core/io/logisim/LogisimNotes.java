package cl.almejo.vsim.core.io.logisim;

import java.util.List;

/**
 * The message keys of the {@link ImportNote}s, in the UI bundles under {@code # Importar circuitos de Logisim}. The
 * arguments of each are listed next to it.
 */
public final class LogisimNotes {

    /** A library VSim does not know (a JAR, another file, a newer Logisim's): its description. */
    public static final String UNKNOWN_LIBRARY = "logisimImport.note.unknownLibrary";
    /** A component of a library the file never declares: its name and the library number. */
    public static final String MISSING_LIBRARY = "logisimImport.note.missingLibrary";
    /** A component without a name or a readable location: what the file says of its name and location. */
    public static final String BAD_COMPONENT = "logisimImport.note.badComponent";
    /** A wire whose ends cannot be read: its two ends as written. */
    public static final String BAD_WIRE = "logisimImport.note.badWire";
    /** An element of {@code <appear>} that Logisim 2.7 does not draw either: its tag. */
    public static final String UNKNOWN_SHAPE = "logisimImport.note.unknownShape";
    /** An element of {@code <appear>} with values that cannot be read: its tag. */
    public static final String BAD_SHAPE = "logisimImport.note.badShape";
    /** {@code <main>} names a circuit the file does not have: that name. */
    public static final String MISSING_MAIN = "logisimImport.note.missingMain";

    /** A built-in part VSim has no equivalent for: its Logisim name. */
    public static final String UNSUPPORTED_PART = "logisimImport.note.unsupportedPart";
    /** A part whose attributes cannot be read or that VSim refuses: its Logisim name. */
    public static final String INVALID_PART = "logisimImport.note.invalidPart";
    /** A gate with more inputs than VSim's: the name, its inputs and VSim's most. */
    public static final String TOO_MANY_INPUTS = "logisimImport.note.tooManyInputs";
    /** A plexer with more select bits than VSim's: the name, its select bits and VSim's most. */
    public static final String TOO_MANY_SELECT_BITS = "logisimImport.note.tooManySelectBits";
    /** Some, not all, inputs of an AND/OR negated: a NOT goes in front of each (one more delay unit); how many. */
    public static final String NEGATED_INPUT = "logisimImport.note.negatedInput";
    /** An XOR that is 1 with exactly one input 1, with more than two inputs: VSim's is odd parity; its inputs. */
    public static final String ONE_HOT_XOR = "logisimImport.note.oneHotXor";
    /** A gate with an open-collector output ({@code 0Z} or {@code Z1}): VSim drives 0 and 1; the mode. */
    public static final String GATE_OUTPUT = "logisimImport.note.gateOutput";
    /** A plexer's enable input, which VSim's has not: the name. */
    public static final String ENABLE_INPUT = "logisimImport.note.enableInput";
    /** A demultiplexer or decoder whose unselected outputs float: VSim's give 0; the name. */
    public static final String FLOATING_OUTPUTS = "logisimImport.note.floatingOutputs";
    /** A priority encoder: VSim's has no enable input nor EO/GS outputs. */
    public static final String PRIORITY_ENCODER = "logisimImport.note.priorityEncoder";
    /** The divider: Logisim gives Q = A, R = 0 when B = 0, VSim Q = 0, R = A. */
    public static final String DIVISION_BY_ZERO = "logisimImport.note.divisionByZero";
    /** A part with a delay in Logisim and none in VSim: its name and Logisim's delay. */
    public static final String NO_DELAY = "logisimImport.note.noDelay";
    /** The clock's half period, chosen above the circuit's settling time: the units. */
    public static final String CLOCK_PERIOD = "logisimImport.note.clockPeriod";
    /** A part that triggers otherwise than VSim's (on the falling edge): the name and its trigger. */
    public static final String EDGE = "logisimImport.note.edge";
    /** A counter with a maximum or an action at it that VSim's has not (it wraps at all ones). */
    public static final String COUNTER_GOAL = "logisimImport.note.counterGoal";
    /** A RAM whose data bus is not separate: VSim's shares one D pin; Logisim's bus option. */
    public static final String MEMORY_BUS = "logisimImport.note.memoryBus";
    /** Memory contents that cannot be read: the memory starts empty; the line and the problem's text key. */
    public static final String MEMORY_CONTENTS = "logisimImport.note.memoryContents";
    /** A pin pulled up or down: VSim's pin is not; the pull. */
    public static final String PIN_PULL = "logisimImport.note.pinPull";
    /** An LED lit by 0: VSim's lights with 1. */
    public static final String ACTIVE_LOW = "logisimImport.note.activeLow";
    /** A bit extender that fills with ones or from an input: VSim fills with zeros or the sign; the fill. */
    public static final String EXTENDER_FILL = "logisimImport.note.extenderFill";
    /** A one-bit splitter, which is just a wire: VSim's take 2 bits or more. */
    public static final String NARROW_SPLITTER = "logisimImport.note.narrowSplitter";
    /** A splitter all of whose bits go nowhere. */
    public static final String EMPTY_SPLITTER = "logisimImport.note.emptySplitter";
    /** A terminal bigger than VSim's: Logisim's rows and columns, the ones VSim uses. */
    public static final String TERMINAL_SIZE = "logisimImport.note.terminalSize";

    /** A pin of a circuit's drawing moved to the edge of its box (VSim's pins sit on the edge): the pin's name. */
    public static final String PORT_MOVED = "logisimImport.note.portMoved";

    /** A pin place of a circuit's drawing names no pin of the circuit: the Logisim point it names. */
    public static final String PORT_WITHOUT_PIN = "logisimImport.note.portWithoutPin";

    /** A pin of a circuit without a place in its drawing: put on a free point of the edge; the pin's name. */
    public static final String PIN_WITHOUT_PORT = "logisimImport.note.pinWithoutPort";

    /** A circuit's label turned sideways: VSim's texts stay upright; the label. */
    public static final String LABEL_TURNED = "logisimImport.note.labelTurned";

    /** A text of a circuit's drawing outside its box: moved inside; the text. */
    public static final String TEXT_MOVED = "logisimImport.note.textMoved";

    /** A text of a circuit's drawing in a font VSim does not draw (another family, italic): the font. */
    public static final String TEXT_FONT = "logisimImport.note.textFont";

    /** A drawing with more figures, texts or points than VSim's shapes take: how many are left out. */
    public static final String DRAWING_TOO_BIG = "logisimImport.note.drawingTooBig";

    /** Circuits that use themselves, directly or through others: the chain, as «a › b › a». */
    public static final String RECURSIVE_CIRCUIT = "logisimImport.note.recursiveCircuit";

    /** A connected end of a part whose VSim part has no such pin, so the connection is lost: the part, the end. */
    public static final String MISSING_PIN = "logisimImport.note.missingPin";
    /** A splitter driven from both sides (a partial bus on a shared bus): it becomes a «Unión de bus». */
    public static final String SPLITTER_BOTH_SIDES = "logisimImport.note.splitterBothSides";

    /** A pin that no added wire could join to its net without touching another one: the part and the pin. */
    public static final String UNROUTED_PIN = "logisimImport.note.unroutedPin";
    /** A part with no free place near its Logisim one: it goes apart, below the circuit; the part. */
    public static final String CROWDED_PART = "logisimImport.note.crowdedPart";

    /**
     * The texts that land on a part in VSim, whose parts are bigger than Logisim's: one note per circuit, with the
     * list of «text» / «Logisim part name» pairs.
     */
    public static final String TEXT_OVER_PART = "logisimImport.note.textOverPart";
    /** A RAM with separate data whose WE the file leaves loose: Logisim's writes on every rising edge, VSim's never. */
    public static final String LOOSE_WRITE_ENABLE = "logisimImport.note.looseWriteEnable";
    /**
     * A RAM on a combined or asynchronous bus: Logisim's writes while OE = 0, VSim's only with its WE, which has no
     * Logisim end, so it never writes; the bus option.
     */
    public static final String RAM_WITHOUT_WRITE_ENABLE = "logisimImport.note.ramWithoutWriteEnable";
    /** A counter whose LD is connected: Logisim's counts down with LD = 1 unless CT = 0, VSim's always loads. */
    public static final String COUNTER_LOAD = "logisimImport.note.counterLoad";
    /** A controlled buffer or inverter whose control is loose: Logisim's gives an error, VSim's is off (Z). */
    public static final String LOOSE_BUFFER_CONTROL = "logisimImport.note.looseBufferControl";

    /** An instance of a circuit the file does not have (or that uses itself): left out; the circuit's name. */
    public static final String MISSING_CIRCUIT = "logisimImport.note.missingCircuit";

    // --- Export (LogisimExporter). The circuit is VSim's (the document's or a component's name), never a point. The
    // first two arguments of a note about a part are what it is and its label (EXPORT_PART_KEYS): the UI turns them
    // into the part's name. ---

    /** A part Logisim has no equivalent for: left out. */
    public static final String EXPORT_UNSUPPORTED_PART = "logisimExport.note.unsupportedPart";
    /** A part wider than Logisim's 32 bits: left out; then its width. */
    public static final String EXPORT_TOO_WIDE = "logisimExport.note.tooWide";
    /** A connected pin the Logisim part lacks, so that connection is lost: then the pin. */
    public static final String EXPORT_MISSING_PIN = "logisimExport.note.missingPin";
    /** A clock whose times are not whole Logisim ticks: then the ticks high and low it got. */
    public static final String EXPORT_CLOCK_TICKS = "logisimExport.note.clockTicks";
    /** A RAM that writes on the falling edge: Logisim's writes on the rising one. */
    public static final String EXPORT_RAM_EDGE = "logisimExport.note.ramEdge";
    /** A RAM that writes while WE is on (level): Logisim's writes on the clock's edge. */
    public static final String EXPORT_RAM_WRITE = "logisimExport.note.ramWrite";
    /** A RAM with initial contents, or a memory linked to a file: Logisim's file keeps only a ROM's own words. */
    public static final String EXPORT_MEMORY_CONTENTS = "logisimExport.note.memoryContents";
    /** A memory whose words are not a power of two: then its words and the words Logisim's has. */
    public static final String EXPORT_MEMORY_WORDS = "logisimExport.note.memoryWords";
    /** A multiplier or divider whose sign Logisim's does not share (it multiplies signed only at 32 bits). */
    public static final String EXPORT_SIGNEDNESS = "logisimExport.note.signedness";
    /** A Terminal or Teclado of other than 7 bits: Logisim's use 7-bit ASCII; then its bits. */
    public static final String EXPORT_CHARACTER_WIDTH = "logisimExport.note.characterWidth";
    /** A Switch inside a component: a Logisim pin there would be one more pin of the component; then its value. */
    public static final String EXPORT_SWITCH_AS_CONSTANT = "logisimExport.note.switchAsConstant";
    /** A RAM with separate data and WE loose: VSim's never writes, Logisim's writes on every rising edge. */
    public static final String EXPORT_LOOSE_WRITE_ENABLE = "logisimExport.note.looseWriteEnable";
    /** A RAM with shared data: VSim's writes with WE, Logisim's (combined) has no WE and writes with OE = 0. */
    public static final String EXPORT_RAM_WITHOUT_WRITE_ENABLE = "logisimExport.note.ramWithoutWriteEnable";
    /** A counter with LD wired: Logisim's counts down with LD = 1 unless its CT (VSim's EN) is 0. */
    public static final String EXPORT_COUNTER_LOAD = "logisimExport.note.counterLoad";
    /** A tri-state buffer whose control is loose: off (Z) in VSim, an error in Logisim. */
    public static final String EXPORT_LOOSE_BUFFER_CONTROL = "logisimExport.note.looseBufferControl";
    /** A Switch of the main circuit that is not 0: Logisim's input pin starts at 0; then its value. */
    public static final String EXPORT_SWITCH_VALUE = "logisimExport.note.switchValue";
    /**
     * A Terminal bigger than Logisim's TTY: it gets Logisim's largest; then its rows and columns, then the rows and
     * columns it got.
     */
    public static final String EXPORT_TERMINAL_SIZE = "logisimExport.note.terminalSize";
    /** A Teclado that holds more than Logisim's keyboard: then its capacity and the one it got. */
    public static final String EXPORT_KEYBOARD_BUFFER = "logisimExport.note.keyboardBuffer";
    /** A pin no added wire could join to its net without touching another one: then the pin. */
    public static final String EXPORT_UNROUTED_PIN = "logisimExport.note.unroutedPin";
    /** A part with no free place near its VSim one: it goes apart, below the circuit. */
    public static final String EXPORT_CROWDED_PART = "logisimExport.note.crowdedPart";
    /** Sticky notes and frames, which Logisim has not: how many were left out. */
    public static final String EXPORT_ANNOTATIONS = "logisimExport.note.annotations";

    /**
     * The export notes about a part: their first two arguments are what it is and its label. What it is is the type id
     * of one of VSim's parts, which the report turns into the part's name, or the name of the custom component an
     * instance is of (never {@code subcircuit:<id>}).
     */
    public static final List<String> EXPORT_PART_KEYS = List.of(EXPORT_UNSUPPORTED_PART, EXPORT_TOO_WIDE,
            EXPORT_MISSING_PIN, EXPORT_CLOCK_TICKS, EXPORT_RAM_EDGE, EXPORT_RAM_WRITE, EXPORT_MEMORY_CONTENTS,
            EXPORT_MEMORY_WORDS, EXPORT_SIGNEDNESS, EXPORT_CHARACTER_WIDTH, EXPORT_SWITCH_AS_CONSTANT,
            EXPORT_SWITCH_VALUE, EXPORT_TERMINAL_SIZE, EXPORT_KEYBOARD_BUFFER, EXPORT_LOOSE_WRITE_ENABLE,
            EXPORT_RAM_WITHOUT_WRITE_ENABLE, EXPORT_COUNTER_LOAD, EXPORT_LOOSE_BUFFER_CONTROL, EXPORT_UNROUTED_PIN,
            EXPORT_CROWDED_PART);

    /** Every key above, for the bundle check. */
    public static final List<String> MESSAGE_KEYS = List.of(UNKNOWN_LIBRARY, MISSING_LIBRARY, BAD_COMPONENT, BAD_WIRE,
            UNKNOWN_SHAPE, BAD_SHAPE, MISSING_MAIN, UNSUPPORTED_PART, INVALID_PART, TOO_MANY_INPUTS,
            TOO_MANY_SELECT_BITS, NEGATED_INPUT, ONE_HOT_XOR, GATE_OUTPUT, ENABLE_INPUT, FLOATING_OUTPUTS,
            PRIORITY_ENCODER, DIVISION_BY_ZERO, NO_DELAY, CLOCK_PERIOD, EDGE, COUNTER_GOAL, MEMORY_BUS,
            MEMORY_CONTENTS, PIN_PULL, ACTIVE_LOW, EXTENDER_FILL, NARROW_SPLITTER, EMPTY_SPLITTER, TERMINAL_SIZE,
            PORT_MOVED, PORT_WITHOUT_PIN, PIN_WITHOUT_PORT, LABEL_TURNED, TEXT_MOVED, TEXT_FONT, DRAWING_TOO_BIG,
            RECURSIVE_CIRCUIT, MISSING_PIN, SPLITTER_BOTH_SIDES, UNROUTED_PIN,
            CROWDED_PART, TEXT_OVER_PART, LOOSE_WRITE_ENABLE, RAM_WITHOUT_WRITE_ENABLE, COUNTER_LOAD,
            LOOSE_BUFFER_CONTROL,
            MISSING_CIRCUIT, EXPORT_UNSUPPORTED_PART, EXPORT_TOO_WIDE, EXPORT_MISSING_PIN,
            EXPORT_CLOCK_TICKS, EXPORT_RAM_EDGE, EXPORT_RAM_WRITE, EXPORT_MEMORY_CONTENTS, EXPORT_MEMORY_WORDS,
            EXPORT_SIGNEDNESS, EXPORT_CHARACTER_WIDTH, EXPORT_SWITCH_AS_CONSTANT, EXPORT_SWITCH_VALUE,
            EXPORT_TERMINAL_SIZE, EXPORT_KEYBOARD_BUFFER, EXPORT_LOOSE_WRITE_ENABLE, EXPORT_RAM_WITHOUT_WRITE_ENABLE,
            EXPORT_COUNTER_LOAD, EXPORT_LOOSE_BUFFER_CONTROL, EXPORT_UNROUTED_PIN, EXPORT_CROWDED_PART,
            EXPORT_ANNOTATIONS);

    private LogisimNotes() {
    }
}
