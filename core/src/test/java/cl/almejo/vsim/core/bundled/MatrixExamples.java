package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.LedMatrixTypes;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_SPLITTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CONSTANT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.COUNTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.GROUPS;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.LED_MATRIX;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.ROM;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.SWITCH;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * The two Matriz LED examples of Archivo ▸ Ejemplos.
 *
 * <p>«Matriz LED»: an 8 x 8 matrix by rows fed by eight 8-bit switches, one per row, set to a heart.
 *
 * <p>«Marquesina»: an 8 x 32 matrix in «Barrido». A fast clock drives a 9-bit counter whose low three bits are the row
 * and whose top six bits are the column the picture starts at. The whole counter is the address of a ROM of 512
 * words of 32 bits: word {@code offset * 8 + row} is the 32-column window, from column {@code offset}, of that row of
 * the picture of the text «HOLA VSIM 2» (a strip of 64 columns that wraps round). The counter's low bits, taken by a
 * bus splitter, are the matrix's «Fila»; the ROM's word is «Datos» and a constant 1 is «Hab.». Each sweep of the
 * eight rows draws the picture one column further along, so the text scrolls.
 *
 * <p>This is not the «shifter» circuit of the mockup: the shifter, the bus splitter and the bit extender stop at
 * 32 bits, so a 64-bit word cannot be cut to a 32-column window with them. A bigger ROM does the same with one part
 * fewer.
 */
public final class MatrixExamples {

    /** The heart of the mockup, one byte per row, the leftmost LED in the top bit. */
    static final int[] HEART = {0x00, 0x66, 0xFF, 0xFF, 0x7E, 0x3C, 0x18, 0x00};

    /** What the marquee says. */
    static final String MARQUEE_TEXT = "HOLA VSIM 2";

    static final int MARQUEE_ROWS = 8;
    /** The picture is a strip of this many columns that wraps round; the matrix shows a window of it. */
    static final int STRIP_COLUMNS = 64;
    static final int WINDOW_COLUMNS = 32;
    /** One window for each column the strip can start at. */
    static final int WINDOWS = STRIP_COLUMNS;

    private static final int GLYPH_ROWS = 7;
    private static final int GLYPH_COLUMNS = 5;
    private static final int SPACE_COLUMNS = 3;
    private static final int FAST_HALF_PERIOD = 50;
    /** Row (3 bits) and window (6 bits) in one counter: the address of the ROM. */
    private static final int COUNTER_BITS = 9;
    private static final String ROW_BITS = "0-2";

    /** The 5 x 7 letters of the text: seven rows of five bits, the leftmost column in bit 4. */
    private static final Map<Character, int[]> FONT = Map.of(
            'H', new int[]{17, 17, 17, 31, 17, 17, 17},
            'O', new int[]{14, 17, 17, 17, 17, 17, 14},
            'L', new int[]{16, 16, 16, 16, 16, 16, 31},
            'A', new int[]{14, 17, 17, 31, 17, 17, 17},
            'V', new int[]{17, 17, 17, 17, 17, 10, 4},
            'S', new int[]{15, 16, 16, 14, 1, 1, 30},
            'I', new int[]{14, 4, 4, 4, 4, 4, 14},
            'M', new int[]{17, 27, 21, 21, 17, 17, 17},
            '2', new int[]{14, 17, 1, 2, 4, 8, 31});

    private MatrixExamples() {
    }

    /**
     * The picture of {@link #MARQUEE_TEXT}: {@code [row][column]}, 8 rows by 64 columns, the letters a pixel apart (an
     * I is only as wide as it is drawn) and the rest blank, so the text wraps round with a gap after the «2».
     */
    static boolean[][] marqueePicture() {
        List<boolean[]> columns = new ArrayList<>();
        for (char letter : MARQUEE_TEXT.toCharArray()) {
            if (letter == ' ') {
                for (int blank = 0; blank < SPACE_COLUMNS; blank++) {
                    columns.add(new boolean[MARQUEE_ROWS]);
                }
                continue;
            }
            addLetter(columns, FONT.get(letter));
        }
        boolean[][] picture = new boolean[MARQUEE_ROWS][STRIP_COLUMNS];
        for (int column = 0; column < columns.size(); column++) {
            for (int row = 0; row < MARQUEE_ROWS; row++) {
                picture[row][column] = columns.get(column)[row];
            }
        }
        return picture;
    }

    private static void addLetter(List<boolean[]> columns, int[] glyph) {
        int first = GLYPH_COLUMNS;
        int last = -1;
        for (int column = 0; column < GLYPH_COLUMNS; column++) {
            if (columnHasPixels(glyph, column)) {
                first = Math.min(first, column);
                last = column;
            }
        }
        for (int column = first; column <= last; column++) {
            boolean[] pixels = new boolean[MARQUEE_ROWS];
            for (int row = 0; row < GLYPH_ROWS; row++) {
                pixels[row] = (glyph[row] >> (GLYPH_COLUMNS - 1 - column) & 1) == 1;
            }
            columns.add(pixels);
        }
        columns.add(new boolean[MARQUEE_ROWS]);
    }

    private static boolean columnHasPixels(int[] glyph, int column) {
        for (int row : glyph) {
            if ((row >> (GLYPH_COLUMNS - 1 - column) & 1) == 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ROM's contents in the file's text form: for each of the {@link #WINDOWS} offsets and each row (address =
     * offset * 8 + row), the 32-column window of that row of the picture that starts at that column, round the end.
     */
    static MemoryContents marqueeContents() {
        boolean[][] picture = marqueePicture();
        StringBuilder text = new StringBuilder("@0");
        for (int offset = 0; offset < WINDOWS; offset++) {
            for (int row = 0; row < MARQUEE_ROWS; row++) {
                text.append(String.format(" %08X", windowWord(picture[row], offset)));
            }
        }
        return MemoryContents.decode(text.toString());
    }

    /** The word of a row's window from {@code offset}: its leftmost column is bit 31. */
    static long windowWord(boolean[] row, int offset) {
        long word = 0;
        for (int column = 0; column < WINDOW_COLUMNS; column++) {
            if (row[(offset + column) % STRIP_COLUMNS]) {
                word |= 1L << (WINDOW_COLUMNS - 1 - column);
            }
        }
        return word;
    }

    /**
     * Matriz LED: eight 8-bit switches F0 to F7, each wired to the row of the same name. The switches sit below
     * their rows and the wires go up and to the right, each turning at a column of its own further right than the one
     * above it, so no two wires cross.
     */
    static CircuitBuilder heart() {
        CircuitBuilder builder = new CircuitBuilder("Matriz LED");
        Component matrix = builder.placeByBox(LED_MATRIX, 60, 8, Rotation.R0, Map.of(), "Matriz");
        for (int row = 0; row < HEART.length; row++) {
            int switchY = 12 + 6 * row;
            int turnX = 40 + 2 * row;
            Component rowSwitch = builder.placeByPin(SWITCH, new GridPoint(36, switchY), 0, Rotation.R0,
                    Map.of(WIDTH, 8, "value", String.format("0x%02X", HEART[row])), "F" + row);
            GridPoint rowPin = builder.pin(matrix, row);
            builder.route(new GridPoint(36, switchY), new GridPoint(turnX, switchY), new GridPoint(turnX, rowPin.y()),
                    rowPin);
            builder.link(rowSwitch, 0, matrix, row);
        }
        builder.text(2, 0, "Matriz LED");
        builder.text(2, 62, "Prueba: cada interruptor es una fila de la matriz y cada bit, un LED (el bit más a la izquierda es el LED");
        builder.text(2, 64, "de la izquierda). Haz clic en un bit de un interruptor: el LED de esa fila se enciende o se apaga.");
        return builder;
    }

    /** Marquesina: see the class comment. */
    static CircuitBuilder marquee() {
        CircuitBuilder builder = new CircuitBuilder("Marquesina");
        Map<String, Object> clockParameters = Map.of("time-up", FAST_HALF_PERIOD, "time-down", FAST_HALF_PERIOD);
        Component clock = builder.placeByPin(CLOCK, new GridPoint(6, 22), 0, Rotation.R0, clockParameters, "Reloj");
        Component counter = builder.placeByBox(COUNTER, 14, 10, Rotation.R0, Map.of(WIDTH, COUNTER_BITS), "Contador");
        Component splitter = builder.placeByPin(BUS_SPLITTER, new GridPoint(30, 6), 0, Rotation.R270,
                Map.of(WIDTH, COUNTER_BITS, GROUPS, ROW_BITS), "");
        Component text = builder.placeByBox(ROM, 36, 8, Rotation.R0,
                Map.of(MemoryTypes.WORDS, String.valueOf(WINDOWS * MARQUEE_ROWS), MemoryTypes.WIDTH, WINDOW_COLUMNS,
                        MemoryTypes.CONTENTS, marqueeContents().encode()),
                "Texto");
        Component matrix = builder.placeByBox(LED_MATRIX, 70, 9, Rotation.R0,
                Map.of(LedMatrixTypes.ROWS, MARQUEE_ROWS, LedMatrixTypes.COLUMNS, WINDOW_COLUMNS,
                        LedMatrixTypes.INPUT, LedMatrixTypes.SCAN), "Matriz");
        Component enable = builder.placeByPin(CONSTANT, new GridPoint(66, 18), 0, Rotation.R0,
                Map.of(WIDTH, 1, "value", "1"), "Hab.");
        // The clock to the counter along y = 22.
        builder.route(new GridPoint(6, 22), new GridPoint(24, 22), new GridPoint(24, 17));
        builder.link(clock, 0, counter, 4);
        // The counter's Q is the ROM's address and, up through the splitter, its low three bits are «Fila».
        builder.route(new GridPoint(26, 12), new GridPoint(30, 12));
        builder.route(new GridPoint(30, 12), new GridPoint(36, 12));
        builder.route(new GridPoint(30, 12), new GridPoint(30, 6));
        builder.link(counter, 5, text, 0);
        builder.link(counter, 5, splitter, 0);
        builder.route(new GridPoint(31, 3), new GridPoint(68, 3), new GridPoint(68, 10), new GridPoint(70, 10));
        builder.link(splitter, 1, matrix, 0);
        // The ROM's word is «Datos»; the constant 1 is «Hab.».
        builder.route(new GridPoint(54, 12), new GridPoint(70, 12));
        builder.link(text, 2, matrix, 1);
        builder.route(new GridPoint(66, 18), new GridPoint(68, 18), new GridPoint(68, 14), new GridPoint(70, 14));
        builder.link(enable, 0, matrix, 2);
        builder.text(2, 0, "Marquesina");
        builder.text(2, 30, "Prueba: el contador de 9 bits cuenta con el reloj. Sus 3 bits de abajo son la fila que barre la matriz («Fila») y");
        builder.text(2, 32, "los 6 de arriba, la columna del texto desde donde se dibuja. La ROM «Texto» da para cada fila y cada columna de");
        builder.text(2, 34, "partida los 32 LED que se ven: «HOLA VSIM 2» pasa por la matriz como en un letrero. Cambia el contenido de la ROM");
        builder.text(2, 36, "para cambiar el texto, o el tiempo del reloj para cambiar la velocidad.");
        return builder;
    }
}
