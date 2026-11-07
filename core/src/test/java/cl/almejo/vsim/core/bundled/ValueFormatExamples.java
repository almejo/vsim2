package cl.almejo.vsim.core.bundled;

import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.examples.CircuitBuilder;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CLOCK;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.CONSTANT;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.COUNTER;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.OUTPUT_PIN;
import static cl.almejo.vsim.core.component.standard.StandardComponentTypes.WIDTH;

/**
 * The «Formatos de valor» example of Archivo ▸ Ejemplos: the same bits written in every «Mostrar como» format. An
 * 8-bit counter feeds seven output pins (binary, hex, octal, decimal, signed, characters and fixed point with 4 bits
 * after the point); a 16-bit Constante holding the half-precision float 1.5 feeds three more (hex, decimal, float).
 */
public final class ValueFormatExamples {

    /** The labels and formats of the 8-bit column, top to bottom. */
    static final List<String[]> BYTE_PINS = List.of(
            new String[]{"Binario", ConstantValue.BINARY}, new String[]{"Hexadecimal", ConstantValue.HEX},
            new String[]{"Octal", ConstantValue.OCTAL}, new String[]{"Decimal", ConstantValue.DECIMAL},
            new String[]{"Con signo", ConstantValue.SIGNED}, new String[]{"Carácter", ConstantValue.ASCII},
            new String[]{"Punto fijo", ConstantValue.FIXED});
    static final int FIXED_FRACTION_BITS = 4;
    /** The 16-bit column: 0x3E00 is 1.5 as an IEEE 754 half. */
    static final List<String[]> HALF_PINS = List.of(
            new String[]{"Hex 16", ConstantValue.HEX}, new String[]{"Decimal 16", ConstantValue.DECIMAL},
            new String[]{"Flotante 16", ConstantValue.FLOAT});

    private static final int ROW_STEP = 6;

    private ValueFormatExamples() {
    }

    /**
     * The 8-bit counter box at (20, 10) has Q at (32, 12) and CLK at (30, 17); a trunk at column 38 takes Q down to the
     * seven pins at column 44, one every 6 rows. The Constante (15 wide at 16 bits in float) sits at (20, 58) with
     * its pin at (35, 59); its trunk is at column 40 and its three pins at column 46.
     */
    static CircuitBuilder valueFormats() {
        CircuitBuilder builder = new CircuitBuilder("Formatos de valor");
        Component counter = builder.placeByBox(COUNTER, 20, 10, Rotation.R0, Map.of(WIDTH, 8), "Contador");
        Component clock = builder.placeByPin(CLOCK, new GridPoint(24, 26), 0, Rotation.R0,
                Map.of("time-up", 500, "time-down", 500), "Reloj");
        builder.route(new GridPoint(24, 26), new GridPoint(30, 26), new GridPoint(30, 17));
        builder.link(clock, 0, counter, 4);
        column(builder, counter, 5, new GridPoint(32, 12), 38, 44, BYTE_PINS, 8);

        Component half = builder.placeByBox(CONSTANT, 20, 58, Rotation.R0,
                Map.of(WIDTH, 16, "value", "1.5", ConstantValue.FORMAT, ConstantValue.FLOAT), "Constante");
        column(builder, half, 0, new GridPoint(35, 59), 40, 46, HALF_PINS, 16);

        builder.text(2, 0, "Formatos de valor");
        builder.text(64, 11, "los mismos 8 bits del contador, escritos de siete maneras");
        builder.text(64, 59, "los 16 bits de la constante 1.5 (media precisión IEEE 754)");
        builder.text(2, 78, "Prueba: espera unos segundos: el contador sube y cada pin lo escribe a su manera (con signo pasa a negativo desde 128)");
        builder.text(2, 80, "Doble clic en la Constante y escribe 0.1 o -2.5: el pin «Flotante 16» lo muestra; cambia «Mostrar como» de un pin en Propiedades");
        return builder;
    }

    /** Wires {@code source}'s pin {@code sourcePin} (at {@code start}) to one output pin per format, down a trunk. */
    private static void column(CircuitBuilder builder, Component source, int sourcePin, GridPoint start,
                               int trunkColumn, int pinColumn, List<String[]> pins, int width) {
        int firstRow = start.y();
        int lastRow = firstRow + ROW_STEP * (pins.size() - 1);
        builder.route(start, new GridPoint(trunkColumn, firstRow), new GridPoint(trunkColumn, lastRow));
        for (int index = 0; index < pins.size(); index++) {
            int row = firstRow + ROW_STEP * index;
            Map<String, Object> parameters = new HashMap<>(Map.of(WIDTH, width, ConstantValue.FORMAT,
                    pins.get(index)[1]));
            if (ConstantValue.FIXED.equals(pins.get(index)[1])) {
                parameters.put(ConstantValue.FRACTION, FIXED_FRACTION_BITS);
            }
            builder.route(new GridPoint(trunkColumn, row), new GridPoint(pinColumn, row));
            Component pin = builder.placeByPin(OUTPUT_PIN, new GridPoint(pinColumn, row), 0, Rotation.R0, parameters,
                    pins.get(index)[0]);
            builder.link(source, sourcePin, pin, 0);
        }
    }
}
