package cl.almejo.vsim.ui.library;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

/**
 * Places an instance of a user definition where the user clicks: the definitions it needs and the instance are one
 * undo step (overview M4-A). A definition the document already has is kept (M4-0 {@code PutDefinitionsCommand}).
 * Shift keeps placing; R / Shift+R rotate; Esc or a right click return to the default tool.
 */
public final class PlaceDefinitionTool implements Tool {

    private static final ElementId PREVIEW_ID = new ElementId("preview");

    private final DefinitionBundle bundle;
    private final ComponentType type;
    private final CircuitFragment definitions;
    private Rotation rotation = Rotation.R0;
    private GridPoint hover;

    public PlaceDefinitionTool(DefinitionBundle bundle) {
        this.bundle = bundle;
        this.type = SubcircuitTypes.typeOf(bundle.root());
        this.definitions = new CircuitFragment(List.of(), List.of(), List.of(), bundle.all());
    }

    public DefinitionBundle bundle() {
        return bundle;
    }

    @Override
    public boolean placesFromPalette() {
        return true;
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        hover = event.snapped();
        context.repaint();
    }

    @Override
    public void mouseDragged(ToolContext context, CanvasMouseEvent event) {
        mouseMoved(context, event);
    }

    @Override
    public void mousePressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() == CanvasMouseEvent.SECONDARY) {
            context.useDefaultTool();
            return;
        }
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return;
        }
        OpenDocument openDocument = context.openDocument();
        Component instance = type.newComponent(openDocument.document().newComponentId(), event.snapped(), rotation,
                Map.of(), "");
        openDocument.execute(new CompositeCommand("command.add", List.of(
                new PutDefinitionsCommand(bundle.all()), new AddElementsCommand(List.of(instance)))));
        context.selection().select(instance.id());
        context.repaint();
        if (!event.shiftDown()) {
            context.useDefaultTool();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_R) {
            rotation = shiftDown ? rotation.counterClockwise() : rotation.clockwise();
            context.repaint();
            return true;
        }
        if (keyCode == KeyEvent.VK_ESCAPE) {
            context.useDefaultTool();
            return true;
        }
        return false;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (hover != null) {
            context.paintGhost(graphics, type.newComponent(PREVIEW_ID, hover, rotation, Map.of(), ""),
                    definitions.registryFor(context.openDocument().registry()));
        }
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
