package cl.almejo.vsim.ui.karnaugh;

import java.util.ArrayList;
import java.util.List;

/**
 * What the mouse points at in the wizard, shared by its views so they light up together: the table row under the
 * mouse (table ↔ map, step 2; -1 for none) and the group being explained (map ↔ legend ↔ explanation, step 3). The
 * explained group stays when the mouse leaves it, so the user can reach «¿Por qué?». A click pins a group: while
 * pinned, hovering does not change the explained group (spec §5).
 */
public final class HoverState {

    private final List<Runnable> listeners = new ArrayList<>();
    private int row = -1;
    private int group;
    private int pinnedGroup = -1;

    public int row() {
        return row;
    }

    public void setRow(int newRow) {
        if (newRow != row) {
            row = newRow;
            fire();
        }
    }

    public int group() {
        return group;
    }

    /** The group the hover points at; ignored while a group is pinned (the pinned one stays explained). */
    public void setGroup(int newGroup) {
        if (pinnedGroup < 0 && newGroup != group) {
            group = newGroup;
            fire();
        }
    }

    /** The pinned group, or -1 when none is. */
    public int pinnedGroup() {
        return pinnedGroup;
    }

    /** Pins {@code newGroup}: it is explained until {@link #unpin()}, whatever the mouse does. */
    public void pin(int newGroup) {
        if (newGroup != pinnedGroup || newGroup != group) {
            pinnedGroup = newGroup;
            group = newGroup;
            fire();
        }
    }

    /** Lets the pinned group go (it stays explained until the mouse finds another). */
    public void unpin() {
        if (pinnedGroup >= 0) {
            pinnedGroup = -1;
            fire();
        }
    }

    /** A click on {@code clickedGroup}: pins it, or lets it go when it is the pinned one. */
    public void togglePin(int clickedGroup) {
        if (clickedGroup == pinnedGroup) {
            unpin();
        } else {
            pin(clickedGroup);
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void fire() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
