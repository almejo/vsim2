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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the select tool does with the «↔» tab of a resizable part ({@link ResizeSpec}; Task 6b, first the Teclado). The
 * tab is there only while the part is selected alone (not as part of a locked block). Dragging it changes the
 * part's size live, snapped to whole values and kept inside the parameter's range, with the wires of the pins that
 * move following them as in a move preview, and a tag above the tab («Ancho 24 · 14 teclas»); the release records one
 * undo step ({@link ResizeComponentCommand}), nothing when the value did not change. Esc cancels the drag. A double
 * click on the tab goes back to the smallest value, as one undo step.
 */
public final class ResizeGestures implements SelectToolDelegate {

    /** The part whose tab is held, as it was at the press; null when no drag is under way. */
    private Component grabbed;
    private ComponentType grabbedType;
    private ResizeSpec resize;
    private Point2D pressPosition;
    private int startExtent;
    private int startValue;
    private int value;
    private CanvasCursor dragCursor = CanvasCursor.RESIZE_EAST;
    /** The wire plans of this drag, by value; planned on {@link #plannedRevision} of the document. */
    private final Map<Integer, FollowPlan> plans = new HashMap<>();
    private long plannedRevision = -1;

    /** The part that shows a tab now: the only selected element, when its type is resizable. */
    private static Optional<Component> resizableSelection(ToolContext context) {
        Set<ElementId> selected = context.selection().ids();
        if (selected.size() != 1 || !context.selection().effectiveIds().equals(selected)) {
            return Optional.empty();
        }
        OpenDocument openDocument = context.openDocument();
        return openDocument.document().component(selected.iterator().next())
                .filter(component -> openDocument.registry().findTypeOf(component)
                        .flatMap(ComponentType::resize).isPresent());
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
            resizeTo(context, component, spec.minimum(type), spec.valueOf(type, component.parameters()));
            return Press.HANDLED;
        }
        grabbed = component;
        grabbedType = type;
        resize = spec;
        pressPosition = event.gridPosition();
        startExtent = spec.extentOf(type, component.parameters());
        startValue = spec.valueOf(type, component.parameters());
        value = startValue;
        dragCursor = cursorFor(component, spec);
        context.repaint();
        return Press.GRABBED;
    }

    /** Executes the resize of {@code component} to {@code target} unless it already has it. */
    private static void resizeTo(ToolContext context, Component component, int target, int current) {
        if (target == current) {
            return;
        }
        OpenDocument openDocument = context.openDocument();
        openDocument.execute(ResizeComponentCommand.of(openDocument.document(), openDocument.registry(),
                component.id(), target));
        context.repaint();
    }

    /** The resize cursor across the moving edge as the document shows it: east–west, or north–south when turned. */
    private static CanvasCursor cursorFor(Component component, ResizeSpec spec) {
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
        double moved = resize.edge() == ResizeSpec.Edge.RIGHT ? along.getX() : along.getY();
        int next = resize.valueFor(grabbedType, grabbed.parameters(), startExtent + moved);
        if (next != value) {
            value = next;
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
            resizeTo(context, grabbed, value, startValue);
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
        resizableSelection(context).ifPresent(component -> ResizeGripPainter.paintTab(graphics, component,
                ResizeGrip.localBounds(component, typeOf(context, component)), false, context.scheme()));
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
        ResizeGripPainter.paintTab(graphics, shown, tab, true, context.scheme());
        Point2D tabTop = ResizeGrip.toDocumentGrid(shown, new Point2D.Double(tab.getCenterX(), tab.getMinY()));
        ResizeGripPainter.paintTag(graphics, ResizeTag.text(shownType, shown.parameters()),
                new Point2D.Double(tabTop.getX() * GridPoint.PIXELS_PER_UNIT,
                        tabTop.getY() * GridPoint.PIXELS_PER_UNIT),
                context.scheme());
    }

    private boolean isPreviewing() {
        return grabbed != null && value != startValue;
    }

    /** The grabbed part at the value being dragged to. */
    private Component resized() {
        return grabbed.withParameters(resize.parametersWith(grabbed.parameters(), value));
    }

    /** How the wires follow the value being dragged to, planned once per value with throwaway wire ids. */
    private FollowPlan plan(ToolContext context) {
        CircuitDocument document = context.openDocument().document();
        if (document.revision() != plannedRevision) {
            plans.clear();
            plannedRevision = document.revision();
        }
        return plans.computeIfAbsent(value, target -> {
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
        plans.clear();
        plannedRevision = -1;
    }
}
