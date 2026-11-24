package cl.almejo.vsim.ui.panels;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * A mouse listener that offers a context menu: {@code popup} is asked on every press and every release, because the
 * platforms differ in which of the two is the popup trigger ({@link MouseEvent#isPopupTrigger()}); {@code popup} checks
 * it and shows the menu. Other events are left to a subclass (it calls {@code super} when it overrides a press or a
 * release).
 */
public class PopupMouseListener extends MouseAdapter {

    private final Consumer<MouseEvent> popup;

    public PopupMouseListener(Consumer<MouseEvent> popup) {
        this.popup = popup;
    }

    @Override
    public void mousePressed(MouseEvent event) {
        popup.accept(event);
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        popup.accept(event);
    }
}
