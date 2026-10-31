package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.MemoryTypes;
import cl.almejo.vsim.core.component.standard.TerminalTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.memory.MemoryContents;
import cl.almejo.vsim.core.terminal.KeyEncoding;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static cl.almejo.vsim.core.GateParameters.NEGATED;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUFFER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.BUS_SPLITTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.COUNTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.KEYBOARD;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.MUX;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OR;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.ROM;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.TERMINAL;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * The Terminal example of Archivo ▸ Ejemplos: «Hola, terminal». A 6-bit counter walks the addresses of a 64 x 8 ROM
 * that holds a 32-character message, and a Terminal writes each byte on the rising edge of the same clock. When the
 * counter reaches 32 its top bit («listo») stops it (through a NOT into its EN) and switches a multiplexer from the ROM
 * to a Teclado: from then on what is typed is echoed, with the Teclado's AV and the counter's «marcha» OR'ed into the
 * Terminal's WE. The Teclado's RE is «listo», so a key typed while the message prints waits in its queue.
 * Boxes: counter 12 x 7 at (14,10); ROM 14 x 12 at (36,8); multiplexer 6 x 8 at (56,9); Terminal 8 rows x 24 columns,
 * 26 x 20, D at (80,13); Teclado 16 x 6 at (36,40). Everything runs on one clock; the counter changes on the falling
 * edge and the Terminal and the Teclado act on the rising one, half a period later, so they see settled values.
 */
public final class TerminalExamples {

    /** What the ROM holds from word 0: exactly 32 characters, so the counter's bit 5 rises right after the last. */
    static final String MESSAGE = "Hola, terminal\r\nEscribe algo\r\n> ";

    private static final int MESSAGE_WORDS = 64;
    private static final int COUNTER_BITS = 6;
    private static final int HALF_PERIOD = 250;

    private TerminalExamples() {
    }

    /** The ROM's contents in the file's text form: «@0» and the hexadecimal bytes of {@link #MESSAGE}. */
    static MemoryContents messageContents() {
        StringBuilder text = new StringBuilder("@0");
        for (byte character : MESSAGE.getBytes(StandardCharsets.US_ASCII)) {
            text.append(String.format(" %02X", character));
        }
        return MemoryContents.decode(text.toString());
    }

    static CircuitBuilder holaTerminal() {
        CircuitBuilder builder = new CircuitBuilder("Hola, terminal");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(6, 22), 0, Rotation.R0,
                Map.of("time-up", HALF_PERIOD, "time-down", HALF_PERIOD), "Reloj");
        Component counter = builder.placeByBox(COUNTER, 14, 10, Rotation.R0, Map.of(WIDTH, COUNTER_BITS), "Contador");
        Component rom = builder.placeByBox(ROM, 36, 8, Rotation.R0,
                Map.of(MemoryTypes.WORDS, String.valueOf(MESSAGE_WORDS), MemoryTypes.WIDTH, 8,
                        MemoryTypes.CONTENTS, messageContents().encode()),
                "Mensaje");
        Component splitter = builder.placeByPin(BUS_SPLITTER, new GridPoint(30, 24), 0, Rotation.R90,
                Map.of(WIDTH, COUNTER_BITS), "");
        Component inverter = builder.placeByPin(BUFFER, new GridPoint(30, 36), 0, Rotation.R0, NEGATED, "");
        Component mux = builder.placeByBox(MUX, 56, 9, Rotation.R0, Map.of(WIDTH, 8), "Fuente");
        Component terminal = builder.placeByPin(TERMINAL, new GridPoint(80, 13), 0, Rotation.R0,
                Map.of(TerminalTypes.ROWS, 8, TerminalTypes.COLUMNS, 24), "Terminal");
        Component keyboard = builder.placeByBox(KEYBOARD, 36, 40, Rotation.R0,
                Map.of(KeyEncoding.ENTER_SENDS, KeyEncoding.CR_LF_VALUE), "Teclado");
        Component writeEnable = builder.placeByPin(OR, new GridPoint(58, 36), 0, Rotation.R0, Map.of(), "");

        // The counter's Q feeds the ROM's address and, through a bus splitter, its top bit («listo»).
        builder.route(new GridPoint(26, 12), new GridPoint(30, 12));
        builder.route(new GridPoint(30, 12), new GridPoint(36, 12));
        builder.route(new GridPoint(30, 12), new GridPoint(30, 24));
        builder.link(counter, 5, rom, 0);
        builder.link(counter, 5, splitter, 0);
        // The ROM's word to the multiplexer's input 0 and the Teclado's D to its input 1, which «listo» selects.
        builder.route(new GridPoint(50, 12), new GridPoint(56, 12));
        builder.link(rom, 2, mux, 0);
        builder.route(new GridPoint(52, 44), new GridPoint(54, 44), new GridPoint(54, 14), new GridPoint(56, 14));
        builder.link(keyboard, 4, mux, 1);
        builder.route(new GridPoint(62, 13), new GridPoint(80, 13));
        builder.link(mux, 3, terminal, 0);

        // The clock: to the counter along y = 22, and round the bottom to the Teclado and the Terminal.
        builder.route(new GridPoint(6, 22), new GridPoint(24, 22), new GridPoint(24, 17));
        builder.link(clock, 0, counter, 4);
        builder.route(new GridPoint(6, 22), new GridPoint(6, 52), new GridPoint(38, 52));
        builder.route(new GridPoint(38, 52), new GridPoint(82, 52), new GridPoint(82, 30));
        builder.route(new GridPoint(38, 46), new GridPoint(38, 52));
        builder.link(clock, 0, keyboard, 0);
        builder.link(clock, 0, terminal, 1);

        // «listo» (bit 5 of the counter): to the NOT, to the multiplexer's S and, round the left, to the Teclado's RE.
        builder.route(new GridPoint(24, 26), new GridPoint(24, 28));
        builder.route(new GridPoint(24, 28), new GridPoint(24, 30));
        builder.route(new GridPoint(24, 30), new GridPoint(28, 30));
        builder.route(new GridPoint(28, 30), new GridPoint(59, 30));
        builder.route(new GridPoint(59, 30), new GridPoint(59, 17));
        builder.route(new GridPoint(28, 30), new GridPoint(28, 36), new GridPoint(30, 36));
        builder.route(new GridPoint(24, 28), new GridPoint(22, 28), new GridPoint(22, 48), new GridPoint(40, 48),
                new GridPoint(40, 46));
        builder.link(splitter, 6, inverter, 0);
        builder.link(splitter, 6, mux, 2);
        builder.link(splitter, 6, keyboard, 1);
        // «marcha» (the NOT's output): to the counter's EN, which stops it, and to the OR of the Terminal's WE.
        builder.route(new GridPoint(34, 36), new GridPoint(34, 19), new GridPoint(20, 19), new GridPoint(20, 17));
        builder.route(new GridPoint(34, 36), new GridPoint(58, 36));
        builder.link(inverter, 1, counter, 2);
        builder.link(inverter, 1, writeEnable, 0);
        // The Teclado's AV to the other input of the OR, and the OR to the Terminal's WE.
        builder.route(new GridPoint(52, 42), new GridPoint(56, 42), new GridPoint(56, 40), new GridPoint(58, 40));
        builder.link(keyboard, 3, writeEnable, 1);
        builder.route(new GridPoint(62, 38), new GridPoint(84, 38), new GridPoint(84, 30));
        builder.link(writeEnable, 2, terminal, 2);

        builder.text(2, 0, "Hola, terminal");
        builder.text(2, 56, "Prueba: la ROM «Mensaje» guarda «Hola, terminal» y el contador la recorre con el reloj: la Terminal lo escribe");
        builder.text(2, 58, "una vez y el contador se detiene en la palabra 32, cuando su bit más alto («listo») se enciende y cambia la «Fuente».");
        builder.text(2, 60, "Con la simulación corriendo, haz clic en el Teclado y escribe: cada letra sale por D, AV habilita la escritura (WE)");
        builder.text(2, 62, "y la Terminal la muestra. Doble clic en su pantalla abre una ventana con el monitor de bytes y la salida esperada.");
        return builder;
    }
}
