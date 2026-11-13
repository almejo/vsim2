package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.library.DefinitionUpdateSafety;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import cl.almejo.vsim.core.simulation.OutdatedPieceChecks;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.i18n.PinNames;
import cl.almejo.vsim.ui.window.InstanceNames;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** User-facing texts for simulation problems (spec §6.12) and the order in which they are listed and visited. */
public final class ProblemText {

    private static final String SEPARATOR = " · ";

    private static final Comparator<Problem> ORDER = Comparator
            .comparingInt(ProblemText::rank)
            .thenComparingLong(problem -> problem instanceof ShortCircuit shortCircuit ? shortCircuit.sinceTime() : 0)
            .thenComparing(Problem::netId);

    private ProblemText() {
    }

    /** Errors before warnings: short circuits, width mismatches, tunnel errors, then display and tunnel warnings. */
    private static int rank(Problem problem) {
        return switch (problem) {
            case ShortCircuit shortCircuit -> 0;
            case WidthMismatch mismatch -> 1;
            case TunnelProblem tunnel when !tunnel.isWarning() -> 2;
            case DisplayBusOverride display -> 3;
            case TunnelProblem tunnel -> 4;
            case ConnectivityProblem connectivity -> 5;
            case LinkedFileProblem linkedFile -> 6;
        };
    }

    /**
     * Short circuits first, oldest first, then width mismatches, tunnel errors, display warnings and tunnel warnings;
     * ties by net id, so the order is stable.
     */
    public static List<Problem> ordered(List<Problem> problems) {
        return problems.stream().sorted(ORDER).toList();
    }

    /** "AND de 2 entradas 'U3' → 1 · NOT → 0": each driver by type name and label, with the value it imposes. */
    public static String drivers(ShortCircuit shortCircuit, CircuitDocument document) {
        return shortCircuit.drivers().stream()
                .map(driver -> driver(driver, document))
                .collect(Collectors.joining(SEPARATOR));
    }

    /**
     * The width mismatch of {@code net} told by its own pins, for a net the netlist flags before any simulation: every
     * pin but a tunnel's, which takes the net's width and so is never one side of a mismatch (like the engine).
     */
    public static WidthMismatch ownPinsMismatch(Net net, CircuitDocument document, ComponentTypeRegistry registry) {
        return new WidthMismatch(net.id().value(), net.pins().stream()
                .filter(pin -> hasAWidthOfItsOwn(document, registry, pin))
                .map(pin -> new FlatPinRef(pin.componentId().value(), pin.pinIndex()))
                .toList());
    }

    private static boolean hasAWidthOfItsOwn(CircuitDocument document, ComponentTypeRegistry registry, PinRef pin) {
        return document.component(pin.componentId())
                .map(component -> !registry.typeOf(component).pin(pin.pinIndex()).isAdaptiveWidth())
                .orElse(true);
    }

    public static String describe(Problem problem, CircuitDocument document, ComponentTypeRegistry registry) {
        return switch (problem) {
            case ShortCircuit shortCircuit -> Messages.format("problem.shortCircuit", shortCircuit.sinceTime(),
                    drivers(shortCircuit, document));
            case WidthMismatch mismatch -> Messages.format("problem.widthMismatch", mismatch.pins().stream()
                    .map(pin -> pin(pin, document, registry))
                    .collect(Collectors.joining(SEPARATOR)));
            case DisplayBusOverride display -> displayBus(display, document);
            case TunnelProblem tunnel -> tunnel(tunnel, document);
            case ConnectivityProblem connectivity -> connectivity(connectivity, document, registry);
            case LinkedFileProblem linkedFile -> linkedFile(linkedFile, document);
        };
    }

    /** «La memoria «Programa» está enlazada a «rom.hex», que no se encuentra…»: the part by its label or type name. */
    private static String linkedFile(LinkedFileProblem problem, CircuitDocument document) {
        String name = pieceName(problem.elementId(), document);
        return switch (problem.kind()) {
            case MISSING -> Messages.format("problem.memoryFile.missing", name, problem.link());
            case UNREADABLE -> Messages.format("problem.memoryFile.unreadable", name, problem.link(),
                    problem.detail());
        };
    }

    private static String connectivity(ConnectivityProblem problem, CircuitDocument document,
                                       ComponentTypeRegistry registry) {
        return switch (problem.cause()) {
            // Inside a component the canvas rule holds (a loose inner pin or wire end is no problem), so these two
            // are never published; only the library-update check looks for them ({@code everyLooseEnd}).
            case INNER_PIN, LOOSE_WIRE_END -> throw new IllegalStateException("never published: " + problem.cause());
            case UNCONNECTED_INTERFACE -> Messages.format("problem.connectivity.interface",
                    InstanceNames.path(problem.elementId(), document).orElse(problem.elementId()),
                    pinName(problem, document, registry));
            // inside a component's body: what an edit inside a component it contains could not carry (spec §6.2 c)
            case LOOSENED_BY_SHAPE_CHANGE -> InstancePath.isNested(problem.elementId())
                    ? Messages.format("problem.pinUnconnectedInside",
                    InstanceNames.routeText(problem.elementId(), document),
                    PinNames.display(pinName(problem, document, registry)))
                    : Messages.format("problem.connectivity.loosened", pieceName(problem.elementId(), document),
                    PinNames.display(pinName(problem, document, registry)));
            case PIN_PLACED_ELSEWHERE -> Messages.format("problem.keptOldShape",
                    pieceOrRoute(problem.elementId(), document),
                    PinNames.display(pinName(problem, document, registry)));
            case OUTDATED_WOULD_BREAK -> Messages.format("problem.connectivity.outdated",
                    pieceName(problem.elementId(), document), outdatedDefinitionName(problem, document));
            case OUTDATED_CANNOT_UPDATE -> Messages.format("problem.connectivity.outdatedNested",
                    pieceName(problem.elementId(), document), outdatedDefinitionName(problem, document),
                    String.join(", ", DefinitionUpdateSafety.containingNames(document, outdatedDefinitionId(problem))));
            case WIRE_OF_REMOVED_PIN -> Messages.format("problem.removedPinWire",
                    pieceOrRoute(problem.elementId(), document), PinNames.display(removedPinName(problem)));
            case WIRE_AT_PIECE_EDGE -> Messages.format("problem.edgeWire",
                    pieceOrRoute(problem.elementId(), document));
            case OWN_CONTENTS_FOR_NOTHING -> Messages.format("problem.ownContentsForNothing",
                    pieceName(problem.elementId(), document), ownContentsPath(problem));
            case OWN_CONTENTS_OVER_LINK -> Messages.format("problem.ownContentsOverLink",
                    InstanceNames.routeText(problem.elementId(), document));
        };
    }

    private static Optional<ComponentType> pinType(ConnectivityProblem problem, CircuitDocument document,
                                                   ComponentTypeRegistry registry) {
        return InstanceNames.chain(problem.elementId(), document)
                .flatMap(components -> registry.findTypeOf(components.getLast()))
                .filter(type -> problem.pinIndex() >= 0 && problem.pinIndex() < type.pinCount());
    }

    private static String pinName(ConnectivityProblem problem, CircuitDocument document,
                                  ComponentTypeRegistry registry) {
        return pinType(problem, document, registry).map(type -> type.pin(problem.pinIndex()).name())
                .orElse(Integer.toString(problem.pinIndex()));
    }

    /** A piece of the circuit by its name; one inside a component by its route («Sumador › Medio sumador 2»). */
    private static String pieceOrRoute(String hierarchicalId, CircuitDocument document) {
        return InstancePath.isNested(hierarchicalId) ? InstanceNames.routeText(hierarchicalId, document)
                : pieceName(hierarchicalId, document);
    }

    /** A placed piece by its name ({@link InstanceNames#name}): «U5», «ALU», «Multiplexor 2». */
    private static String pieceName(String hierarchicalId, CircuitDocument document) {
        return InstanceNames.name(hierarchicalId, document).orElse(hierarchicalId);
    }

    /** The name the removed pin had: what follows the element id in the net id. */
    private static String removedPinName(ConnectivityProblem problem) {
        String rest = problem.netId().substring(ConnectivityProblem.REMOVED_PIN_PREFIX.length());
        return rest.substring(rest.indexOf(':') + 1);
    }

    /** The memory's path inside the piece a piece's own contents are kept for ({@code c3/c10}). */
    private static String ownContentsPath(ConnectivityProblem problem) {
        return problem.netId().substring(ConnectivityProblem.OWN_CONTENTS_PREFIX.length());
    }

    private static String outdatedDefinitionId(ConnectivityProblem problem) {
        return problem.netId().substring(OutdatedPieceChecks.VERSION_PREFIX.length());
    }

    private static String outdatedDefinitionName(ConnectivityProblem problem, CircuitDocument document) {
        String definitionId = outdatedDefinitionId(problem);
        return document.definition(definitionId).map(ComponentDefinition::name).orElse(definitionId);
    }

    /**
     * «La salida «CLK» no tiene entrada.»: one sentence per kind, naming the tunnel. A problem inside a user component
     * says first which instance it is in: «Dentro de U1 (Contador): …».
     */
    private static String tunnel(TunnelProblem problem, CircuitDocument document) {
        String sentence = tunnelSentence(problem);
        Optional<String> nested = problem.tunnels().stream().filter(InstancePath::isNested).findFirst();
        if (nested.isEmpty()) {
            return sentence;
        }
        String instanceId = nested.get().substring(0, nested.get().lastIndexOf('/'));
        return Messages.format("problem.tunnel.inside", InstanceNames.path(instanceId, document).orElse(instanceId),
                sentence);
    }

    private static String tunnelSentence(TunnelProblem problem) {
        return switch (problem.kind()) {
            case DUPLICATE_ENTRANCE -> Messages.format("problem.tunnel.duplicateEntrance", problem.tunnels().size(),
                    problem.name());
            case EXIT_WITHOUT_ENTRANCE -> Messages.format("problem.tunnel.exitWithoutEntrance", problem.name());
            case ENTRANCE_WITHOUT_EXITS -> Messages.format("problem.tunnel.entranceWithoutExits", problem.name());
            case EXIT_DRIVEN -> Messages.format("problem.tunnel.exitDriven", problem.name());
            case LONE_TUNNEL -> Messages.format("problem.tunnel.lone", problem.name());
        };
    }

    /** "Display 'Total': se usa el bus; …", by label; a display inside a template by its path. */
    private static String displayBus(DisplayBusOverride display, CircuitDocument document) {
        if (InstancePath.isNested(display.componentId())) {
            return Messages.format("problem.displayBusNested",
                    InstanceNames.path(display.componentId(), document).orElse(display.componentId()));
        }
        String label = document.component(new ElementId(display.componentId())).map(Component::label).orElse("");
        return label.isBlank() ? Messages.text("problem.displayBus")
                : Messages.format("problem.displayBusLabeled", label);
    }

    private static String driver(DriverRef driver, CircuitDocument document) {
        String value = driver.driven().toString();
        if (InstancePath.isNested(driver.componentId())) {
            return Messages.format("problem.driver",
                    InstanceNames.path(driver.componentId(), document).orElse(driver.componentId()), value);
        }
        Optional<Component> component = document.component(new ElementId(driver.componentId()));
        if (component.isEmpty()) {
            return Messages.format("problem.driver", driver.componentId(), value);
        }
        String name = InstanceNames.typeName(component.get(), document);
        String label = component.get().label();
        return label.isBlank()
                ? Messages.format("problem.driver", pieceName(driver.componentId(), document), value)
                : Messages.format("problem.driverLabeled", name, label, value);
    }

    private static String pin(FlatPinRef pin, CircuitDocument document, ComponentTypeRegistry registry) {
        Optional<List<Component>> chain = InstanceNames.chain(pin.componentId(), document);
        if (chain.isEmpty()) {
            return pin.componentId();
        }
        Component found = chain.get().get(chain.get().size() - 1);
        Optional<ComponentType> type = registry.findTypeOf(found);
        if (type.isEmpty() || pin.pinIndex() < 0 || pin.pinIndex() >= type.get().pinCount()) {
            return pin.componentId();
        }
        int width = type.get().pin(pin.pinIndex()).width();
        if (InstancePath.isNested(pin.componentId())) {
            return Messages.format("problem.pin",
                    InstanceNames.path(pin.componentId(), document).orElse(pin.componentId()), width);
        }
        String name = InstanceNames.typeName(found, document);
        return found.label().isBlank()
                ? Messages.format("problem.pin", pieceName(pin.componentId(), document), width)
                : Messages.format("problem.pinLabeled", name, found.label(), width);
    }
}
