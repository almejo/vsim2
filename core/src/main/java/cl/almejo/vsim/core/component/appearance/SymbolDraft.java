package cl.almejo.vsim.core.component.appearance;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.library.DefinitionPin;

import java.util.ArrayList;
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
        public int width() { return right - left; }
        public int height() { return bottom - top; }
    }

    private final ComponentAppearance appearance;
    private final List<DefinitionPin> pinout;
    private final boolean pinsLocked;
    private final Set<Integer> selection;

    private SymbolDraft(ComponentAppearance appearance, List<DefinitionPin> pinout, boolean pinsLocked,
                        Set<Integer> selection) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.pinout = List.copyOf(pinout);
        this.pinsLocked = pinsLocked;
        this.selection = Set.copyOf(selection);
    }

    public static SymbolDraft of(ComponentAppearance appearance, List<DefinitionPin> pinout, boolean pinsLocked) {
        return new SymbolDraft(appearance, pinout, pinsLocked, Set.of());
    }

    public ComponentAppearance appearance() { return appearance; }
    public Set<Integer> selection() { return selection; }
    public boolean pinsLocked() { return pinsLocked; }
    public List<SymbolElement> elements() {
        return appearance.drawing().map(SymbolDrawing::elements).orElse(List.of());
    }

    public SymbolDraft select(Set<Integer> indices) {
        if (indices.stream().anyMatch(index -> index < 0 || index >= elements().size())) {
            throw new IllegalArgumentException("Selection outside drawing");
        }
        return new SymbolDraft(appearance, pinout, pinsLocked, indices);
    }

    public SymbolDraft selectAll() {
        return select(new LinkedHashSet<>(IntStream.range(0, elements().size()).boxed().toList()));
    }

    public SymbolDraft withPinLock(boolean locked) {
        return new SymbolDraft(appearance, pinout, locked, selection);
    }

    public SymbolDraft addFigure(SymbolPath path) { return append(path); }
    public SymbolDraft addText(SymbolText text) { return append(text); }

    private SymbolDraft append(SymbolElement element) {
        List<SymbolElement> changed = new ArrayList<>(elements());
        changed.add(element);
        return withElements(changed, Set.of(changed.size() - 1));
    }

    public SymbolDraft editText(int index, SymbolText text) { return replaceElement(index, text); }

    public SymbolDraft deleteSelection() {
        if (selection.isEmpty()) { return this; }
        List<SymbolElement> kept = IntStream.range(0, elements().size()).filter(index -> !selection.contains(index))
                .mapToObj(elements()::get).toList();
        return withElements(kept, Set.of());
    }

    public SymbolDraft moveSelection(int deltaX, int deltaY, SnapMode snap) {
        Optional<Box> box = selectionBounds();
        if (box.isEmpty()) { return this; }
        Box bounds = box.get();
        int horizontal = Math.clamp(deltaX, -bounds.left(), maximumX() - bounds.right());
        int vertical = Math.clamp(deltaY, -bounds.top(), maximumY() - bounds.bottom());
        return mapSelection(point -> bounded(new SymbolPoint(point.x() + horizontal, point.y() + vertical), snap));
    }

    public SymbolDraft duplicateSelection() {
        if (selection.isEmpty()) { return this; }
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

    public SymbolDraft bringToFront() { return reorder(true); }
    public SymbolDraft sendToBack() { return reorder(false); }

    private SymbolDraft reorder(boolean front) {
        if (selection.isEmpty()) { return this; }
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

    public SymbolDraft moveControl(int pathIndex, int nodeIndex, SymbolPoint point, SnapMode snap) {
        SymbolPath path = path(pathIndex);
        if (!path.closed() && nodeIndex == path.nodes().size() - 1) {
            throw new IllegalArgumentException("An open path has no closing control");
        }
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.set(nodeIndex, new SymbolNode(nodes.get(nodeIndex).point(), Optional.of(bounded(point, snap))));
        return replacePath(pathIndex, path, nodes);
    }

    public SymbolDraft toggleCurve(int pathIndex, int segment) {
        SymbolPath path = path(pathIndex);
        requireSegment(path, segment);
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        SymbolNode start = nodes.get(segment);
        SymbolPoint end = nodes.get((segment + 1) % nodes.size()).point();
        nodes.set(segment, new SymbolNode(start.point(), start.control().isPresent() ? Optional.empty()
                : Optional.of(midpoint(start.point(), end))));
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
        if (path.nodes().size() == 2) { return select(Set.of(pathIndex)).deleteSelection(); }
        List<SymbolNode> nodes = new ArrayList<>(path.nodes());
        nodes.remove(nodeIndex);
        boolean closed = path.closed() && nodes.size() >= 3;
        int previous = Math.floorMod(nodeIndex - 1, nodes.size());
        nodes.set(previous, new SymbolNode(nodes.get(previous).point(), Optional.empty()));
        if (!closed) {
            nodes.set(nodes.size() - 1, new SymbolNode(nodes.getLast().point(), Optional.empty()));
        }
        return replaceElement(pathIndex, new SymbolPath(nodes, closed, closed && path.filled()));
    }

    public SymbolDraft closePath(int index) {
        SymbolPath path = path(index);
        return replaceElement(index, new SymbolPath(path.nodes(), true, path.filled()));
    }

    public SymbolDraft setFilled(int index, boolean filled) {
        SymbolPath path = path(index);
        return replaceElement(index, new SymbolPath(path.nodes(), path.closed(), path.closed() && filled));
    }

    public SymbolDraft showPinNames(boolean show) {
        Optional<SymbolDrawing> drawing = appearance.drawing().map(current ->
                new SymbolDrawing(appearance.size(), current.elements(), show));
        if (drawing.isEmpty() && !show) {
            drawing = SymbolTemplates.drawing(SymbolTemplates.Template.RECTANGLE, appearance.size())
                    .map(current -> new SymbolDrawing(current.size(), current.elements(), false));
        }
        return withAppearance(new ComponentAppearance(appearance.size(), drawing, appearance.pins()), selection);
    }

    public SymbolDraft applyTemplate(SymbolTemplates.Template template) {
        return withAppearance(new ComponentAppearance(appearance.size(),
                SymbolTemplates.drawing(template, appearance.size()), appearance.pins()), Set.of());
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

    public SymbolDraft resizeBox(GridSize size) {
        requireUnlocked();
        List<PinPlacement> pins = pinsAtSize(size);
        if (pins.stream().anyMatch(pin -> pin.offset().x() > size.width() || pin.offset().y() > size.height())) {
            throw new IllegalArgumentException("Box would cut a pin");
        }
        if (pins.stream().map(PinPlacement::offset).distinct().count() != pins.size()) {
            throw new IllegalArgumentException("Box resize would create coinciding pins");
        }
        Optional<SymbolDrawing> drawing = appearance.drawing().map(current ->
                new SymbolDrawing(size, current.elements(), current.showPinNames()));
        return withAppearance(new ComponentAppearance(size, drawing, pins), selection);
    }

    public GridSize minimumBoxSize() {
        List<SymbolPoint> points = elements().stream().flatMap(element -> points(element).stream()).toList();
        int width = points.stream().mapToInt(SymbolPoint::x).max().orElse(0);
        int height = points.stream().mapToInt(SymbolPoint::y).max().orElse(0);
        for (PinPlacement pin : appearance.pins()) {
            if (pin.offset().x() < appearance.size().width()) { width = Math.max(width, pin.offset().x() * 8); }
            if (pin.offset().y() < appearance.size().height()) { height = Math.max(height, pin.offset().y() * 8); }
        }
        GridSize minimum = new GridSize(Math.max(1, (width + 7) / 8), Math.max(1, (height + 7) / 8));
        for (int attempt = 0; attempt <= appearance.pins().size() * 2; attempt++) {
            List<PinPlacement> resized = pinsAtSize(minimum);
            GridSize enlarged = minimum;
            for (int first = 0; first < resized.size() && enlarged.equals(minimum); first++) {
                for (int second = first + 1; second < resized.size(); second++) {
                    if (!resized.get(first).offset().equals(resized.get(second).offset())) { continue; }
                    GridPoint firstOriginal = appearance.pins().get(first).offset();
                    GridPoint secondOriginal = appearance.pins().get(second).offset();
                    if (firstOriginal.x() != secondOriginal.x()) {
                        enlarged = new GridSize(minimum.width() + 1, minimum.height()); break;
                    }
                    if (firstOriginal.y() != secondOriginal.y()) {
                        enlarged = new GridSize(minimum.width(), minimum.height() + 1); break;
                    }
                }
            }
            if (enlarged.equals(minimum)) { return minimum; }
            minimum = enlarged;
        }
        return appearance.size();
    }

    private List<PinPlacement> pinsAtSize(GridSize size) {
        return appearance.pins().stream().map(pin -> new PinPlacement(pin.source(),
                new GridPoint(pin.offset().x() == appearance.size().width() ? size.width() : pin.offset().x(),
                        pin.offset().y() == appearance.size().height() ? size.height() : pin.offset().y()))).toList();
    }

    public List<DraftProblem> problems() {
        List<DraftProblem> problems = new ArrayList<>();
        Map<GridPoint, List<String>> byPosition = new HashMap<>();
        for (PinPlacement pin : appearance.pins()) {
            String name = pinout.stream().filter(spec -> spec.source().equals(pin.source())).findFirst()
                    .map(spec -> spec.spec().name()).orElse(pin.source().value());
            byPosition.computeIfAbsent(pin.offset(), ignored -> new ArrayList<>()).add(name);
            if (pin.offset().x() != 0 && pin.offset().y() != 0 && pin.offset().x() != appearance.size().width()
                    && pin.offset().y() != appearance.size().height()) {
                problems.add(new DraftProblem(DraftProblem.Kind.PIN_OFF_EDGE, List.of(name)));
            }
        }
        byPosition.values().stream().filter(names -> names.size() > 1).forEach(names ->
                problems.add(new DraftProblem(DraftProblem.Kind.COINCIDING_PINS, names)));
        if (appearance.drawing().isPresent() && elements().isEmpty()) {
            problems.add(new DraftProblem(DraftProblem.Kind.EMPTY_DRAWING, List.of()));
        }
        return List.copyOf(problems);
    }

    public boolean canAccept() { return problems().stream().noneMatch(DraftProblem::blocksAccept); }

    public Optional<Box> selectionBounds() {
        List<SymbolPoint> points = selection.stream().sorted().flatMap(index -> points(elements().get(index)).stream())
                .toList();
        if (points.isEmpty()) { return Optional.empty(); }
        return Optional.of(new Box(points.stream().mapToInt(SymbolPoint::x).min().orElseThrow(),
                points.stream().mapToInt(SymbolPoint::y).min().orElseThrow(),
                points.stream().mapToInt(SymbolPoint::x).max().orElseThrow(),
                points.stream().mapToInt(SymbolPoint::y).max().orElseThrow()));
    }

    public SymbolDraft scaleSelection(Handle handle, SymbolPoint to, boolean keepProportion, SnapMode snap) {
        Optional<Box> selected = selectionBounds();
        if (selected.isEmpty()) { return this; }
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
            case SymbolPath path -> new SymbolPath(path.nodes().stream().map(node ->
                    new SymbolNode(mapper.apply(node.point()), node.control().map(mapper))).toList(),
                    path.closed(), path.filled());
            case SymbolText text -> new SymbolText(mapper.apply(text.anchor()), text.text(), text.size(), text.alignment());
        };
    }

    private static List<SymbolPoint> points(SymbolElement element) {
        if (element instanceof SymbolText text) { return List.of(text.anchor()); }
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
        return new SymbolDraft(changed, pinout, pinsLocked, selected);
    }

    private SymbolDraft replaceElement(int index, SymbolElement element) {
        List<SymbolElement> changed = new ArrayList<>(elements());
        changed.set(index, element);
        return withElements(changed, selection);
    }

    private SymbolDraft replacePath(int index, SymbolPath path, List<SymbolNode> nodes) {
        return replaceElement(index, new SymbolPath(nodes, path.closed(), path.filled()));
    }

    private SymbolPath path(int index) { return (SymbolPath) elements().get(index); }
    private int maximumX() { return appearance.size().width() * SymbolPoint.EIGHTHS; }
    private int maximumY() { return appearance.size().height() * SymbolPoint.EIGHTHS; }
    private SymbolPoint bounded(SymbolPoint point, SnapMode snap) {
        return new SymbolPoint(Math.clamp(snap.snap(point.x()), 0, maximumX()),
                Math.clamp(snap.snap(point.y()), 0, maximumY()));
    }

    private void requireUnlocked() {
        if (pinsLocked) { throw new IllegalStateException("Nested component pins and size are locked"); }
    }

    private static void requireSegment(SymbolPath path, int segment) {
        if (segment < 0 || segment >= path.nodes().size() - (path.closed() ? 0 : 1)) {
            throw new IllegalArgumentException("No segment at " + segment);
        }
    }

    private static SymbolPoint midpoint(SymbolPoint first, SymbolPoint second) {
        return interpolate(first, second, 0.5);
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
            if (candidate < distance) { distance = candidate; nearest = parameter; }
        }
        return nearest;
    }
}
