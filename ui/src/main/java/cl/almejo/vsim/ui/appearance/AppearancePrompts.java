package cl.almejo.vsim.ui.appearance;

import java.awt.Component;

/** Injectable decisions after a completed drawing; tests never open confirmations or message boxes. */
public interface AppearancePrompts {
    /** True when the user chose «Aplicar y conectar a mano»; false for «Volver» (the only answer when blocked). */
    boolean confirmConnections(Component owner, ConnectionsQuestion question);
    boolean retryLibraryWrite(Component owner, boolean circuitApplied);
    boolean reopenStale(Component owner);
}
