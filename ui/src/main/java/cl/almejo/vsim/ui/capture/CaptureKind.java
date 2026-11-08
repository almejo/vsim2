package cl.almejo.vsim.ui.capture;

/**
 * What a capture paints: the whole VSim window as the user sees it (menus, bars, panels and the dialogs it has open)
 * or only the visible circuit (every canvas pane as seen, with nothing around them).
 */
public enum CaptureKind {
    WINDOW,
    VISIBLE_CIRCUIT
}
