package cl.almejo.vsim.ui.karnaugh;

import java.util.ArrayList;
import java.util.List;

/**
 * What the mouse points at in the wizard, shared by its views so they light up together: the table row under the
 * mouse (table ↔ map, step 2; -1 for none) and the group being explained (map ↔ legend ↔ explanation, step 3). The
 * explained group stays when the mouse leaves it, so the user can reach «¿Por qué?».
 */
public final class HoverState {

    private final List<Runnable> listeners = new ArrayList<>();
    private int row = -1;
    private int group;

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

    public void setGroup(int newGroup) {
        if (newGroup != group) {
            group = newGroup;
            fire();
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
