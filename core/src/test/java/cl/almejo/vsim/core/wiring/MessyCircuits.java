package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Makes an example circuit messy the way people leave their wiring (spec M6.2 §3), seeded so a failure can be
 * reproduced: loose ends, wires cut into pieces, overlapping duplicates, clear and blocked U detours, floating wires
 * and a probed floating wire. Everything added stays clear of what it must not touch (no new crossing turns into a
 * connection, nothing enters a component body), so the messy circuit means what the example meant plus floating
 * wires; tidying it is then expected to remove the mess, except what a probe watches and the blocked detour.
 * Sometimes, last, two adversarial changes join two wires that only crossed: one is cut where the other crosses it
 * (merging the pieces back would split them again), or a short wire laid over one of them starts at the crossing
 * (it is the only thing joining them, so it must not be merged away).
 */
final class MessyCircuits {

    /** The four directions on the grid, as unit steps. */
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int ATTEMPTS = 40;
    private static final int MARGIN = 6;

    /**
     * What {@link #mess} did. {@code clearDetours} is how many U detours tidying can flatten; {@code joiningWires}
     * are the ids of the overlapping wires that alone join two crossing wires (tidying must keep them).
     */
    record Mess(List<String> steps, int stubs, int cuts, int duplicates, int clearDetours, int blockedDetours,
                int floatingWires, int probedWires, int joiningCuts, List<ElementId> joiningWires) {

        @Override
        public String toString() {
            return String.join("; ", steps);
        }
    }

    private final CircuitDocument document;
    private final Random random;
    private final Set<GridPoint> pinPoints = new HashSet<>();
    private final List<Bounds> bodies = new ArrayList<>();
    private final Set<ElementId> originalWires = new HashSet<>();
    private final List<String> steps = new ArrayList<>();
    private final Set<GridPoint> reserved = new HashSet<>();
    private final List<ElementId> joiningWires = new ArrayList<>();
    private int stubs;
    private int cuts;
    private int duplicates;
    private int clearDetours;
    private int floatingWires;
    private int probedWires;
    private int blockedDetours;
    private int joiningCuts;

    private MessyCircuits(CircuitDocument document, ComponentTypeRegistry registry, Random random) {
        this.document = document;
        this.random = random;
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            bodies.add(type.bounds(component));
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                pinPoints.add(type.pinLocation(component, pinIndex));
            }
        }
        document.wires().forEach(wire -> originalWires.add(wire.id()));
    }

    /**
     * Adds mess to {@code document} (changed in place, probes included). The detours come first, on wires of the
     * example, so that later mess can only delay their flattening, never prevent it.
     */
    static Mess mess(CircuitDocument document, ComponentTypeRegistry registry, Random random) {
        MessyCircuits messer = new MessyCircuits(document, registry, random);
        messer.run();
        return new Mess(List.copyOf(messer.steps), messer.stubs, messer.cuts, messer.duplicates, messer.clearDetours,
                messer.blockedDetours, messer.floatingWires, messer.probedWires, messer.joiningCuts,
                List.copyOf(messer.joiningWires));
    }

    private void run() {
        int clearCount = random.nextInt(3);
        for (int count = 0; count < clearCount; count++) {
            attempt(() -> detour(false));
        }
        if (random.nextBoolean()) {
            attempt(() -> detour(true));
        }
        if (random.nextBoolean()) {
            attempt(this::probedFloatingWire);
        }
        int otherCount = 3 + random.nextInt(6);
        for (int count = 0; count < otherCount; count++) {
            switch (random.nextInt(5)) {
                case 0, 1 -> attempt(this::stub);
                case 2 -> attempt(this::cut);
                case 3 -> attempt(this::duplicate);
                default -> attempt(this::floatingWire);
            }
        }
        if (random.nextInt(3) == 0) {
            attempt(this::joiningCut);
        }
        if (random.nextInt(3) == 0) {
            attempt(this::joiningOverlap);
        }
    }

    private interface Try {
        boolean once();
    }

    private void attempt(Try action) {
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (action.once()) {
                return;
            }
        }
    }

    // --- the kinds of mess -------------------------------------------------------------------------------------

    /** A short wire from a point of a wire or from a pin out into empty space. */
    private boolean stub() {
        GridPoint anchor;
        List<Wire> wires = document.wires();
        if (!wires.isEmpty() && (pinPoints.isEmpty() || random.nextBoolean())) {
            List<GridPoint> points = pointsOf(wires.get(random.nextInt(wires.size())));
            anchor = points.get(random.nextInt(points.size()));
        } else if (!pinPoints.isEmpty()) {
            List<GridPoint> pins = new ArrayList<>(pinPoints);
            pins.sort(null);
            anchor = pins.get(random.nextInt(pins.size()));
        } else {
            return false;
        }
        if (!isSafeToJoin(anchor)) {
            return false;
        }
        int[] direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        int length = 1 + random.nextInt(4);
        Wire stub = new Wire(document.newWireId(), anchor,
                anchor.translate(direction[0] * length, direction[1] * length));
        if (!isClear(stub, Set.of(anchor))) {
            return false;
        }
        document.add(stub);
        stubs++;
        steps.add("stub " + describe(stub));
        return true;
    }

    /** A wire replaced by two to four collinear pieces, cut where no other wire crosses. */
    private boolean cut() {
        Wire wire = randomWire(2);
        if (wire == null) {
            return false;
        }
        List<GridPoint> points = pointsOf(wire);
        Set<GridPoint> cutPoints = new HashSet<>();
        int cutCount = 1 + random.nextInt(3);
        for (int count = 0; count < cutCount; count++) {
            GridPoint point = points.get(1 + random.nextInt(points.size() - 2));
            if (isCrossedBySomeoneElse(point, wire)) {
                return false;
            }
            cutPoints.add(point);
        }
        List<GridPoint> ends = new ArrayList<>();
        ends.add(wire.start());
        points.stream().filter(cutPoints::contains).forEach(ends::add);
        ends.add(wire.end());
        document.remove(wire.id());
        for (int index = 0; index + 1 < ends.size(); index++) {
            ElementId id = index == 0 ? wire.id() : document.newWireId();
            document.add(new Wire(id, ends.get(index), ends.get(index + 1)));
        }
        cuts++;
        steps.add("cut " + describe(wire) + " at " + cutPoints);
        return true;
    }

    /** A collinear wire over part of another one, sometimes sticking out past one of its ends. */
    private boolean duplicate() {
        Wire wire = randomWire(1);
        if (wire == null) {
            return false;
        }
        List<GridPoint> points = pointsOf(wire);
        int first = random.nextInt(points.size() - 1);
        int last = first + 1 + random.nextInt(points.size() - 1 - first);
        GridPoint start = points.get(first);
        GridPoint end = points.get(last);
        Set<GridPoint> except = new HashSet<>(points);
        if (random.nextBoolean()) {
            int[] direction = wire.isHorizontal() ? new int[]{1, 0} : new int[]{0, 1};
            int extension = 1 + random.nextInt(3);
            if (random.nextBoolean()) {
                start = wire.start().translate(-direction[0] * extension, -direction[1] * extension);
            } else {
                end = wire.end().translate(direction[0] * extension, direction[1] * extension);
            }
        }
        Wire duplicate = new Wire(document.newWireId(), start, end);
        if (isCrossedBySomeoneElse(duplicate.start(), wire) || isCrossedBySomeoneElse(duplicate.end(), wire)
                || !isClear(duplicate, except)) {
            return false;
        }
        document.add(duplicate);
        duplicates++;
        steps.add("duplicate " + describe(duplicate) + " over " + describe(wire));
        return true;
    }

    /**
     * A stretch of a wire of the example replaced by a U (two equal legs and a middle). Blocked: a floating wire,
     * watched by a probe, crosses the stretch the middle would move back to.
     */
    private boolean detour(boolean blocked) {
        List<Wire> candidates = document.wires().stream()
                .filter(wire -> originalWires.contains(wire.id()) && pointsOf(wire).size() >= (blocked ? 4 : 3))
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        Wire base = candidates.get(random.nextInt(candidates.size()));
        List<GridPoint> points = pointsOf(base);
        int minimumGap = blocked ? 2 : 1;
        int first = random.nextInt(points.size() - minimumGap);
        int last = first + minimumGap + random.nextInt(points.size() - minimumGap - first);
        GridPoint left = points.get(first);
        GridPoint right = points.get(last);
        for (GridPoint point : points.subList(first + 1, last)) {
            if (pinPoints.contains(point) || isInsideABody(point) || wiresThrough(point).size() != 1) {
                return false;
            }
        }
        if (isCrossedBySomeoneElse(left, base) || isCrossedBySomeoneElse(right, base)
                || continuesALeg(left, base) || continuesALeg(right, base)) {
            return false;
        }
        int sign = random.nextBoolean() ? 1 : -1;
        int normalX = base.isHorizontal() ? 0 : sign;
        int normalY = base.isHorizontal() ? sign : 0;
        int depth = (blocked ? 2 : 1) + random.nextInt(3);
        GridPoint leftCorner = left.translate(normalX * depth, normalY * depth);
        GridPoint rightCorner = right.translate(normalX * depth, normalY * depth);
        List<Wire> pieces = new ArrayList<>();
        if (!left.equals(base.start())) {
            pieces.add(new Wire(base.id(), base.start(), left));
        }
        Wire leftLeg = new Wire(new ElementId("pending-left"), left, leftCorner);
        Wire middle = new Wire(new ElementId("pending-middle"), leftCorner, rightCorner);
        Wire rightLeg = new Wire(new ElementId("pending-right"), rightCorner, right);
        if (!isClear(leftLeg, Set.of(left)) || !isClear(middle, Set.of()) || !isClear(rightLeg, Set.of(right))) {
            return false;
        }
        Wire blocker = null;
        if (blocked) {
            GridPoint crossing = points.get(first + 1 + random.nextInt(last - first - 1));
            int below = 1 + random.nextInt(2);
            int above = 1 + random.nextInt(depth - 1);
            blocker = new Wire(new ElementId("pending-blocker"),
                    crossing.translate(-normalX * below, -normalY * below),
                    crossing.translate(normalX * above, normalY * above));
            Set<GridPoint> blockerPoints = new HashSet<>(pointsOf(blocker));
            boolean touchesTheU = pointsOf(leftLeg).stream().anyMatch(blockerPoints::contains)
                    || pointsOf(middle).stream().anyMatch(blockerPoints::contains)
                    || pointsOf(rightLeg).stream().anyMatch(blockerPoints::contains);
            if (touchesTheU || !isClear(blocker, Set.of(crossing))) {
                return false;
            }
        }
        document.remove(base.id());
        for (Wire piece : pieces) {
            document.add(piece);
        }
        leftLeg = new Wire(pieces.isEmpty() ? base.id() : document.newWireId(), left, leftCorner);
        document.add(leftLeg);
        middle = new Wire(document.newWireId(), leftCorner, rightCorner);
        document.add(middle);
        rightLeg = new Wire(document.newWireId(), rightCorner, right);
        document.add(rightLeg);
        if (!right.equals(base.end())) {
            document.add(new Wire(document.newWireId(), right, base.end()));
        }
        if (blocked) {
            Wire placed = new Wire(document.newWireId(), blocker.start(), blocker.end());
            document.add(placed);
            watch(placed, "blocker");
            blockedDetours++;
            steps.add("blocked U " + describe(leftLeg) + " " + describe(middle) + " " + describe(rightLeg)
                    + " by probed " + describe(placed));
        } else {
            reserved.addAll(points.subList(first + 1, last));
            clearDetours++;
            steps.add("U " + describe(leftLeg) + " " + describe(middle) + " " + describe(rightLeg));
        }
        return true;
    }

    /** A wire touching nothing, sometimes bent into an L. */
    private boolean floatingWire() {
        List<Wire> wires = floatingShape();
        if (wires.isEmpty()) {
            return false;
        }
        wires.forEach(document::add);
        floatingWires++;
        steps.add("floating " + wires.stream().map(MessyCircuits::describe).toList());
        return true;
    }

    /** A floating wire cut in two, with a probe on its second piece (merging them must retarget the probe). */
    private boolean probedFloatingWire() {
        GridPoint start = randomPointNearTheCircuit();
        int[] direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        int length = 2 + random.nextInt(5);
        int split = 1 + random.nextInt(length - 1);
        GridPoint middle = start.translate(direction[0] * split, direction[1] * split);
        GridPoint end = start.translate(direction[0] * length, direction[1] * length);
        Wire whole = new Wire(new ElementId("pending-whole"), start, end);
        if (!isClear(whole, Set.of())) {
            return false;
        }
        Wire first = new Wire(document.newWireId(), start, middle);
        document.add(first);
        Wire second = new Wire(document.newWireId(), middle, end);
        document.add(second);
        watch(second, "probed");
        probedWires++;
        steps.add("probed floating " + describe(first) + " + " + describe(second));
        return true;
    }

    /** A wire cut where another one crosses it, so the cut joins both. */
    private boolean joiningCut() {
        List<GridPoint> crossings = crossings();
        if (crossings.isEmpty()) {
            return false;
        }
        GridPoint crossing = crossings.get(random.nextInt(crossings.size()));
        Wire cutWire = wiresThrough(crossing).get(random.nextInt(2));
        document.remove(cutWire.id());
        document.add(new Wire(cutWire.id(), cutWire.start(), crossing));
        document.add(new Wire(document.newWireId(), crossing, cutWire.end()));
        joiningCuts++;
        steps.add("joining cut " + describe(cutWire) + " at " + crossing);
        return true;
    }

    /**
     * A short wire laid over one of two crossing wires, from the crossing to a point of that wire: it is what joins
     * them, so it must stay (the merge would make the crossing an interior point again).
     */
    private boolean joiningOverlap() {
        List<GridPoint> crossings = crossings();
        if (crossings.isEmpty()) {
            return false;
        }
        GridPoint anchor = crossings.get(random.nextInt(crossings.size()));
        Wire along = wiresThrough(anchor).get(random.nextInt(2));
        List<GridPoint> points = pointsOf(along);
        GridPoint other = points.get(random.nextInt(points.size()));
        if (other.equals(anchor) || !wiresThrough(other).equals(List.of(along))) {
            return false;
        }
        Wire overlap = new Wire(document.newWireId(), anchor, other);
        document.add(overlap);
        joiningWires.add(overlap.id());
        steps.add("joining overlap " + describe(overlap) + " over " + describe(along));
        return true;
    }

    /** Points in the interior of exactly two wires (they cross without joining), with no pin there. */
    private List<GridPoint> crossings() {
        List<GridPoint> crossings = new ArrayList<>();
        for (Wire horizontal : document.wires()) {
            if (!horizontal.isHorizontal()) {
                continue;
            }
            for (Wire vertical : document.wires()) {
                if (vertical.isHorizontal()) {
                    continue;
                }
                GridPoint point = new GridPoint(vertical.start().x(), horizontal.start().y());
                if (WireGraph.isInterior(horizontal, point) && WireGraph.isInterior(vertical, point)
                        && !pinPoints.contains(point) && wiresThrough(point).size() == 2) {
                    crossings.add(point);
                }
            }
        }
        return crossings;
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private List<Wire> floatingShape() {
        GridPoint start = randomPointNearTheCircuit();
        int[] direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        int length = 1 + random.nextInt(5);
        GridPoint corner = start.translate(direction[0] * length, direction[1] * length);
        Wire first = new Wire(new ElementId("pending-first"), start, corner);
        if (!isClear(first, Set.of())) {
            return List.of();
        }
        if (random.nextBoolean()) {
            return List.of(new Wire(document.newWireId(), start, corner));
        }
        int turnLength = 1 + random.nextInt(4);
        int turnSign = random.nextBoolean() ? 1 : -1;
        GridPoint end = corner.translate(direction[1] * turnSign * turnLength, direction[0] * turnSign * turnLength);
        Wire second = new Wire(new ElementId("pending-second"), corner, end);
        if (!isClear(second, Set.of(corner))) {
            return List.of();
        }
        return List.of(new Wire(document.newWireId(), start, corner), new Wire(document.newWireId(), corner, end));
    }

    private void watch(Wire wire, String name) {
        List<Probe> probes = new ArrayList<>(document.probes());
        probes.add(new Probe(document.newProbeId(), new ProbeTarget.WireTarget(wire.id()), name));
        document.setProbes(probes);
    }

    private Wire randomWire(int minimumLength) {
        List<Wire> wires = document.wires().stream()
                .filter(wire -> pointsOf(wire).size() > minimumLength)
                .toList();
        return wires.isEmpty() ? null : wires.get(random.nextInt(wires.size()));
    }

    private GridPoint randomPointNearTheCircuit() {
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        List<GridPoint> corners = new ArrayList<>();
        bodies.forEach(body -> {
            corners.add(new GridPoint(body.x(), body.y()));
            corners.add(new GridPoint(body.maximumX(), body.maximumY()));
        });
        document.wires().forEach(wire -> {
            corners.add(wire.start());
            corners.add(wire.end());
        });
        if (corners.isEmpty()) {
            corners.add(GridPoint.ORIGIN);
        }
        for (GridPoint corner : corners) {
            minimumX = Math.min(minimumX, corner.x());
            minimumY = Math.min(minimumY, corner.y());
            maximumX = Math.max(maximumX, corner.x());
            maximumY = Math.max(maximumY, corner.y());
        }
        return new GridPoint(minimumX - MARGIN + random.nextInt(maximumX - minimumX + 2 * MARGIN + 1),
                minimumY - MARGIN + random.nextInt(maximumY - minimumY + 2 * MARGIN + 1));
    }

    /**
     * True when every point of {@code wire}, except {@code except}, is empty: no pin, no wire through it (end,
     * interior or crossing) and outside every component body (edges included).
     */
    private boolean isClear(Wire wire, Set<GridPoint> except) {
        for (GridPoint point : pointsOf(wire)) {
            if (except.contains(point)) {
                continue;
            }
            if (pinPoints.contains(point) || isInsideABody(point) || reserved.contains(point)
                    || !wiresThrough(point).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when a new wire end at {@code point} joins only what is already joined there: at most one wire passes
     * through it, or a pin or a wire end already connects everything through it.
     */
    private boolean isSafeToJoin(GridPoint point) {
        List<Wire> through = wiresThrough(point);
        if (through.size() <= 1 || pinPoints.contains(point)) {
            return true;
        }
        return through.stream().anyMatch(wire -> wire.start().equals(point) || wire.end().equals(point));
    }

    /** True when a wire other than {@code wire} has {@code point} in its interior (a new end there would join it). */
    private boolean isCrossedBySomeoneElse(GridPoint point, Wire wire) {
        return document.wires().stream()
                .anyMatch(other -> !other.id().equals(wire.id()) && WireGraph.isInterior(other, point));
    }

    /**
     * True when a wire perpendicular to {@code base} passes through {@code point}: a leg starting there would be
     * collinear with it and the merge rule would lengthen the leg first (the U then no longer has the legs the
     * flattening needs; spec 2.2.4 moves the middle by the shorter leg).
     */
    private boolean continuesALeg(GridPoint point, Wire base) {
        return wiresThrough(point).stream()
                .anyMatch(other -> !other.id().equals(base.id()) && other.isHorizontal() != base.isHorizontal());
    }

    private boolean isInsideABody(GridPoint point) {
        return bodies.stream().anyMatch(body -> body.contains(point));
    }

    private List<Wire> wiresThrough(GridPoint point) {
        return document.wires().stream().filter(wire -> wire.contains(point)).toList();
    }

    /** Every grid point of {@code wire}, from its start to its end. */
    static List<GridPoint> pointsOf(Wire wire) {
        List<GridPoint> points = new ArrayList<>();
        int stepX = Integer.signum(wire.end().x() - wire.start().x());
        int stepY = Integer.signum(wire.end().y() - wire.start().y());
        GridPoint point = wire.start();
        points.add(point);
        while (!point.equals(wire.end())) {
            point = point.translate(stepX, stepY);
            points.add(point);
        }
        return points;
    }

    static String describe(Wire wire) {
        return wire.id().value() + "(" + wire.start().x() + "," + wire.start().y() + "-" + wire.end().x() + ","
                + wire.end().y() + ")";
    }
}
