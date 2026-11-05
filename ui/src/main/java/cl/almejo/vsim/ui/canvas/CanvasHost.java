package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.Optional;

/** What the canvas asks of the window around it. */
public interface CanvasHost {

    void showContextMenu(CircuitCanvas canvas, Optional<DocumentElement> target, Point2D screenPosition);

    void configure(Component component);

    /** Edits the label of a component, or the text of a free text label. Hosts without label editing ignore it. */
    default void editLabel(DocumentElement element) {
    }

    /** Opens the inline editor for a new free text at {@code point}. Hosts without label editing ignore it. */
    default void addTextAt(GridPoint point) {
    }

    /**
     * How moves made in the canvas treat the attached wires (M6.7); asked once, when the canvas is built. Hosts
     * without a window get the defaults and no notices.
     */
    default SelectionMoves selectionMoves() {
        return SelectionMoves.detached();
    }

    /** Edits in place the value of a Constante; hosts without in-place editing open the configure dialog. */
    default void editValue(Component component) {
        configure(component);
    }

    /**
     * Enters the user component {@code instance} is an instance of: a tab of its definition in the circuit's group,
     * showing that instance's values (spec §4.4). Hosts without tabs ignore it.
     */
    default void enterComponent(Component instance) {
    }

    /** Opens the content editor of a RAM or ROM; hosts without one open the configure dialog. */
    default void editMemory(Component component) {
        configure(component);
    }

    /** Opens the window of a Terminal (a double click on its screen); hosts without one open the configure dialog. */
    default void editTerminal(Component component) {
        configure(component);
    }

    /**
     * Whether {@code gridPosition} is on the ▾ / ▸ square of the memory {@code component} (a click there folds or
     * unfolds its content window); never in hosts without memories.
     */
    default boolean memoryFoldToggleAt(Component component, Point2D gridPosition) {
        return false;
    }

    /** M7: edits a bus input pin's value starting from {@code startingValue}; by default as {@link #editValue}. */
    default void editValue(Component component, Value startingValue) {
        editValue(component);
    }

    /**
     * A plain click on {@code component}, a part that takes typing (a Teclado): its window captures the keyboard for
     * it (decision 5). Hosts without a keyboard capture ignore it.
     */
    default void captureKeyboard(Component component) {
    }

    /** The part that has this window's keyboard now, if any (its painter shows it is capturing). */
    default java.util.Optional<cl.almejo.vsim.core.document.ElementId> capturedPart() {
        return java.util.Optional.empty();
    }

    /**
     * A key pressed or typed on {@code canvas}, before its own handling and any key binding: true when the window
     * used it (a part has the keyboard), so the canvas consumes it. False by default.
     */
    default boolean takeKey(CircuitCanvas canvas, KeyEvent event) {
        return false;
    }

    /**
     * {@code canvas} lost the keyboard focus to {@code opposite} (empty: another window, or its window was
     * deactivated). The window ends a keyboard capture unless the focus went to another of its panes. Ignored by
     * default.
     */
    default void canvasFocusLost(CircuitCanvas canvas, Optional<java.awt.Component> opposite) {
    }
}
