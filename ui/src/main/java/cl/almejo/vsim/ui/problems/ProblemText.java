package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
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
            .comparingInt((Problem problem) -> problem instanceof ShortCircuit ? 0 : 1)
            .thenComparingLong(problem -> problem instanceof ShortCircuit shortCircuit ? shortCircuit.sinceTime() : 0)
            .thenComparing(Problem::netId);

    private ProblemText() {
    }

    /** Short circuits first, oldest first, then width mismatches; ties by net id, so the order is stable. */
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
        };
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
