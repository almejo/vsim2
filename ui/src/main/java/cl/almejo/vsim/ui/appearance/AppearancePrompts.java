package cl.almejo.vsim.ui.appearance;

import java.awt.Component;
import java.util.List;

/** Injectable decisions after a completed drawing; tests never open confirmations or message boxes. */
public interface AppearancePrompts {
    boolean confirmConnections(Component owner, List<String> disconnected, List<String> kept, boolean blockedInstance);
    boolean confirmLibraryChange(Component owner);
    boolean retryLibraryWrite(Component owner, boolean circuitApplied);
    boolean reopenStale(Component owner);
}
