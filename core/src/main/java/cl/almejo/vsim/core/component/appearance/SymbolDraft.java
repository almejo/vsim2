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

    private final ComponentAppearance appearance;
    private final List<DefinitionPin> pinout;
    private final String definitionName;
    private final boolean pinsLocked;
    private final Set<Integer> selection;

    private SymbolDraft(ComponentAppearance appearance, List<DefinitionPin> pinout, String definitionName,
                        boolean pinsLocked, Set<Integer> selection) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.pinout = List.copyOf(pinout);
        this.definitionName = Objects.requireNonNull(definitionName, "definitionName");
        this.pinsLocked = pinsLocked;
        this.selection = Set.copyOf(selection);
    }

    public static SymbolDraft of(ComponentAppearance appearance, List<DefinitionPin> pinout, boolean pinsLocked) {
        return new SymbolDraft(appearance, pinout, "", pinsLocked, Set.of());
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
        return new SymbolDraft(appearance, pinout, definitionName, pinsLocked, indices);
    }

    public SymbolDraft selectAll() {
        return select(new LinkedHashSet<>(IntStream.range(0, elements().size()).boxed().toList()));
    }

    /** The component's name, which templates write in the free area of the box. */
    public SymbolDraft withDefinitionName(String name) {
        return new SymbolDraft(appearance, pinout, name, pinsLocked, selection);
    }

    public SymbolDraft withPinLock(boolean locked) {
        return new SymbolDraft(appearance, pinout, definitionName, locked, selection);
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
        List<SymbolElement> kept = IntStream.range(0, elements().size()).filter(index -> !selection.contains(index))
                .mapToObj(elements()::get).toList();
        return withElements(kept, Set.of());
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
        List<SymbolElement> chosen = new ArrayList<>();
        List<SymbolElement> rest = new ArrayList<>();
        for (int index = 0; index < elements().size(); index++) {
            (selection.contains(index) ? chosen : rest).add(elements().get(index));
        }
        List<SymbolElement> changed = new ArrayList<>(front ? rest : chosen);
        changed.addAll(front ? chosen : rest);
        int first = front ? rest.size() : 0;
        return withElements(changed, new LinkedHashSet<>(IntStream.range(first, first + chosen.size()).boxed().toList()));
    }

    public SymbolDraft moveNode(int pathIndex, int nodeIndex, SymbolPoint point, SnapMode snap) {
        SymbolPath path = path(pathIndex);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(nodeIndex, new SymbolNode(bounded(point, snap), nodes.get(nodeIndex).control()));
        return replacePath(pathIndex, path, nodes);
    }


    /**
     * Curves segment {@code segment} so that its middle passes through {@code through} (the handle «Curvar tramo»
     * drags): the quadratic's control is 2·through − (start + end)/2, kept inside the box, so the stored format is
     * the same {@code Q} as always.
     */
    public SymbolDraft curveThrough(int pathIndex, int segment, SymbolPoint through, SnapMode snap) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        SymbolNode start = nodes.get(segment);
        SymbolPoint end = nodes.get((segment + 1) % nodes.size()).point();
        SymbolPoint handle = new SymbolPoint(snap.snap(through.x()), snap.snap(through.y()));
        SymbolPoint control = new SymbolPoint(
                (int) Math.round(2.0 * handle.x() - (start.point().x() + end.x()) / 2.0),
                (int) Math.round(2.0 * handle.y() - (start.point().y() + end.y()) / 2.0));
        nodes.set(segment, new SymbolNode(start.point(), Optional.of(bounded(control, SnapMode.FREE))));
        return replacePath(pathIndex, path, nodes);
    }

    /** Segment {@code segment} back to a straight line. */
    public SymbolDraft straighten(int pathIndex, int segment) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(segment, new SymbolNode(nodes.get(segment).point(), Optional.empty()));
        return replacePath(pathIndex, path, nodes);
    }

    public SymbolDraft insertNode(int pathIndex, int segment, SymbolPoint at) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        SymbolNode start = path.nodes().get(segment);
        SymbolPoint end = path.nodes().get((segment + 1) % path.nodes().size()).point();
        List<SymbolNode> changed = new ArrayList<>(path.nodes());
        if (start.control().isEmpty()) {
            changed.add(segment + 1, new SymbolNode(bounded(at, SnapMode.FREE), Optional.empty()));
        } else {
            SymbolPoint control = start.control().get();
            double parameter = nearestParameter(start.point(), control, end, at);
            SymbolPoint left = interpolate(start.point(), control, parameter);
            SymbolPoint right = interpolate(control, end, parameter);
            changed.set(segment, new SymbolNode(start.point(), Optional.of(left)));
            changed.add(segment + 1, new SymbolNode(interpolate(left, right, parameter), Optional.of(right)));
        }
        return replacePath(pathIndex, path, changed);
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
        nodes.set(previous, new SymbolNode(nodes.get(previous).point(), Optional.empty()));
        if (!closed) {
            nodes.set(nodes.size() - 1, new SymbolNode(nodes.getLast().point(), Optional.empty()));
        }
        return replaceElement(pathIndex, path.reshaped(nodes, closed, closed && path.filled()));
    }

    public SymbolDraft closePath(int index) {
        SymbolPath path = path(index);
        return replaceElement(index, path.reshaped(path.nodes(), true, path.filled()));
    }

    /** The closed figure at {@code index} opened where it closed: its closing segment and its fill go. */
    public SymbolDraft openPath(int index) {
        SymbolPath path = path(index);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(nodes.size() - 1, new SymbolNode(nodes.getLast().point(), Optional.empty()));
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
                : SymbolTemplates.drawing(template, appearance.size(), definitionName, pinLabels()), Set.of());
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
            case SymbolPath path -> mapNodes(path, node -> resizedNode(node, width, height));
            case SymbolText text -> mapElement(text, point -> new SymbolPoint(
                    point.x() + (width - maximumX()) / 2, point.y() + (height - maximumY()) / 2));
        }).toList();
    }

    /**
     * A node in a box of {@code width} × {@code height} eighths: on an edge it moves with that edge, elsewhere it stays
     * centred. Its control points move by as much as the node, so the curve leaves the node the same way; a control
     * coordinate on an edge follows that edge instead, which keeps it inside the box and a curve drawn to a corner
     * (the AND gate's) still reaching that corner.
     */
    private SymbolNode resizedNode(SymbolNode node, int width, int height) {
        SymbolPoint point = node.point();
        SymbolPoint moved = new SymbolPoint(followingTheEdges(point.x(), maximumX(), width),
                followingTheEdges(point.y(), maximumY(), height));
        UnaryOperator<SymbolPoint> withTheNode = control -> new SymbolPoint(
                onAnEdge(control.x(), maximumX()) ? followingTheEdges(control.x(), maximumX(), width)
                        : control.x() + moved.x() - point.x(),
                onAnEdge(control.y(), maximumY()) ? followingTheEdges(control.y(), maximumY(), height)
                        : control.y() + moved.y() - point.y());
        return new SymbolNode(moved, node.control().map(withTheNode));
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
                    new SymbolNode(mapper.apply(node.point()), node.control().map(mapper)));
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
            node.control().ifPresent(points::add);
        }
        return points;
    }

    private SymbolDraft withElements(List<SymbolElement> elements, Set<Integer> selected) {
        boolean names = appearance.drawing().map(SymbolDrawing::showPinNames).orElse(true);
        return withAppearance(new ComponentAppearance(appearance.size(),
                Optional.of(new SymbolDrawing(appearance.size(), elements, names)), appearance.pins()), selected);
    }

    private SymbolDraft withAppearance(ComponentAppearance changed, Set<Integer> selected) {
        return new SymbolDraft(changed, pinout, definitionName, pinsLocked, selected);
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
        if (segment < 0 || segment >= path.nodes().size() - (path.closed() ? 0 : 1)) {
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

    private static double nearestParameter(SymbolPoint start, SymbolPoint control, SymbolPoint end, SymbolPoint target) {
        double nearest = 0.5;
        double distance = Double.POSITIVE_INFINITY;
        for (int sample = 1; sample < 1024; sample++) {
            double parameter = sample / 1024.0;
            double inverse = 1 - parameter;
            double x = inverse * inverse * start.x() + 2 * inverse * parameter * control.x() + parameter * parameter * end.x();
            double y = inverse * inverse * start.y() + 2 * inverse * parameter * control.y() + parameter * parameter * end.y();
            double candidate = Math.pow(x - target.x(), 2) + Math.pow(y - target.y(), 2);
            if (candidate < distance) {
                distance = candidate;
                nearest = parameter;
            }
        }
        return nearest;
    }
}
