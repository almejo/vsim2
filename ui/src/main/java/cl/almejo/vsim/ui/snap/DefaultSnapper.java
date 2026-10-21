package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Axis;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.SnapInput;
import cl.almejo.vsim.core.geometry.SnapKind;
import cl.almejo.vsim.core.geometry.SnapMatch;
import cl.almejo.vsim.core.geometry.SnapResult;
import cl.almejo.vsim.core.geometry.SnapTarget;
import cl.almejo.vsim.core.geometry.Snapping;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.canvas.Viewport;
import cl.almejo.vsim.ui.tools.ToolContext;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The snapper of one canvas (alignment-guides spec §3): reads «Ajustar» and «Ajustar a ▸» from the preferences on
 * every call, turns the zoom into the magnet's reach and asks {@link Snapping#solve}. A moving box aligns with the
 * components and frames on screen (not the excluded ones), with the guides when they are shown and attract, and with
 * the grid lines every {@value #GRID_LINE_SPACING} units when asked; moved wires only with guides and grid lines.
 * The part lines are worked out once and kept until the document's revision, the exclusions or the visible area
 * change, so a drag does not measure every part on every mouse move.
 */
public final class DefaultSnapper implements Snapper {

    /** Grid lines attract every two units whatever the grid shows and whatever the zoom (spec §3.5). */
    static final int GRID_LINE_SPACING = 2;
    /** Parts this close outside the visible area still pull, in grid units (spec §3.1, «a small margin»). */
    static final int VISIBLE_MARGIN_UNITS = 4;

    private final AppPreferences preferences;
    private final Supplier<Dimension> canvasSize;
    private PartTargetsKey partTargetsKey;
    private List<SnapTarget> partTargets = List.of();
    private int partTargetBuilds;

    /**
     * {@code canvasSize} is the size of the canvas in screen pixels: what it shows decides which parts pull. A canvas
     * not laid out yet (no size) lets every part pull.
     */
    public DefaultSnapper(AppPreferences preferences, Supplier<Dimension> canvasSize) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.canvasSize = Objects.requireNonNull(canvasSize, "canvasSize");
    }

    @Override
    public SnapResult snapBox(ToolContext context, MovingSet moving, int rawDeltaX, int rawDeltaY) {
        return boxInput(context, moving, rawDeltaX, rawDeltaY).map(Snapping::solve)
                .orElseGet(() -> SnapResult.unsnapped(rawDeltaX, rawDeltaY));
    }

    @Override
    public List<SnapMatch> boxMatchesAt(ToolContext context, MovingSet moving, int deltaX, int deltaY) {
        return boxInput(context, moving, deltaX, deltaY).map(input -> Snapping.matchesAt(input, deltaX, deltaY))
                .orElse(List.of());
    }

    /** Task 8 snaps wire and frame points to guides and grid lines; until then a point stays on its grid point. */
    @Override
    public PointResolution snapPoint(ToolContext context, GridPoint point) {
        return new PointResolution(point, PointResolution.Reason.GRID);
    }

    @Override
    public SnapResult snapWires(ToolContext context, Set<ElementId> wireIds, int rawDeltaX, int rawDeltaY) {
        return wiresInput(context, wireIds, rawDeltaX, rawDeltaY).map(Snapping::solve)
                .orElseGet(() -> SnapResult.unsnapped(rawDeltaX, rawDeltaY));
    }

    @Override
    public List<SnapMatch> wiresMatchesAt(ToolContext context, Set<ElementId> wireIds, int deltaX, int deltaY) {
        return wiresInput(context, wireIds, deltaX, deltaY).map(input -> Snapping.matchesAt(input, deltaX, deltaY))
                .orElse(List.of());
    }

    /** For tests: how many times the part lines were worked out. */
    int partTargetBuilds() {
        return partTargetBuilds;
    }

    private Optional<SnapInput> boxInput(ToolContext context, MovingSet moving, int rawDeltaX, int rawDeltaY) {
        if (!preferences.snapToAlignments() || moving.box().isEmpty()) {
            return Optional.empty();
        }
        List<SnapTarget> targets = new ArrayList<>();
        if (preferences.snapToParts()) {
            targets.addAll(partTargets(context, moving.excludedIds()));
        }
        targets.addAll(guideTargets(context));
        OptionalInt spacing = gridLineSpacing();
        if (targets.isEmpty() && spacing.isEmpty()) {
            return Optional.empty();
        }
        Bounds box = moving.box().get();
        Map<Axis, List<Double>> movingLines = new EnumMap<>(Axis.class);
        movingLines.put(Axis.X, List.of((double) box.x(), box.x() + box.width() / 2.0, (double) box.maximumX()));
        movingLines.put(Axis.Y, List.of((double) box.y(), box.y() + box.height() / 2.0, (double) box.maximumY()));
        return Optional.of(new SnapInput(movingLines, rawDeltaX, rawDeltaY, targets, thresholdUnits(context), spacing,
                moving.box()));
    }

    /** Moved wires (spec §4.4): their end points are the moving lines; only guides and grid lines pull them. */
    private Optional<SnapInput> wiresInput(ToolContext context, Set<ElementId> wireIds, int rawDeltaX,
                                           int rawDeltaY) {
        if (!preferences.snapToAlignments()) {
            return Optional.empty();
        }
        List<SnapTarget> targets = guideTargets(context);
        OptionalInt spacing = gridLineSpacing();
        if (targets.isEmpty() && spacing.isEmpty()) {
            return Optional.empty();
        }
        CircuitDocument document = context.openDocument().document();
        Set<Double> columns = new LinkedHashSet<>();
        Set<Double> rows = new LinkedHashSet<>();
        for (ElementId id : wireIds) {
            Optional<Wire> wire = document.wire(id);
            if (wire.isPresent()) {
                for (GridPoint end : List.of(wire.get().start(), wire.get().end())) {
                    columns.add((double) end.x());
                    rows.add((double) end.y());
                }
            }
        }
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        Map<Axis, List<Double>> movingLines = new EnumMap<>(Axis.class);
        movingLines.put(Axis.X, List.copyOf(columns));
        movingLines.put(Axis.Y, List.copyOf(rows));
        return Optional.of(new SnapInput(movingLines, rawDeltaX, rawDeltaY, targets, thresholdUnits(context), spacing,
                Optional.empty()));
    }

    private OptionalInt gridLineSpacing() {
        return preferences.snapToGridLines() ? OptionalInt.of(GRID_LINE_SPACING) : OptionalInt.empty();
    }

    private static int thresholdUnits(ToolContext context) {
        return Snapping.thresholdUnits(context.viewport().pixelsPerUnit());
    }

    /** The guides as target lines while they are shown and «Guías» is chosen (a vertical guide is an X line). */
    private List<SnapTarget> guideTargets(ToolContext context) {
        if (!preferences.showGuides() || !preferences.snapToGuides()) {
            return List.of();
        }
        List<SnapTarget> targets = new ArrayList<>();
        for (Guide guide : context.openDocument().document().guides()) {
            Axis axis = guide.orientation() == Guide.Orientation.VERTICAL ? Axis.X : Axis.Y;
            targets.add(new SnapTarget(SnapKind.GUIDE, axis, guide.position(), Optional.empty(), Optional.empty()));
        }
        return targets;
    }

    /** Edges and centres of the components and frames that are on screen and not excluded, kept per gesture. */
    private List<SnapTarget> partTargets(ToolContext context, Set<ElementId> excludedIds) {
        CircuitDocument document = context.openDocument().document();
        Optional<Bounds> visible = visibleArea(context.viewport());
        PartTargetsKey key = new PartTargetsKey(document, document.revision(), context.openDocument().registry(),
                excludedIds, visible);
        if (key.equals(partTargetsKey)) {
            return partTargets;
        }
        partTargetBuilds++;
        ComponentTypeRegistry registry = context.openDocument().registry();
        List<DocumentElement> parts = new ArrayList<>(document.components());
        parts.addAll(document.frames());
        List<SnapTarget> targets = new ArrayList<>();
        for (DocumentElement part : parts) {
            if (excludedIds.contains(part.id())) {
                continue;
            }
            Bounds box = ElementBounds.of(part, registry);
            if (visible.isPresent() && !visible.get().intersects(box)) {
                continue;
            }
            Optional<ElementId> owner = Optional.of(part.id());
            Optional<Bounds> ownerBox = Optional.of(box);
            for (double position : List.of((double) box.x(), box.x() + box.width() / 2.0,
                    (double) box.maximumX())) {
                targets.add(new SnapTarget(SnapKind.PART, Axis.X, position, owner, ownerBox));
            }
            for (double position : List.of((double) box.y(), box.y() + box.height() / 2.0,
                    (double) box.maximumY())) {
                targets.add(new SnapTarget(SnapKind.PART, Axis.Y, position, owner, ownerBox));
            }
        }
        partTargetsKey = key;
        partTargets = List.copyOf(targets);
        return partTargets;
    }

    /** What the canvas shows, in grid units, grown by the margin; empty for a canvas with no size yet. */
    private Optional<Bounds> visibleArea(Viewport viewport) {
        Dimension size = canvasSize.get();
        if (size == null || size.width <= 0 || size.height <= 0) {
            return Optional.empty();
        }
        Point2D topLeft = viewport.screenToGrid(0, 0);
        Point2D bottomRight = viewport.screenToGrid(size.width, size.height);
        int left = (int) Math.floor(topLeft.getX()) - VISIBLE_MARGIN_UNITS;
        int top = (int) Math.floor(topLeft.getY()) - VISIBLE_MARGIN_UNITS;
        int right = (int) Math.ceil(bottomRight.getX()) + VISIBLE_MARGIN_UNITS;
        int bottom = (int) Math.ceil(bottomRight.getY()) + VISIBLE_MARGIN_UNITS;
        return Optional.of(new Bounds(left, top, right - left, bottom - top));
    }

    /** What the cached part lines depend on. */
    private record PartTargetsKey(CircuitDocument document, long revision, ComponentTypeRegistry registry,
                                  Set<ElementId> excludedIds, Optional<Bounds> visible) {

        @Override
        public boolean equals(Object other) {
            return other instanceof PartTargetsKey key && key.document == document && key.revision == revision
                    && key.registry == registry && key.excludedIds.equals(excludedIds)
                    && key.visible.equals(visible);
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(document), revision, excludedIds, visible);
        }
    }
}
