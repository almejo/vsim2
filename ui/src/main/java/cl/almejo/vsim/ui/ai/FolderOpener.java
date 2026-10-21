package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.library.SwingLibraryDialogs;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

/**
 * Shows an exported file in the system's file manager («Abrir carpeta» on the AI's export notice). Injectable so
 * tests never open one.
 */
interface FolderOpener {

    /** True when this desktop can show a folder at all (otherwise the notice offers no button). */
    boolean available();

    /** Shows the folder of {@code file} (the file selected where the platform can); false when it could not. */
    boolean open(Path file);

    /** The system's file manager through {@link Desktop}, guarded where it is unsupported (headless included). */
    static FolderOpener desktop() {
        return new FolderOpener() {
            @Override
            public boolean available() {
                try {
                    if (GraphicsEnvironment.isHeadless() || !Desktop.isDesktopSupported()) {
                        return false;
                    }
                    Desktop desktop = Desktop.getDesktop();
                    return desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)
                            || desktop.isSupported(Desktop.Action.OPEN);
                } catch (UnsupportedOperationException | SecurityException unsupported) {
                    return false;
                }
            }

            @Override
            public boolean open(Path file) {
                return SwingLibraryDialogs.openFolder(file);
            }
        };
    }
}
