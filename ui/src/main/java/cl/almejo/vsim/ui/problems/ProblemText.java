package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.ui.i18n.Messages;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** User-facing texts for simulation problems (spec §6.12) and the order in which they are listed and visited. */
public final class ProblemText {

    private static final String SEPARATOR = " · ";

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
        Optional<Component> component = document.component(new ElementId(driver.componentId()));
        String value = driver.driven().toString();
        if (component.isEmpty()) {
            return Messages.format("problem.driver", driver.componentId(), value);
        }
        String name = Messages.componentName(component.get().typeId());
        String label = component.get().label();
        return label.isBlank() ? Messages.format("problem.driver", name, value)
                : Messages.format("problem.driverLabeled", name, label, value);
    }

    private static String pin(FlatPinRef pin, CircuitDocument document, ComponentTypeRegistry registry) {
        Optional<Component> component = document.component(new ElementId(pin.componentId()));
        if (component.isEmpty()) {
            return pin.componentId();
        }
        Component found = component.get();
        ComponentType type = registry.require(found.typeId());
        if (pin.pinIndex() < 0 || pin.pinIndex() >= type.pinCount()) {
            return pin.componentId();
        }
        int width = type.pin(pin.pinIndex()).width();
        String name = Messages.componentName(found.typeId());
        return found.label().isBlank() ? Messages.format("problem.pin", name, width)
                : Messages.format("problem.pinLabeled", name, found.label(), width);
    }
}
