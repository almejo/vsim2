package cl.almejo.vsim.core.machine;

/**
 * The fill a user chooses for a state of the diagram (hito 3). The names are stable (they are written to files in
 * lower case); the shades come from the colour scheme at paint time. There is no orange, amber, yellow or red: those
 * mean the active state, a warning and an error, and a state's colour must never look like them.
 */
public enum StateColor {
    BLUE, CYAN, GREEN, LIME, PURPLE, PINK, BROWN, GREY
}
