package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.HitTester;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.wiring.SelectionMoves;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.Optional;

/** Everything a tool may use; implemented by the canvas and by a fake in tests. */
public interface ToolContext {

    OpenDocument openDocument();

    Selection selection();

    HitTester hitTester();

    Viewport viewport();

    ColorScheme scheme();

    /** How moves of the selection treat the attached wires (M6.7), and where their notices go. */
    SelectionMoves selectionMoves();

    /** The current dash phase of marching-ants outlines. */
    double selectionDashPhase();

    /** Paints {@code element} semi-transparent (drag and placement previews). */
    void paintGhost(Graphics2D graphics, DocumentElement element);

    /** Like {@link #paintGhost(Graphics2D, DocumentElement)}, resolving component types with {@code registry}. */
    default void paintGhost(Graphics2D graphics, DocumentElement element, ComponentTypeRegistry registry) {
        paintGhost(graphics, element);
    }

    void repaint();

    void setTool(Tool tool);

    void useDefaultTool();

    /** Shows {@code cursor} over the canvas; while the view is being panned the canvas keeps MOVE and shows it after. */
    void setCursor(CanvasCursor cursor);

    void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition);

    void configure(Component component);

    /** Edits the label of a component, or the text of a free text label. */
    void editLabel(DocumentElement element);

    /** Edits in place the value of a Constante; by default what a double click did before: the configure dialog. */
    default void editValue(Component component) {
        configure(component);
    }

    /**
     * M7: edits the value of a bus input pin starting from {@code startingValue} instead of the live one (a double
     * click puts back the value its first click toggled).
     */
    default void editValue(Component component, Value startingValue) {
        editValue(component);
    }
}
