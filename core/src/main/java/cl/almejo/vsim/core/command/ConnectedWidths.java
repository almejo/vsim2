package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterKind;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * «Anchos que siguen lo conectado»: right after a connection (a wire drawn, a part placed or moved), a part with an
 * «Ancho (bits)» shared by its data pins (input and output pins, Constante, gates, buffer, tri-state, Interruptor, LED,
 * register, counter, adder and the other N-bit blocks) takes the width of what it now touches. Pins with a width of
 * their own (a mux's select, an enable, a clock, a carry) do not count. Only on connecting, never spreading further
 * through the net (a later idea, recorded in the design notes).
 *
 * <p>The rule: a part changes only when every pin with a width of its own met by its data pins carries the same width
 * W, W differs from the part's and the part can take it. When they disagree (an AND whose inputs land on a 4-bit and
 * an 8-bit bus), when something already connected keeps the current width (its output on a 1-bit wire), when W is
 * beyond the part's maximum or a constant's value would not fit, or when the change would join or split anything else
 * (a wider constant's pin moves), nothing changes and the netlist's width mismatch shows as usual: never hidden.
 *
 * <p>Who may change: after a placement or a move, only the parts it placed or moved; after a wire, every part whose
 * data pin it joins to something new. When two of those meet, the one that ranks lower takes the other's width and
 * the other ignores it ({@link #priority}): a part that was already connected ranks above a loose one; then blocks
 * and gates, above constants, above input pins and switches (they drive), above output pins and LEDs (they show). So
 * a fresh constant wired to an adder takes the adder's width, a fresh output pin or LED wired to an 8-bit input pin
 * takes 8 bits, and a fresh input pin wired to a connected register takes the register's. Two parts of the same rank
 * give no width to each other: an 8-bit adder's sum wired to a fresh 4-bit register keeps the mismatch in sight.
 */
public final class ConnectedWidths {

    /**
     * Parts with a «width» that never follow: a memory's word width goes with its contents and files, and a bus
     * splitter's or joiner's with its bit groups.
     */
    private static final Set<String> NEVER_FOLLOWING = Set.of(StandardComponentTypes.RAM, StandardComponentTypes.ROM,
            StandardComponentTypes.BUS_SPLITTER, StandardComponentTypes.BUS_JOINER);

    /**
     * The rank of the loose parts that are not blocks or gates (those rank {@value #BLOCK_RANK}), see
     * {@link #priority}.
     */
    private static final Map<String, Integer> RANKS = Map.of(StandardComponentTypes.CONSTANT, 2,
            StandardComponentTypes.INPUT_PIN, 1, StandardComponentTypes.SWITCH, 1,
            StandardComponentTypes.OUTPUT_PIN, 0, StandardComponentTypes.LED, 0);

    private static final int BLOCK_RANK = 3;

    /** How far a part connected before the edit ranks above the loose ones (whose ranks go up to the blocks'). */
    private static final int CONNECTED_BONUS = BLOCK_RANK + 1;

    private static final String WIDTH = StandardComponentTypes.WIDTH;

    /** What {@link #follow} changed: the parts, and the steps that did it, in the order they were applied. */
    public record Followed(List<WidthChange> changes, List<Command> steps) {

        public static final Followed NOTHING = new Followed(List.of(), List.of());

        public Followed {
            changes = List.copyOf(changes);
            steps = List.copyOf(steps);
        }

        /** Undoes the steps, last first. */
        public void revert(CircuitDocument document) {
            for (int index = steps.size() - 1; index >= 0; index--) {
                steps.get(index).revert(document);
            }
        }
    }

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private final NetlistCompiler compiler;
    private final Netlist before;
    private final Set<ElementId> placedIds;

    private ConnectedWidths(CircuitDocument document, ComponentTypeRegistry registry, Netlist before,
                            Set<ElementId> placedIds) {
        this.document = document;
        this.registry = registry;
        this.compiler = new NetlistCompiler(registry);
        this.before = before;
        this.placedIds = Set.copyOf(placedIds);
    }

    /**
     * Whether a connection of {@code document} as it is now could make a part follow, so the caller compiles the
     * netlist before it only when it may be needed: with {@code placedIds}, when one of them has a width or is not in
     * the document yet (it is being placed); without, when the document has a part with a width at all.
     */
    public static boolean mayFollow(CircuitDocument document, ComponentTypeRegistry registry,
                                    Set<ElementId> placedIds) {
        if (placedIds.isEmpty()) {
            return document.components().stream().anyMatch(component -> hasWidth(registry, component));
        }
        return placedIds.stream().anyMatch(id -> document.element(id).isEmpty()
                || document.component(id).filter(component -> hasWidth(registry, component)).isPresent());
    }

    /**
     * Makes the parts that follow take their new widths in {@code document}, which a connection has just changed;
     * {@code before} is the netlist of the document before it ({@link NetlistCompiler#compile}), {@code placedIds}
     * the parts the connection placed or moved (empty for a wire).
     */
    public static Followed follow(CircuitDocument document, ComponentTypeRegistry registry, Netlist before,
                                  Set<ElementId> placedIds) {
        Objects.requireNonNull(before, "before");
        return new ConnectedWidths(document, registry, before, placedIds).follow();
    }

    private Followed follow() {
        Netlist after = compiler.compile(document);
        List<ElementId> candidates = candidates(after);
        Set<ElementId> pending = new HashSet<>(candidates);
        List<WidthChange> changes = new ArrayList<>();
        List<Command> steps = new ArrayList<>();
        for (ElementId id : candidates) {
            pending.remove(id);
            Component component = document.component(id).orElseThrow();
            OptionalInt target = targetWidth(component, after, candidates);
            if (target.isEmpty()) {
                continue;
            }
            Optional<Command> step = tryWidth(component, target.getAsInt(), after, pending);
            if (step.isPresent()) {
                steps.add(step.get());
                changes.add(new WidthChange(id, component.typeId(), widthOf(component), target.getAsInt()));
                after = compiler.compile(document);
            }
        }
        return changes.isEmpty() ? Followed.NOTHING : new Followed(changes, steps);
    }

    /** Whether {@code component} has an «Ancho (bits)» it may change (its data pins decide, {@link #widthPins}). */
    private static boolean hasWidth(ComponentTypeRegistry registry, Component component) {
        return !NEVER_FOLLOWING.contains(component.typeId()) && registry.findTypeOf(component)
                .flatMap(type -> type.parameterNamed(WIDTH))
                .filter(parameter -> parameter.kind() == ParameterKind.INTEGER)
                .isPresent();
    }

    /**
     * The parts that may change: with a width, placed or moved by the edit (any, for a wire), with a data pin joined
     * to something new; highest {@link #priority} first.
     */
    private List<ElementId> candidates(Netlist after) {
        List<ElementId> candidates = new ArrayList<>();
        for (Component component : document.components()) {
            if (!hasWidth(registry, component) || !placedIds.isEmpty() && !placedIds.contains(component.id())) {
                continue;
            }
            if (widthPins(component).stream()
                    .anyMatch(pinIndex -> newlyJoined(new PinRef(component.id(), pinIndex), after))) {
                candidates.add(component.id());
            }
        }
        candidates.sort(Comparator.comparingInt(this::priority).reversed());
        return candidates;
    }

    /**
     * Who gives way when two parts that may change meet: the lower takes the higher's width, the higher ignores the
     * lower, equals ignore each other. Connected before the edit ranks above loose; then blocks and gates (3),
     * constants (2), input pins and switches (1), output pins and LEDs (0).
     */
    private int priority(ElementId id) {
        Component component = document.component(id).orElseThrow();
        int rank = RANKS.getOrDefault(component.typeId(), BLOCK_RANK);
        return (wasConnected(component) ? CONNECTED_BONUS : 0) + rank;
    }

    /** Whether any pin of {@code component} met a pin with a width of its own before the connection. */
    private boolean wasConnected(Component component) {
        for (int pinIndex = 0; pinIndex < registry.typeOf(component).pinCount(); pinIndex++) {
            if (!widthMates(before, new PinRef(component.id(), pinIndex)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean newlyJoined(PinRef pin, Netlist after) {
        return !widthMates(before, pin).containsAll(widthMates(after, pin));
    }

    /** The pins with a width of their own that share {@code pin}'s net, other than the pins of its own part. */
    private Set<PinRef> widthMates(Netlist netlist, PinRef pin) {
        Set<PinRef> mates = new HashSet<>();
        for (PinRef other : netlist.netForPin(pin).map(Net::pins).orElse(Set.of())) {
            if (!other.componentId().equals(pin.componentId()) && !specOf(other).isAdaptiveWidth()) {
                mates.add(other);
            }
        }
        return mates;
    }

    /**
     * The one width the pins met by {@code component}'s width pins carry, leaving out the other {@code candidates}
     * that do not rank above it ({@link #priority}); empty when they carry none, more than one, or the width the
     * part already has.
     */
    private OptionalInt targetWidth(Component component, Netlist after, List<ElementId> candidates) {
        int rank = priority(component.id());
        Set<Integer> widths = new HashSet<>();
        for (int pinIndex : widthPins(component)) {
            for (PinRef mate : widthMates(after, new PinRef(component.id(), pinIndex))) {
                if (!candidates.contains(mate.componentId()) || priority(mate.componentId()) > rank) {
                    widths.add(specOf(mate).width());
                }
            }
        }
        if (widths.size() != 1) {
            return OptionalInt.empty();
        }
        int width = widths.iterator().next();
        return width == widthOf(component) ? OptionalInt.empty() : OptionalInt.of(width);
    }

    /**
     * Gives {@code component} {@code width} bits, wires following its pins; kept only when the part can take it and
     * the change joins its pins to exactly what they met, with no width left over on its width pins' nets.
     */
    private Optional<Command> tryWidth(Component component, int width, Netlist after, Set<ElementId> pending) {
        Map<String, Object> parameters = withWidth(component, width);
        if (parameters.isEmpty()) {
            return Optional.empty();
        }
        Command step = ChangeParametersWithWiresCommand.of(document, registry, component.id(), parameters);
        step.apply(document);
        Netlist changed = compiler.compile(document);
        Component resized = document.component(component.id()).orElseThrow();
        if (registry.typeOf(resized).pinCount() == registry.typeOf(component).pinCount()
                && sameMates(component, after, changed) && fits(resized, width, changed, pending)) {
            return Optional.of(step);
        }
        step.revert(document);
        return Optional.empty();
    }

    /** {@code component}'s parameters with {@code width}; empty when its type refuses that width. */
    private Map<String, Object> withWidth(Component component, int width) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(WIDTH, width);
        try {
            registry.typeOf(component).resolveParameters(parameters);
            return parameters;
        } catch (IllegalArgumentException exception) {
            // Beyond the part's maximum or a value that would not fit: never clamped, the mismatch stays in sight.
            return Map.of();
        }
    }

    private boolean sameMates(Component component, Netlist first, Netlist second) {
        for (int pinIndex = 0; pinIndex < registry.typeOf(component).pinCount(); pinIndex++) {
            PinRef pin = new PinRef(component.id(), pinIndex);
            if (!allMates(first, pin).equals(allMates(second, pin))) {
                return false;
            }
        }
        return true;
    }

    private static Set<PinRef> allMates(Netlist netlist, PinRef pin) {
        Set<PinRef> mates = new HashSet<>(netlist.netForPin(pin).map(Net::pins).orElse(Set.of()));
        mates.remove(pin);
        return mates;
    }

    private boolean fits(Component resized, int width, Netlist changed, Set<ElementId> pending) {
        for (int pinIndex : widthPins(resized)) {
            for (PinRef mate : widthMates(changed, new PinRef(resized.id(), pinIndex))) {
                if (!pending.contains(mate.componentId()) && specOf(mate).width() != width) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The pins whose width is the part's «Ancho» (a register's D and Q, not its clock): those whose width changes
     * when the width does, found by trying another width the part accepts.
     */
    private List<Integer> widthPins(Component component) {
        ComponentType type = registry.typeOf(component);
        int width = widthOf(component);
        for (int other : List.of(1, 2, maximumWidth(type))) {
            Map<String, Object> parameters = other == width ? Map.of() : withWidth(component, other);
            if (parameters.isEmpty()) {
                continue;
            }
            ComponentType otherType = type.specializedFor(parameters);
            if (otherType.pinCount() != type.pinCount()) {
                return List.of();
            }
            List<Integer> pins = new ArrayList<>();
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                if (type.pin(pinIndex).width() != otherType.pin(pinIndex).width()) {
                    pins.add(pinIndex);
                }
            }
            return pins;
        }
        return List.of();
    }

    private static int maximumWidth(ComponentType type) {
        return type.parameterNamed(WIDTH).map(ParameterSpec::maximum).orElse(1);
    }

    /** The part's «Ancho», its default when the file left it out. */
    private int widthOf(Component component) {
        return ((Number) registry.typeOf(component).resolveParameters(component.parameters()).get(WIDTH)).intValue();
    }

    private PinSpec specOf(PinRef pin) {
        return registry.typeOf(document.component(pin.componentId()).orElseThrow()).pin(pin.pinIndex());
    }
}
