package cl.almejo.vsim.ui.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.snap.AlignmentLinesPainter;
import cl.almejo.vsim.ui.snap.ContactCache;
import cl.almejo.vsim.ui.snap.ContactDots;
import cl.almejo.vsim.ui.snap.MatchedGuides;
import cl.almejo.vsim.ui.snap.SnappedPlacement;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Places a component where the user clicks, then returns to the default tool (Shift keeps placing). Ajustar
 * (alignment-guides spec §3.4): the ghost is snapped to the circuit's parts, guides and grid lines and shows its
 * alignment lines; a press places it where the magnet takes the press point (the ghost's place when the pointer has
 * not moved), and R or {@link #refreshSnap} redo the ghost from the last raw point. A dot marks each point where
 * the ghost would touch a net of the circuit ({@link ContactDots}), before the lines.
 */
public final class PlaceComponentTool implements Tool {

    private static final ElementId PREVIEW_ID = new ElementId("preview");

    private final ComponentType type;
    /** A palette preset's parameters (NAND: {@code negate}) on top of the type's defaults; empty for a type. */
    private final Map<String, Object> presetParameters;
    private Rotation rotation = Rotation.R0;
    private GridPoint hover;
    /** The grid point under the pointer before the magnet. */
    private GridPoint rawHover;
    private SnappedPlacement snapped;
    private final ContactCache contactCache = new ContactCache();
    /** Where the pointer last was, in grid units, for the pin names of the contacts. */
    private Point2D pointer;

    public PlaceComponentTool(ComponentType type) {
        this(type, Map.of());
    }

    /** Places {@code type} with {@code presetParameters} set: the palette's NAND is an AND with «Negar salida». */
    public PlaceComponentTool(ComponentType type, Map<String, Object> presetParameters) {
        this.type = type;
        this.presetParameters = Map.copyOf(presetParameters);
    }

    public ComponentType type() {
        return type;
    }

    public Rotation rotation() {
        return rotation;
    }

    @Override
    public boolean placesFromPalette() {
        return true;
    }

    @Override
    public void mouseMoved(ToolContext context, CanvasMouseEvent event) {
        rawHover = event.snapped();
        pointer = event.gridPosition();
        snapHover(context);
        context.repaint();
    }

    @Override
    public boolean refreshSnap(ToolContext context) {
        if (rawHover == null) {
            return false;
        }
        snapHover(context);
        context.repaint();
        return true;
    }

    @Override
    public List<Guide> snappedGuides() {
        return snapped == null ? List.of() : MatchedGuides.of(snapped.matches());
    }

    /** For tests: where the ghost is (after the magnet), or null before the pointer came over the canvas. */
    GridPoint hover() {
        return hover;
    }

    private void snapHover(ToolContext context) {
        snapped = ComponentPlacement.snapped(context, type, presetParameters, rawHover, rotation);
        hover = snapped.position();
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
        if (!event.snapped().equals(rawHover)) {
            rawHover = event.snapped();
            snapHover(context);
        }
        ComponentPlacement.place(context, type, presetParameters, hover, rotation);
        if (!event.shiftDown()) {
            context.useDefaultTool();
        }
    }

    @Override
    public boolean keyPressed(ToolContext context, int keyCode, boolean shiftDown) {
        if (keyCode == KeyEvent.VK_R) {
            rotation = shiftDown ? rotation.counterClockwise() : rotation.clockwise();
            if (rawHover != null) {
                snapHover(context);
            }
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
            context.paintGhost(graphics, ghost(context));
            ContactDots.paint(graphics, context, contacts(context), Optional.ofNullable(pointer));
            AlignmentLinesPainter.paint(graphics, context, snapped.box(), snapped.matches());
        }
    }

    private Component ghost(ToolContext context) {
        return type.newComponent(PREVIEW_ID, hover, rotation,
                ComponentPlacement.startingParameters(context, type, presetParameters), "");
    }

    /**
     * The points where the ghost, placed where it is, would join two nets of the circuit that are not joined today
     * (spec §3.7); none before the pointer came over the canvas.
     */
    List<NewContact> contacts(ToolContext context) {
        if (hover == null) {
            return List.of();
        }
        return contactCache.contacts(context.openDocument().document(), context.openDocument().registry(),
                List.of(ghost(context)), 0, 0, Set.of());
    }

    @Override
    public Cursor cursor() {
        return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }
}
