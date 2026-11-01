package cl.almejo.vsim.ui.resize;

import cl.almejo.vsim.core.command.ResizeComponentCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ResizeSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.tools.CanvasCursor;
import cl.almejo.vsim.ui.tools.CanvasMouseEvent;
import cl.almejo.vsim.ui.tools.SelectToolDelegate;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the select tool does with the tab of a resizable part ({@link ResizeSpec}): the Teclado's «↔» on one edge (Task
 * 6b), the Terminal's «⤡» on its bottom-right corner (Task 7b; a UART 16550's integrated screen too). The tab is there
 * only while the part is selected alone (not as part of a locked block) and its resize applies. Dragging it changes the
 * part's size live, each axis snapped to whole values (a Terminal's whole cells) and kept inside its parameter's range,
 * with the wires of the pins that move following them as in a move preview, and a tag above the tab («Ancho 24 · 14
 * teclas», «80×24»); the release records one undo step ({@link ResizeComponentCommand}), nothing when no value changed.
 * Esc cancels the drag. A double click on the tab goes back to the parameters' defaults (the Teclado's smallest width,
 * a Terminal's 80×24), as one undo step.
 */
public final class ResizeGestures implements SelectToolDelegate {

    /** The part whose tab is held, as it was at the press; null when no drag is under way. */
    private Component grabbed;
    private ComponentType grabbedType;
    private ResizeSpec resize;
    private Point2D pressPosition;
    private Map<String, Integer> startValues = Map.of();
    private Map<String, Integer> values = Map.of();
    private CanvasCursor dragCursor = CanvasCursor.RESIZE_EAST;
    /** The wire plans of this drag, by values; planned on {@link #plannedRevision} of the document. */
    private final Map<Map<String, Integer>, FollowPlan> plans = new HashMap<>();
    private long plannedRevision = -1;

    /**
     * The part that shows a tab now: the only selected element, when its type is resizable with its parameters (a
     * UART 16550 only with its integrated screen).
     */
    private static Optional<Component> resizableSelection(ToolContext context) {
        Set<ElementId> selected = context.selection().ids();
        if (selected.size() != 1 || !context.selection().effectiveIds().equals(selected)) {
            return Optional.empty();
        }
        OpenDocument openDocument = context.openDocument();
        return openDocument.document().component(selected.iterator().next())
                .filter(component -> openDocument.registry().findTypeOf(component)
                        .filter(type -> type.resize().filter(resize -> resize.appliesTo(type,
                                component.parameters())).isPresent())
                        .isPresent());
    }

    private static ComponentType typeOf(ToolContext context, Component component) {
        return context.openDocument().registry().typeOf(component);
    }

    private static Optional<Component> tabUnder(ToolContext context, CanvasMouseEvent event) {
        return resizableSelection(context)
                .filter(component -> ResizeGrip.hits(component, typeOf(context, component), event.gridPosition()));
    }

    @Override
    public Press pressed(ToolContext context, CanvasMouseEvent event) {
        if (event.button() != CanvasMouseEvent.PRIMARY) {
            return Press.IGNORED;
        }
        Optional<Component> under = tabUnder(context, event);
        if (under.isEmpty()) {
            return Press.IGNORED;
        }
        Component component = under.get();
        ComponentType type = typeOf(context, component);
        ResizeSpec spec = type.resize().orElseThrow();
        if (event.clickCount() >= 2) {
            resizeTo(context, component, spec.defaults(type), spec.valuesOf(type, component.parameters()));
            return Press.HANDLED;
        }
        grabbed = component;
        grabbedType = type;
        resize = spec;
        pressPosition = event.gridPosition();
        startValues = spec.valuesOf(type, component.parameters());
        values = startValues;
        dragCursor = cursorFor(component, spec);
        context.repaint();
        return Press.GRABBED;
    }

    /** Executes the resize of {@code component} to {@code target} unless it already has those values. */
    private static void resizeTo(ToolContext context, Component component, Map<String, Integer> target,
                                 Map<String, Integer> current) {
        if (target.equals(current)) {
            return;
        }
        OpenDocument openDocument = context.openDocument();
        openDocument.execute(ResizeComponentCommand.of(openDocument.document(), openDocument.registry(),
                component.id(), target));
        context.repaint();
    }

    /**
     * The resize cursor across the moving edge as the document shows it: east–west, or north–south when turned; for a
     * corner, the diagonal that its bottom-right corner points to once turned.
     */
    private static CanvasCursor cursorFor(Component component, ResizeSpec spec) {
        if (spec.isCorner()) {
            Point2D outwards = ResizeGrip.toDocumentVector(component, 1, 1);
            boolean east = outwards.getX() > 0;
            if (outwards.getY() > 0) {
                return east ? CanvasCursor.RESIZE_SOUTH_EAST : CanvasCursor.RESIZE_SOUTH_WEST;
            }
            return east ? CanvasCursor.RESIZE_NORTH_EAST : CanvasCursor.RESIZE_NORTH_WEST;
        }
        boolean horizontalInPart = spec.edge() == ResizeSpec.Edge.RIGHT;
        boolean turnedSideways = component.rotation().quarterTurns() % 2 == 1;
        return horizontalInPart != turnedSideways ? CanvasCursor.RESIZE_EAST : CanvasCursor.RESIZE_SOUTH;
    }

    @Override
    public void dragged(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        if (context.openDocument().document().component(grabbed.id()).isEmpty()) {
            reset();
            return;
        }
        Point2D along = ResizeGrip.toLocalVector(grabbed, event.gridPosition().getX() - pressPosition.getX(),
                event.gridPosition().getY() - pressPosition.getY());
        Map<String, Integer> next = new LinkedHashMap<>();
        for (ResizeSpec.Axis axis : resize.axes()) {
            double moved = axis.edge() == ResizeSpec.Edge.RIGHT ? along.getX() : along.getY();
            next.put(axis.parameter(), axis.valueAfter(grabbedType, grabbed.parameters(), moved));
        }
        if (!next.equals(values)) {
            values = next;
            context.repaint();
        }
    }

    @Override
    public void released(ToolContext context, CanvasMouseEvent event) {
        if (grabbed == null) {
            return;
        }
        dragged(context, event);
        if (grabbed != null) {
            resizeTo(context, grabbed, values, startValues);
        }
        reset();
        context.repaint();
    }

    @Override
    public void cancelled(ToolContext context) {
        reset();
        context.repaint();
    }

    @Override
    public CanvasCursor pressCursor() {
        return dragCursor;
    }

    @Override
    public Optional<CanvasCursor> moved(ToolContext context, CanvasMouseEvent event) {
        return tabUnder(context, event).map(component -> cursorFor(component,
                typeOf(context, component).resize().orElseThrow()));
    }

    @Override
    public Map<ElementId, Component> previewedParts(ToolContext context) {
        return isPreviewing() ? Map.of(grabbed.id(), resized()) : Map.of();
    }

    @Override
    public Set<ElementId> hiddenWires(ToolContext context) {
        if (!isPreviewing()) {
            return Set.of();
        }
        FollowPlan plan = plan(context);
        Set<ElementId> hidden = new HashSet<>(plan.removedWireIds());
        plan.replacedWires().forEach(wire -> hidden.add(wire.id()));
        return hidden;
    }

    @Override
    public void paintOverlay(Graphics2D graphics, ToolContext context) {
        if (grabbed != null) {
            paintDrag(graphics, context);
            return;
        }
        resizableSelection(context).ifPresent(component -> {
            ComponentType type = typeOf(context, component);
            ResizeGripPainter.paintTab(graphics, component, ResizeGrip.localBounds(component, type),
                    type.resize().orElseThrow().isCorner(), false, context.scheme());
        });
    }

    /** The planned wires as ghosts, the tab filled, and the tag above it. */
    private void paintDrag(Graphics2D graphics, ToolContext context) {
        if (isPreviewing()) {
            FollowPlan plan = plan(context);
            for (Wire wire : plan.replacedWires()) {
                context.paintGhost(graphics, wire);
            }
            for (Wire wire : plan.addedWires()) {
                context.paintGhost(graphics, wire);
            }
        }
        Component shown = resized();
        ComponentType shownType = typeOf(context, shown);
        Rectangle2D tab = ResizeGrip.localBounds(shown, shownType);
        ResizeGripPainter.paintTab(graphics, shown, tab, resize.isCorner(), true, context.scheme());
        Point2D tabTop = ResizeGrip.toDocumentGrid(shown, new Point2D.Double(tab.getCenterX(), tab.getMinY()));
        ResizeGripPainter.paintTag(graphics, ResizeTag.text(shownType, shown.parameters()),
                new Point2D.Double(tabTop.getX() * GridPoint.PIXELS_PER_UNIT,
                        tabTop.getY() * GridPoint.PIXELS_PER_UNIT),
                context.scheme());
    }

    private boolean isPreviewing() {
        return grabbed != null && !values.equals(startValues);
    }

    /** The grabbed part at the values being dragged to. */
    private Component resized() {
        return grabbed.withParameters(resize.parametersWith(grabbed.parameters(), values));
    }

    /** How the wires follow the values being dragged to, planned once per values with throwaway wire ids. */
    private FollowPlan plan(ToolContext context) {
        CircuitDocument document = context.openDocument().document();
        if (document.revision() != plannedRevision) {
            plans.clear();
            plannedRevision = document.revision();
        }
        return plans.computeIfAbsent(values, target -> {
            int[] previewWires = {0};
            return ResizeComponentCommand.wirePlan(document, context.openDocument().registry(), grabbed.id(), target,
                    () -> new ElementId("preview-wire-" + previewWires[0]++));
        });
    }

    private void reset() {
        grabbed = null;
        grabbedType = null;
        resize = null;
        pressPosition = null;
        startValues = Map.of();
        values = Map.of();
        plans.clear();
        plannedRevision = -1;
    }
}
