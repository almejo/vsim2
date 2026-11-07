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

    /** The sections a saved state may name. */
    private static final java.util.List<String> SECTIONS = java.util.List.of(InspectorPanel.PROPERTIES,
            InspectorPanel.PROBLEMS, InspectorPanel.NOTES, InspectorPanel.MEMORY_MAP);
    /** The widest a saved panel is taken to be (px, before UI scaling): a corrupt value must not fill the window. */
    static final int MAXIMUM_SAVED_WIDTH = 1200;

    /**
     * A state read from a saved session: an unknown section becomes {@code properties}, the width is kept between
     * the panel's minimum and {@link #MAXIMUM_SAVED_WIDTH} (both UI-scaled).
     */
    public static InspectorState fromSession(boolean open, String selectedTab, int width) {
        String section = SECTIONS.contains(selectedTab) ? selectedTab : InspectorPanel.PROPERTIES;
        int clamped = Math.max(UIScale.scale(InspectorPanel.MINIMUM_WIDTH),
                Math.min(UIScale.scale(MAXIMUM_SAVED_WIDTH), width));
        return new InspectorState(open, section, clamped);
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
