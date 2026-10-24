package cl.almejo.vsim.ui.appearance;

/** Headless Escape/Enter precedence, evaluated before the dialog's default button bindings. */
public final class ShapeKeys {
    public enum Intent { CANCEL_OPERATION, CLEAR_SELECTION, CLOSE, FINISH_OPERATION, ACCEPT }

    private ShapeKeys() { }

    public static Intent escape(boolean operationActive, boolean selectionPresent) {
        return operationActive ? Intent.CANCEL_OPERATION : selectionPresent ? Intent.CLEAR_SELECTION : Intent.CLOSE;
    }

    public static Intent enter(boolean operationActive) {
        return operationActive ? Intent.FINISH_OPERATION : Intent.ACCEPT;
    }
}
