package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Whether a parameter change would join one of the part's pins to something it was not joined to, the one check
 * every path that changes parameters runs before the change (Propiedades, «Configurar…», the «Grupos» window, «Bits
 * por pin», the AI's {@code SetParameters}); a refused change changes nothing.
 *
 * <p>A «Separador de bus» or «Unión de bus» ({@link StandardComponentTypes#hasBitGroups}) is tried on a copy of the
 * document, wires following as {@link ChangeParametersWithWiresCommand} makes them: a pin, new or moved, whose net
 * gains a pin of its own width (or a tunnel, which takes any width) is refused. Its signal would reach the wrong reader
 * with no warning: grouping {@code 0-3|4|5|6|7} on a splitter whose one-bit wires each go to a reader puts {@code b4}
 * on bit 2's wire. A pin that only meets pins of another width is allowed, since the netlist shows that touch as a width
 * mismatch (spec §2.1): {@code b0-3} landing on bit 0's wire, or a narrower {@code b4-5} on the wire its {@code b4-7}
 * left. Every other part keeps {@link NewPinTouch}'s rule: a new pin on any wire or pin is refused.
 */
public final class PinJoinCheck {

    /** Why the change is refused. */
    public enum Kind {
        /** A pin the change adds (Q̅ shown) would sit on a wire or a pin ({@link NewPinTouch}). */
        NEW_PIN_TOUCHES,
        /** A bus part's pin would join a net with a pin of its own width that it was not joined to. */
        JOINS_ANOTHER_NET
    }

    /** The pin, by name, that the change would join, and why it is refused. */
    public record PinJoin(String pinName, Kind kind) {

        public PinJoin {
            Objects.requireNonNull(pinName, "pinName");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** A pin by owner, name and which of the pins of that name it is: the same pin before and after the change. */
    private record PinKey(ElementId owner, String name, int occurrence) {
    }

    private PinJoinCheck() {
    }

    /**
     * The pin that giving part {@code id} {@code rawParameters} would join to something it should not, or empty when
     * the change is safe. The document is left as it is.
     *
     * @throws IllegalArgumentException when the parameters are not valid for the part
     */
    public static Optional<PinJoin> find(CircuitDocument document, ComponentTypeRegistry registry, ElementId id,
                                         Map<String, Object> rawParameters) {
        Component component = document.component(id).orElseThrow(
                () -> new IllegalArgumentException("no component " + id));
        ComponentType type = registry.typeOf(component);
        if (!StandardComponentTypes.hasBitGroups(component.typeId())) {
            return NewPinTouch.find(document, registry, component,
                            component.withParameters(type.resolveParameters(rawParameters)))
                    .map(name -> new PinJoin(name, Kind.NEW_PIN_TOUCHES));
        }
        CircuitDocument changed = document.copy();
        ChangeParametersWithWiresCommand.of(changed, registry, id, rawParameters).apply(changed);
        return sameWidthJoin(registry, id, membersByPin(registry, document, id), changed)
                .map(name -> new PinJoin(name, Kind.JOINS_ANOTHER_NET));
    }

    /** The first pin of part {@code id} in {@code changed} whose net has a pin of its width that it lacked before. */
    private static Optional<String> sameWidthJoin(ComponentTypeRegistry registry, ElementId id,
                                                  Map<PinKey, Set<PinKey>> before, CircuitDocument changed) {
        Netlist netlist = new NetlistCompiler(registry).compile(changed);
        Component component = changed.component(id).orElseThrow();
        ComponentType type = registry.typeOf(component);
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            PinRef pin = new PinRef(id, pinIndex);
            Set<PinKey> joinedBefore = before.getOrDefault(keyOf(registry, changed, pin), Set.of());
            int width = type.pin(pinIndex).width();
            for (PinRef other : netlist.netForPin(pin).map(Net::pins).orElse(Set.of())) {
                if (!other.equals(pin) && !joinedBefore.contains(keyOf(registry, changed, other))
                        && takesWidth(specOf(registry, changed, other), width)) {
                    return Optional.of(type.pin(pinIndex).name());
                }
            }
        }
        return Optional.empty();
    }

    /** For each pin of part {@code id} in {@code document}, the other pins of its net. */
    private static Map<PinKey, Set<PinKey>> membersByPin(ComponentTypeRegistry registry, CircuitDocument document,
                                                         ElementId id) {
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        ComponentType type = registry.typeOf(document.component(id).orElseThrow());
        Map<PinKey, Set<PinKey>> members = new HashMap<>();
        for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
            PinRef pin = new PinRef(id, pinIndex);
            Set<PinKey> others = new HashSet<>();
            for (PinRef other : netlist.netForPin(pin).map(Net::pins).orElse(Set.of())) {
                if (!other.equals(pin)) {
                    others.add(keyOf(registry, document, other));
                }
            }
            members.put(keyOf(registry, document, pin), others);
        }
        return members;
    }

    /** Whether a pin of {@code spec} in a net carries {@code width} bits without a width warning. */
    private static boolean takesWidth(PinSpec spec, int width) {
        return spec.isAdaptiveWidth() || spec.width() == width;
    }

    private static PinSpec specOf(ComponentTypeRegistry registry, CircuitDocument document, PinRef pin) {
        return registry.typeOf(document.component(pin.componentId()).orElseThrow()).pin(pin.pinIndex());
    }

    private static PinKey keyOf(ComponentTypeRegistry registry, CircuitDocument document, PinRef pin) {
        ComponentType type = registry.typeOf(document.component(pin.componentId()).orElseThrow());
        String name = type.pin(pin.pinIndex()).name();
        int occurrence = 0;
        for (int pinIndex = 0; pinIndex < pin.pinIndex(); pinIndex++) {
            if (type.pin(pinIndex).name().equals(name)) {
                occurrence++;
            }
        }
        return new PinKey(pin.componentId(), name, occurrence);
    }
}
