package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.AND;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUFFER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_JOINER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_SPLITTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CONSTANT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.GATE_INPUTS;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.NEGATE;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.XOR;

/**
 * A big showcase circuit of the parameterized gates (AND, OR, XOR, Buffer with «Entradas», «Ancho» and «Negar
 * salida»), for the user to open and look at; it is not a bundled example and nothing in the product reads it. Run
 * {@code VSIM_WRITE_SHOWCASE=/absolute/path/file.vsim ./gradlew :core:test --tests '*GateShowcaseTest.writeTheShowcase'}
 * (from the repository root) to write it with the document writer; without the variable nothing is written.
 *
 * <p>Layout: every group sits in a labelled frame with a short note under its title. Inputs fan out from a column of
 * switches or constants (three rows apart) to the gate's pins in nested L shapes, so no two wires cross; a bus gate's
 * output goes into a bus splitter and from there to a row of LEDs, bit 0 on the right.
 */
public final class GateShowcase {

    /** The environment variable that names the file to write. */
    public static final String TARGET_VARIABLE = "VSIM_WRITE_SHOWCASE";

    private static final int SOURCE_SPACING = 5;
    /** Columns between neighbouring LEDs of a row. */
    private static final int LED_SPACING = 3;
    /** Columns between a column of sources and the gate in front of it. */
    private static final int FAN_WIDTH = 10;
    private static final int FRAME_CONTENT_OFFSET = 7;
    private static final int ONE_BIT_COLUMN_PITCH = 28;
    private static final int ONE_BIT_ROW_HEADER = 16;
    private static final int[] INPUT_COUNTS = {2, 3, 4, 5, 6, 8};
    private static final String[] LETTERS = {"A", "B", "C", "D", "E", "F", "G", "H"};

    private static final String[] FOUR_BIT_VALUES = {"0b1100", "0b1010", "0b1110"};
    private static final String[] EIGHT_BIT_VALUES = {"0b11001100", "0b10101010", "0b11110000"};

    /** One gate family: the type id, the name of its plain version and of its negated one. */
    private record Family(String typeId, String plainName, String negatedName) {
    }

    private static final List<Family> FAMILIES = List.of(new Family(AND, "AND", "NAND"), new Family(OR, "OR", "NOR"),
            new Family(XOR, "XOR", "XNOR"));

    private final CircuitBuilder builder = new CircuitBuilder("Compuertas con 1 bit y con buses");
    private final Map<String, List<Component>> leds = new LinkedHashMap<>();

    private GateShowcase() {
    }

    /** The showcase circuit, new each time. */
    public static GateShowcase create() {
        GateShowcase showcase = new GateShowcase();
        showcase.buildAll();
        return showcase;
    }

    public CircuitBuilder builder() {
        return builder;
    }

    /**
     * The LEDs that show a gate's output, by key: {@code 1bit:<name>:<inputs>} ({@code 1bit:nand:5}),
     * {@code 1bit:buffer}, {@code 1bit:not}, {@code bus:<name>:<width>:<inputs>} (LEDs from bit 0 up),
     * {@code joiners:and}, {@code mixed:<index>}.
     */
    public Map<String, List<Component>> leds() {
        return leds;
    }

    /** The switch value of the input {@code index} of a 1-bit gate: on, except the second of every three. */
    public static boolean switchIsOn(int index) {
        return index % 3 != 1;
    }

    /** The text the file would have. */
    public String text() {
        return new CircuitFormat(builder.registry()).write(builder.document()) + "\n";
    }

    /** Writes the showcase into {@code file} when {@link #TARGET_VARIABLE} names one; otherwise does nothing. */
    public static void main(String[] arguments) throws IOException {
        String target = System.getenv(TARGET_VARIABLE);
        if (target == null || target.isBlank()) {
            System.out.println(TARGET_VARIABLE + " is not set: nothing written");
            return;
        }
        Path file = Path.of(target);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, create().text());
        System.out.println("wrote " + file);
    }

    // ------------------------------------------------------------------------------------------------ the groups

    private void buildAll() {
        builder.text(2, 0, "Compuertas con 1 bit y con buses");
        builder.text(2, 2, "Todas las compuertas de VSim con sus parámetros «Entradas», «Ancho (bits)» y «Negar salida».");
        int top = 5;
        int afterOneBit = oneBitGates(top);
        int afterBufferAndNot = bufferAndNot(afterOneBit + 3);
        int afterBuses = busGates(afterBufferAndNot + 3);
        mixedExample(afterBuses + 3);
    }

    /** AND/OR/XOR and their negated twins from 2 to 8 inputs, one row of six gates per input count. */
    private int oneBitGates(int top) {
        int plainLeft = 2;
        int frameWidth = ONE_BIT_ROW_HEADER + 3 * ONE_BIT_COLUMN_PITCH + 8;
        int negatedLeft = plainLeft + frameWidth + 4;
        int rowTop = top + FRAME_CONTENT_OFFSET;
        for (int inputs : INPUT_COUNTS) {
            int rowBottom = rowTop;
            int headerRow = gateTopFor(AND, inputs, rowTop) + StandardComponentTypes.gateOutputRow(inputs);
            builder.text(plainLeft + 2, headerRow, inputs + " entradas");
            builder.text(negatedLeft + 2, headerRow, inputs + " entradas");
            for (int column = 0; column < FAMILIES.size(); column++) {
                Family family = FAMILIES.get(column);
                int pinX = plainLeft + ONE_BIT_ROW_HEADER + ONE_BIT_COLUMN_PITCH * column;
                rowBottom = gateCell(family.typeId(), false, inputs, pinX, rowTop, family.plainName(),
                        "1bit:" + family.plainName().toLowerCase() + ":" + inputs);
                pinX = negatedLeft + ONE_BIT_ROW_HEADER + ONE_BIT_COLUMN_PITCH * column;
                rowBottom = gateCell(family.typeId(), true, inputs, pinX, rowTop, family.negatedName(),
                        "1bit:" + family.negatedName().toLowerCase() + ":" + inputs);
            }
            rowTop = rowBottom;
        }
        int height = rowTop - top;
        frame(plainLeft, top, frameWidth, height, "AND, OR y XOR de 1 bit", FrameTint.BLUE);
        builder.text(plainLeft + 2, top + 3, "De 2 a 8 entradas: un interruptor por entrada (A, B, C…) y un LED en la salida");
        frame(negatedLeft, top, frameWidth, height, "NAND, NOR y XNOR de 1 bit", FrameTint.PURPLE);
        builder.text(negatedLeft + 2, top + 3, "Las mismas con «Negar salida»: la burbuja invierte la salida");
        return top + height;
    }

    /** The Buffer and the NOT (a Buffer with «Negar salida»), side by side. */
    private int bufferAndNot(int top) {
        int left = 2;
        int rowTop = top + FRAME_CONTENT_OFFSET;
        int bottom = gateCell(BUFFER, false, 1, left + 12, rowTop, "Buffer", "1bit:buffer");
        bottom = gateCell(BUFFER, true, 1, left + 12 + ONE_BIT_COLUMN_PITCH, rowTop, "NOT", "1bit:not");
        int height = bottom - top;
        frame(left, top, 2 * ONE_BIT_COLUMN_PITCH + 8, height, "Buffer y NOT", FrameTint.GREEN);
        builder.text(left + 2, top + 3, "El Buffer copia su entrada; con «Negar salida» es el NOT");
        return top + height;
    }

    /** Bus gates of 4 bits (left) and of 8 bits (right); plain and negated side by side, rows by operation. */
    private int busGates(int top) {
        int fourBitLeft = 2;
        int fourBitPitch = 40;
        int fourBitWidth = 16 + 2 * fourBitPitch + 4;
        int eightBitLeft = fourBitLeft + fourBitWidth + 4;
        int eightBitPitch = 56;
        int eightBitWidth = 16 + 2 * eightBitPitch + 4;
        int rowTop = top + FRAME_CONTENT_OFFSET;
        for (int inputs = 2; inputs <= 3; inputs++) {
            for (Family family : FAMILIES) {
                int fourBottom = rowTop;
                int eightBottom = rowTop;
                for (int column = 0; column < 2; column++) {
                    boolean negated = column == 1;
                    String name = negated ? family.negatedName() : family.plainName();
                    fourBottom = busCell(family.typeId(), negated, 4, inputs, fourBitLeft + 16 + 12 + fourBitPitch * column,
                            rowTop, name, "bus:" + name.toLowerCase() + ":4:" + inputs);
                    eightBottom = busCell(family.typeId(), negated, 8, inputs,
                            eightBitLeft + 16 + 12 + eightBitPitch * column, rowTop, name,
                            "bus:" + name.toLowerCase() + ":8:" + inputs);
                }
                int rowText = rowTop + 2;
                builder.text(fourBitLeft + 2, rowText, family.plainName() + " y");
                builder.text(fourBitLeft + 2, rowText + 2, family.negatedName());
                builder.text(fourBitLeft + 2, rowText + 4, inputs + " entradas");
                builder.text(eightBitLeft + 2, rowText, family.plainName() + " y");
                builder.text(eightBitLeft + 2, rowText + 2, family.negatedName());
                builder.text(eightBitLeft + 2, rowText + 4, inputs + " entradas");
                rowTop = Math.max(fourBottom, eightBottom);
            }
        }
        // Under the 4-bit rows: the same idea with switches joined by bus joiners.
        int joinersTop = rowTop;
        int joinersBottom = joinerCell(fourBitLeft + 16 + 12, joinersTop);
        int bottom = Math.max(joinersBottom, rowTop);
        int height = bottom - top;
        frame(fourBitLeft, top, fourBitWidth, height, "Compuertas de bus de 4 bits", FrameTint.YELLOW);
        builder.text(fourBitLeft + 2, top + 3, "Entradas de constantes en binario (A, B, C); la salida se abre con un separador de bus");
        frame(eightBitLeft, top, eightBitWidth, height, "Compuertas de bus de 8 bits", FrameTint.RED);
        builder.text(eightBitLeft + 2, top + 3, "Lo mismo con 8 bits; los LED van del bit 7 (izquierda) al bit 0 (derecha)");
        return top + height;
    }

    /** An 8-input AND with its output negated, fed through a bus splitter from an 8-bit constant. */
    private void mixedExample(int top) {
        int left = 2;
        int rowTop = top + FRAME_CONTENT_OFFSET + 1;
        String[] values = {"0b11111111", "0b11101111"};
        for (int index = 0; index < values.length; index++) {
            rowTop = mixedCell(left + 16, rowTop, values[index], index);
        }
        frame(left, top, 120, rowTop - top, "Mezcla: constante de bus, separador y NAND de 8 entradas", FrameTint.GRAY);
        builder.text(left + 2, top + 3, "El separador abre el byte en 8 cables; el AND de 8 entradas está negado: se apaga solo si todos son 1");
    }

    // ----------------------------------------------------------------------------------------------- the cells

    /**
     * One gate of 1 bit with one switch per input and an LED on its output; {@code pinX} is the column of the
     * switches' pins and {@code top} the first free row. Returns the first free row below the cell.
     */
    private int gateCell(String typeId, boolean negated, int inputs, int pinX, int top, String gateLabel, String key) {
        int outputRow = outputRow(typeId, inputs);
        int gateHeight = typeId.equals(BUFFER) ? 4 : 2 * outputRow;
        int firstSourceRow = firstSourceRow(outputRow, inputs);
        int gateTop = gateTopFor(typeId, inputs, top);
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (!typeId.equals(BUFFER)) {
            parameters.put(GATE_INPUTS, inputs);
        }
        parameters.put(NEGATE, negated);
        int gateLeft = pinX + FAN_WIDTH;
        Component gate = place(typeId, parameters, new GridPoint(gateLeft, gateTop), Rotation.R0, gateLabel);
        List<Pin> sources = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            Component input = placeByPin(SWITCH, Map.of("value", switchIsOn(index) ? "1" : "0"),
                    new GridPoint(pinX, gateTop + firstSourceRow + SOURCE_SPACING * index), 0, Rotation.R0,
                    LETTERS[index]);
            sources.add(new Pin(input, 0));
        }
        List<Pin> targets = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            targets.add(new Pin(gate, index));
        }
        fanIn(sources, targets);
        GridPoint output = builder.pin(gate, inputs);
        Component led = ledFacingLeft(new GridPoint(output.x() + 3, output.y()));
        builder.route(output, new GridPoint(output.x() + 3, output.y()));
        builder.link(gate, inputs, led, 0);
        leds.put(key, List.of(led));
        int lastSourceRow = firstSourceRow + SOURCE_SPACING * (inputs - 1);
        return gateTop + Math.max(gateHeight, lastSourceRow + 1) + 3;
    }

    /**
     * One bus gate fed by constants A, B (and C) shown in binary, its output opened by a bus splitter into a row of
     * LEDs below it. Returns the first free row below the cell.
     */
    private int busCell(String typeId, boolean negated, int width, int inputs, int pinX, int top, String gateLabel,
                        String key) {
        int outputRow = StandardComponentTypes.gateOutputRow(inputs);
        int firstSourceRow = firstSourceRow(outputRow, inputs);
        int gateTop = gateTopFor(typeId, inputs, top);
        Component gate = place(typeId, Map.of(GATE_INPUTS, inputs, WIDTH, width, NEGATE, negated),
                new GridPoint(pinX + FAN_WIDTH, gateTop), Rotation.R0, gateLabel);
        String[] values = width == 4 ? FOUR_BIT_VALUES : EIGHT_BIT_VALUES;
        List<Pin> sources = new ArrayList<>();
        List<Pin> targets = new ArrayList<>();
        for (int index = 0; index < inputs; index++) {
            Component constant = placeByPin(CONSTANT, Map.of(WIDTH, width, "value", values[index],
                            ConstantValue.FORMAT, ConstantValue.BINARY),
                    new GridPoint(pinX, gateTop + firstSourceRow + SOURCE_SPACING * index), 0, Rotation.R0,
                    LETTERS[index]);
            sources.add(new Pin(constant, 0));
            targets.add(new Pin(gate, index));
        }
        fanIn(sources, targets);
        GridPoint output = builder.pin(gate, inputs);
        int bottom = splitIntoLeds(gate, inputs, width, new GridPoint(output.x() + width + 4, output.y() + 2), key);
        return Math.max(bottom, gateTop + Math.max(2 * outputRow, firstSourceRow + SOURCE_SPACING * (inputs - 1) + 1) + 3);
    }

    /** An AND of 4 bits whose two inputs are made of four switches each, joined by bus joiners. */
    private int joinerCell(int pinX, int top) {
        int gateTop = top + 14;
        int joinerLeft = pinX + FAN_WIDTH;
        int gateLeft = joinerLeft + 10;
        Component gate = place(AND, Map.of(GATE_INPUTS, 2, WIDTH, 4, NEGATE, false), new GridPoint(gateLeft, gateTop),
                Rotation.R0, "AND");
        int[] joinerRows = {gateTop - 6, gateTop + 14};
        int[] gateRows = {gateTop, gateTop + 4};
        String[] names = {"A", "B"};
        boolean[][] switches = {{false, false, true, true}, {false, true, false, true}};
        for (int joinerIndex = 0; joinerIndex < 2; joinerIndex++) {
            Component joiner = placeByPin(BUS_JOINER, Map.of(WIDTH, 4), new GridPoint(joinerLeft + 2, joinerRows[joinerIndex]), 0,
                    Rotation.R0, names[joinerIndex]);
            List<Pin> sources = new ArrayList<>();
            List<Pin> targets = new ArrayList<>();
            for (int bit = 0; bit < 4; bit++) {
                Component input = placeByPin(SWITCH, Map.of("value", switches[joinerIndex][bit] ? "1" : "0"),
                        new GridPoint(pinX, joinerRows[joinerIndex] + 2 + SOURCE_SPACING * bit
                                - Math.floorDiv(3 * SOURCE_SPACING, 2)), 0, Rotation.R0,
                        names[joinerIndex].toLowerCase() + bit);
                sources.add(new Pin(input, 0));
                targets.add(new Pin(joiner, 1 + bit));
            }
            fanIn(sources, targets);
            int turn = gateLeft - 3 + joinerIndex;
            GridPoint busPin = builder.pin(joiner, 0);
            builder.route(busPin, new GridPoint(turn, busPin.y()), new GridPoint(turn, gateRows[joinerIndex]),
                    builder.pin(gate, joinerIndex));
            builder.link(joiner, 0, gate, joinerIndex);
        }
        GridPoint output = builder.pin(gate, 2);
        int ledsBottom = splitIntoLeds(gate, 2, 4, new GridPoint(output.x() + 8, output.y() + 2), "joiners:and");
        return Math.max(ledsBottom, joinerRows[1] + 10 + 4);
    }

    /** A constant into a bus splitter into an 8-input AND, negated, into an LED. */
    private int mixedCell(int pinX, int top, String value, int index) {
        int constantRow = top + 1;
        Component constant = placeByPin(CONSTANT, Map.of(WIDTH, 8, "value", value,
                        ConstantValue.FORMAT, ConstantValue.BINARY), new GridPoint(pinX, constantRow), 0, Rotation.R0,
                "Byte");
        Component splitter = placeByPin(BUS_SPLITTER, Map.of(WIDTH, 8), new GridPoint(pinX + 6, constantRow), 0,
                Rotation.R0, "");
        builder.route(builder.pin(constant, 0), builder.pin(splitter, 0));
        builder.link(constant, 0, splitter, 0);
        Component gate = place(AND, Map.of(GATE_INPUTS, 8, WIDTH, 1, NEGATE, true),
                new GridPoint(pinX + 6 + 2 + FAN_WIDTH, constantRow + 1), Rotation.R0, "NAND");
        List<Pin> sources = new ArrayList<>();
        List<Pin> targets = new ArrayList<>();
        for (int bit = 0; bit < 8; bit++) {
            sources.add(new Pin(splitter, 1 + bit));
            targets.add(new Pin(gate, bit));
        }
        fanIn(sources, targets);
        GridPoint output = builder.pin(gate, 8);
        Component led = ledFacingLeft(new GridPoint(output.x() + 3, output.y()));
        builder.route(output, new GridPoint(output.x() + 3, output.y()));
        builder.link(gate, 8, led, 0);
        leds.put("mixed:" + index, List.of(led));
        builder.text(pinX + 6 + 2 + FAN_WIDTH + 8, constantRow - 1, "bit 7 … bit 0 → entradas 7 … 0");
        return constantRow + 14;
    }

    // ----------------------------------------------------------------------------------------------- helpers

    /** A pin of a placed component. */
    private record Pin(Component component, int index) {
    }

    private static int outputRow(String typeId, int inputs) {
        return typeId.equals(BUFFER) ? 2 : StandardComponentTypes.gateOutputRow(inputs);
    }

    /** Row (from the gate's top) of the first of {@code inputs} sources, three rows apart and centred on the output. */
    private static int firstSourceRow(int outputRow, int inputs) {
        return outputRow - Math.floorDiv(SOURCE_SPACING * (inputs - 1), 2);
    }

    /** The row of a gate's top when its cell starts at {@code top}: room above for the switches' and the gate's labels. */
    private static int gateTopFor(String typeId, int inputs, int top) {
        return top - Math.min(-2, firstSourceRow(outputRow(typeId, inputs), inputs) - 3);
    }

    private Component place(String typeId, Map<String, Object> parameters, GridPoint position, Rotation rotation,
                            String label) {
        ComponentType specialized = builder.registry().require(typeId).specializedFor(parameters);
        Component component = specialized.newComponent(builder.document().newComponentId(), position, rotation,
                parameters, label);
        builder.document().add(component);
        return component;
    }

    /** Places a component so that its pin {@code pinIndex} lands on {@code pinPoint} (the type specialized first). */
    private Component placeByPin(String typeId, Map<String, Object> parameters, GridPoint pinPoint, int pinIndex,
                                 Rotation rotation, String label) {
        GridPoint local = builder.registry().require(typeId).specializedFor(parameters).pin(pinIndex).position();
        GridPoint rotated = rotation.apply(local);
        return place(typeId, parameters, new GridPoint(pinPoint.x() - rotated.x(), pinPoint.y() - rotated.y()),
                rotation, label);
    }

    /** An LED whose pin is at {@code pinPoint} and whose body is to the right of it. */
    private Component ledFacingLeft(GridPoint pinPoint) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.apply(new GridPoint(0, 1)).equals(new GridPoint(-1, 0))) {
                return placeByPin(LED, Map.of("color", "green"), pinPoint, 0, rotation, "");
            }
        }
        throw new IllegalStateException("no rotation turns the LED's pin to the left");
    }

    /**
     * Routes each source to its target (the lists run top to bottom, all sources share a column and all targets
     * another): the sources above their target go down, the ones below go up, each in an L shape nested inside the
     * ones nearer the end of its half so no two wires cross.
     */
    private void fanIn(List<Pin> sources, List<Pin> targets) {
        int count = sources.size();
        int turnStart = builder.pin(targets.get(0).component(), targets.get(0).index()).x() - 2;
        int downRank = 0;
        int[] turnColumns = new int[count];
        for (int index = 0; index < count; index++) {
            int sourceRow = builder.pin(sources.get(index).component(), sources.get(index).index()).y();
            int targetRow = builder.pin(targets.get(index).component(), targets.get(index).index()).y();
            if (targetRow > sourceRow) {
                turnColumns[index] = turnStart - downRank++;
            }
        }
        int upRank = 0;
        for (int index = count - 1; index >= 0; index--) {
            int sourceRow = builder.pin(sources.get(index).component(), sources.get(index).index()).y();
            int targetRow = builder.pin(targets.get(index).component(), targets.get(index).index()).y();
            if (targetRow < sourceRow) {
                turnColumns[index] = turnStart - upRank++;
            }
        }
        for (int index = 0; index < count; index++) {
            Pin source = sources.get(index);
            Pin target = targets.get(index);
            GridPoint from = builder.pin(source.component(), source.index());
            GridPoint to = builder.pin(target.component(), target.index());
            if (from.y() == to.y()) {
                builder.route(from, to);
            } else {
                if (turnColumns[index] <= from.x()) {
                    throw new IllegalStateException("not enough room to fan out " + source);
                }
                builder.route(from, new GridPoint(turnColumns[index], from.y()),
                        new GridPoint(turnColumns[index], to.y()), to);
            }
            builder.link(source.component(), source.index(), target.component(), target.index());
        }
    }

    /**
     * Wires {@code gate}'s output (pin {@code outputPin}) to a bus splitter whose bus pin lands on
     * {@code splitterPoint} (turned 90 degrees: its bits come out at the bottom) and fans its bits out to a row of
     * LEDs hanging below, three columns apart, with the bit number under each. Returns the first free row below.
     */
    private int splitIntoLeds(Component gate, int outputPin, int width, GridPoint splitterPoint, String key) {
        Component splitter = placeByPin(BUS_SPLITTER, Map.of(WIDTH, width), splitterPoint, 0, Rotation.R90, "");
        GridPoint output = builder.pin(gate, outputPin);
        builder.route(output, new GridPoint(splitterPoint.x(), output.y()), splitterPoint);
        builder.link(gate, outputPin, splitter, 0);
        List<Integer> bitsLeftToRight = new ArrayList<>();
        for (int bit = 0; bit < width; bit++) {
            bitsLeftToRight.add(bit);
        }
        bitsLeftToRight.sort(Comparator.comparingInt(bit -> builder.pin(splitter, 1 + bit).x()));
        int firstX = builder.pin(splitter, 1 + bitsLeftToRight.get(0)).x();
        int lastX = builder.pin(splitter, 1 + bitsLeftToRight.get(width - 1)).x();
        int pinRow = builder.pin(splitter, 1).y();
        int ledRow = pinRow + width / 2 + 2;
        Component[] ledsByBit = new Component[width];
        for (int position = 0; position < width; position++) {
            int bit = bitsLeftToRight.get(position);
            GridPoint bitPin = builder.pin(splitter, 1 + bit);
            int ledX = (firstX + lastX - LED_SPACING * (width - 1)) / 2 + LED_SPACING * position;
            int rank = ledX > bitPin.x() ? width - 1 - position : ledX < bitPin.x() ? position : 0;
            int turnRow = pinRow + 1 + rank;
            if (ledX == bitPin.x()) {
                builder.route(bitPin, new GridPoint(ledX, ledRow));
            } else {
                builder.route(bitPin, new GridPoint(bitPin.x(), turnRow), new GridPoint(ledX, turnRow),
                        new GridPoint(ledX, ledRow));
            }
            Component led = placeByPin(LED, Map.of("color", "red"), new GridPoint(ledX, ledRow), 0, Rotation.R180, "");
            builder.link(splitter, 1 + bit, led, 0);
            ledsByBit[bit] = led;
            builder.text(ledX, ledRow + 4, Integer.toString(bit));
        }
        leds.put(key, List.of(ledsByBit));
        return ledRow + 6;
    }

    private void frame(int left, int top, int width, int height, String title, FrameTint tint) {
        builder.document().add(new Frame(builder.document().newFrameId(), new GridPoint(left, top), width, height,
                title, tint, true));
    }
}
