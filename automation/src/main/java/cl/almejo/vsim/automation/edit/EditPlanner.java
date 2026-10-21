package cl.almejo.vsim.automation.edit;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.automation.edit.EditOperation.AddGuide;
import cl.almejo.vsim.automation.edit.EditOperation.AddNote;
import cl.almejo.vsim.automation.edit.EditOperation.AddWire;
import cl.almejo.vsim.automation.edit.EditOperation.Connect;
import cl.almejo.vsim.automation.edit.EditOperation.CreateComponent;
import cl.almejo.vsim.automation.edit.EditOperation.Delete;
import cl.almejo.vsim.automation.edit.EditOperation.MakeFrame;
import cl.almejo.vsim.automation.edit.EditOperation.Move;
import cl.almejo.vsim.automation.edit.EditOperation.PlaceInstance;
import cl.almejo.vsim.automation.edit.EditOperation.PlacePart;
import cl.almejo.vsim.automation.edit.EditOperation.RemoveGuide;
import cl.almejo.vsim.automation.edit.EditOperation.SetLabel;
import cl.almejo.vsim.automation.edit.EditOperation.SetParameters;
import cl.almejo.vsim.automation.edit.EditOperation.TidyWires;
import cl.almejo.vsim.automation.edit.EditReceipt.ConnectionReport;
import cl.almejo.vsim.automation.edit.EditReceipt.CreatedComponent;
import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.command.MoveWithWiresCommand;
import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.command.RemoveElementsCommand;
import cl.almejo.vsim.core.command.SetGuidesCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.command.TidyWiresCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.ConvertedSelection;
import cl.almejo.vsim.core.library.DefinitionBuilder;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.DefinitionIds;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.PinBridges;
import cl.almejo.vsim.core.library.PinOrder;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.library.TestParts;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.wiring.TidyOptions;
import cl.almejo.vsim.core.wiring.TidyPlan;
import cl.almejo.vsim.core.wiring.WireTidy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Carries out an AI edit call on a {@link CircuitDocument#copy() candidate copy} of the circuit (A§6.2): every
 * operation in order, with the commands the user's own actions use, resolving aliases, pins, widths and routes. The
 * first operation that cannot be done throws an {@link AutomationException} naming it, and the circuit itself was
 * never touched; when all succeed, the candidate is the circuit as the call leaves it, ready to be written in one
 * step.
 */
final class EditPlanner {

    /** The most operations one call may carry: a whole stage is a few dozen; more is a mistake or a runaway. */
    static final int MAXIMUM_OPERATIONS = 500;

    /** Targets that name what an earlier operation of the call made start with this. */
    static final String ALIAS_PREFIX = "new:";

    /** Grid units between a frame and what it wraps, as «Enmarcar selección» leaves them. */
    private static final int FRAME_MARGIN = 2;
    /** Grid units the frame's title bar takes above the margin. */
    private static final int FRAME_TITLE_ROOM = 2;

    /** What the call leaves: the candidate circuit and what the receipt reports. */
    record PlannedEdit(CircuitDocument candidate, Map<String, String> aliases, List<ConnectionReport> connections,
                       List<CreatedComponent> components, List<String> warnings) {
    }

    private final CircuitDocument original;
    private final ComponentTypeRegistry liveRegistry;
    private final List<DefinitionBundle> library;
    private final CircuitDocument candidate;
    private final ComponentTypeRegistry registry;
    private final NetlistCompiler compiler;
    private final ConnectRouting routing;
    private final Map<String, ElementId> elementAliases = new LinkedHashMap<>();
    private final Map<String, String> definitionAliases = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final List<ConnectionReport> connections = new ArrayList<>();
    private final List<CreatedComponent> components = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private int operationIndex;

    private EditPlanner(CircuitDocument original, ComponentTypeRegistry liveRegistry,
                        List<DefinitionBundle> library) {
        this.original = original;
        this.liveRegistry = liveRegistry;
        this.library = List.copyOf(library);
        this.candidate = original.copy();
        // The live registry resolves the live circuit's components; the candidate needs its own (it may add one).
        this.registry = liveRegistry.copy();
        this.registry.addResolver(SubcircuitTypes.resolverFor(candidate));
        this.compiler = new NetlistCompiler(registry);
        this.routing = new ConnectRouting(candidate, registry, this::reference);
    }

    /**
     * Carries out {@code operations} on a copy of {@code document}; {@code registry} is the circuit's own. Neither is
     * changed. {@code library} is the user's library: an instance of one of its components brings the component's
     * definitions into the circuit, as placing it from the palette does.
     *
     * @throws AutomationException for the first operation that cannot be done, with its index in the recovery data
     */
    static PlannedEdit plan(CircuitDocument document, ComponentTypeRegistry registry,
                            List<EditOperation> operations) {
        return plan(document, registry, List.of(), operations);
    }

    static PlannedEdit plan(CircuitDocument document, ComponentTypeRegistry registry, List<DefinitionBundle> library,
                            List<EditOperation> operations) {
        if (operations.isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "An edit needs at least one operation");
        }
        if (operations.size() > MAXIMUM_OPERATIONS) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "An edit carries at most "
                    + MAXIMUM_OPERATIONS + " operations; split it", Map.of("maximum", MAXIMUM_OPERATIONS));
        }
        EditPlanner planner = new EditPlanner(document, registry, library);
        for (EditOperation operation : operations) {
            planner.carryOut(operation);
            planner.operationIndex++;
        }
        planner.warnAboutNewWidthMismatches();
        return new PlannedEdit(planner.candidate, planner.aliases, planner.connections, planner.components,
                planner.warnings);
    }

    private void carryOut(EditOperation operation) {
        try {
            switch (operation) {
                case PlacePart placePart -> placePart(placePart);
                case SetParameters setParameters -> setParameters(setParameters);
                case SetLabel setLabel -> setLabel(setLabel);
                case Connect connect -> connect(connect);
                case AddWire addWire -> addWire(addWire);
                case Move move -> move(move);
                case Delete delete -> delete(delete);
                case MakeFrame makeFrame -> makeFrame(makeFrame);
                case AddNote addNote -> addNote(addNote);
                case AddGuide addGuide -> addGuide(addGuide);
                case RemoveGuide removeGuide -> removeGuide(removeGuide);
                case CreateComponent createComponent -> createComponent(createComponent);
                case PlaceInstance placeInstance -> placeInstance(placeInstance);
                case TidyWires tidyWires -> tidyWires(tidyWires);
            }
        } catch (AutomationException refusal) {
            throw located(operation, refusal.error(), refusal.getMessage(), refusal.recovery());
        } catch (IllegalArgumentException invalid) {
            throw located(operation, AutomationError.INVALID_PARAMETER, invalid.getMessage(), Map.of());
        }
    }

    private AutomationException located(EditOperation operation, AutomationError error, String message,
                                        Map<String, Object> recovery) {
        Map<String, Object> located = new LinkedHashMap<>(recovery);
        located.put("operation", operationIndex);
        located.put("kind", operation.kind());
        return new AutomationException(error, "Operation " + operationIndex + " (" + operation.kind()
                + ", counting from 0): "
                + message + ". Nothing was changed", located);
    }

    // --- parts ---

    private void placePart(PlacePart operation) {
        String alias = newAlias(operation.alias());
        bringFromLibrary(operation.typeId());
        ComponentType type = registry.find(operation.typeId())
                .orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE, "There is no component "
                        + "type " + operation.typeId() + "; describe the components to see the types"));
        requireKnownParameters(type, operation.parameters());
        Component component = type.newComponent(candidate.newComponentId(),
                new GridPoint(operation.x(), operation.y()), operation.rotation(), operation.parameters(),
                operation.label());
        place(component, alias);
    }

    private void placeInstance(PlaceInstance operation) {
        String alias = newAlias(operation.alias());
        ComponentDefinition definition = requireDefinition(operation.definition());
        Component instance = new Component(candidate.newComponentId(), SubcircuitTypes.typeIdOf(definition.id()),
                new GridPoint(operation.x(), operation.y()), operation.rotation(), Map.of(), "");
        place(instance, alias);
    }

    private void place(Component component, String alias) {
        new AddElementsCommand(List.of(component)).apply(candidate);
        nameElement(alias, component.id());
        warnAboutTouchingPins(component);
    }

    /** A part set down on a wire or on another pin joins it at once; the AI should know. */
    private void warnAboutTouchingPins(Component component) {
        Netlist netlist = compiler.compile(candidate);
        ComponentType type = registry.typeOf(component);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            PinRef pin = new PinRef(component.id(), pinIndex);
            Net net = netlist.netForPin(pin).orElseThrow();
            List<PinReference> others = routing.references(net).stream()
                    .filter(other -> !other.elementId().equals(component.id().value()))
                    .toList();
            if (!others.isEmpty() || !net.wires().isEmpty()) {
                warnings.add(at() + component.id() + "." + type.pin(pinIndex).name() + " was set down touching "
                        + others + " " + net.wires().stream().map(ElementId::value).sorted().toList());
            }
        }
    }

    private void setParameters(SetParameters operation) {
        Component component = requireComponent(operation.target());
        ComponentType type = registry.require(component.typeId());
        requireKnownParameters(type, operation.parameters());
        Map<String, Object> merged = new HashMap<>(component.parameters());
        merged.putAll(operation.parameters());
        new ChangeParametersCommand(component.id(), type, merged).apply(candidate);
    }

    private static void requireKnownParameters(ComponentType type, Map<String, Object> parameters) {
        List<String> known = type.parameters().stream().map(ParameterSpec::name).toList();
        for (String name : parameters.keySet()) {
            if (!known.contains(name)) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, type.id() + " has no parameter "
                        + name + "; it has " + known, Map.of("parameters", known));
            }
        }
    }

    private void setLabel(SetLabel operation) {
        DocumentElement element = requireElement(operation.target());
        if (element instanceof Wire) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, element.id() + " is a wire: wires have "
                    + "no label");
        }
        new SetLabelCommand(element.id(), operation.text()).apply(candidate);
    }

    // --- wires ---

    private void connect(Connect operation) {
        PinRef from = resolvePin(operation.from());
        PinRef to = resolvePin(operation.to());
        if (from.equals(to)) {
            throw new AutomationException(AutomationError.INVALID_PIN, "A pin cannot be connected to itself");
        }
        ConnectRouting.Outcome outcome = routing.connect(from, to, operation.via());
        outcome.warnings().forEach(warning -> warnings.add(at() + warning));
        connections.add(new ConnectionReport(operationIndex, reference(from), reference(to), outcome.wireIds(),
                outcome.joinedNets().stream().map(routing::references).toList(), outcome.alreadyConnected()));
    }

    private void addWire(AddWire operation) {
        List<GridPoint> points = operation.points();
        if (points.size() < 2) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A wire needs at least two points");
        }
        for (int index = 1; index < points.size(); index++) {
            GridPoint previous = points.get(index - 1);
            GridPoint next = points.get(index);
            boolean straight = previous.x() == next.x() ^ previous.y() == next.y();
            if (!straight) {
                throw new AutomationException(AutomationError.INVALID_PARAMETER, "Each stretch of a wire goes straight "
                        + "across or down: " + previous + " to " + next + " does not");
            }
            new AddWireCommand(new Wire(candidate.newWireId(), previous, next)).apply(candidate);
        }
    }

    private void tidyWires(TidyWires operation) {
        Set<ElementId> scope = new LinkedHashSet<>();
        if (operation.targets().isEmpty()) {
            candidate.wires().forEach(wire -> scope.add(wire.id()));
        }
        for (String target : operation.targets()) {
            DocumentElement element = requireElement(target);
            if (element instanceof Wire) {
                scope.add(element.id());
            } else if (element instanceof Frame frame) {
                FrameContents.of(candidate, registry, frame).stream()
                        .filter(id -> candidate.wire(id).isPresent())
                        .forEach(scope::add);
            }
        }
        TidyPlan plan = WireTidy.plan(candidate, registry, scope, TidyOptions.DEFAULTS);
        if (plan.isEmpty()) {
            warnings.add(at() + "the wires were tidy already");
            return;
        }
        new TidyWiresCommand(plan).apply(candidate);
    }

    // --- moving and deleting ---

    private void move(Move operation) {
        Set<ElementId> ids = FrameContents.withLockedBlocks(candidate, registry, requireTargets(operation.targets()));
        if (operation.deltaX() == 0 && operation.deltaY() == 0) {
            warnings.add(at() + "a move by (0, 0) moves nothing");
            return;
        }
        MoveWithWiresCommand.of(candidate, registry, ids, operation.deltaX(), operation.deltaY()).apply(candidate);
    }

    private void delete(Delete operation) {
        Set<ElementId> ids = FrameContents.withLockedBlocks(candidate, registry, requireTargets(operation.targets()));
        new RemoveElementsCommand(ids).apply(candidate);
    }

    // --- frames, notes and guides ---

    private void makeFrame(MakeFrame operation) {
        String alias = newAlias(operation.alias());
        Frame frame = operation.bounds()
                .map(bounds -> new Frame(candidate.newFrameId(), new GridPoint(bounds.x(), bounds.y()), bounds.width(),
                        bounds.height(), operation.title(), FrameTint.NONE, operation.locked()))
                .orElseGet(() -> frameAround(operation));
        new CreateFrameCommand(frame).apply(candidate);
        nameElement(alias, frame.id());
    }

    private Frame frameAround(MakeFrame operation) {
        if (operation.targets().isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A frame needs targets to wrap or "
                    + "its bounds");
        }
        List<DocumentElement> elements = requireTargets(operation.targets()).stream()
                .map(id -> candidate.element(id).orElseThrow())
                .toList();
        Bounds around = ElementBounds.union(elements, registry).orElseThrow();
        int top = FRAME_MARGIN + FRAME_TITLE_ROOM;
        return new Frame(candidate.newFrameId(), new GridPoint(around.x() - FRAME_MARGIN, around.y() - top),
                Math.max(Frame.MINIMUM_WIDTH, around.width() + 2 * FRAME_MARGIN),
                Math.max(Frame.MINIMUM_HEIGHT, around.height() + FRAME_MARGIN + top),
                operation.title(), FrameTint.NONE, operation.locked());
    }

    private void addNote(AddNote operation) {
        String alias = newAlias(operation.alias());
        if (operation.text().isBlank()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A note needs a text");
        }
        TextLabel note = new TextLabel(candidate.newLabelId(), new GridPoint(operation.x(), operation.y()),
                operation.text());
        new AddElementsCommand(List.of(note)).apply(candidate);
        nameElement(alias, note.id());
    }

    private void addGuide(AddGuide operation) {
        Guide guide = new Guide(operation.orientation(), operation.position());
        SetGuidesCommand.adding(candidate, guide).ifPresentOrElse(command -> command.apply(candidate),
                () -> warnings.add(at() + "that guide was there already"));
    }

    private void removeGuide(RemoveGuide operation) {
        Guide guide = new Guide(operation.orientation(), operation.position());
        SetGuidesCommand command = SetGuidesCommand.removing(candidate, guide)
                .orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE, "There is no "
                        + operation.orientation().name().toLowerCase(Locale.ROOT) + " guide at "
                        + operation.position()));
        command.apply(candidate);
    }

    // --- user components ---

    /** «Crear componente desde selección» with every test part turned into pins, as the dialog proposes. */
    private void createComponent(CreateComponent operation) {
        String name = requireFreeName(operation.name());
        String alias = newAlias(operation.alias());
        // The AI names parts; the wires between them come along, as with the user's rectangle (Task 7).
        InnerWires.Scope scope = InnerWires.of(candidate, registry, requireTargets(operation.targets()));
        Set<ElementId> ids = new LinkedHashSet<>(scope.elements());
        if (!scope.mixedNetPins().isEmpty()) {
            warnings.add(at() + "these pins share a net with parts outside the targets and no wire of it was "
                    + "targeted, so they stay unconnected inside the component (target those wires to keep them): "
                    + scope.mixedNetPins().stream().map(this::reference)
                    .map(pin -> pin.elementId() + "." + pin.pinName()).toList());
        }
        List<TestParts.TestPart> testParts = TestParts.of(candidate, ids, registry);
        if (testParts.isEmpty() && !DefinitionBuilder.hasInterfacePins(candidate, ids)) {
            throw new AutomationException(AutomationError.INVALID_REFERENCE, "The targets have no interface pins "
                    + "(input-pin, output-pin) nor test parts (switches, LEDs…) to become the component's pins");
        }
        Set<ElementId> convertedParts = new LinkedHashSet<>();
        testParts.forEach(part -> convertedParts.add(part.id()));
        ConvertedSelection selection = ConvertedSelection.of(candidate, ids, convertedParts, Map.of(), registry);
        Map<ElementId, String> renamed = renamedPins(operation.pinNames(), selection);
        Set<String> takenIds = new LinkedHashSet<>();
        candidate.definitions().forEach(definition -> takenIds.add(definition.id()));
        // A library component with the same id would make the new one look like an outdated copy of it.
        library.forEach(bundle -> bundle.all().forEach(definition -> takenIds.add(definition.id())));
        DefinitionBundle bundle = selection.definition(DefinitionIds.slug(name, takenIds), name,
                selection.defaultPinOrder(), renamed, registry);
        new PutDefinitionsCommand(bundle.all()).apply(candidate);
        Optional<String> instanceId = operation.replaceSelection() ? Optional.of(replace(selection, bundle))
                : Optional.empty();
        String definitionId = bundle.root().id();
        if (!alias.isEmpty()) {
            definitionAliases.put(alias, definitionId);
            aliases.put(alias, definitionId);
        }
        components.add(new CreatedComponent(operationIndex, operation.alias().map(EditPlanner::bare), definitionId,
                bundle.root().name(), pinNames(bundle.root(), PinDirection.INPUT),
                pinNames(bundle.root(), PinDirection.OUTPUT), instanceId));
    }

    /** Replaces the selection by one instance of the new definition, bridged to what was connected outside. */
    private String replace(ConvertedSelection selection, DefinitionBundle bundle) {
        GridPoint origin = selection.instanceOrigin(registry);
        PinBridges.Bridging bridging = selection.bridges(bundle.root(), origin, registry);
        if (!bridging.blockedPins().isEmpty()) {
            throw new AutomationException(AutomationError.ROUTE_REJECTED, "The instance's pins "
                    + bridging.blockedPins() + " would land on something outside the selection; create it without "
                    + "replacing, or move what is in the way", Map.of("blockedPins", bridging.blockedPins()));
        }
        List<String> splitTunnels = Tunnels.namesSplitBy(candidate, selection.replacedIds());
        Component instance = new Component(candidate.newComponentId(), SubcircuitTypes.typeIdOf(bundle.root().id()),
                origin, Rotation.R0, Map.of(), "");
        if (!selection.replacedIds().isEmpty()) {
            new RemoveElementsCommand(selection.replacedIds()).apply(candidate);
        }
        List<DocumentElement> added = new ArrayList<>(List.of(instance));
        added.addAll(bridging.wires());
        new AddElementsCommand(added).apply(candidate);
        if (!bridging.unconnectedPins().isEmpty()) {
            warnings.add(at() + "pins left unconnected outside: " + bridging.unconnectedPins());
        }
        if (!splitTunnels.isEmpty()) {
            warnings.add(at() + "tunnel names now cut by the component's boundary: " + splitTunnels);
        }
        return instance.id().value();
    }

    private String requireFreeName(String requested) {
        String name = requested.strip();
        if (name.isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "A component needs a name");
        }
        boolean taken = candidate.definitions().stream().anyMatch(definition -> definition.name().equalsIgnoreCase(name));
        if (taken) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The circuit has a component named "
                    + name + " already; choose another name");
        }
        return name;
    }

    /** The AI's new pin names by the pin they rename: an interface pin, or a test part that gives one pin. */
    private Map<ElementId, String> renamedPins(Map<String, String> pinNames, ConvertedSelection selection) {
        PinOrder order = selection.defaultPinOrder();
        Map<ElementId, String> renamed = new LinkedHashMap<>();
        pinNames.forEach((target, newName) -> {
            ElementId id = requireElement(target).id();
            ConvertedSelection.PartPins partPins = selection.parts().get(id);
            if (order.inputs().contains(id) || order.outputs().contains(id)) {
                renamed.put(id, newName);
            } else if (partPins != null && partPins.pins().size() == 1) {
                renamed.put(partPins.pins().getFirst(), newName);
            } else {
                throw new AutomationException(AutomationError.INVALID_PIN, target + " does not give one pin of the "
                        + "component, so it cannot be renamed");
            }
        });
        return renamed;
    }

    private static List<String> pinNames(ComponentDefinition definition, PinDirection direction) {
        return definition.pinout().stream()
                .map(DefinitionPin::spec)
                .filter(spec -> spec.direction() == direction)
                .map(PinSpec::name)
                .toList();
    }

    private ComponentDefinition requireDefinition(String reference) {
        if (reference.startsWith(ALIAS_PREFIX)) {
            String definitionId = definitionAliases.get(bare(reference));
            if (definitionId == null) {
                throw new AutomationException(AutomationError.INVALID_REFERENCE, "No component was created as "
                        + reference + " earlier in this call");
            }
            return candidate.definition(definitionId).orElseThrow();
        }
        Optional<ComponentDefinition> byIdOrName = byIdOrName(candidate.definitions(), reference);
        if (byIdOrName.isPresent()) {
            return byIdOrName.get();
        }
        Optional<DefinitionBundle> fromLibrary = libraryBundle(reference);
        if (fromLibrary.isPresent()) {
            new PutDefinitionsCommand(fromLibrary.get().all()).apply(candidate);
            return candidate.definition(fromLibrary.get().root().id()).orElseThrow();
        }
        List<String> known = new ArrayList<>(candidate.definitions().stream().map(ComponentDefinition::name).toList());
        library.forEach(bundle -> known.add(bundle.root().name()));
        throw new AutomationException(AutomationError.INVALID_REFERENCE, "Neither the circuit nor the user's library "
                + "has a component " + reference, Map.of("components", known.stream().distinct().toList()));
    }

    /** By id, then by exact name, then by name ignoring case. */
    private static Optional<ComponentDefinition> byIdOrName(List<ComponentDefinition> definitions, String reference) {
        return definitions.stream().filter(definition -> definition.id().equals(reference)).findFirst()
                .or(() -> definitions.stream().filter(definition -> definition.name().equals(reference)).findFirst())
                .or(() -> definitions.stream()
                        .filter(definition -> definition.name().equalsIgnoreCase(reference)).findFirst());
    }

    private Optional<DefinitionBundle> libraryBundle(String reference) {
        return byIdOrName(library.stream().map(DefinitionBundle::root).toList(), reference)
                .flatMap(root -> library.stream().filter(bundle -> bundle.root() == root).findFirst());
    }

    /**
     * A {@code subcircuit:<id>} type the circuit lacks but the user's library has: its definitions come into the
     * candidate first, as the palette brings them when the user places one.
     */
    private void bringFromLibrary(String typeId) {
        if (registry.find(typeId).isPresent()) {
            return;
        }
        SubcircuitTypes.definitionIdOf(typeId)
                .flatMap(definitionId -> library.stream()
                        .filter(bundle -> bundle.root().id().equals(definitionId)).findFirst())
                .ifPresent(bundle -> new PutDefinitionsCommand(bundle.all()).apply(candidate));
    }

    // --- references ---

    /** The alias an operation gives (without {@code new:}), or "" when it gives none. */
    private String newAlias(Optional<String> alias) {
        if (alias.isEmpty()) {
            return "";
        }
        String bare = bare(alias.get());
        if (bare.isBlank()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "An alias is never blank");
        }
        if (aliases.containsKey(bare)) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The alias " + bare + " is used twice "
                    + "in this call");
        }
        return bare;
    }

    private void nameElement(String alias, ElementId id) {
        if (!alias.isEmpty()) {
            elementAliases.put(alias, id);
            aliases.put(alias, id.value());
        }
    }

    private static String bare(String alias) {
        return alias.startsWith(ALIAS_PREFIX) ? alias.substring(ALIAS_PREFIX.length()) : alias;
    }

    private Set<ElementId> requireTargets(List<String> targets) {
        if (targets.isEmpty()) {
            throw new AutomationException(AutomationError.INVALID_PARAMETER, "The operation needs targets");
        }
        Set<ElementId> ids = new LinkedHashSet<>();
        targets.forEach(target -> ids.add(requireElement(target).id()));
        return ids;
    }

    private DocumentElement requireElement(String target) {
        Objects.requireNonNull(target, "target");
        if (target.startsWith(ALIAS_PREFIX)) {
            ElementId id = elementAliases.get(bare(target));
            if (id == null) {
                throw new AutomationException(AutomationError.INVALID_REFERENCE, "Nothing was placed as " + target
                        + " earlier in this call", Map.of("aliases", List.copyOf(elementAliases.keySet())));
            }
            return candidate.element(id).orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE,
                    target + " was removed earlier in this call"));
        }
        Optional<DocumentElement> element = target.isBlank() || target.contains("/") ? Optional.empty()
                : candidate.element(new ElementId(target));
        return element.orElseThrow(() -> new AutomationException(AutomationError.INVALID_REFERENCE, "The circuit has "
                + "no element " + target + " (removed, or merged into another wire?); read the circuit again"));
    }

    private Component requireComponent(String target) {
        DocumentElement element = requireElement(target);
        if (element instanceof Component component) {
            return component;
        }
        throw new AutomationException(AutomationError.INVALID_REFERENCE, target + " is not a part");
    }

    private PinRef resolvePin(PinTarget target) {
        Component component = requireComponent(target.target());
        ComponentType type = registry.typeOf(component);
        List<String> pins = IntStream.range(0, type.pinCount())
                .mapToObj(index -> type.pin(index).name() + "#" + index)
                .toList();
        OptionalInt index = target.index();
        if (target.pinName().isEmpty()) {
            // No name: the index is the pin's place among the part's pins.
            if (index.isEmpty() || index.getAsInt() < 0 || index.getAsInt() >= type.pinCount()) {
                throw invalidPin(component, target, pins);
            }
            return new PinRef(component.id(), index.getAsInt());
        }
        String name = target.pinName().get();
        List<Integer> matches = IntStream.range(0, type.pinCount())
                .filter(candidateIndex -> type.pin(candidateIndex).name().equals(name))
                .boxed()
                .toList();
        // With a name, the index says which pin of that name (as in PinReference); a repeated name needs it.
        int occurrence = index.isPresent() ? index.getAsInt() : matches.size() == 1 ? 0 : -1;
        if (occurrence < 0 || occurrence >= matches.size()) {
            throw invalidPin(component, target, pins);
        }
        return new PinRef(component.id(), matches.get(occurrence));
    }

    private static AutomationException invalidPin(Component component, PinTarget target, List<String> pins) {
        String named = target.pinName().orElse("") + target.index().stream().mapToObj(index -> "#" + index)
                .findFirst().orElse("");
        return new AutomationException(AutomationError.INVALID_PIN, component.id() + " (" + component.typeId()
                + ") has no single pin " + named + "; its pins are " + pins, Map.of("pins", pins));
    }

    /** {@code pin} as the AI names it, with the pins the candidate gives its part now. */
    private PinReference reference(PinRef pin) {
        Component component = candidate.component(pin.componentId()).orElseThrow();
        return PinReference.of(pin.componentId().value(), registry.typeOf(component), pin.pinIndex());
    }

    private String at() {
        return "operation " + operationIndex + ": ";
    }

    /** Nets that join pins of different widths and did not before: legal to draw, wrong to simulate. */
    private void warnAboutNewWidthMismatches() {
        Set<Set<PinRef>> before = new LinkedHashSet<>();
        new NetlistCompiler(liveRegistry).compile(original).nets().stream()
                .filter(Net::widthMismatch)
                .forEach(net -> before.add(net.pins()));
        for (Net net : compiler.compile(candidate).nets()) {
            if (net.widthMismatch() && !before.contains(net.pins())) {
                warnings.add("a net now joins pins of different widths: " + routing.references(net));
            }
        }
    }
}
