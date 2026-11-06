package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.layout.CircuitTidyOptions;

/** What the user confirmed in the tidy dialog: the options and whether only the selection is tidied. */
public record TidyChoice(CircuitTidyOptions options, boolean selectionOnly) {
}
