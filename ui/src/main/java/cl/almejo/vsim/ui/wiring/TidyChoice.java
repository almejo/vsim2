package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.wiring.TidyOptions;

/** What the user confirmed in the tidy dialog: the options and whether only the selection is tidied. */
public record TidyChoice(TidyOptions options, boolean selectionOnly) {
}
