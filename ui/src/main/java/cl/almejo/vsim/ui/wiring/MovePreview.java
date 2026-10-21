package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.wiring.FollowPlan;
import cl.almejo.vsim.core.wiring.NewContact;
import cl.almejo.vsim.ui.snap.ContactCache;
import cl.almejo.vsim.ui.snap.ContactDots;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The preview of a move while it is dragged (M6.7): the ghosts of the moved elements and, when the wires follow, the
 * ghosts of the wires as they will be (stretched, inserted, moved whole); then a dot on every new contact, with the
 * wires following or not (alignment guides spec §3.7, {@link ContactDots}). The plan and the contacts are worked out
 * once per snapped delta and kept until the drag ends ({@link #clear}) or the document changes.
 *
 * <p>The contacts are those {@link cl.almejo.vsim.core.wiring.ContactProbe} finds for the moved elements (for a Ctrl
 * copy, against the whole circuit, the originals included). When the wires follow, the contacts of the follow plan
 * (the pieces the wires stretch, insert or move whole) are added, one per point, and the wires the plan changes are
 * left out of what the moved elements can touch, as they will not be where they are; so the contacts of the moved
 * parts themselves are the same with the wires following or not.
 *
 * <p>So that a wire is never drawn twice, solid where it is and translucent where it goes, the canvas leaves out the
 * wires the plan changes ({@link #hiddenWires}): during the drag, everything solid stays as it is and everything
 * translucent is where it will be. The moved elements themselves stay drawn in place, as since M1 (their selection
 * outline marks them).
 */
public final class MovePreview {

    private final Map<GridPoint, FollowPlan> plans = new HashMap<>();
    private final ContactCache contactCache = new ContactCache();
    private Set<ElementId> plannedIds = Set.of();
    private long plannedRevision = -1;
    private CircuitDocument plannedDocument;

    /**
     * Paints the preview of moving {@code ids} by the delta (a Ctrl copy of them when {@code copy}), then its new
     * contacts, the pin names near {@code pointer} (grid units); nothing for a zero delta.
     */
    public void paint(Graphics2D graphics, ToolContext context, Set<ElementId> ids, int deltaX, int deltaY,
                      boolean follow, boolean copy, Optional<Point2D> pointer) {
        if (deltaX == 0 && deltaY == 0) {
            return;
        }
        CircuitDocument document = context.openDocument().document();
        for (ElementId id : ids) {
            document.element(id).ifPresent(element -> context.paintGhost(graphics, element.translate(deltaX, deltaY)));
        }
        if (follow && !copy) {
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
        }
        ContactDots.paint(graphics, context, contacts(context, ids, deltaX, deltaY, follow, copy), pointer);
    }

    /**
     * The new contacts of moving {@code ids} by the delta (see the class comment), sorted by point; none for a zero
     * delta. A copy never drags wires, so {@code follow} is ignored with {@code copy}.
     */
    public List<NewContact> contacts(ToolContext context, Set<ElementId> ids, int deltaX, int deltaY, boolean follow,
                                     boolean copy) {
        if (deltaX == 0 && deltaY == 0) {
            return List.of();
        }
        CircuitDocument document = context.openDocument().document();
        List<DocumentElement> moved = new ArrayList<>();
        for (ElementId id : ids) {
            document.element(id).ifPresent(moved::add);
        }
        Set<ElementId> excluded = new HashSet<>(copy ? Set.of() : ids);
        boolean following = follow && !copy;
        FollowPlan plan = following ? plan(context, ids, deltaX, deltaY) : FollowPlan.EMPTY;
        excluded.addAll(plan.movedWholeWireIds());
        plan.replacedWires().forEach(wire -> excluded.add(wire.id()));
        excluded.addAll(plan.removedWireIds());
        List<NewContact> ofTheMoved = contactCache.contacts(document, context.openDocument().registry(), moved,
                deltaX, deltaY, excluded);
        if (plan.newContacts().isEmpty()) {
            return ofTheMoved;
        }
        Map<GridPoint, NewContact> byPoint = new TreeMap<>();
        ofTheMoved.forEach(contact -> byPoint.put(contact.point(), contact));
        plan.newContacts().forEach(contact -> byPoint.putIfAbsent(contact.point(), contact));
        return List.copyOf(byPoint.values());
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

    /** Forgets the cached plans (the drag ended). */
    public void clear() {
        plans.clear();
        plannedIds = Set.of();
        plannedRevision = -1;
        plannedDocument = null;
        contactCache.clear();
    }
}
