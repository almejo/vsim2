package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The preview of a move while it is dragged (M6.7): the ghosts of the moved elements and, when the wires follow, the
 * ghosts of the wires as they will be (stretched, inserted, moved whole) and a red dot on every new contact. The plan
 * is worked out once per snapped delta and kept until the drag ends ({@link #clear}) or the document changes.
 *
 * <p>So that a wire is never drawn twice, solid where it is and translucent where it goes, the canvas leaves out the
 * wires the plan changes ({@link #hiddenWires}): during the drag, everything solid stays as it is and everything
 * translucent is where it will be. The moved elements themselves stay drawn in place, as since M1 (their selection
 * outline marks them).
 */
public final class MovePreview {

    private static final double CONTACT_RADIUS = 4.5;
    private static final BasicStroke CONTACT_RING = new BasicStroke(1.5f);

    private final Map<GridPoint, FollowPlan> plans = new HashMap<>();
    private Set<ElementId> plannedIds = Set.of();
    private long plannedRevision = -1;
    private CircuitDocument plannedDocument;

    /** Paints the preview of moving {@code ids} by the delta; nothing for a zero delta. */
    public void paint(Graphics2D graphics, ToolContext context, Set<ElementId> ids, int deltaX, int deltaY,
                      boolean follow) {
        if (deltaX == 0 && deltaY == 0) {
            return;
        }
        CircuitDocument document = context.openDocument().document();
        for (ElementId id : ids) {
            document.element(id).ifPresent(element -> context.paintGhost(graphics, element.translate(deltaX, deltaY)));
        }
        if (!follow) {
            return;
        }
        FollowPlan plan = plan(context, ids, deltaX, deltaY);
        for (ElementId id : plan.movedWholeWireIds()) {
            document.wire(id).ifPresent(wire -> context.paintGhost(graphics, wire.translate(deltaX, deltaY)));
        }
        for (Wire wire : plan.replacedWires()) {
            context.paintGhost(graphics, wire);
        }
        for (Wire wire : plan.addedWires()) {
            context.paintGhost(graphics, wire);
        }
        paintContacts(graphics, context, plan);
    }

    /** The wires the plan for that delta stretches, moves whole or removes (drawn by the preview, not by the canvas). */
    public Set<ElementId> hiddenWires(ToolContext context, Set<ElementId> ids, int deltaX, int deltaY) {
        if (deltaX == 0 && deltaY == 0) {
            return Set.of();
        }
        FollowPlan plan = plan(context, ids, deltaX, deltaY);
        Set<ElementId> hidden = new HashSet<>(plan.movedWholeWireIds());
        plan.replacedWires().forEach(wire -> hidden.add(wire.id()));
        hidden.addAll(plan.removedWireIds());
        return hidden;
    }

    /** The plan for that delta, from the cache when the same ids were planned on the same document revision. */
    public FollowPlan plan(ToolContext context, Set<ElementId> ids, int deltaX, int deltaY) {
        CircuitDocument document = context.openDocument().document();
        if (document != plannedDocument || document.revision() != plannedRevision || !ids.equals(plannedIds)) {
            plans.clear();
            plannedDocument = document;
            plannedRevision = document.revision();
            plannedIds = Set.copyOf(ids);
        }
        return plans.computeIfAbsent(new GridPoint(deltaX, deltaY),
                delta -> SelectionMoves.preview(context.openDocument(), ids, deltaX, deltaY));
    }

    /** A red dot ringed with the board color on each new contact: an overlay only, never exported. */
    private static void paintContacts(Graphics2D graphics, ToolContext context, FollowPlan plan) {
        if (plan.newContacts().isEmpty()) {
            return;
        }
        Object antialiasing = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setStroke(CONTACT_RING);
        double unit = GridPoint.PIXELS_PER_UNIT;
        for (NewContact contact : plan.newContacts()) {
            Ellipse2D dot = new Ellipse2D.Double(contact.point().x() * unit - CONTACT_RADIUS,
                    contact.point().y() * unit - CONTACT_RADIUS, 2 * CONTACT_RADIUS, 2 * CONTACT_RADIUS);
            graphics.setColor(context.scheme().wireConflict());
            graphics.fill(dot);
            graphics.setColor(context.scheme().background());
            graphics.draw(dot);
        }
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                Objects.requireNonNullElse(antialiasing, RenderingHints.VALUE_ANTIALIAS_DEFAULT));
    }

    /** Forgets the cached plans (the drag ended). */
    public void clear() {
        plans.clear();
        plannedIds = Set.of();
        plannedRevision = -1;
        plannedDocument = null;
    }
}
