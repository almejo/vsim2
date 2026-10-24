package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinDirection;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.OutdatedPieceChecks;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** User-facing texts for simulation problems (spec §6.12) and the order in which they are listed and visited. */
public final class ProblemText {

    private static final String SEPARATOR = " · ";
    private static final String PATH_SEPARATOR = " › ";

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
        };
    }

    private static String connectivity(ConnectivityProblem problem, CircuitDocument document,
                                       ComponentTypeRegistry registry) {
        return switch (problem.cause()) {
            case INNER_PIN -> innerPin(problem, document, registry);
            case LOOSE_WIRE_END -> looseWire(problem, document);
            case UNCONNECTED_INTERFACE -> Messages.format("problem.connectivity.interface",
                    path(problem.elementId(), document).orElse(problem.elementId()),
                    pinName(problem, document, registry));
            case LOOSENED_BY_SHAPE_CHANGE -> Messages.format("problem.connectivity.loosened",
                    pieceName(problem.elementId(), document), pinName(problem, document, registry));
            case OUTDATED_WOULD_BREAK -> Messages.format("problem.connectivity.outdated",
                    pieceName(problem.elementId(), document), outdatedDefinitionName(problem, document));
        };
    }

    private static String looseWire(ConnectivityProblem problem, CircuitDocument document) {
        String location = InstancePath.isNested(problem.elementId())
                ? path(problem.elementId().substring(0, problem.elementId().lastIndexOf('/')), document)
                        .orElse(problem.elementId())
                : Messages.text("problem.connectivity.circuit");
        return Messages.format("problem.connectivity.looseWire", location,
                problem.position().x(), problem.position().y());
    }

    /** «Padre › A1 › AND: entrada B desconectada»: the whole path, then the pin by its direction and name. */
    private static String innerPin(ConnectivityProblem problem, CircuitDocument document,
                                   ComponentTypeRegistry registry) {
        String componentPath = path(problem.elementId(), document).orElse(problem.elementId());
        String pinName = pinName(problem, document, registry);
        Optional<PinDirection> direction = pinType(problem, document, registry)
                .map(type -> type.pin(problem.pinIndex()).direction());
        return switch (direction.orElse(PinDirection.BIDIRECTIONAL)) {
            case INPUT -> Messages.format("problem.connectivity.pin.input", componentPath, pinName);
            case OUTPUT -> Messages.format("problem.connectivity.pin.output", componentPath, pinName);
            case BIDIRECTIONAL -> Messages.format("problem.connectivity.pin", componentPath, pinName);
        };
    }

    private static Optional<ComponentType> pinType(ConnectivityProblem problem, CircuitDocument document,
                                                   ComponentTypeRegistry registry) {
        return chain(problem.elementId(), document)
                .flatMap(components -> registry.findTypeOf(components.getLast()))
                .filter(type -> problem.pinIndex() >= 0 && problem.pinIndex() < type.pinCount());
    }

    private static String pinName(ConnectivityProblem problem, CircuitDocument document,
                                  ComponentTypeRegistry registry) {
        return pinType(problem, document, registry).map(type -> type.pin(problem.pinIndex()).name())
                .orElse(Integer.toString(problem.pinIndex()));
    }

    /** A placed piece by its label, or by its type or definition name when it has none: «U5», «ALU». */
    private static String pieceName(String hierarchicalId, CircuitDocument document) {
        return chain(hierarchicalId, document).map(List::getLast)
                .map(component -> component.label().isBlank() ? typeName(component, document) : component.label())
                .orElse(hierarchicalId);
    }

    private static String outdatedDefinitionName(ConnectivityProblem problem, CircuitDocument document) {
        String definitionId = problem.netId().substring(OutdatedPieceChecks.VERSION_PREFIX.length());
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
        return Messages.format("problem.tunnel.inside", path(instanceId, document).orElse(instanceId), sentence);
    }

    private static String tunnelSentence(TunnelProblem problem) {
        return switch (problem.kind()) {
            case DUPLICATE_ENTRANCE -> Messages.format("problem.tunnel.duplicateEntrance", problem.tunnels().size(),
                    problem.name());
            case EXIT_WITHOUT_ENTRANCE -> Messages.format("problem.tunnel.exitWithoutEntrance", problem.name());
            case ENTRANCE_WITHOUT_EXITS -> Messages.format("problem.tunnel.entranceWithoutExits", problem.name());
            case EXIT_DRIVEN -> Messages.format("problem.tunnel.exitDriven", problem.name());
        };
    }

    /** "Display 'Total': se usa el bus; …", by label; a display inside a template by its path. */
    private static String displayBus(DisplayBusOverride display, CircuitDocument document) {
        if (InstancePath.isNested(display.componentId())) {
            return Messages.format("problem.displayBusNested",
                    path(display.componentId(), document).orElse(display.componentId()));
        }
        String label = document.component(new ElementId(display.componentId())).map(Component::label).orElse("");
        return label.isBlank() ? Messages.text("problem.displayBus")
                : Messages.format("problem.displayBusLabeled", label);
    }

    private static String driver(DriverRef driver, CircuitDocument document) {
        String value = driver.driven().toString();
        if (InstancePath.isNested(driver.componentId())) {
            return Messages.format("problem.driver",
                    path(driver.componentId(), document).orElse(driver.componentId()), value);
        }
        Optional<Component> component = document.component(new ElementId(driver.componentId()));
        if (component.isEmpty()) {
            return Messages.format("problem.driver", driver.componentId(), value);
        }
        String name = typeName(component.get(), document);
        String label = component.get().label();
        return label.isBlank() ? Messages.format("problem.driver", name, value)
                : Messages.format("problem.driverLabeled", name, label, value);
    }

    private static String pin(FlatPinRef pin, CircuitDocument document, ComponentTypeRegistry registry) {
        Optional<List<Component>> chain = chain(pin.componentId(), document);
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
            return Messages.format("problem.pin", path(pin.componentId(), document).orElse(pin.componentId()), width);
        }
        String name = typeName(found, document);
        return found.label().isBlank() ? Messages.format("problem.pin", name, width)
                : Messages.format("problem.pinLabeled", name, found.label(), width);
    }

    /**
     * "U1 › U3 (AND de 2 entradas)" for a simulation id such as {@code c5/c2} (spec §6.12): each level by its label, or
     * by its type or definition name when unlabeled; a labeled innermost level also shows its type. Empty when a level
     * cannot be found in the document and its definitions.
     */
    static Optional<String> path(String hierarchicalId, CircuitDocument document) {
        Optional<List<Component>> chain = chain(hierarchicalId, document);
        if (chain.isEmpty()) {
            return Optional.empty();
        }
        List<String> names = new ArrayList<>();
        List<Component> levels = chain.get();
        for (int index = 0; index < levels.size(); index++) {
            Component component = levels.get(index);
            String typeName = typeName(component, document);
            if (component.label().isBlank()) {
                names.add(typeName);
            } else if (index == levels.size() - 1) {
                names.add(Messages.format("problem.pathLabeled", component.label(), typeName));
            } else {
                names.add(component.label());
            }
        }
        return Optional.of(String.join(PATH_SEPARATOR, names));
    }

    /** The components named by each segment of {@code hierarchicalId}, outermost first. */
    private static Optional<List<Component>> chain(String hierarchicalId, CircuitDocument document) {
        List<Component> level = document.components();
        List<Component> found = new ArrayList<>();
        for (String segment : InstancePath.segments(hierarchicalId)) {
            Optional<Component> component = level.stream()
                    .filter(candidate -> candidate.id().value().equals(segment))
                    .findFirst();
            if (component.isEmpty()) {
                return Optional.empty();
            }
            found.add(component.get());
            level = definitionOf(component.get(), document)
                    .map(definition -> definition.body().components())
                    .orElse(List.of());
        }
        return Optional.of(found);
    }

    private static Optional<ComponentDefinition> definitionOf(Component component, CircuitDocument document) {
        return SubcircuitTypes.definitionIdOf(component.typeId()).flatMap(document::definition);
    }

    /** The name of the document's definition for a sub-circuit instance, the bundle name of a standard type. */
    private static String typeName(Component component, CircuitDocument document) {
        return definitionOf(component, document).map(ComponentDefinition::name)
                .orElseGet(() -> Messages.componentName(component.typeId()));
    }
}
