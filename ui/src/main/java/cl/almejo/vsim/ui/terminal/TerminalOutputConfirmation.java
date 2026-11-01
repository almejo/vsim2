package cl.almejo.vsim.ui.terminal;

import java.awt.Component;

/**
 * Asks whether a saved circuit may record the output of one of its Terminals to the file it names («Este circuito
 * guarda la salida de «pantalla» en «…/x.txt». ¿Grabar ahí?»). Injectable so tests never show a dialog; the real one
 * answers «no» unless the user says otherwise. Asked once per real file in each run of the app.

 */
@FunctionalInterface
public interface TerminalOutputConfirmation {

    /**
     * Whether to record the output of {@code terminalName} to {@code shownFile} (as the user reads it); the dialog
     * belongs to {@code owner} (the editor window; null when there is none).
     */
    boolean recordThere(Component owner, String terminalName, String shownFile);
}
