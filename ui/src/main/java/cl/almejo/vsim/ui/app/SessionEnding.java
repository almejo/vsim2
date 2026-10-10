package cl.almejo.vsim.ui.app;

/**
 * When the session ends (M6-B, Assumption 22): it is saved once, by Salir before the windows close or else by the
 * last window while it is still listed — but only once the start has finished — and autosave shuts down once, after the last window is gone.
 */
final class SessionEnding {

    private final Runnable saveSession;
    private final Runnable shutdown;
    private boolean started;
    private boolean saved;
    private boolean shutDown;

    SessionEnding(Runnable saveSession, Runnable shutdown) {
        this.saveSession = saveSession;
        this.shutdown = shutdown;
    }

    /**
     * The start has finished (every window of the session it could open is open). Before that, Salir or a last window
     * would save a session with none or only some of its windows over the stored one, so nothing is saved.
     */
    void started() {
        started = true;
    }

    /** Salir / Cmd+Q, after every document was confirmed and before the windows close. */
    void exiting() {
        save();
    }

    /** A window was disposed and is still listed; {@code lastWindow} when no other window remains. */
    void windowClosing(boolean lastWindow) {
        if (lastWindow) {
            save();
        }
    }

    /** A window was removed; {@code noneLeft} when it was the application's last one. */
    void windowClosed(boolean noneLeft) {
        if (noneLeft && !shutDown) {
            shutDown = true;
            shutdown.run();
        }
    }

    private void save() {
        if (started && !saved) {
            saved = true;
            saveSession.run();
        }
    }
}
