package cl.almejo.vsim.ui.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Where each Terminal window was and how big (spec §9: «position and size remembered per part»), in the preferences
 * node {@code terminal-windows}: one entry per (document, part), «x,y,width,height». The key is a digest of the
 * document's path (or its untitled name) and the part's id, since preference keys are short.
 */
public final class TerminalWindowPreferences {

    private static final Logger LOGGER = LoggerFactory.getLogger(TerminalWindowPreferences.class);
    private static final int BOUNDS_FIELDS = 4;
    /** Hex digits of the digest kept as the key: well under the 80 characters a preference key may have. */
    private static final int KEY_LENGTH = 32;

    private final Preferences node;

    public TerminalWindowPreferences(Preferences node) {
        this.node = Objects.requireNonNull(node, "node");
    }

    /** The bounds remembered for part {@code componentId} of document {@code documentKey}. */
    public Optional<Rectangle> bounds(String documentKey, String componentId) {
        String stored = node.get(key(documentKey, componentId), "");
        String[] fields = stored.split(",");
        if (fields.length != BOUNDS_FIELDS) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Rectangle(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
        } catch (NumberFormatException exception) {
            LOGGER.warn("Ignoring the unreadable Terminal window bounds {}", stored, exception);
            return Optional.empty();
        }
    }

    /** Remembers {@code bounds} for part {@code componentId} of document {@code documentKey}. */
    public void saveBounds(String documentKey, String componentId, Rectangle bounds) {
        node.put(key(documentKey, componentId), bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
        try {
            node.flush();
        } catch (BackingStoreException exception) {
            LOGGER.warn("Could not save the Terminal window bounds", exception);
        }
    }

    private static String key(String documentKey, String componentId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((documentKey + "\n" + componentId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, KEY_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("every Java has SHA-256", exception);
        }
    }
}
