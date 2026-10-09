package cl.almejo.vsim.ui.paint;

import java.awt.Color;

/** Colors of the circuit drawing (independent of the Swing theme; more schemes arrive in M4). */
public record ColorScheme(Color background, Color grid, Color wireZero, Color wireOne, Color wireHighImpedance,
                          Color wireConflict, Color wireUnknown, Color componentStroke, Color componentFill,
                          Color componentShade, Color shadow, Color text, Color selection, Color preview) {

    public static final ColorScheme LIGHT = new ColorScheme(
            new Color(0xF7F8FA),
            new Color(0xC9CED6),
            new Color(0x1B5E20),
            new Color(0x3CC23C),
            new Color(0x2962FF),
            new Color(0xE53935),
            new Color(0x8A8F98),
            new Color(0x263238),
            new Color(0xFFFFFF),
            new Color(0xE3E8EE),
            new Color(0, 0, 0, 40),
            new Color(0x263238),
            new Color(0x1E88E5),
            new Color(0x1E88E5));
}
