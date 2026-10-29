package cl.almejo.vsim.ui.about;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;

/** Opens a link in the browser where the platform can; otherwise the dialog shows the address as plain text. */
public interface LinkOpener {

    boolean available();

    /** False when the browser could not be started. */
    boolean open(String link);

    static LinkOpener desktop() {
        return new LinkOpener() {
            @Override
            public boolean available() {
                try {
                    return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                            && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
                } catch (UnsupportedOperationException | SecurityException unsupported) {
                    return false;
                }
            }

            @Override
            public boolean open(String link) {
                try {
                    Desktop.getDesktop().browse(URI.create(link));
                    return true;
                } catch (IOException | RuntimeException failure) {
                    return false;
                }
            }
        };
    }
}
