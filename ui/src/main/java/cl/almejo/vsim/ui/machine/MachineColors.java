package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.ui.paint.ColorScheme;

import java.awt.Color;

/**
 * The colours of a machine's diagram, taken from the current {@link ColorScheme} each time they are asked (never kept:
 * the scheme changes live). The active state and the next arrow use the scheme's {@code machineActive} orange, as in
 * the agreed mockup.
 */
public final class MachineColors {

    /** How much of the active colour tints the active state's inside (the mockup's «active-soft»). */
    private static final double ACTIVE_FILL_TINT = 0.2;
    /** How far the arrows and their labels go from the text colour towards the background (the mockup's «muted»). */
    private static final double MUTED_MIX = 0.35;

    private MachineColors() {
    }

    static Color background(ColorScheme scheme) {
        return scheme.background();
    }

    public static Color active(ColorScheme scheme) {
        return scheme.machineActive();
    }

    /** The inside of the active state: the background tinted with the active colour, opaque. */
    static Color activeFill(ColorScheme scheme) {
        return mix(scheme.background(), active(scheme), ACTIVE_FILL_TINT);
    }

    public static Color stateFill(ColorScheme scheme) {
        return scheme.background();
    }

    /**
     * The inside of {@code state}: the colour the user gave it (hito 3), else the active tint when it is the active
     * state, else the plain inside. A coloured active state keeps its colour; its orange ring says it is active.
     */
    static Color stateFill(ColorScheme scheme, MachineState state, boolean active) {
        return state.color().map(scheme::stateFill)
                .orElseGet(() -> active ? activeFill(scheme) : stateFill(scheme));
    }

    /** The name of {@code state}, readable on its {@link #stateFill(ColorScheme, MachineState, boolean) inside}. */
    static Color stateText(ColorScheme scheme, MachineState state) {
        return state.color().map(scheme::stateFillText).orElseGet(() -> text(scheme));
    }

    /** The Moore outputs inside {@code state}: its name's colour a little towards its inside, like {@link #muted}. */
    static Color stateOutputs(ColorScheme scheme, MachineState state) {
        return state.color().map(color -> mix(scheme.stateFillText(color), scheme.stateFill(color), MUTED_MIX))
                .orElseGet(() -> muted(scheme));
    }

    public static Color stateOutline(ColorScheme scheme) {
        return scheme.componentStroke();
    }

    static Color text(ColorScheme scheme) {
        return scheme.text();
    }

    /** Arrows, their conditions and the Moore outputs: the text colour a little towards the background. */
    public static Color muted(ColorScheme scheme) {
        return mix(scheme.text(), scheme.background(), MUTED_MIX);
    }

    /** A condition that cannot be read: the scheme's red (the conflict's). */
    static Color problem(ColorScheme scheme) {
        return scheme.wireConflict();
    }

    /** A warning's glow: the scheme's amber. */
    static Color warning(ColorScheme scheme) {
        return scheme.warning();
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

    /** The border around a diagram in test mode, and the bar's accent (hito 4). */
    static Color testMode(ColorScheme scheme) {
        return scheme.testMode();
    }

    /** The test mode's bar over the diagram. */
    static Color testModeBar(ColorScheme scheme) {
        return scheme.testModeBar();
    }
}
