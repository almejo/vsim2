package cl.almejo.vsim.core.machine;

/**
 * The fill a user chooses for a state of the diagram (hito 3), in the order Propiedades offers them after «sin color».
 * The names are stable (they are written to files in lower case); the shades come from the colour scheme at paint
 * time. Chosen with the user from the swatch sheets (2026-11-17): soft fills under the state's name, with no orange,
 * so no fill looks like the active state's ring; the warning gold and the error red stay rings, halos and «!» badges.
 */
public enum StateColor {
    RED, PINK, YELLOW, GREEN, TURQUOISE, BLUE, VIOLET, GREY
}
