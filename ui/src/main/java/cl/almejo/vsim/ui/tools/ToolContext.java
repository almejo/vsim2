package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.HitTester;
import cl.almejo.vsim.ui.canvas.Selection;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.paint.ColorScheme;

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

    /** The current dash phase of marching-ants outlines. */
    double selectionDashPhase();

    /** Paints {@code element} semi-transparent (drag and placement previews). */
    void paintGhost(Graphics2D graphics, DocumentElement element);

    void repaint();

    void setTool(Tool tool);

    void useDefaultTool();

    void showContextMenu(Optional<DocumentElement> target, Point2D screenPosition);

    void configure(Component component);

    /** Edits the label of a component, or the text of a free text label. */
    void editLabel(DocumentElement element);
}
