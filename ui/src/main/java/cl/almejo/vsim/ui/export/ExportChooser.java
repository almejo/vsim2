package cl.almejo.vsim.ui.export;

import java.awt.Component;
import java.nio.file.Path;
import java.util.Optional;

/** Asks the user where and how to export; empty when cancelled. Tests replace it, they never show a dialog. */
@FunctionalInterface
public interface ExportChooser {

    Optional<ExportChoice> choose(Component parent, Path directory, String suggestedName, int scale,
                                  boolean printBackground, boolean includeStickyNotes);
}
