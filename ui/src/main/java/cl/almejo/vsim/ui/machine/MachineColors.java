package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Color;

/**
 * The colours of a machine's diagram, taken from the current {@link ColorScheme} each time they are asked (never kept:
 * the scheme changes live). The active state and the next arrow use the scheme's {@code machineActive} orange, as in
 * the agreed mockup.
 */
final class MachineColors {

    /** How much of the active colour tints the active state's inside (the mockup's «active-soft»). */
    private static final double ACTIVE_FILL_TINT = 0.2;
    /** How far the arrows and their labels go from the text colour towards the background (the mockup's «muted»). */
    private static final double MUTED_MIX = 0.35;

    private MachineColors() {
    }

    static Color background(ColorScheme scheme) {
        return scheme.background();
    }

    static Color active(ColorScheme scheme) {
        return scheme.machineActive();
    }

    /** The inside of the active state: the background tinted with the active colour, opaque. */
    static Color activeFill(ColorScheme scheme) {
        return mix(scheme.background(), active(scheme), ACTIVE_FILL_TINT);
    }

    static Color stateFill(ColorScheme scheme) {
        return scheme.background();
    }

    static Color stateOutline(ColorScheme scheme) {
        return scheme.componentStroke();
    }

    static Color text(ColorScheme scheme) {
        return scheme.text();
    }

    /** Arrows, their conditions and the Moore outputs: the text colour a little towards the background. */
    static Color muted(ColorScheme scheme) {
        return mix(scheme.text(), scheme.background(), MUTED_MIX);
    }

    /** A condition that cannot be read: the scheme's red (the conflict's). */
    static Color problem(ColorScheme scheme) {
        return scheme.wireConflict();
    }

    static Color selection(ColorScheme scheme) {
        return scheme.selection();
    }

    /** {@code base} moved {@code amount} (0..1) towards {@code towards}, opaque. */
    private static Color mix(Color base, Color towards, double amount) {
        return new Color(channel(base.getRed(), towards.getRed(), amount),
                channel(base.getGreen(), towards.getGreen(), amount),
                channel(base.getBlue(), towards.getBlue(), amount));
    }

    private static int channel(int base, int towards, double amount) {
        return (int) Math.round(base + (towards - base) * amount);
    }
}
