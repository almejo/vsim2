package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.NoteColor;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/** Immutable working symbol with transient selection and validation, independent of Swing and circuit history. */
public final class SymbolDraft {
    public enum Handle { TOP_LEFT, TOP, TOP_RIGHT, RIGHT, BOTTOM_RIGHT, BOTTOM, BOTTOM_LEFT, LEFT }

    /** Selection bounds in eighth-grid coordinates, including curve controls. */
    public record Box(int left, int top, int right, int bottom) {
        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }
    }

    /** A node of a figure: its element index and its place in the figure. */
    private record NodeKey(int path, int node) { }

    /** Body drags never use a parameter closer to an end than this: there the handles would have to fly off. */
    private static final double BEND_PARAMETER_MARGIN = 0.1;
    /** Body drags at a parameter up to this far from an end move only that end's handle (Inkscape's weighting). */
    private static final double BEND_END_SHARE = 1.0 / 6;

    private final ComponentAppearance appearance;
    private final List<DefinitionPin> pinout;
    private final String definitionName;
    private final boolean pinsLocked;
    private final Set<Integer> selection;
    /**
     * Smooth or corner as the user chose it while editing, which the file does not keep: a corner whose handles still
     * lie on one line, or a smooth node rounded slightly off it. Nodes not here are inferred from their handles.
     */
    private final Map<NodeKey, Boolean> smoothChoices;

    private SymbolDraft(ComponentAppearance appearance, List<DefinitionPin> pinout, String definitionName,
                        boolean pinsLocked, Set<Integer> selection, Map<NodeKey, Boolean> smoothChoices) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.pinout = List.copyOf(pinout);
        this.definitionName = Objects.requireNonNull(definitionName, "definitionName");
        this.pinsLocked = pinsLocked;
        this.selection = Set.copyOf(selection);
        this.smoothChoices = Map.copyOf(smoothChoices);
    }

    public static SymbolDraft of(ComponentAppearance appearance, List<DefinitionPin> pinout, boolean pinsLocked) {
        return new SymbolDraft(appearance, pinout, "", pinsLocked, Set.of(), Map.of());
    }

    /**
     * Whether {@code other} is the same edit: the same appearance and the same smooth or corner choices (selection
     * aside), so the editor's history keeps one step per change the user can see or feel.
     */
    public boolean sameEdit(SymbolDraft other) {
        return appearance.equals(other.appearance) && smoothChoices.equals(other.smoothChoices);
    }

    public ComponentAppearance appearance() {
        return appearance;
    }

    /**
     * The default shape (no own drawing) as the real figures it is drawn with ({@link SymbolTemplates#defaultShape}):
     * the rectangle on the box edges and the component's name. The editor opens on this, so the shape can be chosen
     * and edited from the first click; any other drawing is returned as it is.
     */
    public SymbolDraft withDefaultShapeAsFigures() {
        return appearance.drawing().isPresent() ? this : withDrawing(defaultShape(), Set.of());
    }

    /**
     * The appearance to store: a drawing equal to the default shape for the current box becomes no own drawing, so
     * the component keeps following future changes of the default shape.
     */
    public ComponentAppearance storedAppearance() {
        return isDefaultShape() ? new ComponentAppearance(appearance.size(), Optional.empty(), appearance.pins())
                : appearance;
    }

    /** No own drawing, or a drawing equal to the default shape for the current box. */
    private boolean isDefaultShape() {
        return appearance.drawing().isEmpty() || appearance.drawing().equals(defaultShape());
    }

    private Optional<SymbolDrawing> defaultShape() {
        return Optional.of(SymbolTemplates.defaultShape(appearance.size()));
    }

    /**
     * {@code changed} (this draft with another box) still showing the default shape when this one showed it: the
     * default for the new box, whose name keeps the default box's place, so an untouched default never turns into
     * an own drawing.
     */
    private SymbolDraft keepingTheDefaultShape(SymbolDraft changed) {
        if (appearance.drawing().isEmpty() || !isDefaultShape()) {
            return changed;
        }
        return changed.withDrawing(changed.defaultShape(), changed.selection);
    }

    private SymbolDraft withDrawing(Optional<SymbolDrawing> drawing, Set<Integer> selected) {
        return withAppearance(new ComponentAppearance(appearance.size(), drawing, appearance.pins()), selected);
    }

    public Set<Integer> selection() {
        return selection;
    }

    public boolean pinsLocked() {
        return pinsLocked;
    }

    public List<SymbolElement> elements() {
        return appearance.drawing().map(SymbolDrawing::elements).orElse(List.of());
    }

    public SymbolDraft select(Set<Integer> indices) {
        if (indices.stream().anyMatch(index -> index < 0 || index >= elements().size())) {
            throw new IllegalArgumentException("Selection outside drawing");
        }
        return new SymbolDraft(appearance, pinout, definitionName, pinsLocked, indices, smoothChoices);
    }

    public SymbolDraft selectAll() {
        return select(new LinkedHashSet<>(IntStream.range(0, elements().size()).boxed().toList()));
    }

    /** The component's name, which templates write in the free area of the box. */
    public SymbolDraft withDefinitionName(String name) {
        return new SymbolDraft(appearance, pinout, name, pinsLocked, selection, smoothChoices);
    }

    public SymbolDraft withPinLock(boolean locked) {
        return new SymbolDraft(appearance, pinout, definitionName, locked, selection, smoothChoices);
    }

    public SymbolDraft addFigure(SymbolPath path) {
        return append(path);
    }

    public SymbolDraft addText(SymbolText text) {
        return append(text);
    }

    private SymbolDraft append(SymbolElement element) {
        List<SymbolElement> changed = new ArrayList<>(elements());
        changed.add(element);
        return withElements(changed, Set.of(changed.size() - 1));
    }

    public SymbolDraft editText(int index, SymbolText text) {
        return replaceElement(index, text);
    }

    public SymbolDraft deleteSelection() {
        if (selection.isEmpty()) {
            return this;
        }
        List<Integer> keptIndices = IntStream.range(0, elements().size()).filter(index -> !selection.contains(index))
                .boxed().toList();
        List<SymbolElement> kept = keptIndices.stream().map(elements()::get).toList();
        return withElements(kept, Set.of()).withSmoothChoices(choicesForElements(keptIndices::indexOf));
    }

    public SymbolDraft moveSelection(int deltaX, int deltaY, SnapMode snap) {
        Optional<Box> box = selectionBounds();
        if (box.isEmpty()) {
            return this;
        }
        Box bounds = box.get();
        // The delta is snapped, not each point: a figure drawn off the magnet's lattice keeps its shape when moved.
        int horizontal = Math.clamp(snap.snap(deltaX), -bounds.left(), maximumX() - bounds.right());
        int vertical = Math.clamp(snap.snap(deltaY), -bounds.top(), maximumY() - bounds.bottom());
        return mapSelection(point -> new SymbolPoint(point.x() + horizontal, point.y() + vertical));
    }

    public SymbolDraft duplicateSelection() {
        if (selection.isEmpty()) {
            return this;
        }
        List<SymbolElement> changed = new ArrayList<>(elements());
        Set<Integer> duplicates = new LinkedHashSet<>();
        Box box = selectionBounds().orElseThrow();
        int horizontal = Math.min(SymbolPoint.EIGHTHS, maximumX() - box.right());
        int vertical = Math.min(SymbolPoint.EIGHTHS, maximumY() - box.bottom());
        for (int index = 0; index < elements().size(); index++) {
            if (selection.contains(index)) {
                duplicates.add(changed.size());
                changed.add(mapElement(elements().get(index), point -> bounded(
                        new SymbolPoint(point.x() + horizontal, point.y() + vertical), SnapMode.FREE)));
            }
        }
        return withElements(changed, duplicates);
    }

    public SymbolDraft bringToFront() {
        return reorder(true);
    }

    public SymbolDraft sendToBack() {
        return reorder(false);
    }

    private SymbolDraft reorder(boolean front) {
        if (selection.isEmpty()) {
            return this;
        }
        List<Integer> chosen = new ArrayList<>();
        List<Integer> rest = new ArrayList<>();
        for (int index = 0; index < elements().size(); index++) {
            (selection.contains(index) ? chosen : rest).add(index);
        }
        List<Integer> order = new ArrayList<>(front ? rest : chosen);
        order.addAll(front ? chosen : rest);
        int first = front ? rest.size() : 0;
        return withElements(order.stream().map(elements()::get).toList(),
                new LinkedHashSet<>(IntStream.range(first, first + chosen.size()).boxed().toList()))
                .withSmoothChoices(choicesForElements(order::indexOf));
    }

    /** Node {@code nodeIndex} to {@code point}; its own handles (of cubic segments) travel with it. */
    public SymbolDraft moveNode(int pathIndex, int nodeIndex, SymbolPoint point, SnapMode snap) {
        SymbolPath path = path(pathIndex);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        SymbolPoint original = nodes.get(nodeIndex).point();
        SymbolPoint moved = bounded(point, snap);
        nodes.set(nodeIndex, nodes.get(nodeIndex).withPoint(moved));
        for (HandleSide side : HandleSide.values()) {
            path.segmentOf(nodeIndex, side).ifPresent(segment -> {
                if (nodes.get(segment).curve().orElse(null) instanceof SegmentCurve.Cubic) {
                    SymbolPoint handle = path.handle(nodeIndex, side).orElseThrow();
                    setHandle(nodes, segment, side, bounded(new SymbolPoint(handle.x() + moved.x() - original.x(),
                            handle.y() + moved.y() - original.y()), SnapMode.FREE));
                }
            });
        }
        return replacePath(pathIndex, path, nodes);
    }

    /** Whether node {@code nodeIndex} is smooth: between two curves, as the user chose or as its handles say. */
    public boolean smooth(int pathIndex, int nodeIndex) {
        SymbolPath path = path(pathIndex);
        return path.hasTwoHandles(nodeIndex)
                && smoothChoices.getOrDefault(new NodeKey(pathIndex, nodeIndex), path.looksSmooth(nodeIndex));
    }

    /**
     * Handle {@code side} of node {@code nodeIndex} to {@code to} (a quadratic segment becomes its cubic first). On a
     * smooth node the other handle turns the opposite way with the same length (shortened only where the box ends:
     * the handle under the pointer always follows it); {@code alone} (Alt) moves only this one and makes the node a
     * corner.
     */
    public SymbolDraft moveHandle(int pathIndex, int nodeIndex, HandleSide side, SymbolPoint to, boolean alone,
                                  SnapMode snap) {
        SymbolPath path = path(pathIndex);
        if (path.handle(nodeIndex, side).isEmpty()) {
            throw new IllegalArgumentException("No " + side + " handle at node " + nodeIndex);
        }
        boolean mirrored = !alone && smooth(pathIndex, nodeIndex);
        SymbolPoint target = bounded(to, snap);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        setHandle(nodes, path.segmentOf(nodeIndex, side).orElseThrow(), side, target);
        if (mirrored) {
            SymbolPoint point = path.nodes().get(nodeIndex).point();
            double length = distance(point, target);
            SymbolPoint opposite = length == 0 ? point : alongInside(point, (point.x() - target.x()) / length,
                    (point.y() - target.y()) / length, length);
            setHandle(nodes, path.segmentOf(nodeIndex, side.opposite()).orElseThrow(), side.opposite(), opposite);
        }
        SymbolDraft changed = replacePath(pathIndex, path, nodes);
        return path.hasTwoHandles(nodeIndex) ? changed.choose(pathIndex, nodeIndex, mirrored) : changed;
    }

    /**
     * A smooth node becomes a corner (nothing moves); a corner becomes smooth with its handles turned by the same
     * angle onto one line through the node, each keeping its length (shortened only if it would leave the box).
     */
    public SymbolDraft toggleSmooth(int pathIndex, int nodeIndex) {
        SymbolPath path = path(pathIndex);
        if (!path.hasTwoHandles(nodeIndex)) {
            throw new IllegalArgumentException("Node " + nodeIndex + " is not between two curves");
        }
        if (smooth(pathIndex, nodeIndex)) {
            return choose(pathIndex, nodeIndex, false);
        }
        SymbolPoint point = path.nodes().get(nodeIndex).point();
        SymbolPoint incoming = path.handle(nodeIndex, HandleSide.INCOMING).orElseThrow();
        SymbolPoint outgoing = path.handle(nodeIndex, HandleSide.OUTGOING).orElseThrow();
        CurvePoint direction = smoothDirection(path, nodeIndex, point, incoming, outgoing);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        setHandle(nodes, path.segmentOf(nodeIndex, HandleSide.INCOMING).orElseThrow(), HandleSide.INCOMING,
                alongInside(point, -direction.x(), -direction.y(), distance(point, incoming)));
        setHandle(nodes, path.segmentOf(nodeIndex, HandleSide.OUTGOING).orElseThrow(), HandleSide.OUTGOING,
                alongInside(point, direction.x(), direction.y(), distance(point, outgoing)));
        return replacePath(pathIndex, path, nodes).choose(pathIndex, nodeIndex, true);
    }

    /** The parameter of the point of segment {@code segment} nearest to {@code point}: where a body drag grabs it. */
    public double nearestParameter(int pathIndex, int segment, SymbolPoint point) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        return path.nearestParameter(segment, point.x(), point.y());
    }

    /**
     * Segment {@code segment} bent so its point at {@code parameter} passes through {@code target} («Curvar tramo»):
     * a straight segment first becomes a cubic with its handles at a third and two thirds, then both handles move,
     * the nearer one more (Inkscape's weighting), kept inside the box. A smooth node at either end stays smooth: its
     * other handle turns with this one, keeping its length.
     */
    public SymbolDraft bendSegment(int pathIndex, int segment, double parameter, SymbolPoint target, SnapMode snap) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        CubicSpan span = path.span(segment);
        double grabbed = Math.clamp(parameter, BEND_PARAMETER_MARGIN, 1 - BEND_PARAMETER_MARGIN);
        CurvePoint now = span.at(grabbed);
        SymbolPoint aim = new SymbolPoint(snap.snap(target.x()), snap.snap(target.y()));
        double shiftX = aim.x() - now.x();
        double shiftY = aim.y() - now.y();
        double weight = bendWeight(grabbed);
        double rest = 1 - grabbed;
        double firstShare = (1 - weight) / (3 * grabbed * rest * rest);
        double secondShare = weight / (3 * grabbed * grabbed * rest);
        SymbolPoint first = bounded(CubicSpan.rounded(new CurvePoint(span.first().x() + firstShare * shiftX,
                span.first().y() + firstShare * shiftY)), SnapMode.FREE);
        SymbolPoint second = bounded(CubicSpan.rounded(new CurvePoint(span.second().x() + secondShare * shiftX,
                span.second().y() + secondShare * shiftY)), SnapMode.FREE);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(segment, SymbolNode.cubic(nodes.get(segment).point(), first, second));
        keepSmooth(pathIndex, path, nodes, segment, HandleSide.OUTGOING);
        keepSmooth(pathIndex, path, nodes, (segment + 1) % nodes.size(), HandleSide.INCOMING);
        return replacePath(pathIndex, path, nodes);
    }

    /** Segment {@code segment} back to a straight line. */
    public SymbolDraft straighten(int pathIndex, int segment) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(segment, nodes.get(segment).straightened());
        return replacePath(pathIndex, path, nodes);
    }

    /**
     * A node on segment {@code segment}: at {@code at} on a straight segment; on a curve, at its point nearest to
     * {@code at}, splitting it so the outline stays the same (a split cubic gives a smooth node).
     */
    public SymbolDraft insertNode(int pathIndex, int segment, SymbolPoint at) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        SymbolNode start = path.nodes().get(segment);
        List<SymbolNode> changed = new ArrayList<>(path.nodes());
        if (start.curve().isEmpty()) {
            changed.add(segment + 1, SymbolNode.straight(bounded(at, SnapMode.FREE)));
        } else if (start.curve().get() instanceof SegmentCurve.Quadratic quadratic) {
            SymbolPoint control = quadratic.control();
            SymbolPoint end = path.segmentEnd(segment);
            double parameter = path.nearestParameter(segment, at.x(), at.y());
            SymbolPoint left = interpolate(start.point(), control, parameter);
            SymbolPoint right = interpolate(control, end, parameter);
            changed.set(segment, SymbolNode.quadratic(start.point(), left));
            changed.add(segment + 1, SymbolNode.quadratic(interpolate(left, right, parameter), right));
        } else {
            List<CubicSpan> halves = path.span(segment).split(path.nearestParameter(segment, at.x(), at.y()));
            changed.set(segment, cubicNode(halves.getFirst()));
            changed.add(segment + 1, cubicNode(halves.getLast()));
        }
        return replacePath(pathIndex, path, changed)
                .withSmoothChoices(choicesForNodes(pathIndex, node -> node > segment ? node + 1 : node));
    }

    public SymbolDraft deleteNode(int pathIndex, int nodeIndex) {
        SymbolPath path = path(pathIndex);
        if (path.nodes().size() == 2) {
            return select(Set.of(pathIndex)).deleteSelection();
        }
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.remove(nodeIndex);
        boolean closed = path.closed() && nodes.size() >= 3;
        int previous = Math.floorMod(nodeIndex - 1, nodes.size());
        nodes.set(previous, nodes.get(previous).straightened());
        if (!closed) {
            nodes.set(nodes.size() - 1, nodes.getLast().straightened());
        }
        return replaceElement(pathIndex, path.reshaped(nodes, closed, closed && path.filled()))
                .withSmoothChoices(choicesForNodes(pathIndex, node -> node == nodeIndex ? -1
                        : node > nodeIndex ? node - 1 : node));
    }

    public SymbolDraft closePath(int index) {
        SymbolPath path = path(index);
        return replaceElement(index, path.reshaped(path.nodes(), true, path.filled()));
    }

    /** The closed figure at {@code index} opened where it closed: its closing segment and its fill go. */
    public SymbolDraft openPath(int index) {
        SymbolPath path = path(index);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(nodes.size() - 1, nodes.getLast().straightened());
        return replaceElement(index, path.reshaped(nodes, false, false));
    }

    /** The figure at {@code index} with those fill and outline colours (a fill colour fills the figure). */
    public SymbolDraft setFigureColours(int index, NoteColor fill, NoteColor line) {
        SymbolPath path = path(index);
        boolean filled = path.filled() || fill != NoteColor.NONE && path.closed();
        return replaceElement(index, path.reshaped(path.nodes(), path.closed(), filled).coloured(
                filled ? fill : NoteColor.NONE, line));
    }

    public SymbolDraft setFilled(int index, boolean filled) {
        SymbolPath path = path(index);
        return replaceElement(index, path.reshaped(path.nodes(), path.closed(), path.closed() && filled));
    }

    public SymbolDraft showPinNames(boolean show) {
        if (appearance.drawing().isEmpty() && show) {
            return this;
        }
        Optional<SymbolDrawing> drawing = appearance.drawing().or(this::defaultShape).map(current ->
                new SymbolDrawing(appearance.size(), current.elements(), show));
        return withDrawing(drawing, selection);
    }

    /** The template's figures; «Forma predeterminada» gives the default shape's (stored as no own drawing). */
    public SymbolDraft applyTemplate(SymbolTemplates.Template template) {
        return withDrawing(template == SymbolTemplates.Template.DEFAULT_BOX ? defaultShape()
                : SymbolTemplates.drawing(template, appearance.size(), definitionName, pinLabels()), Set.of())
                .withSmoothChoices(Map.of());
    }

    private List<SymbolTemplates.PinLabel> pinLabels() {
        return appearance.pins().stream().map(pin -> pinout.stream()
                .filter(definitionPin -> definitionPin.source().equals(pin.source())).findFirst()
                .map(definitionPin -> new SymbolTemplates.PinLabel(pinText(definitionPin.spec()), pin.offset())))
                .flatMap(Optional::stream).toList();
    }

    private static String pinText(PinSpec spec) {
        return spec.width() > 1 ? spec.name() + "[" + spec.width() + "]" : spec.name();
    }

    public SymbolDraft movePin(ElementId source, GridPoint offset) {
        requireUnlocked();
        if (appearance.pins().stream().noneMatch(pin -> pin.source().equals(source))) {
            throw new IllegalArgumentException("Unknown pin source " + source);
        }
        GridPoint clamped = new GridPoint(Math.clamp(offset.x(), 0, appearance.size().width()),
                Math.clamp(offset.y(), 0, appearance.size().height()));
        return withAppearance(new ComponentAppearance(appearance.size(), appearance.drawing(),
                appearance.pins().stream().map(pin -> pin.source().equals(source)
                        ? new PinPlacement(source, clamped) : pin).toList()), selection);
    }

    /**
     * The box at {@code size}, with one rule for what touches its edges: a figure's node on an edge moves with that
     * edge (the rectangle of the default shape grows with the box, a triangle's tip stays on the right edge), a
     * control point moves with its node, and everything else stays centred, moving by half the change (half units
     * are fine for a drawing). Pins move by half the change rounded to whole units, so they stay on the grid (an odd
     * change leaves the extra unit to the right or below them); a pin on an edge stays on that edge. The default shape
     * stays the default shape for the new box. Refused (an exception) when the drawing or a pin would fall outside,
     * or two pins would meet.
     */
    public SymbolDraft resizeBox(GridSize size) {
        requireUnlocked();
        List<PinPlacement> pins = centredPins(size);
        if (pins.stream().anyMatch(pin -> !inside(pin.offset(), size))) {
            throw new IllegalArgumentException("Box would cut a pin");
        }
        if (pins.stream().map(PinPlacement::offset).distinct().count() != pins.size()) {
            throw new IllegalArgumentException("Box resize would create coinciding pins");
        }
        List<SymbolElement> elements = resizedElements(size);
        if (elements.stream().flatMap(element -> points(element).stream()).anyMatch(point -> !inside(point, size))) {
            throw new IllegalArgumentException("Box would cut the drawing");
        }
        Optional<SymbolDrawing> drawing = appearance.drawing().map(current ->
                new SymbolDrawing(size, elements, current.showPinNames()));
        return keepingTheDefaultShape(withAppearance(new ComponentAppearance(size, drawing, pins), selection));
    }

    /**
     * The box at {@code size} with the drawing and the pins scaled with it (Ctrl while dragging the box's handle):
     * every point keeps its place in proportion, pins rounded to the grid, edges staying edges. Refused (an exception)
     * when two pins would meet.
     */
    public SymbolDraft scaleBox(GridSize size) {
        requireUnlocked();
        GridSize old = appearance.size();
        double horizontal = (double) size.width() / old.width();
        double vertical = (double) size.height() / old.height();
        List<PinPlacement> pins = appearance.pins().stream().map(pin -> new PinPlacement(pin.source(), new GridPoint(
                (int) Math.round(pin.offset().x() * horizontal), (int) Math.round(pin.offset().y() * vertical))))
                .toList();
        if (pins.stream().map(PinPlacement::offset).distinct().count() != pins.size()) {
            throw new IllegalArgumentException("Box scale would create coinciding pins");
        }
        List<SymbolElement> elements = elements().stream().map(element -> mapElement(element,
                point -> new SymbolPoint((int) Math.round(point.x() * horizontal),
                        (int) Math.round(point.y() * vertical)))).toList();
        Optional<SymbolDrawing> drawing = appearance.drawing().map(current ->
                new SymbolDrawing(size, elements, current.showPinNames()));
        return keepingTheDefaultShape(withAppearance(new ComponentAppearance(size, drawing, pins), selection));
    }

    /**
     * The smallest box {@link #resizeBox} accepts from here: narrower while it fits, then lower; if both together
     * make two pins meet, a unit back at a time.
     */
    public GridSize minimumBoxSize() {
        GridSize size = appearance.size();
        int width = size.width();
        while (width > 1 && fits(new GridSize(width - 1, size.height()))) {
            width--;
        }
        int height = size.height();
        while (height > 1 && fits(new GridSize(width, height - 1))) {
            height--;
        }
        while (!fits(new GridSize(width, height)) && (width < size.width() || height < size.height())) {
            if (height < size.height()) {
                height++;
            } else {
                width++;
            }
        }
        return new GridSize(width, height);
    }

    private boolean fits(GridSize size) {
        List<PinPlacement> pins = centredPins(size);
        return pins.stream().allMatch(pin -> inside(pin.offset(), size))
                && pins.stream().map(PinPlacement::offset).distinct().count() == pins.size()
                && resizedElements(size).stream().flatMap(element -> points(element).stream())
                .allMatch(point -> inside(point, size));
    }

    /** Each pin at {@code size}: on its edge if it was on one, else moved by half the change, rounded towards zero. */
    private List<PinPlacement> centredPins(GridSize size) {
        GridSize old = appearance.size();
        return appearance.pins().stream().map(pin -> new PinPlacement(pin.source(), new GridPoint(
                followingTheEdges(pin.offset().x(), old.width(), size.width()),
                followingTheEdges(pin.offset().y(), old.height(), size.height())))).toList();
    }

    /**
     * One coordinate when its extent changes from {@code oldExtent} to {@code newExtent}: on an edge it stays on that
     * edge, elsewhere it moves by half the change (rounded towards zero for pins in grid units; exact for figures in
     * eighths, where half a unit is four eighths).
     */
    private static int followingTheEdges(int coordinate, int oldExtent, int newExtent) {
        if (coordinate == 0) {
            return 0;
        }
        if (coordinate == oldExtent) {
            return newExtent;
        }
        return coordinate + (newExtent - oldExtent) / 2;
    }

    /** Every element at {@code size} by the rule of {@link #resizeBox}: texts centred, figures by their nodes. */
    private List<SymbolElement> resizedElements(GridSize size) {
        int width = size.width() * SymbolPoint.EIGHTHS;
        int height = size.height() * SymbolPoint.EIGHTHS;
        return elements().stream().map(element -> switch (element) {
            case SymbolPath path -> resizedPath(path, width, height);
            case SymbolText text -> mapElement(text, point -> new SymbolPoint(
                    point.x() + (width - maximumX()) / 2, point.y() + (height - maximumY()) / 2));
        }).toList();
    }

    /**
     * A figure in a box of {@code width} × {@code height} eighths: a node on an edge moves with that edge, elsewhere it
     * stays centred. A control point moves by as much as its node, so the curve leaves the node the same way (a
     * quadratic's single control goes with the node that stores it; a cubic's first handle with the segment's start
     * node and its second with its end node); a control coordinate on an edge follows that edge instead, which keeps it
     * inside the box and a curve drawn to a corner (the AND gate's) still reaching that corner.
     */
    private SymbolPath resizedPath(SymbolPath path, int width, int height) {
        List<SymbolPoint> moved = path.nodes().stream().map(node -> new SymbolPoint(
                followingTheEdges(node.point().x(), maximumX(), width),
                followingTheEdges(node.point().y(), maximumY(), height))).toList();
        List<SymbolNode> nodes = new ArrayList<>();
        for (int index = 0; index < path.nodes().size(); index++) {
            SymbolNode node = path.nodes().get(index);
            int next = (index + 1) % path.nodes().size();
            UnaryOperator<SymbolPoint> withStart = withTheNode(node.point(), moved.get(index), width, height);
            UnaryOperator<SymbolPoint> withEnd = withTheNode(path.nodes().get(next).point(), moved.get(next), width,
                    height);
            nodes.add(new SymbolNode(moved.get(index), node.curve().map(curve -> switch (curve) {
                case SegmentCurve.Quadratic quadratic -> quadratic.map(withStart);
                case SegmentCurve.Cubic cubic -> new SegmentCurve.Cubic(withStart.apply(cubic.first()),
                        withEnd.apply(cubic.second()));
            })));
        }
        return path.reshaped(nodes, path.closed(), path.filled());
    }

    /** A control point moved as its node moved from {@code original} to {@code moved}, or along a box edge. */
    private UnaryOperator<SymbolPoint> withTheNode(SymbolPoint original, SymbolPoint moved, int width, int height) {
        return control -> new SymbolPoint(
                onAnEdge(control.x(), maximumX()) ? followingTheEdges(control.x(), maximumX(), width)
                        : control.x() + moved.x() - original.x(),
                onAnEdge(control.y(), maximumY()) ? followingTheEdges(control.y(), maximumY(), height)
                        : control.y() + moved.y() - original.y());
    }

    private static boolean onAnEdge(int coordinate, int extent) {
        return coordinate == 0 || coordinate == extent;
    }

    private static boolean inside(GridPoint offset, GridSize size) {
        return offset.x() >= 0 && offset.y() >= 0 && offset.x() <= size.width() && offset.y() <= size.height();
    }

    private static boolean inside(SymbolPoint point, GridSize size) {
        return point.x() >= 0 && point.y() >= 0 && point.x() <= size.width() * SymbolPoint.EIGHTHS
                && point.y() <= size.height() * SymbolPoint.EIGHTHS;
    }

    private boolean isOffBoxEdge(GridPoint offset) {
        return offset.x() != 0 && offset.y() != 0 && offset.x() != appearance.size().width()
                && offset.y() != appearance.size().height();
    }

    /** A pin on the box edge whose stub would end in empty space: too far from every figure (spec §5). */
    private boolean isFloating(GridPoint offset) {
        return appearance.drawing().filter(drawing -> !OutlineReach.reaches(drawing, appearance.size(), offset))
                .isPresent();
    }

    public List<DraftProblem> problems() {
        List<DraftProblem> problems = new ArrayList<>();
        Map<GridPoint, List<String>> byPosition = new HashMap<>();
        for (PinPlacement pin : appearance.pins()) {
            String name = pinout.stream().filter(spec -> spec.source().equals(pin.source())).findFirst()
                    .map(spec -> spec.spec().name()).orElse(pin.source().value());
            byPosition.computeIfAbsent(pin.offset(), ignored -> new ArrayList<>()).add(name);
            if (isOffBoxEdge(pin.offset()) || isFloating(pin.offset())) {
                problems.add(new DraftProblem(DraftProblem.Kind.PIN_OFF_EDGE, List.of(name)));
            }
        }
        byPosition.values().stream().filter(names -> names.size() > 1).forEach(names ->
                problems.add(new DraftProblem(DraftProblem.Kind.COINCIDING_PINS, names)));
        if (appearance.drawing().isPresent() && elements().isEmpty()) {
            problems.add(new DraftProblem(DraftProblem.Kind.EMPTY_DRAWING, List.of()));
        }
        // Blocking problems first: the editor shows the first one, and a warning must not hide what stops Accept.
        problems.sort(Comparator.comparing((DraftProblem problem) -> !problem.blocksAccept()));
        return List.copyOf(problems);
    }

    public boolean canAccept() {
        return problems().stream().noneMatch(DraftProblem::blocksAccept);
    }

    public Optional<Box> selectionBounds() {
        List<SymbolPoint> points = selection.stream().sorted().flatMap(index -> points(elements().get(index)).stream())
                .toList();
        if (points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Box(points.stream().mapToInt(SymbolPoint::x).min().orElseThrow(),
                points.stream().mapToInt(SymbolPoint::y).min().orElseThrow(),
                points.stream().mapToInt(SymbolPoint::x).max().orElseThrow(),
                points.stream().mapToInt(SymbolPoint::y).max().orElseThrow()));
    }

    public SymbolDraft scaleSelection(Handle handle, SymbolPoint to, boolean keepProportion, SnapMode snap) {
        Optional<Box> selected = selectionBounds();
        if (selected.isEmpty()) {
            return this;
        }
        Box box = selected.get();
        boolean left = handle == Handle.LEFT || handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT;
        boolean right = handle == Handle.RIGHT || handle == Handle.TOP_RIGHT || handle == Handle.BOTTOM_RIGHT;
        boolean top = handle == Handle.TOP || handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT;
        boolean bottom = handle == Handle.BOTTOM || handle == Handle.BOTTOM_LEFT || handle == Handle.BOTTOM_RIGHT;
        int anchorX = left ? box.right() : box.left();
        int anchorY = top ? box.bottom() : box.top();
        double scaleX = left || right ? Math.max(0, (double) (snap.snap(to.x()) - anchorX)
                / (left ? -Math.max(1, box.width()) : Math.max(1, box.width()))) : 1;
        double scaleY = top || bottom ? Math.max(0, (double) (snap.snap(to.y()) - anchorY)
                / (top ? -Math.max(1, box.height()) : Math.max(1, box.height()))) : 1;
        if (keepProportion) {
            double scale = left || right ? (top || bottom ? Math.min(scaleX, scaleY) : scaleX) : scaleY;
            scale = Math.min(scale, maximumScale(anchorX, box.left(), box.right(), maximumX()));
            scale = Math.min(scale, maximumScale(anchorY, box.top(), box.bottom(), maximumY()));
            scaleX = scale;
            scaleY = scale;
        }
        double horizontal = scaleX;
        double vertical = scaleY;
        return mapSelection(point -> bounded(new SymbolPoint(
                anchorX + (int) Math.round((point.x() - anchorX) * horizontal),
                anchorY + (int) Math.round((point.y() - anchorY) * vertical)), snap));
    }

    private SymbolDraft mapSelection(UnaryOperator<SymbolPoint> mapper) {
        return withElements(IntStream.range(0, elements().size()).mapToObj(index -> selection.contains(index)
                ? mapElement(elements().get(index), mapper) : elements().get(index)).toList(), selection);
    }

    private static SymbolElement mapElement(SymbolElement element, UnaryOperator<SymbolPoint> mapper) {
        return switch (element) {
            case SymbolPath path -> mapNodes(path, node ->
                    new SymbolNode(mapper.apply(node.point()), node.curve().map(curve -> curve.map(mapper))));
            case SymbolText text -> text.movedTo(mapper.apply(text.anchor()));
        };
    }

    /** The path with each node replaced by {@code mapper}'s, the rest of the figure as it was. */
    private static SymbolPath mapNodes(SymbolPath path, UnaryOperator<SymbolNode> mapper) {
        return path.reshaped(path.nodes().stream().map(mapper).toList(), path.closed(), path.filled());
    }

    private static List<SymbolPoint> points(SymbolElement element) {
        if (element instanceof SymbolText text) {
            return List.of(text.anchor());
        }
        SymbolPath path = (SymbolPath) element;
        List<SymbolPoint> points = new ArrayList<>();
        for (SymbolNode node : path.nodes()) {
            points.add(node.point());
            node.curve().ifPresent(curve -> points.addAll(curve.controls()));
        }
        return points;
    }

    private SymbolDraft withElements(List<SymbolElement> elements, Set<Integer> selected) {
        boolean names = appearance.drawing().map(SymbolDrawing::showPinNames).orElse(true);
        return withAppearance(new ComponentAppearance(appearance.size(),
                Optional.of(new SymbolDrawing(appearance.size(), elements, names)), appearance.pins()), selected);
    }

    private SymbolDraft withAppearance(ComponentAppearance changed, Set<Integer> selected) {
        return new SymbolDraft(changed, pinout, definitionName, pinsLocked, selected, smoothChoices);
    }

    private SymbolDraft withSmoothChoices(Map<NodeKey, Boolean> choices) {
        return new SymbolDraft(appearance, pinout, definitionName, pinsLocked, selection, choices);
    }

    private SymbolDraft choose(int pathIndex, int nodeIndex, boolean smooth) {
        Map<NodeKey, Boolean> choices = new HashMap<>(smoothChoices);
        choices.put(new NodeKey(pathIndex, nodeIndex), smooth);
        return withSmoothChoices(choices);
    }

    /** The choices with each figure at its new index ({@code -1}: gone). */
    private Map<NodeKey, Boolean> choicesForElements(IntUnaryOperator newIndex) {
        Map<NodeKey, Boolean> moved = new HashMap<>();
        smoothChoices.forEach((key, smooth) -> {
            int index = newIndex.applyAsInt(key.path());
            if (index >= 0) {
                moved.put(new NodeKey(index, key.node()), smooth);
            }
        });
        return moved;
    }

    /** The choices with the nodes of figure {@code pathIndex} at their new places ({@code -1}: gone). */
    private Map<NodeKey, Boolean> choicesForNodes(int pathIndex, IntUnaryOperator newIndex) {
        Map<NodeKey, Boolean> moved = new HashMap<>();
        smoothChoices.forEach((key, smooth) -> {
            int node = key.path() == pathIndex ? newIndex.applyAsInt(key.node()) : key.node();
            if (node >= 0) {
                moved.put(new NodeKey(key.path(), node), smooth);
            }
        });
        return moved;
    }

    /** Sets handle {@code side} of segment {@code segment}'s cubic (a quadratic or straight one is raised first). */
    private static void setHandle(List<SymbolNode> nodes, int segment, HandleSide side, SymbolPoint handle) {
        SymbolNode start = nodes.get(segment);
        SymbolPoint end = nodes.get((segment + 1) % nodes.size()).point();
        SegmentCurve.Cubic cubic = start.curve().map(curve -> curve.cubic(start.point(), end))
                .orElseGet(() -> straightCubic(start.point(), end));
        nodes.set(segment, side == HandleSide.OUTGOING
                ? SymbolNode.cubic(start.point(), handle, cubic.second())
                : SymbolNode.cubic(start.point(), cubic.first(), handle));
    }

    private static SegmentCurve.Cubic straightCubic(SymbolPoint start, SymbolPoint end) {
        CubicSpan span = CubicSpan.of(start, Optional.empty(), end);
        return new SegmentCurve.Cubic(CubicSpan.rounded(span.first()), CubicSpan.rounded(span.second()));
    }

    private static SymbolNode cubicNode(CubicSpan span) {
        return SymbolNode.cubic(CubicSpan.rounded(span.start()), CubicSpan.rounded(span.first()),
                CubicSpan.rounded(span.second()));
    }

    /**
     * After a body drag moved handle {@code side} of node {@code nodeIndex}: if the node was smooth, its other handle
     * turns onto the same line, keeping its own length.
     */
    private void keepSmooth(int pathIndex, SymbolPath before, List<SymbolNode> nodes, int nodeIndex, HandleSide side) {
        if (!smooth(pathIndex, nodeIndex)) {
            return;
        }
        SymbolPath bent = before.reshaped(nodes, before.closed(), before.filled());
        SymbolPoint point = bent.nodes().get(nodeIndex).point();
        SymbolPoint moved = bent.handle(nodeIndex, side).orElseThrow();
        double length = distance(point, moved);
        if (length == 0) {
            return;
        }
        SymbolPoint other = bent.handle(nodeIndex, side.opposite()).orElseThrow();
        setHandle(nodes, bent.segmentOf(nodeIndex, side.opposite()).orElseThrow(), side.opposite(),
                alongInside(point, (point.x() - moved.x()) / length, (point.y() - moved.y()) / length,
                        distance(point, other)));
    }

    /**
     * The unit vector (outgoing) a corner's handles lie on when it becomes smooth: both turned by the same angle; for
     * a handle of zero length, the other one's line; for two, the line between the neighbour nodes.
     */
    private static CurvePoint smoothDirection(SymbolPath path, int nodeIndex, SymbolPoint point, SymbolPoint incoming,
                                              SymbolPoint outgoing) {
        double backLength = distance(point, incoming);
        double aheadLength = distance(point, outgoing);
        CurvePoint direction;
        if (backLength > 0 && aheadLength > 0) {
            direction = new CurvePoint(
                    (outgoing.x() - point.x()) / aheadLength - (incoming.x() - point.x()) / backLength,
                    (outgoing.y() - point.y()) / aheadLength - (incoming.y() - point.y()) / backLength);
            if (Math.hypot(direction.x(), direction.y()) < 1e-9) {
                // Both handles point the same way: the line across them.
                direction = new CurvePoint(-(outgoing.y() - point.y()), outgoing.x() - point.x());
            }
        } else if (aheadLength > 0) {
            direction = new CurvePoint(outgoing.x() - point.x(), outgoing.y() - point.y());
        } else if (backLength > 0) {
            direction = new CurvePoint(point.x() - incoming.x(), point.y() - incoming.y());
        } else {
            SymbolPoint previous = path.nodes().get(path.segmentOf(nodeIndex, HandleSide.INCOMING).orElseThrow())
                    .point();
            SymbolPoint next = path.segmentEnd(nodeIndex);
            direction = new CurvePoint(next.x() - previous.x(), next.y() - previous.y());
        }
        double length = Math.hypot(direction.x(), direction.y());
        return length == 0 ? new CurvePoint(1, 0) : new CurvePoint(direction.x() / length, direction.y() / length);
    }

    /** The point {@code length} from {@code point} along the unit vector, shortened so it stays in the box. */
    private SymbolPoint alongInside(SymbolPoint point, double directionX, double directionY, double length) {
        double reach = Math.min(length, Math.min(room(point.x(), directionX, maximumX()),
                room(point.y(), directionY, maximumY())));
        return bounded(CubicSpan.rounded(new CurvePoint(point.x() + directionX * reach,
                point.y() + directionY * reach)), SnapMode.FREE);
    }

    /** How many steps of {@code direction} fit from {@code coordinate} before leaving 0..{@code limit}. */
    private static double room(int coordinate, double direction, int limit) {
        if (direction > 0) {
            return (limit - coordinate) / direction;
        }
        return direction < 0 ? coordinate / -direction : Double.POSITIVE_INFINITY;
    }

    private static double distance(SymbolPoint from, SymbolPoint to) {
        return Math.hypot(to.x() - from.x(), to.y() - from.y());
    }

    /**
     * How a body drag at {@code parameter} shares the move between the handles: 0 moves only the first, 1 only the
     * second, smoothly in between (Inkscape's curve drag).
     */
    private static double bendWeight(double parameter) {
        if (parameter <= BEND_END_SHARE) {
            return 0;
        }
        if (parameter <= 0.5) {
            return Math.pow((6 * parameter - 1) / 2, 3) / 2;
        }
        if (parameter <= 1 - BEND_END_SHARE) {
            return (1 - Math.pow((6 * (1 - parameter) - 1) / 2, 3)) / 2 + 0.5;
        }
        return 1;
    }

    private SymbolDraft replaceElement(int index, SymbolElement element) {
        List<SymbolElement> changed = new ArrayList<>(elements());
        changed.set(index, element);
        return withElements(changed, selection);
    }

    private SymbolDraft replacePath(int index, SymbolPath path, List<SymbolNode> nodes) {
        return replaceElement(index, path.reshaped(nodes, path.closed(), path.filled()));
    }

    private SymbolPath path(int index) {
        return (SymbolPath) elements().get(index);
    }

    private int maximumX() {
        return appearance.size().width() * SymbolPoint.EIGHTHS;
    }

    private int maximumY() {
        return appearance.size().height() * SymbolPoint.EIGHTHS;
    }

    private SymbolPoint bounded(SymbolPoint point, SnapMode snap) {
        return new SymbolPoint(Math.clamp(snap.snap(point.x()), 0, maximumX()),
                Math.clamp(snap.snap(point.y()), 0, maximumY()));
    }

    private void requireUnlocked() {
        if (pinsLocked) {
            throw new IllegalStateException("Nested component pins and size are locked");
        }
    }

    private static void requireSegment(SymbolPath path, int segment) {
        if (segment < 0 || segment >= path.segmentCount()) {
            throw new IllegalArgumentException("No segment at " + segment);
        }
    }


    private static double maximumScale(int anchor, int minimum, int maximum, int limit) {
        double before = minimum < anchor ? (double) anchor / (anchor - minimum) : Double.POSITIVE_INFINITY;
        double after = maximum > anchor ? (double) (limit - anchor) / (maximum - anchor) : Double.POSITIVE_INFINITY;
        return Math.min(before, after);
    }

    private static SymbolPoint interpolate(SymbolPoint first, SymbolPoint second, double fraction) {
        return new SymbolPoint((int) Math.round(first.x() + (second.x() - first.x()) * fraction),
                (int) Math.round(first.y() + (second.y() - first.y()) * fraction));
    }

}
