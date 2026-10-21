package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.ui.window.CircuitWindow;
import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.JComponent;
import java.awt.Window;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One window showing a circuit, as the AI's pictures need it (EDT only): its view (for the active pane and the colour
 * scheme), the component that holds everything inside the frame (the root pane, menu bar included), the title and
 * the dialogs it has open.
 */
public record ShownWindow(EditorView view, JComponent root, String title, List<Window> dialogs) {

    public ShownWindow {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(root, "root");
        title = title == null ? "" : title;
        dialogs = List.copyOf(dialogs);
    }

    /** {@code windows} as the AI's pictures want them: the active (focused) one first, then in their order. */
    public static List<ShownWindow> activeFirst(List<CircuitWindow> windows) {
        return windows.stream()
                .sorted(Comparator.comparing((CircuitWindow window) -> !window.isActive()))
                .map(ShownWindow::of)
                .toList();
    }

    /** {@code window} with the dialogs it shows now. */
    public static ShownWindow of(CircuitWindow window) {
        return new ShownWindow(window.view(), window.getRootPane(), window.getTitle(),
                Arrays.stream(window.getOwnedWindows()).filter(Window::isShowing).toList());
    }
}
