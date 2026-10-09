package cl.almejo.vsim.ui.clipboard;

import cl.almejo.vsim.core.geometry.GridPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The clipboard as VSim uses it: fragment JSON under {@link FragmentTransferable}'s flavors. Also remembers where
 * the last fragment it copied came from, so a paste without the mouse over the canvas lands next to the original.
 * A clipboard locked by another application is ignored (nothing is copied or pasted).
 */
public final class FragmentClipboard {

    private static final Logger LOGGER = LoggerFactory.getLogger(FragmentClipboard.class);
    private static FragmentClipboard system;

    private final Clipboard clipboard;
    private String lastJson;
    private GridPoint lastOrigin;

    public FragmentClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    /** The system clipboard, shared by every window; a private clipboard when headless (tests). */
    public static synchronized FragmentClipboard system() {
        if (system == null) {
            system = new FragmentClipboard(GraphicsEnvironment.isHeadless() ? new Clipboard("vsim2")
                    : Toolkit.getDefaultToolkit().getSystemClipboard());
        }
        return system;
    }

    /** Puts {@code fragmentJson} on the clipboard and remembers that it was copied from {@code origin}. */
    public void put(String fragmentJson, GridPoint origin) {
        try {
            clipboard.setContents(new FragmentTransferable(fragmentJson), null);
            lastJson = fragmentJson;
            lastOrigin = origin;
        } catch (IllegalStateException exception) {
            LOGGER.warn("Clipboard unavailable, nothing copied", exception);
        }
    }

    /** The text on the clipboard: VSim's flavor first, plain text otherwise; empty when there is none. */
    public Optional<String> read() {
        try {
            for (DataFlavor flavor : List.of(FragmentTransferable.FRAGMENT_FLAVOR, DataFlavor.stringFlavor)) {
                if (clipboard.isDataFlavorAvailable(flavor) && clipboard.getData(flavor) instanceof String text) {
                    return Optional.of(text);
                }
            }
        } catch (IllegalStateException | UnsupportedFlavorException | IOException exception) {
            LOGGER.warn("Clipboard unavailable, nothing pasted", exception);
        }
        return Optional.empty();
    }

    /** Where {@code fragmentJson} was copied from, when it is the last fragment this clipboard put. */
    public Optional<GridPoint> originOf(String fragmentJson) {
        return fragmentJson.equals(lastJson) ? Optional.of(lastOrigin) : Optional.empty();
    }
}
