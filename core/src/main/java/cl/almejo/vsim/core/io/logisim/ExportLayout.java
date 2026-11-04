package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Lays one VSim circuit out as a Logisim circuit. One VSim unit is one Logisim grid step of 10 px, so every VSim point
 * is a distinct point of Logisim's grid (inverting the import's ×1.5 would put two VSim points on one Logisim point
 * and join what VSim keeps apart). The VSim wires are copied as they are. Each part becomes its Logisim part
 * ({@link LogisimExportRules}) at the spot, turned as the VSim part is, that brings its Logisim ends nearest the VSim
 * pins they stand for without any end touching another net; an end that is not where its net is gets added wires
 * ({@link WireRouter}, with VSim's own rules of what touches, {@link GridOccupancy}). Last, Logisim only joins wires
 * at their ends and ends at their exact point, so every wire is cut wherever a wire end or a part's end of its own net
 * lies inside it: then Logisim joins exactly what VSim does. A custom component's instance needs no added wire: its
 * Logisim drawing puts each end on its VSim pin ({@link ExportAppearance}).
 */
final class ExportLayout {

    private static final int PIXELS_PER_UNIT = 10;
    /** Logisim's widest bus (32 bits). */
    private static final int MAXIMUM_WIDTH = 32;
    private static final String TEXT = "Text";
    /** Logisim draws a text from its baseline: a VSim text's first line sits one unit above it. */
    private static final int TEXT_LINE_UNITS = 1;
    private static final LogisimDefaults DEFAULTS = LogisimDefaults.logisim27();

    private ExportLayout() {
    }

    /** What one circuit became: the Logisim circuit (without its look), each pin's end and the notes. */
    record Result(LogisimCircuit circuit, Map<PinRef, LogisimPoint> ends, Map<ElementId, LogisimPoint> pinLocations,
                  List<ImportNote> notes) {
        Result {
            ends = Map.copyOf(ends);
            pinLocations = Map.copyOf(pinLocations);
            notes = List.copyOf(notes);
        }
    }

    /** What the layout of one circuit is given. */
    record Request(String circuit, boolean main, CircuitDocument document, ComponentTypeRegistry registry,
                   LogisimProject exportedSoFar, Function<String, Optional<SubcircuitExport>> subcircuits,
                   Map<ElementId, String> pinLabels) {
        Request {
            Objects.requireNonNull(circuit, "circuit");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(registry, "registry");
            Objects.requireNonNull(exportedSoFar, "exportedSoFar");
            Objects.requireNonNull(subcircuits, "subcircuits");
            pinLabels = Map.copyOf(pinLabels);
        }
    }

    static Result layOut(Request request) {
        return new Run(request).layOut();
    }

    /** One Logisim end of a part being placed: its offset from the part's location, its net and its VSim pin. */
    private record EndPlan(GridPoint offset, int net, Optional<Integer> pin, boolean connected) {
    }

    /** The work of laying out one circuit. */
    private static final class Run {
        private final Request request;
        private final Netlist drawn;
        private final Netlist joined;
        private final GridOccupancy occupancy = new GridOccupancy();
        private final WireRouter router = new WireRouter(occupancy);
        private final PartPlacement placement = new PartPlacement(occupancy);
        private final Map<PinRef, Integer> pinNets = new HashMap<>();
        private final Set<PinRef> connectedPins = new HashSet<>();
        private final Set<Integer> anchoredNets = new HashSet<>();
        private final List<Wire> wires = new ArrayList<>();
        private final Set<GridPoint> endPoints = new HashSet<>();
        private final List<LogisimComponent> components = new ArrayList<>();
        private final Map<PinRef, LogisimPoint> ends = new HashMap<>();
        private final Map<ElementId, LogisimPoint> pinLocations = new HashMap<>();
        private final List<ImportNote> notes = new ArrayList<>();
        private int nextLoneNet = -1;

        Run(Request request) {
            this.request = request;
            NetlistCompiler compiler = new NetlistCompiler(request.registry());
            this.drawn = compiler.compileAsDrawn(request.document());
            this.joined = compiler.compile(request.document());
        }

        Result layOut() {
            readVsimNets();
            for (Component component : request.document().components()) {
                place(component);
            }
            request.document().labels().forEach(this::addText);
            LogisimCircuit circuit = new LogisimCircuit(request.circuit(), components, logisimWires(),
                    LogisimAppearance.NONE);
            return new Result(circuit, ends, pinLocations, notes);
        }

        // --- VSim's nets ---

        /** Every VSim wire and pin on the grid, by drawn net; the wires are where each net already is. */
        private void readVsimNets() {
            List<Net> nets = drawn.nets();
            Map<NetId, Integer> netIndexes = new HashMap<>();
            for (int index = 0; index < nets.size(); index++) {
                Net net = nets.get(index);
                netIndexes.put(net.id(), index);
                for (PinRef pin : net.pins()) {
                    pinNets.put(pin, index);
                }
                if (net.pins().size() > 1 || !net.wires().isEmpty()) {
                    connectedPins.addAll(net.pins());
                }
            }
            for (Wire wire : request.document().wires()) {
                int net = drawn.netForWire(wire.id()).map(found -> netIndexes.get(found.id()))
                        .orElseGet(() -> nextLoneNet--);
                occupancy.addAnchoredWire(wire.start(), wire.end(), net);
                anchoredNets.add(net);
                wires.add(wire);
            }
            for (Component component : request.document().components()) {
                ComponentType type = typeOf(component);
                for (int pin = 0; pin < type.pinCount(); pin++) {
                    occupancy.addPoint(type.pinLocation(component, pin), netOf(new PinRef(component.id(), pin)));
                }
            }
        }

        private int netOf(PinRef pin) {
            return pinNets.computeIfAbsent(pin, missing -> nextLoneNet--);
        }

        private ComponentType typeOf(Component component) {
            return request.registry().typeOf(component).forComponent(component);
        }

        // --- Parts ---

        private void place(Component component) {
            Optional<ExportDraft.ExportedPart> part = exportedPart(component);
            if (part.isEmpty()) {
                return;
            }
            ComponentType type = typeOf(component);
            LogisimAttributes attributes = DEFAULTS.resolve(part.get().library(), part.get().name(),
                    part.get().attributes(), LogisimVersion.LATEST);
            List<LogisimEnd> logisimEnds = LogisimEnds.of(new LogisimComponent(part.get().library(),
                    part.get().name(), new LogisimPoint(0, 0), attributes), request.exportedSoFar());
            List<EndPlan> plans = plansFor(component, part.get(), logisimEnds);
            noteMissingPins(component, type, plans);
            GridPoint location = bestLocation(component, type, plans);
            LogisimPoint logisimLocation = pixels(location);
            components.add(new LogisimComponent(part.get().library(), part.get().name(), logisimLocation,
                    attributes));
            if (isInterfacePin(component.typeId())) {
                pinLocations.put(component.id(), logisimLocation);
            }
            for (EndPlan plan : plans) {
                GridPoint point = location.plus(plan.offset());
                occupancy.addPoint(point, plan.net());
                endPoints.add(point);
                plan.pin().ifPresent(pin -> ends.put(new PinRef(component.id(), pin), pixels(point)));
            }
            for (EndPlan plan : plans) {
                if (plan.connected()) {
                    join(component, type, plan, location.plus(plan.offset()));
                }
            }
        }

        /** The Logisim part of {@code component}, or empty (noted) when it has none. */
        private Optional<ExportDraft.ExportedPart> exportedPart(Component component) {
            Optional<String> definitionId = SubcircuitTypes.definitionIdOf(component.typeId());
            if (definitionId.isPresent()) {
                return request.subcircuits().apply(definitionId.get()).map(made -> instance(component, made));
            }
            ComponentType type = typeOf(component);
            int widest = type.pins().stream().mapToInt(PinSpec::width).max().orElse(0);
            Component labelled = component.withLabel(request.pinLabels().getOrDefault(component.id(),
                    component.label()));
            ExportDraft draft = new ExportDraft(labelled, type, new ExportContext(request.circuit(), request.main(),
                    pin -> joinedWidth(new PinRef(component.id(), pin)),
                    pin -> connectedPins.contains(new PinRef(component.id(), pin))));
            if (widest > MAXIMUM_WIDTH) {
                draft.leaveOut(LogisimNotes.EXPORT_TOO_WIDE, String.valueOf(widest));
            } else {
                LogisimExportRules.apply(draft);
            }
            notes.addAll(draft.notes());
            return draft.result();
        }

        /** An instance of an exported custom component: turned like the VSim one; each end named by its inner pin. */
        private ExportDraft.ExportedPart instance(Component component, SubcircuitExport made) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("facing", LogisimFacing.EAST.turnedBy(component.rotation()).text());
            if (!component.label().isBlank()) {
                attributes.put("label", component.label());
            }
            Map<String, Integer> endPins = new HashMap<>();
            made.innerPins().forEach((pin, inner) -> endPins.put(inner.x() + "," + inner.y(), pin));
            return new ExportDraft.ExportedPart(LogisimLibraries.SUBCIRCUIT, made.circuit(), attributes, endPins);
        }

        private int joinedWidth(PinRef pin) {
            return joined.netForPin(pin).map(Net::width).orElse(0);
        }

        /** One plan per Logisim end: the VSim pin it stands for (if any) with that pin's net, or a net of its own. */
        private List<EndPlan> plansFor(Component component, ExportDraft.ExportedPart part,
                                       List<LogisimEnd> logisimEnds) {
            List<EndPlan> plans = new ArrayList<>();
            for (LogisimEnd end : logisimEnds) {
                GridPoint offset = new GridPoint(Math.floorDiv(end.point().x(), PIXELS_PER_UNIT),
                        Math.floorDiv(end.point().y(), PIXELS_PER_UNIT));
                Integer pin = end.inVsim() ? part.endPins().get(end.name()) : null;
                if (pin == null) {
                    plans.add(new EndPlan(offset, nextLoneNet--, Optional.empty(), false));
                } else {
                    PinRef reference = new PinRef(component.id(), pin);
                    plans.add(new EndPlan(offset, netOf(reference), Optional.of(pin),
                            connectedPins.contains(reference)));
                }
            }
            return plans;
        }

        /** A connected VSim pin with no Logisim end loses its connection: noted. */
        private void noteMissingPins(Component component, ComponentType type, List<EndPlan> plans) {
            Set<Integer> withEnd = new HashSet<>();
            plans.forEach(plan -> plan.pin().ifPresent(withEnd::add));
            for (int pin = 0; pin < type.pinCount(); pin++) {
                if (!withEnd.contains(pin) && connectedPins.contains(new PinRef(component.id(), pin))) {
                    notes.add(partNote(ImportNote.Kind.APPROXIMATED, LogisimNotes.EXPORT_MISSING_PIN, component,
                            type.pin(pin).name()));
                }
            }
        }

        /**
         * The location whose connected ends come nearest their VSim pins without any end touching another net
         * ({@link PartPlacement}); with none free the part goes apart, below everything drawn so far, noted: never
         * where an end would join another net (its ends are then joined by added wires, or left unconnected and
         * noted).
         */
        private GridPoint bestLocation(Component component, ComponentType type, List<EndPlan> plans) {
            PartPlacement.Choice choice = choice(component, type, plans);
            Optional<PartPlacement.Spot> free = placement.bestFree(List.of(choice));
            if (free.isPresent()) {
                return free.get().position();
            }
            notes.add(partNote(ImportNote.Kind.APPROXIMATED, LogisimNotes.EXPORT_CROWDED_PART, component));
            return placement.apart(choice);
        }

        /**
         * The part's ends, a connected one with its VSim pin as target. A part with nothing connected goes where its
         * first end lands on its pin, or at its VSim position.
         */
        private PartPlacement.Choice choice(Component component, ComponentType type, List<EndPlan> plans) {
            List<PartPlacement.End> ends = new ArrayList<>();
            Optional<GridPoint> first = Optional.empty();
            for (EndPlan plan : plans) {
                Optional<GridPoint> pin = plan.pin().map(index -> type.pinLocation(component, index));
                if (first.isEmpty() && pin.isPresent()) {
                    first = Optional.of(pin.get().translate(-plan.offset().x(), -plan.offset().y()));
                }
                ends.add(new PartPlacement.End(plan.offset(), plan.net(),
                        plan.connected() ? pin : Optional.empty()));
            }
            Optional<GridPoint> unconnected = first;
            GridPoint estimate = PartPlacement.estimate(ends, () -> unconnected.orElse(component.position()));
            return new PartPlacement.Choice(ends, estimate, 0);
        }

        /**
         * An end already where its net is stays; the first end of a net that has no wire is where that net starts;
         * any other gets added wires to a point already joined to its net, or a note when none touches no other net.
         */
        private void join(Component component, ComponentType type, EndPlan plan, GridPoint point) {
            if (occupancy.isAnchored(point, plan.net())) {
                return;
            }
            if (anchoredNets.add(plan.net())) {
                occupancy.anchor(point, plan.net());
                return;
            }
            Optional<List<GridPoint>> corners = router.route(point, plan.net(),
                    candidate -> occupancy.isAnchored(candidate, plan.net()));
            if (corners.isEmpty()) {
                notes.add(partNote(ImportNote.Kind.DIFFERENT, LogisimNotes.EXPORT_UNROUTED_PIN, component,
                        type.pin(plan.pin().orElseThrow()).name()));
                return;
            }
            List<GridPoint> path = corners.get();
            for (int index = 1; index < path.size(); index++) {
                wires.add(new Wire(new ElementId("added"), path.get(index - 1), path.get(index)));
                occupancy.addAnchoredWire(path.get(index - 1), path.get(index), plan.net());
            }
        }

        /** A note about {@code component}: its kind ({@link #partKind}) and label come first, then {@code more}. */
        private ImportNote partNote(ImportNote.Kind kind, String messageKey, Component component, String... more) {
            List<String> arguments = new ArrayList<>(List.of(partKind(component, request.document()),
                    component.label()));
            arguments.addAll(List.of(more));
            return new ImportNote(kind, request.circuit(), Optional.empty(),
                    Optional.of(component.id()), messageKey, arguments);
        }

        // --- Texts and wires ---

        /** A VSim text as Logisim texts, one per line, left-aligned on the baseline under the VSim corner. */
        private void addText(TextLabel label) {
            List<String> lines = label.text().lines().toList();
            for (int line = 0; line < lines.size(); line++) {
                if (lines.get(line).isBlank()) {
                    continue;
                }
                Map<String, String> attributes = Map.of("text", lines.get(line), "halign", "left");
                GridPoint baseline = label.position().translate(0, TEXT_LINE_UNITS * (line + 1));
                components.add(new LogisimComponent(LogisimLibraries.BASE, TEXT, pixels(baseline),
                        DEFAULTS.resolve(LogisimLibraries.BASE, TEXT, attributes, LogisimVersion.LATEST)));
            }
        }

        /**
         * Every wire cut at each wire end and part end inside it (they are of its own net: the placement and the
         * router never let another net's point lie on a wire), in pixels, each piece once.
         */
        private List<LogisimWire> logisimWires() {
            Set<GridPoint> cuts = new HashSet<>(endPoints);
            wires.forEach(wire -> {
                cuts.add(wire.start());
                cuts.add(wire.end());
            });
            Set<LogisimWire> pieces = new LinkedHashSet<>();
            for (Wire wire : wires) {
                GridPoint from = wire.start();
                int steps = Math.abs(wire.end().x() - from.x()) + Math.abs(wire.end().y() - from.y());
                int stepX = Integer.signum(wire.end().x() - from.x());
                int stepY = Integer.signum(wire.end().y() - from.y());
                for (int step = 1; step <= steps; step++) {
                    GridPoint point = wire.start().translate(step * stepX, step * stepY);
                    if (step == steps || cuts.contains(point)) {
                        pieces.add(piece(from, point));
                        from = point;
                    }
                }
            }
            return List.copyOf(pieces);
        }

        /** A wire piece with its ends in a fixed order, so the same piece drawn twice is written once. */
        private static LogisimWire piece(GridPoint first, GridPoint second) {
            boolean ordered = first.x() < second.x() || first.x() == second.x() && first.y() <= second.y();
            return ordered ? new LogisimWire(pixels(first), pixels(second))
                    : new LogisimWire(pixels(second), pixels(first));
        }

        private static LogisimPoint pixels(GridPoint point) {
            return new LogisimPoint(point.x() * PIXELS_PER_UNIT, point.y() * PIXELS_PER_UNIT);
        }
    }

    /**
     * What an export note calls a part before its label: the type id of one of VSim's parts (the report turns it into
     * the part's name), or the name of the custom component an instance is of, never {@code subcircuit:<id>}.
     */
    static String partKind(Component component, CircuitDocument document) {
        return SubcircuitTypes.definitionIdOf(component.typeId()).flatMap(document::definition)
                .map(ComponentDefinition::name).orElse(component.typeId());
    }

    /** Whether a VSim part is a circuit's interface: its Logisim pin names an end of the component's instances. */
    private static boolean isInterfacePin(String typeId) {
        return typeId.equals(StandardComponentTypes.INPUT_PIN) || typeId.equals(StandardComponentTypes.OUTPUT_PIN);
    }
}
