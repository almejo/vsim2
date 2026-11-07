package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A parameter change that makes a pin appear (turning «Mostrar Q̅» on) is refused when the new pin would land on a
 * wire or on another pin: in a circuit saved before the parameter existed, the Q wire may run right over that point,
 * and the pin would join its net silently (a short between Q and Q̅). The caller refuses the edit with the pin's name
 * (the UI shows {@code properties.newPinTouches} with the pin's name, the AI gets an invalid-parameter error); nothing is changed.
 */
public final class NewPinTouch {

    private NewPinTouch() {
    }

    /**
     * The name of the first pin that {@code after} has and {@code before} lacks and that would sit on a wire or on a
     * pin of another component, or empty. Both are the same component with different parameters.
     */
    public static Optional<String> find(CircuitDocument document, ComponentTypeRegistry registry, Component before,
                                        Component after) {
        ComponentType beforeType = registry.typeOf(before);
        ComponentType afterType = registry.typeOf(after);
        Set<String> existing = new HashSet<>();
        for (int index = 0; index < beforeType.pinCount(); index++) {
            existing.add(beforeType.pin(index).name());
        }
        for (int index = 0; index < afterType.pinCount(); index++) {
            String name = afterType.pin(index).name();
            if (!existing.contains(name)
                    && touchesSomething(document, registry, after, afterType.pinLocation(after, index))) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static boolean touchesSomething(CircuitDocument document, ComponentTypeRegistry registry,
                                            Component owner, GridPoint point) {
        if (document.wires().stream().anyMatch(wire -> wire.contains(point))) {
            return true;
        }
        for (Component other : document.components()) {
            if (other.id().equals(owner.id())) {
                continue;
            }
            ComponentType type = registry.typeOf(other);
            for (int index = 0; index < type.pinCount(); index++) {
                if (type.pinLocation(other, index).equals(point)) {
                    return true;
                }
            }
        }
        return false;
    }
}
