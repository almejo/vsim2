package cl.almejo.vsim.core.component.standard;

import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.memory.MemoryKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where each signal of a RAM or ROM is among its pins, and where the pins are on its box. The pins come in this order:
 * the address (A, or A0…), the data in of a RAM (D, or D0…; bidirectional with «un solo bus D»), WE (RAM), CS, CLK
 * (a RAM that writes with the clock) and the data out (Q, or Q0…; none with «un solo bus D»). An index of -1 means
 * the memory has no such pin.
 *
 * <p>The box: the address and data in on the left edge from y = 4, the data out on the right edge from y = 4, WE, CS
 * and CLK in the middle of the bottom edge; the title and the content window fill the rest. The width follows only the
 * word width (room for the window's address and value), the height only the loose pins, so A and Q stay put when
 * «Palabras» changes, and in bus mode every pin does. With loose pins the D pins sit below the A pins and move when the
 * address grows, like the mux's S.
 */
record MemoryPinout(MemoryKind kind, int addressBits, int width, boolean loose, boolean shared, boolean clocked) {

    /** The first pin row, below the title bar. */
    private static final int FIRST_ROW = 4;
    private static final int MINIMUM_SIZE = 12;
    /** Hex digits of the longest address (24 bits), so the window has room for every size. */
    private static final int ADDRESS_DIGITS = 6;

    static MemoryPinout of(MemoryKind kind, Map<String, Object> resolved) {
        boolean ram = kind == MemoryKind.RAM;
        return new MemoryPinout(kind, MemoryTypes.addressBits(resolved), (Integer) resolved.get(MemoryTypes.WIDTH),
                MemoryTypes.CONNECTION_PINS.equals(resolved.get(MemoryTypes.CONNECTION)),
                ram && MemoryTypes.DATA_SHARED.equals(resolved.get(MemoryTypes.DATA)),
                ram && MemoryTypes.WRITE_CLOCK.equals(resolved.get(MemoryTypes.WRITE)));
    }

    boolean isRam() {
        return kind == MemoryKind.RAM;
    }

    int addressPins() {
        return loose ? addressBits : 1;
    }

    int dataPins() {
        return isRam() ? wordPins() : 0;
    }

    int outputPins() {
        return shared ? 0 : wordPins();
    }

    private int wordPins() {
        return loose ? width : 1;
    }

    int firstAddress() {
        return 0;
    }

    int firstData() {
        return isRam() ? addressPins() : -1;
    }

    int writeEnable() {
        return isRam() ? addressPins() + dataPins() : -1;
    }

    int chipSelect() {
        return addressPins() + dataPins() + (isRam() ? 1 : 0);
    }

    int clock() {
        return clocked ? chipSelect() + 1 : -1;
    }

    int firstOutput() {
        return shared ? -1 : chipSelect() + 1 + (clocked ? 1 : 0);
    }

    ComponentShape shape() {
        int boxWidth = boxWidth();
        int boxHeight = boxHeight();
        List<PinSpec> pins = new ArrayList<>();
        addWord(pins, "A", addressBits, 0, FIRST_ROW, PinDirection.INPUT);
        if (isRam()) {
            int dataRow = loose ? FIRST_ROW + addressBits + 1 : FIRST_ROW + 2;
            addWord(pins, "D", width, 0, dataRow, shared ? PinDirection.BIDIRECTIONAL : PinDirection.INPUT);
            pins.add(PinSpec.input("WE", boxWidth / 2 - 2, boxHeight));
        }
        pins.add(PinSpec.input("CS", boxWidth / 2, boxHeight));
        if (clocked) {
            pins.add(PinSpec.input("CLK", boxWidth / 2 + 2, boxHeight));
        }
        if (!shared) {
            addWord(pins, "Q", width, boxWidth, FIRST_ROW, PinDirection.OUTPUT);
        }
        return new ComponentShape(new GridSize(boxWidth, boxHeight), pins);
    }

    /** A bus pin {@code name} at (x, y), or one pin {@code name0…} per bit going down from there. */
    private void addWord(List<PinSpec> pins, String name, int bits, int x, int y, PinDirection direction) {
        if (!loose) {
            pins.add(new PinSpec(name, new GridPoint(x, y), direction, bits));
            return;
        }
        for (int bit = 0; bit < bits; bit++) {
            pins.add(new PinSpec(name + bit, new GridPoint(x, y + bit), direction, 1));
        }
    }

    /** Room for «address  value» in hex in the window, rounded up to even so CS sits in the middle. */
    private int boxWidth() {
        int valueDigits = (width + 3) / 4;
        int needed = (ADDRESS_DIGITS + valueDigits) * 3 / 4 + 8;
        return Math.max(MINIMUM_SIZE, needed + needed % 2);
    }

    private int boxHeight() {
        if (!loose) {
            return MINIMUM_SIZE;
        }
        int left = FIRST_ROW + addressBits + (isRam() ? width + 1 : 0);
        int right = shared ? 0 : FIRST_ROW + width;
        return Math.max(MINIMUM_SIZE, Math.max(left, right) + 2);
    }
}
