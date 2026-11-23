package cl.almejo.vsim.ui.window;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JSplitPane;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Tells when the user moves a split's divider: a mouse drag of the divider (once, on release, when it ended somewhere
 * else) or one of the split's keyboard moves (F8, then the arrows, Home or End). The split's own moves (its layout
 * after a window resize, a program setting the location) are never told. It follows a new divider when the look and
 * feel replaces the split's UI (a theme or interface size change). EDT only.
 */
final class DividerDrags {

    /** The keyboard moves of {@code BasicSplitPaneUI}'s action map. */
    private static final List<String> KEYBOARD_MOVES =
            List.of("negativeIncrement", "positiveIncrement", "selectMin", "selectMax");

    private final JSplitPane split;
    private final MouseAdapter mouse;
    private final PropertyChangeListener uiChange;
    private final PropertyChangeListener locationChange;
    private Component divider;
    private int pressedAt = -1;
    private boolean keyboardMove;

    /** Calls {@code moved} with the divider's new location after each move by the user; {@link #dispose()} stops. */
    DividerDrags(JSplitPane split, IntConsumer moved) {
        this.split = Objects.requireNonNull(split, "split");
        Objects.requireNonNull(moved, "moved");
        this.mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                pressedAt = split.getDividerLocation();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                // the divider's own handler, added before this one, has already put the divider where the drag ended
                int before = pressedAt;
                pressedAt = -1;
                int location = split.getDividerLocation();
                if (before >= 0 && location != before) {
                    moved.accept(location);
                }
            }
        };
        this.uiChange = change -> followDivider();
        this.locationChange = change -> {
            if (keyboardMove) {
                moved.accept(split.getDividerLocation());
            }
        };
        followDivider();
        split.addPropertyChangeListener("UI", uiChange);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, locationChange);
        ActionMap actions = split.getActionMap();
        for (String name : KEYBOARD_MOVES) {
            actions.put(name, keyboardMove(name));
        }
    }

    void dispose() {
        if (divider != null) {
            divider.removeMouseListener(mouse);
            divider = null;
        }
        split.removePropertyChangeListener("UI", uiChange);
        split.removePropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, locationChange);
        ActionMap actions = split.getActionMap();
        KEYBOARD_MOVES.forEach(actions::remove);
    }

    private void followDivider() {
        if (divider != null) {
            divider.removeMouseListener(mouse);
        }
        SplitPaneUI ui = split.getUI();
        divider = ui instanceof BasicSplitPaneUI basic ? basic.getDivider() : null;
        if (divider != null) {
            divider.addMouseListener(mouse);
        }
    }

    /** The look and feel's keyboard move {@code name}, marked as the user's while it runs. */
    private Action keyboardMove(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent event) {
                ActionMap lookAndFeelActions = split.getActionMap().getParent();
                Action original = lookAndFeelActions == null ? null : lookAndFeelActions.get(name);
                if (original == null) {
                    return;
                }
                keyboardMove = true;
                try {
                    original.actionPerformed(event);
                } finally {
                    keyboardMove = false;
                }
            }
        };
    }
}
