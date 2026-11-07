package cl.almejo.vsim.ui.window;

import com.formdev.flatlaf.util.UIScale;

import java.util.Objects;

/**
 * What the right panel of a window remembers: whether it is open, which section it shows (an {@link InspectorPanel}
 * id: {@code properties}, {@code problems}, {@code notes}, {@code memoryMap}) and its width in pixels, the one the
 * user last dragged the divider to. Closing keeps the section and the width, so reopening looks as it did.
 */
public record InspectorState(boolean open, String selectedTab, int width) {

    public InspectorState {
        Objects.requireNonNull(selectedTab, "selectedTab");
        if (width <= 0) {
            throw new IllegalArgumentException("the inspector's width must be positive: " + width);
        }
    }

    /** A new window's panel: open on «Propiedades», {@link InspectorPanel#PREFERRED_WIDTH} wide (UI-scaled). */
    public static InspectorState initial() {
        return new InspectorState(true, InspectorPanel.PROPERTIES, UIScale.scale(InspectorPanel.PREFERRED_WIDTH));
    }

    InspectorState withOpen(boolean newOpen) {
        return new InspectorState(newOpen, selectedTab, width);
    }

    InspectorState withSelectedTab(String newSelectedTab) {
        return new InspectorState(open, newSelectedTab, width);
    }

    InspectorState withWidth(int newWidth) {
        return new InspectorState(open, selectedTab, newWidth);
    }
}
