package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.ArithmeticTypes;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.SequentialTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.component.standard.WiringTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CONSTANT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OUTPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * «Todas las piezas»: a big showcase circuit of every part the «Componentes para clases» work added, for the user to
 * open and look at (it is not a bundled example; {@link ClassExampleFiles} writes it). One locked frame per palette
 * category with a title and a short note; inside, one cell per case: a caption, the part, switches or constants on
 * its left (and below or above it for the pins there) and output pins or LEDs on its right, wired in nested L
 * shapes so no two wires cross. The 1-bit switches named «reloj» stand for a clock: a click from 1 to 0 is the
 * falling edge the flip-flops and registers act on.
 */
public final class PartsShowcase {

    static final String NAME = "Todas las piezas";

    /** Columns of a cell: the widest source (16) and its margin, the fans, the part and the result's value. */
    private static final int CELL_WIDTH = 56;
    /** Columns from a cell's left side to the column where its left sources' pins end. */
    private static final int SOURCE_PIN_COLUMN = 18;
    /** Columns between the sources and the part (and the part and the sinks): room for a 32-bit wire value. */
    private static final int MINIMUM_FAN = 7;
    private static final int CELL_GAP = 2;
    private static final int FRAME_HEADER = 7;
    private static final int FRAME_GAP = 4;
    private static final String CLOCK_LABEL = "reloj";
    private static final String SHIFTED = "0b10010110";

    private final CircuitBuilder builder = new CircuitBuilder(NAME);
    private final Map<String, Component> probes = new LinkedHashMap<>();
    private final Map<String, Component> inputs = new LinkedHashMap<>();

    private PartsShowcase() {
    }

    /** The showcase circuit, new each time. */
    public static PartsShowcase create() {
        PartsShowcase showcase = new PartsShowcase();
        showcase.buildAll();
        return showcase;
    }

    public CircuitBuilder builder() {
        return builder;
    }

    /**
     * The output pins and LEDs that show a result, by key ({@code subtractor:borrow}, {@code shifter:rotate-left},
     * {@code sr:not-q}…).
     */
    public Map<String, Component> probes() {
        return probes;
    }

    /** The switches a test clicks, by key ({@code jk:clock}, {@code tri-state:enable}…). */
    public Map<String, Component> inputs() {
        return inputs;
    }

    /** The text the file would have. */
    public String text() {
        return new CircuitFormat(builder.registry()).write(builder.document()) + "\n";
    }

    // ------------------------------------------------------------------------------------------------ the frames

    private void buildAll() {
        builder.text(2, 0, NAME);
        builder.text(2, 2, "Cada pieza nueva con entradas que muestran su caso interesante. Clic en un bit de un "
                + "interruptor lo cambia; en «reloj», pasar de 1 a 0 es el flanco de bajada.");
        int top = 6;
        int leftColumn = 0;
        int rightColumn = 2 * CELL_WIDTH + FRAME_GAP + 4;
        int leftBottom = frame(leftColumn, top, "Aritmética", FrameTint.GREEN,
                "Restador, negador, multiplicador, divisor, desplazador y contador de unos, de 8 bits",
                arithmeticCells());
        frame(leftColumn, leftBottom + FRAME_GAP, "Secuenciales", FrameTint.RED,
                "Flip-flops JK, T, SR y D con Q̅, y el registro delgado de 8 y 32 bits: actúan en el flanco de bajada",
                sequentialCells());
        int rightTop = frame(rightColumn, top, "Cableado", FrameTint.BLUE,
                "El extensor cambia el ancho de un bus; el selector saca un grupo de bits", wiringCells());
        rightTop = frame(rightColumn, rightTop + FRAME_GAP, "Compuertas", FrameTint.YELLOW,
                "Triestado de 8 bits e inversor controlado: con el control en 0 la salida queda en Z", gateCells());
        rightTop = frame(rightColumn, rightTop + FRAME_GAP, "Multiplexores", FrameTint.PURPLE,
                "Multiplexor de 8 entradas, demultiplexor y codificador con prioridad", plexerCells());
        frame(rightColumn, rightTop + FRAME_GAP, "Entrada/Salida", FrameTint.GRAY,
                "Interruptor y LED de 4, 8 y 16 bits: un clic en un bit lo cambia", inputOutputCells());
    }

    private List<Cell> arithmeticCells() {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell("Restador: 3 − 5 − 1 = 253 (−3) y pide prestado", ArithmeticTypes.SUBTRACTOR,
                Map.of(WIDTH, 8), "")
                .left(0, Source.bus(8, "3", "A", "")).left(1, Source.bus(8, "5", "B", ""))
                .above(2, Source.bit("1", "Bin", ""))
                .right(3, Sink.number(8, ConstantValue.DECIMAL, "D", "subtractor:difference"))
                .under(4, Sink.light("Bout", "subtractor:borrow")));
        cells.add(new Cell("Negador: −(−128) no cabe en 8 bits y da −128", ArithmeticTypes.NEGATOR,
                Map.of(WIDTH, 8), "Negador")
                .left(0, Source.signedBus("-128", "A", ""))
                .right(1, Sink.number(8, ConstantValue.SIGNED, "−A", "negator")));
        cells.add(new Cell("Multiplicador sin signo: 200 × 150 = 30000 = 0x7530", ArithmeticTypes.MULTIPLIER,
                Map.of(WIDTH, 8), "Sin signo")
                .left(0, Source.bus(8, "200", "A", "")).left(1, Source.bus(8, "150", "B", ""))
                .right(2, Sink.number(8, ConstantValue.HEX, "P (baja)", "multiplier:low"))
                .right(3, Sink.number(8, ConstantValue.HEX, "H (alta)", "multiplier:high")));
        cells.add(new Cell("Multiplicador con signo: −2 × 3 = −6 = 0xFFFA", ArithmeticTypes.MULTIPLIER,
                Map.of(WIDTH, 8, StandardComponentTypes.SIGNEDNESS, StandardComponentTypes.SIGNEDNESS_SIGNED),
                "Con signo")
                .left(0, Source.signedBus("-2", "A", "")).left(1, Source.signedBus("3", "B", ""))
                .right(2, Sink.number(8, ConstantValue.HEX, "P (baja)", "multiplier-signed:low"))
                .right(3, Sink.number(8, ConstantValue.HEX, "H (alta)", "multiplier-signed:high")));
        cells.add(divider("Divisor: 100 ÷ 7 = 14, resto 2", "7", "divider"));
        cells.add(divider("Divisor por cero: Q = 0, R = A y E = 1", "0", "divider-zero"));
        cells.add(shifter("Lógico a la izquierda (<<): entran ceros", ArithmeticTypes.LOGICAL_LEFT));
        cells.add(shifter("Lógico a la derecha (>>): entran ceros", ArithmeticTypes.LOGICAL_RIGHT));
        cells.add(shifter("Aritmético a la derecha (>>>): entra el signo", ArithmeticTypes.ARITHMETIC_RIGHT));
        cells.add(shifter("Rotar a la izquierda: lo que sale vuelve a entrar", ArithmeticTypes.ROTATE_LEFT));
        cells.add(shifter("Rotar a la derecha", ArithmeticTypes.ROTATE_RIGHT));
        cells.add(new Cell("Contador de unos: 1011 0110 tiene 5", ArithmeticTypes.BIT_COUNTER, Map.of(WIDTH, 8),
                "Unos")
                .left(0, Source.bus(8, "0b10110110", "A", ""))
                .right(1, Sink.number(4, ConstantValue.DECIMAL, "Y", "bit-counter")));
        return cells;
    }

    private Cell divider(String caption, String divisor, String key) {
        return new Cell(caption, ArithmeticTypes.DIVIDER, Map.of(WIDTH, 8), "")
                .left(0, Source.bus(8, "100", "A", "")).left(1, Source.bus(8, divisor, "B", ""))
                .right(2, Sink.number(8, ConstantValue.DECIMAL, "Q", key + ":quotient"))
                .right(3, Sink.number(8, ConstantValue.DECIMAL, "R", key + ":remainder"))
                .under(4, Sink.light("E", key + ":error"));
    }

    private Cell shifter(String caption, String shiftType) {
        return new Cell("Desplazador " + caption.substring(0, 1).toLowerCase() + caption.substring(1),
                ArithmeticTypes.SHIFTER, Map.of(WIDTH, 8, ArithmeticTypes.SHIFT_TYPE, shiftType), "")
                .left(0, Source.bus(8, SHIFTED, "A", ""))
                .below(1, Source.constant(3, "2", ConstantValue.DECIMAL))
                .right(2, Sink.number(8, ConstantValue.BINARY, "Y", "shifter:" + shiftType));
    }

    private List<Cell> wiringCells() {
        List<Cell> cells = new ArrayList<>();
        cells.add(extender("Extensor 8 → 16 con ceros: 0x8D → 0x008D", 16, WiringTypes.FILL_ZEROS,
                ConstantValue.HEX, "extender:zeros"));
        cells.add(extender("Extensor 8 → 16 con signo: 0x8D → 0xFF8D", 16, WiringTypes.FILL_SIGN,
                ConstantValue.HEX, "extender:sign"));
        cells.add(extender("Extensor 8 → 3: quedan los bits de abajo", 3, WiringTypes.FILL_ZEROS,
                ConstantValue.BINARY, "extender:narrow"));
        cells.add(new Cell("Selector de bits: S elige el grupo de 4 bits", WiringTypes.BIT_SELECTOR,
                Map.of(WiringTypes.INPUT_WIDTH, 8, WiringTypes.OUTPUT_WIDTH, 4), "")
                .left(0, Source.bus(8, "0b10100110", "entrada", ""))
                .below(1, Source.bit("1", "S", "selector:group"))
                .right(2, Sink.number(4, ConstantValue.BINARY, "grupo", "selector")));
        return cells;
    }

    private Cell extender(String caption, int outputWidth, String fill, String format, String key) {
        return new Cell(caption, WiringTypes.BIT_EXTENDER, Map.of(WiringTypes.INPUT_WIDTH, 8,
                WiringTypes.OUTPUT_WIDTH, outputWidth, WiringTypes.FILL, fill), "")
                .left(0, Source.bus(8, "0b10001101", "A", ""))
                .right(1, Sink.number(outputWidth, format, "salida", key));
    }

    private List<Cell> gateCells() {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell("Triestado de 8 bits: control en 0 da Z", StandardComponentTypes.TRI_STATE,
                Map.of(WIDTH, 8), "")
                .left(0, Source.bus(8, "0b11001010", "entrada", ""))
                .below(2, Source.bit("1", "control", "tri-state:enable"))
                .right(1, Sink.number(8, ConstantValue.BINARY, "salida", "tri-state")));
        cells.add(new Cell("Inversor controlado: invierte mientras control = 1", StandardComponentTypes.TRI_STATE,
                Map.of(WIDTH, 8, StandardComponentTypes.NEGATE, true), "")
                .left(0, Source.bus(8, "0b11001010", "entrada", ""))
                .below(2, Source.bit("1", "control", ""))
                .right(1, Sink.number(8, ConstantValue.BINARY, "salida", "controlled-inverter")));
        return cells;
    }

    private List<Cell> plexerCells() {
        List<Cell> cells = new ArrayList<>();
        Cell mux = new Cell("Multiplexor de 8 entradas: S = 5 elige D5", StandardComponentTypes.MUX,
                Map.of(WIDTH, 8, StandardComponentTypes.GATE_INPUTS, "8"), "");
        for (int input = 0; input < 8; input++) {
            mux.left(input, Source.constant(8, String.valueOf(10 * (input + 1)), ConstantValue.DECIMAL));
        }
        cells.add(mux.below(8, Source.bus(3, "5", "S", ""))
                .right(9, Sink.number(8, ConstantValue.DECIMAL, "Y", "mux")));
        Cell demux = new Cell("Demultiplexor: D va a la salida que elige S", "demux",
                Map.of(WIDTH, 8, "outputs", "4"), "")
                .left(0, Source.bus(8, "42", "D", ""))
                .below(1, Source.bus(2, "2", "S", ""));
        for (int output = 0; output < 4; output++) {
            demux.right(2 + output, Sink.number(8, ConstantValue.DECIMAL, "Y" + output, "demux:" + output));
        }
        cells.add(demux);
        Cell encoder = new Cell("Codificador con prioridad: gana la entrada más alta en 1", "priority-encoder",
                Map.of(StandardComponentTypes.GATE_INPUTS, "8"), "");
        for (int input = 0; input < 8; input++) {
            encoder.left(input, Source.unlabeledBit(input == 1 || input == 5 ? "1" : "0"));
        }
        cells.add(encoder.right(8, Sink.number(3, ConstantValue.DECIMAL, "A", "encoder:number"))
                .right(9, Sink.light("V", "encoder:valid")));
        return cells;
    }

    private List<Cell> sequentialCells() {
        List<Cell> cells = new ArrayList<>();
        cells.add(flipFlop(new Cell("JK con J = K = 1: cambia en cada flanco", SequentialTypes.FLIP_FLOP_JK,
                Map.of(), "JK").left(0, Source.bit("1", "J", "")).left(1, Source.bit("1", "K", "")), "jk", 2, 3, 4));
        cells.add(flipFlop(new Cell("T con T = 1: cambia en cada flanco", SequentialTypes.FLIP_FLOP_T, Map.of(), "T")
                .left(0, Source.bit("1", "T", "")), "t", 1, 2, 3));
        cells.add(flipFlop(new Cell("SR con S = R = 1: el estado prohibido, Q = Q̅ = 0", SequentialTypes.FLIP_FLOP_SR,
                Map.of(), "SR").left(0, Source.bit("1", "S", "")).left(1, Source.bit("1", "R", "")), "sr", 2, 3, 4));
        cells.add(flipFlop(new Cell("D con Q̅: copia D en el flanco", StandardComponentTypes.FLIP_FLOP_DATA,
                Map.of(StandardComponentTypes.SHOW_INVERTED, true), "D").left(0, Source.bit("1", "D", "")),
                "d", 2, 1, 3));
        cells.add(new Cell("Registro delgado de 8 bits", StandardComponentTypes.REGISTER,
                Map.of(WIDTH, 8, ConstantValue.FORMAT, ConstantValue.HEX), "Registro")
                .left(0, Source.bus(8, "0xA5", "D", ""))
                .below(3, Source.bit("1", CLOCK_LABEL, "register-8:clock"))
                .right(4, Sink.number(8, ConstantValue.HEX, "Q", "register-8")));
        cells.add(new Cell("Registro de 32 bits: el valor en dos líneas", StandardComponentTypes.REGISTER,
                Map.of(WIDTH, 32, ConstantValue.FORMAT, ConstantValue.BINARY), "Registro")
                .left(0, Source.constant(32, "0xDEADBEEF", ConstantValue.HEX))
                .below(3, Source.bit("1", CLOCK_LABEL, "register-32:clock"))
                .right(4, Sink.number(32, ConstantValue.HEX, "Q", "register-32")));
        return cells;
    }

    /** A flip-flop cell with its clock switch below and green LEDs for Q and Q̅. */
    private Cell flipFlop(Cell cell, String key, int clockPin, int outputPin, int invertedPin) {
        return cell.below(clockPin, Source.bit("1", CLOCK_LABEL, key + ":clock"))
                .right(outputPin, Sink.light("Q", key + ":q"))
                .right(invertedPin, Sink.light("Q̅", key + ":not-q"));
    }

    private List<Cell> inputOutputCells() {
        List<Cell> cells = new ArrayList<>();
        cells.add(new LedCell("4 bits", 4, "0b1010", "leds:4"));
        cells.add(new LedCell("8 bits", 8, "0b11000011", "leds:8"));
        cells.add(new LedCell("16 bits: dos filas de 8", 16, "0xBEEF", "leds:16"));
        return cells;
    }

    /**
     * Lays the cells out two per row inside a locked frame with {@code title} and {@code note}, from {@code top}.
     * Returns the frame's bottom row.
     */
    private int frame(int left, int top, String title, FrameTint tint, String note, List<Cell> cells) {
        builder.text(left + 2, top + 3, note);
        int rowTop = top + FRAME_HEADER;
        for (int index = 0; index < cells.size(); index += 2) {
            int rowBottom = rowTop;
            for (int column = 0; column < 2 && index + column < cells.size(); column++) {
                rowBottom = Math.max(rowBottom, cells.get(index + column).build(left + 2 + column * CELL_WIDTH,
                        rowTop));
            }
            rowTop = rowBottom + CELL_GAP;
        }
        int height = rowTop - top;
        builder.document().add(new Frame(builder.document().newFrameId(), new GridPoint(left, top),
                2 * CELL_WIDTH + 4, height, title, tint, true));
        return top + height;
    }

    // ------------------------------------------------------------------------------------------------ the cells

    /** What feeds a pin: a switch (1 or more bits) or a constant, with its label and the key a test clicks it by. */
    private record Source(String typeId, Map<String, Object> parameters, String label, String key) {

        static Source bus(int width, String value, String label, String key) {
            return new Source(SWITCH, Map.of(WIDTH, width, "value", value), label, key);
        }

        static Source signedBus(String value, String label, String key) {
            return new Source(SWITCH, Map.of(WIDTH, 8, "value", value, StandardComponentTypes.SIGNEDNESS,
                    StandardComponentTypes.SIGNEDNESS_SIGNED), label, key);
        }

        static Source bit(String value, String label, String key) {
            return new Source(SWITCH, Map.of("value", value), label, key);
        }

        static Source unlabeledBit(String value) {
            return bit(value, "", "");
        }

        static Source constant(int width, String value, String format) {
            return new Source(CONSTANT, Map.of(WIDTH, width, "value", value, ConstantValue.FORMAT, format), "", "");
        }

        /** Rows from one source's pin to the next one's: its body, its label above and one free row. */
        int step(CircuitBuilder builder) {
            return height(builder) + (label.isBlank() ? 1 : 3);
        }

        int height(CircuitBuilder builder) {
            return builder.registry().require(typeId).specializedFor(parameters).size().height();
        }

        /** Rows from the top of its label (or body) down to its pin. */
        int above(CircuitBuilder builder) {
            return builder.registry().require(typeId).specializedFor(parameters).pin(0).position().y()
                    + (label.isBlank() ? 0 : 2);
        }
    }

    /** What shows a result: an output pin (a number) or a green LED (one bit). */
    private record Sink(String typeId, Map<String, Object> parameters, String label, String key) {

        static Sink number(int width, String format, String label, String key) {
            return new Sink(OUTPUT_PIN, Map.of(WIDTH, width, ConstantValue.FORMAT, format), label, key);
        }

        static Sink light(String label, String key) {
            return new Sink(LED, Map.of("color", "green"), label, key);
        }

        /** Rows from one sink's pin to the next one's: an LED stands 3 rows above its pin with its label on top. */
        int step() {
            return typeId.equals(LED) ? 7 : 5;
        }
    }

    /** A pin of the cell's part and what is wired to it. */
    private record Attached<T>(int pin, T end) {
    }

    /** One case: a caption, the part and what feeds it and shows it. {@link #build} places and wires them. */
    private class Cell {

        final String caption;
        private final String typeId;
        private final Map<String, Object> parameters;
        private final String label;
        private final List<Attached<Source>> leftSources = new ArrayList<>();
        private final List<Attached<Source>> belowSources = new ArrayList<>();
        private final List<Attached<Source>> aboveSources = new ArrayList<>();
        private final List<Attached<Sink>> rightSinks = new ArrayList<>();
        private final List<Attached<Sink>> underSinks = new ArrayList<>();

        Cell(String caption, String typeId, Map<String, Object> parameters, String label) {
            this.caption = caption;
            this.typeId = typeId;
            this.parameters = parameters;
            this.label = label;
        }

        Cell left(int pin, Source source) {
            leftSources.add(new Attached<>(pin, source));
            return this;
        }

        Cell below(int pin, Source source) {
            belowSources.add(new Attached<>(pin, source));
            return this;
        }

        Cell above(int pin, Source source) {
            aboveSources.add(new Attached<>(pin, source));
            return this;
        }

        Cell right(int pin, Sink sink) {
            rightSinks.add(new Attached<>(pin, sink));
            return this;
        }

        Cell under(int pin, Sink sink) {
            underSinks.add(new Attached<>(pin, sink));
            return this;
        }

        /** Places the cell with its top-left corner at (left, top) and returns its bottom row. */
        int build(int left, int top) {
            builder.text(left + 1, top, caption);
            ComponentType type = builder.registry().require(typeId).specializedFor(parameters);
            int sourceStep = leftSources.stream().mapToInt(source -> source.end().step(builder)).max().orElse(0);
            int sinkStep = rightSinks.stream().mapToInt(sink -> sink.end().step()).max().orElse(0);
            int[] sourceRows = spread(type, leftSources.stream().map(Attached::pin).toList(), sourceStep);
            int[] sinkRows = spread(type, rightSinks.stream().map(Attached::pin).toList(), sinkStep);
            int clearance = Math.max(aboveSources.isEmpty() ? 0 : 7, label.isBlank() ? 1 : 3);
            if (!leftSources.isEmpty()) {
                clearance = Math.max(clearance, leftSources.get(0).end().above(builder) - sourceRows[0] + 1);
            }
            if (!rightSinks.isEmpty()) {
                clearance = Math.max(clearance, rightSinks.get(0).end().step() - 2 - sinkRows[0]);
            }
            int partTop = top + 3 + clearance;
            int pinColumn = left + SOURCE_PIN_COLUMN;
            int partLeft = pinColumn + Math.max(MINIMUM_FAN, leftSources.size() + 3);
            Component part = place(typeId, parameters, new GridPoint(partLeft, partTop), Rotation.R0, label);
            int bottom = partTop + type.size().height();

            List<GridPoint> sourcePins = new ArrayList<>();
            for (int index = 0; index < leftSources.size(); index++) {
                Source source = leftSources.get(index).end();
                GridPoint pin = new GridPoint(pinColumn, partTop + sourceRows[index]);
                sourcePins.add(pin);
                Component placed = placeSource(source, pin);
                builder.link(placed, 0, part, leftSources.get(index).pin());
                bottom = Math.max(bottom, pin.y() + source.height(builder));
            }
            fanIn(sourcePins, leftSources.stream().map(attached -> builder.pin(part, attached.pin())).toList());

            for (Attached<Source> attached : aboveSources) {
                GridPoint target = builder.pin(part, attached.pin());
                GridPoint pin = new GridPoint(target.x() - 2, target.y() - 3);
                Component placed = placeSource(attached.end(), pin);
                builder.route(pin, new GridPoint(target.x(), pin.y()), target);
                builder.link(placed, 0, part, attached.pin());
            }

            int sinkColumn = partLeft + type.size().width() + Math.max(MINIMUM_FAN, rightSinks.size() + 3);
            List<GridPoint> sinkPins = new ArrayList<>();
            for (int index = 0; index < rightSinks.size(); index++) {
                GridPoint pin = new GridPoint(sinkColumn, partTop + sinkRows[index]);
                sinkPins.add(pin);
                Component placed = placeSink(rightSinks.get(index).end(), pin);
                builder.link(part, rightSinks.get(index).pin(), placed, 0);
                bottom = Math.max(bottom, pin.y() + 2);
            }
            fanOut(rightSinks.stream().map(attached -> builder.pin(part, attached.pin())).toList(), sinkPins);

            int lowestSource = sourcePins.stream().mapToInt(GridPoint::y).max().orElse(partTop);
            int row = Math.max(partTop + type.size().height() + 2, lowestSource + 2);
            List<Attached<Source>> fromLeftToRight = new ArrayList<>(belowSources);
            fromLeftToRight.sort(Comparator.comparingInt(attached -> builder.pin(part, attached.pin()).x()));
            for (Attached<Source> attached : fromLeftToRight) {
                Source source = attached.end();
                row = Math.max(row, lowestSource + source.above(builder) + 2);
                GridPoint target = builder.pin(part, attached.pin());
                GridPoint pin = new GridPoint(pinColumn, row);
                Component placed = placeSource(source, pin);
                builder.route(pin, new GridPoint(target.x(), row), target);
                builder.link(placed, 0, part, attached.pin());
                bottom = Math.max(bottom, row + source.height(builder));
                lowestSource = row;
                row += source.step(builder);
            }

            int lowestSink = sinkPins.stream().mapToInt(GridPoint::y).max().orElse(partTop);
            int underRow = Math.max(partTop + type.size().height() + 2, lowestSink + 7);
            for (Attached<Sink> attached : underSinks) {
                GridPoint from = builder.pin(part, attached.pin());
                GridPoint pin = new GridPoint(sinkColumn, underRow);
                Component placed = placeSink(attached.end(), pin);
                builder.route(from, new GridPoint(from.x(), underRow), pin);
                builder.link(part, attached.pin(), placed, 0);
                bottom = Math.max(bottom, underRow + 2);
                underRow += attached.end().step();
            }
            return bottom + 1;
        }

        /**
         * The rows (from the part's top) of the ends wired to {@code pins}, {@code step} apart and centred on the
         * pins; a single end sits on its pin's row so its wire is straight.
         */
        private static int[] spread(ComponentType type, List<Integer> pins, int step) {
            int[] rows = new int[pins.size()];
            if (pins.isEmpty()) {
                return rows;
            }
            int first = type.pin(pins.get(0)).position().y();
            int last = type.pin(pins.get(pins.size() - 1)).position().y();
            int start = (first + last) / 2 - step * (pins.size() - 1) / 2;
            for (int index = 0; index < rows.length; index++) {
                rows[index] = pins.size() == 1 ? first : start + step * index;
            }
            return rows;
        }
    }

    /** Switch → LED of the same width, side by side. */
    private final class LedCell extends Cell {

        private final int width;
        private final String value;
        private final String key;

        LedCell(String caption, int width, String value, String key) {
            super(caption, LED, Map.of(), "");
            this.width = width;
            this.value = value;
            this.key = key;
        }

        @Override
        int build(int left, int top) {
            builder.text(left + 1, top, "Interruptor y LED de " + caption);
            GridPoint pin = new GridPoint(left + SOURCE_PIN_COLUMN + 2, top + 8);
            Component input = placeSource(Source.bus(width, value, "interruptor", ""), pin);
            GridPoint ledPin = new GridPoint(pin.x() + 8, pin.y());
            Component led = place(LED, Map.of(WIDTH, width, "color", "red"), origin(LED, Map.of(WIDTH, width),
                    ledPin), Rotation.R0, "LED");
            builder.route(pin, ledPin);
            builder.link(input, 0, led, 0);
            probes.put(key, led);
            return pin.y() + 5;
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private Component placeSource(Source source, GridPoint pin) {
        Component placed = place(source.typeId(), source.parameters(), origin(source.typeId(), source.parameters(),
                pin), Rotation.R0, source.label());
        if (!source.key().isBlank()) {
            inputs.put(source.key(), placed);
        }
        return placed;
    }

    /**
     * An output pin (its pin on the left, the value to the right) or a 1-bit LED standing on the wire's end (its pin
     * at the bottom, its label above it, away from the wire); the pin lands on {@code pin}.
     */
    private Component placeSink(Sink sink, GridPoint pin) {
        Component placed = place(sink.typeId(), sink.parameters(), origin(sink.typeId(), sink.parameters(), pin),
                Rotation.R0, sink.label());
        probes.put(sink.key(), placed);
        return placed;
    }

    /** The top-left corner that puts pin 0 of the unrotated part on {@code pin}. */
    private GridPoint origin(String typeId, Map<String, Object> parameters, GridPoint pin) {
        PinSpec spec = builder.registry().require(typeId).specializedFor(parameters).pin(0);
        return new GridPoint(pin.x() - spec.position().x(), pin.y() - spec.position().y());
    }

    private Component place(String typeId, Map<String, Object> parameters, GridPoint position, Rotation rotation,
                            String label) {
        ComponentType specialized = builder.registry().require(typeId).specializedFor(parameters);
        Component component = specialized.newComponent(builder.document().newComponentId(), position, rotation,
                parameters, label);
        builder.document().add(component);
        return component;
    }

    /**
     * Routes each source pin (one column, top to bottom) to its target pin (another column): the ones above their
     * target turn down, the ones below turn up, each L nested inside the ones nearer the end of its half.
     */
    private void fanIn(List<GridPoint> sources, List<GridPoint> targets) {
        if (sources.isEmpty()) {
            return;
        }
        int turnStart = targets.get(0).x() - 2;
        int[] turns = new int[sources.size()];
        int rank = 0;
        for (int index = 0; index < sources.size(); index++) {
            if (targets.get(index).y() > sources.get(index).y()) {
                turns[index] = turnStart - rank++;
            }
        }
        rank = 0;
        for (int index = sources.size() - 1; index >= 0; index--) {
            if (targets.get(index).y() < sources.get(index).y()) {
                turns[index] = turnStart - rank++;
            }
        }
        for (int index = 0; index < sources.size(); index++) {
            route(sources.get(index), targets.get(index), turns[index]);
        }
    }

    /** The mirror of {@link #fanIn}: from the part's pins (one column) out to the sinks (another column). */
    private void fanOut(List<GridPoint> pins, List<GridPoint> sinks) {
        if (pins.isEmpty()) {
            return;
        }
        int turnStart = pins.get(0).x() + 2;
        int[] turns = new int[pins.size()];
        int rank = 0;
        for (int index = 0; index < pins.size(); index++) {
            if (sinks.get(index).y() < pins.get(index).y()) {
                turns[index] = turnStart + rank++;
            }
        }
        rank = 0;
        for (int index = pins.size() - 1; index >= 0; index--) {
            if (sinks.get(index).y() > pins.get(index).y()) {
                turns[index] = turnStart + rank++;
            }
        }
        for (int index = 0; index < pins.size(); index++) {
            route(pins.get(index), sinks.get(index), turns[index]);
        }
    }

    private void route(GridPoint from, GridPoint to, int turn) {
        if (from.y() == to.y()) {
            builder.route(from, to);
        } else {
            builder.route(from, new GridPoint(turn, from.y()), new GridPoint(turn, to.y()), to);
        }
    }
}
