package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.tunnel.TunnelKind;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

/**
 * Lays one Logisim circuit out on VSim's grid, as faithfully as VSim's parts allow (the user accepts some ugly wire,
 * spec decision 1):
 * <ul>
 *   <li>Logisim's grid of 10 px is 1.5 VSim units: every wire and end is spread that way and rounded ({@link
 *   #scaled}); the parts keep VSim's size, so they have room.</li>
 *   <li>Each Logisim part becomes its VSim part ({@link LogisimPartMapper}) at the place and rotation that bring its
 *   pins nearest the Logisim ends they stand for, without any pin touching another net. A mirrored part (a
 *   multiplexer with its select on top, a splitter whose ends come out the other way) takes the nearest rotation.</li>
 *   <li>A pin that is not on its end (nor on a wire of its net) is joined to its net by added wire segments
 *   ({@link WireRouter}) that touch no other net; when there is no such way the pin stays unconnected, noted.</li>
 *   <li>A gate input Logisim negates alone gets a NOT in front of it; a one-bit splitter is just a wire between its
 *   two ends; texts and labelled probes become texts at their place; a subcircuit instance becomes the part the
 *   subcircuit step made of its circuit ({@link SubcircuitPart}).</li>
 * </ul>
 */
public final class LogisimLayout {

    /**
     * Logisim's 10-pixel grid step is 1.5 VSim units ({@value #SPREAD_UNITS} units every {@value #SPREAD_PIXELS}
     * pixels): VSim's parts are bigger than Logisim's for the same pins, so a circuit copied step for step is crowded.
     */
    private static final int SPREAD_UNITS = 3;
    private static final int SPREAD_PIXELS = 20;
    /**
     * VSim's label font (SansSerif 10 by default) as core estimates it, in eighths of a unit (a unit is 8 px): about
     * 5 px per character, about 14 px per line, and the baseline about a unit below the top.
     */
    private static final int EIGHTHS = 8;
    private static final int CHARACTER_EIGHTHS = 5;
    private static final int LINE_EIGHTHS = 14;
    private static final int BASELINE_UNITS = 1;
    /** What turning a part away from its Logisim facing costs, in units of pin distance. */
    private static final int ROTATION_PENALTY = 3;

    private final ComponentTypeRegistry registry;
    private final LogisimPartMapper mapper;

    /** A layout with {@code registry}'s parts (it must also resolve the subcircuit steps's types). */
    public LogisimLayout(ComponentTypeRegistry registry) {
        this(registry, new LogisimPartMapper());
    }

    LogisimLayout(ComponentTypeRegistry registry, LogisimPartMapper mapper) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * The circuit of {@code analysis} on VSim's grid: each component mapped with the context {@code contexts} gives
     * it (the nets step's directions), each subcircuit instance as {@code subcircuits} says its circuit is made
     * (an instance of a circuit it does not know is left out).
     */
    public LaidOutCircuit layOut(LogisimNetAnalysis analysis, Function<LogisimComponent, MappingContext> contexts,
                                 Function<String, Optional<SubcircuitPart>> subcircuits) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(contexts, "contexts");
        Objects.requireNonNull(subcircuits, "subcircuits");
        return new Run(analysis, contexts, subcircuits).layOut();
    }

    /** Logisim's point on VSim's grid: its pixels × 3 / 20 (1.5 units per Logisim grid step), rounded half up. */
    static GridPoint scaled(LogisimPoint point) {
        return new GridPoint(spread(point.x()), spread(point.y()));
    }

    private static int spread(int pixels) {
        return Math.floorDiv(pixels * SPREAD_UNITS + SPREAD_PIXELS / 2, SPREAD_PIXELS);
    }

    /** What one VSim pin of a part being placed belongs to and, when it must meet one, where its Logisim end is. */
    private record PinPlan(int net, Optional<GridPoint> target) {
    }

    /**
     * A VSim part to place: its type, parameters, preferred rotation, label, one plan per pin, when known the point of
     * the unturned part (from its origin) that its Logisim location stands for, and whether it must keep its rotation
     * ({@link MappedPart.Extras#upright}).
     */
    private record PartRequest(String typeId, Map<String, Object> parameters, Rotation rotation, String label,
                               List<PinSpec> pins, List<PinPlan> plans, Optional<GridPoint> anchor,
                               boolean upright) {
        PartRequest {
            parameters = Map.copyOf(parameters);
            pins = List.copyOf(pins);
            plans = List.copyOf(plans);
        }
    }

    /** Where a part goes: its position and rotation. */
    private record Spot(GridPoint position, Rotation rotation) {
    }

    /** The work of laying out one circuit. */
    private final class Run {
        private final LogisimNetAnalysis analysis;
        private final Function<LogisimComponent, MappingContext> contexts;
        private final Function<String, Optional<SubcircuitPart>> subcircuits;
        private final CircuitDocument document = new CircuitDocument();
        private final GridOccupancy occupancy = new GridOccupancy();
        private final WireRouter router = new WireRouter(occupancy);
        private final PartPlacement placement = new PartPlacement(occupancy);
        private final Map<PinRef, Integer> pinNets = new HashMap<>();
        private final Map<LogisimComponent, ElementId> parts = new HashMap<>();
        /** Where the placed parts' pins are. */
        private final Set<GridPoint> pinPoints = new HashSet<>();
        private final List<ImportNote> notes = new ArrayList<>();
        private final List<PlacedText> texts = new ArrayList<>();
        /** Nets of pins that join nothing in Logisim (or only each other): negative, so never a Logisim net. */
        private int nextLoneNet = -1;
        private int pinsOnTheirEnd;
        private int pinsRouted;
        private int addedWires;
        private int unrouted;
        private int turnedParts;

        Run(LogisimNetAnalysis analysis, Function<LogisimComponent, MappingContext> contexts,
            Function<String, Optional<SubcircuitPart>> subcircuits) {
            this.analysis = analysis;
            this.contexts = contexts;
            this.subcircuits = subcircuits;
        }

        LaidOutCircuit layOut() {
            drawLogisimWires();
            reserveConnectedEnds();
            for (LogisimComponent component : analysis.circuit().components()) {
                if (analysis.isJunction(component)) {
                    joinJunction(component);
                }
            }
            for (LogisimComponent component : analysis.circuit().components()) {
                if (!analysis.isJunction(component)) {
                    place(component);
                }
            }
            trimWiresPastMovedPins();
            noteTextsOverParts();
            return new LaidOutCircuit(document, parts, pinNets,
                    new LaidOutCircuit.Statistics(pinsOnTheirEnd, pinsRouted, addedWires, unrouted, turnedParts),
                    notes);
        }

        // --- Logisim's own wires ---

        private void drawLogisimWires() {
            for (LogisimWire wire : analysis.circuit().wires()) {
                GridPoint from = scaled(wire.from());
                GridPoint to = scaled(wire.to());
                int net = analysis.netOf(wire.from());
                if (from.equals(to)) {
                    occupancy.addPoint(from, net);
                    occupancy.anchor(from, net);
                } else {
                    addWire(from, to, net);
                }
            }
        }

        /** Every connected end's point belongs to its net from the start, so no other net's pin takes it. */
        private void reserveConnectedEnds() {
            for (LogisimComponent component : analysis.circuit().components()) {
                for (LogisimEnd end : analysis.ends(component)) {
                    if (analysis.isConnected(end.point())) {
                        occupancy.addPoint(scaled(end.point()), analysis.netOf(end.point()));
                        occupancy.anchor(scaled(end.point()), analysis.netOf(end.point()));
                    }
                }
            }
        }

        /** A one-bit splitter: a wire from its bus to the end its bit goes to. */
        private void joinJunction(LogisimComponent junction) {
            List<LogisimEnd> ends = analysis.ends(junction);
            GridPoint bus = scaled(ends.getFirst().point());
            Optional<LogisimEnd> target = ends.stream().skip(1).filter(end -> end.width() > 0).findFirst();
            if (target.isEmpty()) {
                return;
            }
            GridPoint end = scaled(target.get().point());
            int net = analysis.netOf(ends.getFirst().point());
            if (bus.equals(end)) {
                return;
            }
            occupancy.addPoint(bus, net);
            occupancy.addPoint(end, net);
            Optional<List<GridPoint>> corners = router.route(bus, net, end::equals);
            if (corners.isPresent()) {
                addPath(corners.get(), net);
            } else {
                unrouted++;
                notes.add(ImportNote.at(ImportNote.Kind.DIFFERENT, analysis.circuit().name(), junction.location(),
                        LogisimNotes.UNROUTED_PIN, junction.name(), "bus"));
            }
        }

        // --- Parts ---

        private void place(LogisimComponent component) {
            LogisimMapping mapping = mapper.map(component, contexts.apply(component));
            notes.addAll(mapping.notes());
            switch (mapping) {
                case MappedPart part -> placeMappedPart(component, part);
                case MappedSubcircuit subcircuit -> subcircuits.apply(subcircuit.circuit())
                        .ifPresent(made -> placeSubcircuit(component, subcircuit, made));
                case MappedText text -> addText(component, text);
                case NotMapped notMapped -> {
                    // Left out: its notes say why.
                }
            }
        }

        private void placeMappedPart(LogisimComponent component, MappedPart part) {
            List<PinSpec> pins = pinsOf(part.typeId(), part.parameters(), part.label());
            List<PinPlan> plans = plansFor(component, pins, (end, index) -> pinOf(part, pins, end, index));
            Map<Integer, Integer> negatedPins = new HashMap<>();
            for (int input : part.extras().notInFront()) {
                pinIndex(pins, "in" + input).ifPresent(pin -> {
                    int net = nextLoneNet--;
                    negatedPins.put(input, pin);
                    plans.set(pin, new PinPlan(net, Optional.empty()));
                });
            }
            PartRequest request = new PartRequest(part.typeId(), coloured(part), part.rotation(), part.label(),
                    pins, plans, Optional.empty(), part.extras().upright());
            Component placed = placePart(component, request);
            parts.put(component, placed.id());
            negatedPins.forEach((input, pin) -> placeNotInFront(component, part, placed, request, input, pin));
        }

        /**
         * A NOT (VSim's buffer, negated, with a gate's delay) whose output meets the gate's input {@code pin} and
         * whose input meets the Logisim end of that input.
         */
        private void placeNotInFront(LogisimComponent component, MappedPart gate, Component placedGate,
                                     PartRequest gateRequest, int input, int pin) {
            Map<String, Object> parameters = Map.of(StandardComponentTypes.WIDTH,
                    gate.parameters().get(StandardComponentTypes.WIDTH), StandardComponentTypes.NEGATE, true,
                    "delay", LogisimTiming.GATE);
            List<PinSpec> pins = pinsOf(StandardComponentTypes.BUFFER, parameters, "");
            GridPoint gatePin = placedGate.position().plus(placedGate.rotation()
                    .apply(gateRequest.pins().get(pin).position()));
            LogisimEnd end = analysis.ends(component).stream().filter(candidate -> candidate.name()
                    .equals("in" + input)).findFirst().orElseThrow();
            occupancy.anchor(gatePin, gateRequest.plans().get(pin).net());
            List<PinPlan> plans = new ArrayList<>();
            for (PinSpec notPin : pins) {
                plans.add(notPin.isInput() ? planFor(end) : new PinPlan(gateRequest.plans().get(pin).net(),
                        Optional.of(gatePin)));
            }
            placePart(component, new PartRequest(StandardComponentTypes.BUFFER, parameters, placedGate.rotation(),
                    "", pins, plans, Optional.empty(), false));
        }

        private void placeSubcircuit(LogisimComponent component, MappedSubcircuit subcircuit, SubcircuitPart made) {
            List<PinSpec> pins = pinsOf(made.typeId(), made.parameters(), subcircuit.label());
            List<PinPlan> plans = plansFor(component, pins, (end, index) -> {
                Integer pin = made.pinOfInnerPin().get(LogisimPoint.parse(end.name()));
                return pin == null ? OptionalInt.empty() : OptionalInt.of(pin);
            });
            Rotation rotation = made.rotationFor(subcircuit.facing());
            Component placed = placePart(component, new PartRequest(made.typeId(), made.parameters(), rotation,
                    subcircuit.label(), pins, plans, made.anchor(), false));
            parts.put(component, placed.id());
        }

        /** Which VSim pin a Logisim end of a part is, if any. */
        private interface EndMatcher {
            OptionalInt pinOf(LogisimEnd end, int endIndex);
        }

        /** One plan per VSim pin: the Logisim end it stands for when that end is connected, or a lone net. */
        private List<PinPlan> plansFor(LogisimComponent component, List<PinSpec> pins, EndMatcher matcher) {
            List<PinPlan> plans = new ArrayList<>();
            for (int pin = 0; pin < pins.size(); pin++) {
                plans.add(new PinPlan(nextLoneNet--, Optional.empty()));
            }
            List<LogisimEnd> ends = analysis.ends(component);
            for (int index = 0; index < ends.size(); index++) {
                OptionalInt pin = matcher.pinOf(ends.get(index), index);
                if (pin.isPresent() && pin.getAsInt() < pins.size()) {
                    plans.set(pin.getAsInt(), planFor(ends.get(index)));
                }
            }
            return plans;
        }

        private PinPlan planFor(LogisimEnd end) {
            if (!analysis.isConnected(end.point())) {
                return new PinPlan(nextLoneNet--, Optional.empty());
            }
            return new PinPlan(analysis.netOf(end.point()), Optional.of(scaled(end.point())));
        }

        /**
         * A splitter's bus is its first pin and Logisim end k ≥ 1 its {@code splitterEnds} pin; any other part's ends
         * are named after their VSim pin ({@link LogisimEnds}).
         */
        private OptionalInt pinOf(MappedPart part, List<PinSpec> pins, LogisimEnd end, int endIndex) {
            boolean splitter = part.typeId().equals(StandardComponentTypes.BUS_SPLITTER)
                    || part.typeId().equals(StandardComponentTypes.BUS_JOINER);
            if (splitter) {
                if (endIndex == 0) {
                    return OptionalInt.of(0);
                }
                Integer pin = part.extras().splitterEnds().get(endIndex - 1);
                return pin == null ? OptionalInt.empty() : OptionalInt.of(pin + 1);
            }
            return end.inVsim() ? pinIndex(pins, end.name()) : OptionalInt.empty();
        }

        private OptionalInt pinIndex(List<PinSpec> pins, String name) {
            for (int index = 0; index < pins.size(); index++) {
                if (pins.get(index).name().equals(name)) {
                    return OptionalInt.of(index);
                }
            }
            return OptionalInt.empty();
        }

        /** The pins a part of {@code typeId} has with {@code parameters} (at rotation 0, from its origin). */
        private List<PinSpec> pinsOf(String typeId, Map<String, Object> parameters, String label) {
            ComponentType type = registry.require(typeId);
            Component sample = type.newComponent(new ElementId("sample"), GridPoint.ORIGIN, Rotation.R0, parameters,
                    label);
            return registry.typeOf(sample).forComponent(sample).pins();
        }

        /**
         * The part's parameters; an imported tunnel (an entrada «Sin dirección») also gets a colour, since Logisim
         * has none: its name's, when a tunnel of that name is already placed, else the one VSim gives a new entrada
         * (the first palette colour no entrada of this circuit uses), so each name has one colour.
         */
        private Map<String, Object> coloured(MappedPart part) {
            if (!TunnelKind.ENTRANCE.typeId().equals(part.typeId())) {
                return part.parameters();
            }
            Map<String, Object> parameters = new HashMap<>(part.parameters());
            int nameColor = Tunnels.colorOf(document, String.valueOf(part.parameters().get(Tunnels.NAME)).strip());
            parameters.put(Tunnels.COLOR, nameColor == Tunnels.NO_COLOR ? Tunnels.freeColor(document) : nameColor);
            return parameters;
        }

        /** Puts the part at its best spot, joins its pins to their nets and returns it. */
        private Component placePart(LogisimComponent component, PartRequest request) {
            Spot spot = bestSpot(component, request);
            if (spot.rotation() != request.rotation()) {
                turnedParts++;
            }
            // A new part made from the request's parameters, not a placed one looked up (TypeLookupRuleTest).
            String typeId = request.typeId();
            Component placed = registry.require(typeId).newComponent(document.newComponentId(), spot.position(),
                    spot.rotation(), request.parameters(), request.label());
            document.add(placed);
            List<GridPoint> points = new ArrayList<>();
            List<Boolean> alreadyJoined = new ArrayList<>();
            for (int pin = 0; pin < request.pins().size(); pin++) {
                GridPoint point = pinPoint(spot, request.pins().get(pin));
                points.add(point);
                alreadyJoined.add(occupancy.isAnchored(point, request.plans().get(pin).net()));
            }
            for (int pin = 0; pin < request.pins().size(); pin++) {
                pinNets.put(new PinRef(placed.id(), pin), request.plans().get(pin).net());
                occupancy.addPoint(points.get(pin), request.plans().get(pin).net());
                pinPoints.add(points.get(pin));
            }
            for (int pin = 0; pin < request.pins().size(); pin++) {
                PinPlan plan = request.plans().get(pin);
                if (plan.target().isEmpty()) {
                    continue;
                }
                if (alreadyJoined.get(pin)) {
                    pinsOnTheirEnd++;
                    occupancy.anchor(points.get(pin), plan.net());
                } else {
                    join(component, request.pins().get(pin), points.get(pin), plan.net());
                }
            }
            return placed;
        }

        /**
         * Added wire segments from the pin to a point already joined to its net ({@link GridOccupancy#anchor}), or a
         * note when no way touches no other net.
         */
        private void join(LogisimComponent component, PinSpec pin, GridPoint point, int net) {
            Optional<List<GridPoint>> corners = router.route(point, net,
                    candidate -> occupancy.isAnchored(candidate, net));
            if (corners.isPresent()) {
                pinsRouted++;
                addPath(corners.get(), net);
            } else {
                unrouted++;
                notes.add(ImportNote.at(ImportNote.Kind.DIFFERENT, analysis.circuit().name(), component.location(),
                        LogisimNotes.UNROUTED_PIN, component.name(), pin.name()));
            }
        }

        /**
         * The rotation and position whose pins come nearest their Logisim ends without touching another net
         * ({@link PartPlacement}); a rotation other than the part's own costs {@link #ROTATION_PENALTY}. With no spot
         * free the part goes apart, below everything drawn so far, noted: never where a pin would join another net
         * (its pins are then joined by added wires, or left unconnected and noted).
         */
        private Spot bestSpot(LogisimComponent component, PartRequest request) {
            List<Rotation> rotations = request.upright() ? List.of(request.rotation()) : List.of(Rotation.values());
            List<PartPlacement.Choice> choices = rotations.stream()
                    .map(rotation -> choice(component, request, rotation)).toList();
            Optional<PartPlacement.Spot> free = placement.bestFree(choices);
            if (free.isPresent()) {
                return new Spot(free.get().position(), rotations.get(free.get().choice()));
            }
            notes.add(ImportNote.at(ImportNote.Kind.APPROXIMATED, analysis.circuit().name(), component.location(),
                    LogisimNotes.CROWDED_PART, component.name()));
            Rotation own = request.rotation();
            return new Spot(placement.apart(choice(component, request, own)), own);
        }

        /**
         * The part at {@code rotation}: each pin turned, with its net and its Logisim end. A part with no end to meet
         * goes where its anchor (or else its first pin) is at its Logisim location.
         */
        private PartPlacement.Choice choice(LogisimComponent component, PartRequest request, Rotation rotation) {
            List<PartPlacement.End> ends = new ArrayList<>();
            for (int pin = 0; pin < request.pins().size(); pin++) {
                PinPlan plan = request.plans().get(pin);
                ends.add(new PartPlacement.End(rotation.apply(request.pins().get(pin).position()), plan.net(),
                        plan.target()));
            }
            GridPoint estimate = PartPlacement.estimate(ends, () -> {
                GridPoint location = scaled(component.location());
                GridPoint offset = request.anchor().map(rotation::apply).orElseGet(() -> request.pins().isEmpty()
                        ? GridPoint.ORIGIN : rotation.apply(request.pins().getFirst().position()));
                return location.translate(-offset.x(), -offset.y());
            });
            return new PartPlacement.Choice(ends, estimate, rotation == request.rotation() ? 0 : ROTATION_PENALTY);
        }

        private GridPoint pinPoint(Spot spot, PinSpec pin) {
            return spot.position().plus(spot.rotation().apply(pin.position()));
        }

        // --- Wires and texts ---

        /**
         * A Logisim wire that ended on a part's end whose VSim pin landed elsewhere (on the wire further back, or
         * joined by added segments) would end loose there: it is cut back to the nearest point where it still touches
         * something (a pin, another wire's end), or left out when it touches nothing more. Wire ends that were loose
         * in Logisim already (not on any part's end) stay, and so do the ones on a Logisim end the VSim part lacks
         * or on a part left out. Cutting never changes what is joined.
         */
        private void trimWiresPastMovedPins() {
            Set<GridPoint> trimmable = new HashSet<>();
            for (LogisimComponent component : parts.keySet()) {
                analysis.ends(component).stream().filter(LogisimEnd::inVsim)
                        .forEach(end -> trimmable.add(scaled(end.point())));
            }
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Wire wire : List.copyOf(document.wires())) {
                    for (GridPoint end : List.of(wire.start(), wire.end())) {
                        if (trimmable.contains(end) && !touches(end, wire)) {
                            trim(wire, end, trimmable);
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        /**
         * {@code wire} cut from its loose {@code end} back to the first point where it touches something; a wire that
         * only touches something at its other end is left out, and that end becomes {@code trimmable} (the wire that
         * led there may now end loose too).
         */
        private void trim(Wire wire, GridPoint end, Set<GridPoint> trimmable) {
            GridPoint other = end.equals(wire.start()) ? wire.end() : wire.start();
            int stepX = Integer.signum(other.x() - end.x());
            int stepY = Integer.signum(other.y() - end.y());
            GridPoint point = end;
            while (!point.equals(other)) {
                point = point.translate(stepX, stepY);
                if (touches(point, wire)) {
                    if (point.equals(other)) {
                        break;
                    }
                    document.replace(new Wire(wire.id(), point, other));
                    return;
                }
            }
            document.remove(wire.id());
            trimmable.add(other);
        }

        /**
         * Whether something other than {@code wire} joins it at {@code point}: a pin there, another wire's end there,
         * or another wire running through it when {@code point} is one of {@code wire}'s ends.
         */
        private boolean touches(GridPoint point, Wire wire) {
            if (pinPoints.contains(point)) {
                return true;
            }
            boolean ownEnd = point.equals(wire.start()) || point.equals(wire.end());
            for (Wire other : document.wires()) {
                if (other.id().equals(wire.id())) {
                    continue;
                }
                if (point.equals(other.start()) || point.equals(other.end())
                        || ownEnd && liesInside(point, other)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean liesInside(GridPoint point, Wire wire) {
            int minimumX = Math.min(wire.start().x(), wire.end().x());
            int maximumX = Math.max(wire.start().x(), wire.end().x());
            int minimumY = Math.min(wire.start().y(), wire.end().y());
            int maximumY = Math.max(wire.start().y(), wire.end().y());
            return (minimumY == maximumY && point.y() == minimumY && point.x() > minimumX && point.x() < maximumX)
                    || (minimumX == maximumX && point.x() == minimumX && point.y() > minimumY && point.y() < maximumY);
        }

        private void addPath(List<GridPoint> corners, int net) {
            for (int index = 1; index < corners.size(); index++) {
                addWire(corners.get(index - 1), corners.get(index), net);
                addedWires++;
            }
        }

        private void addWire(GridPoint from, GridPoint to, int net) {
            document.add(new Wire(document.newWireId(), from, to));
            occupancy.addAnchoredWire(from, to, net);
        }

        /**
         * A text at its Logisim place, with Logisim's anchor: its left end, middle or right end ({@code halign}) at
         * the location, and its top, baseline, bottom or middle ({@code valign}; a probe's label sits on its
         * baseline). VSim places a text by its top-left corner and draws it with its own font, whose box
         * {@link #textBox} estimates. A probe without a label shows nothing.
         */
        private void addText(LogisimComponent component, MappedText text) {
            if (text.text().isBlank()) {
                return;
            }
            GridPoint anchor = scaled(component.location());
            Bounds box = textBox(text.text(), GridPoint.ORIGIN);
            int left = switch (text.horizontalAlignment()) {
                case "center" -> box.width() / 2;
                case "right" -> box.width();
                default -> 0;
            };
            int up = switch (text.verticalAlignment()) {
                case "top" -> 0;
                case "bottom" -> box.height();
                case "center" -> box.height() / 2;
                default -> BASELINE_UNITS;
            };
            TextLabel label = new TextLabel(document.newLabelId(), anchor.translate(-left, -up), text.text());
            document.add(label);
            texts.add(new PlacedText(component, label));
        }

        /**
         * The texts that land on a part are noted, all in one note of the circuit: VSim's parts are bigger than
         * Logisim's, so a text that sat beside one in Logisim may cover it now. The note lists each text with the
         * part it covers, named as Logisim does.
         */
        private void noteTextsOverParts() {
            Map<ElementId, LogisimComponent> origins = new HashMap<>();
            parts.forEach((component, id) -> origins.put(id, component));
            List<String> covered = new ArrayList<>();
            for (PlacedText text : texts) {
                Bounds box = textBox(text.label().text(), text.label().position());
                for (Component part : document.components()) {
                    LogisimComponent origin = origins.get(part.id());
                    if (origin != null && overlaps(registry.typeOf(part).bounds(part), box)) {
                        covered.add("«" + text.label().text() + "» / «" + origin.name() + "»");
                        break;
                    }
                }
            }
            if (!covered.isEmpty()) {
                notes.add(ImportNote.about(ImportNote.Kind.APPROXIMATED, analysis.circuit().name(),
                        LogisimNotes.TEXT_OVER_PART, String.join(", ", covered)));
            }
        }
    }

    /** Whether two boxes share some area: a text right beside a part, touching its edge, does not cover it. */
    private static boolean overlaps(Bounds first, Bounds second) {
        return first.x() < second.maximumX() && second.x() < first.maximumX()
                && first.y() < second.maximumY() && second.y() < first.maximumY();
    }

    /** A text placed from a Logisim {@code Text} or probe. */
    private record PlacedText(LogisimComponent component, TextLabel label) {
    }

    /**
     * Where VSim draws {@code text} placed at {@code corner}, estimated in whole units (core cannot measure fonts):
     * {@value #CHARACTER_EIGHTHS}/8 of a unit per character of its longest line, {@value #LINE_EIGHTHS}/8 per line,
     * rounded up.
     */
    static Bounds textBox(String text, GridPoint corner) {
        List<String> lines = text.lines().toList();
        int longestLine = lines.stream().mapToInt(String::length).max().orElse(0);
        int width = Math.ceilDiv(longestLine * CHARACTER_EIGHTHS, EIGHTHS);
        int height = Math.ceilDiv(Math.max(1, lines.size()) * LINE_EIGHTHS, EIGHTHS);
        return new Bounds(corner.x(), corner.y(), Math.max(1, width), height);
    }
}
