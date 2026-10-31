package cl.almejo.vsim.ui.terminal;

/**
 * Asks whether a saved circuit may record the output of one of its Terminals to the file it names («Este circuito
 * guarda la salida de «pantalla» en «…/x.txt». ¿Grabar ahí?»). Injectable so tests never show a dialog; the real one
 * answers «no» unless the user says otherwise.
 */
@FunctionalInterface
public interface TerminalOutputConfirmation {

    /** Whether to record the output of {@code terminalName} to {@code shownFile} (as the user reads it). */
    boolean recordThere(String terminalName, String shownFile);
}
