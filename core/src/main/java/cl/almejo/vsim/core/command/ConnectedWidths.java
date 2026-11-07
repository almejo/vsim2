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
import java.util.OptionalInt;
import java.util.Set;

/**
 * «Anchos que siguen lo conectado»: right after a connection (a wire drawn, a part placed or moved), a part with an
 * «Ancho (bits)» shared by its data pins (input and output pins, Constante, gates, buffer, tri-state, Interruptor, LED,
 * Punto de parada, register, counter, adder and the other N-bit blocks) takes the width of what it now touches. Pins
 * with a width of their own (a mux's select, an enable, a clock, a carry) do not count. Only on connecting, never
 * spreading further through the net (a later idea, recorded in the design notes).
 *
 * <p>The parts that may change are those with a width whose data pin the connection joined to something new (after a
 * placement or a move, only the parts it placed or moved). Those that share a net through their data pins are decided
 * together, as a group, on every net their data pins touch: the nets reached through the other parts of the group
 * count too, since those parts may change with them. The rule never picks between disagreeing nets:
 * <ol>
 *     <li>When everything in those nets other than the loose parts of the group (connected to nothing before the
 *     edit) carries one width W, the loose parts take W: a fresh constant wired to an 8-bit adder whose other input
 *     is on an 8-bit bus.</li>
 *     <li>When the group is only loose parts and nothing else, the {@link Role} breaks the tie: blocks and gates give
 *     their width to sources (constants, input pins, switches), which give theirs to sinks (output pins, LEDs, Puntos
 *     de parada). Two of the same role whose widths differ change nothing: an 8-bit adder's sum wired to a fresh
 *     4-bit register keeps the mismatch in sight.</li>
 *     <li>Otherwise a part already connected changes only when every other pin in those nets carries one width,
 *     and only when it is the only part of the group in that case: an AND whose first input sat on an 8-bit bus
 *     (a mismatch) takes 8 bits when its second input is wired to another 8-bit bus.</li>
 * </ol>
 * The result never depends on the order of the parts in the file. When a part of the group cannot take the width
 * (beyond its maximum, a constant's value that would not fit, a pin that would move), the whole group stays as it was
 * and the netlist's width mismatch shows as usual: never hidden.
 */
public final class ConnectedWidths {

    /**
     * Parts with a «width» that never follow: a memory's word width goes with its contents and files, and a bus
     * splitter's or joiner's with its bit groups.
     */
    private static final Set<String> NEVER_FOLLOWING = Set.of(StandardComponentTypes.RAM, StandardComponentTypes.ROM,
            StandardComponentTypes.BUS_SPLITTER, StandardComponentTypes.BUS_JOINER);

    private static final String WIDTH = StandardComponentTypes.WIDTH;

    /**
     * What a part with a width does with values, read from its pins, which decides who gives way when only loose
     * parts meet (rule 2 of the class comment): the higher role keeps its width.
     */
    public enum Role {
        /** Only reads: no pin drives (output pin, LED, Punto de parada). */
        SINK,
        /** Only drives: no pin reads (Constante, input pin, Interruptor). */
        SOURCE,
        /** Reads and drives (gates, registers, arithmetic blocks): the user chose its width for what it computes. */
        BLOCK
    }

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

    /** Parts that share nets through their data pins and are decided together, with every net those pins touch. */
    private record Group(List<Component> members, Set<NetId> nets) {
    }

    /** A group's decision applied: its steps and changes, kept only if the group holds afterwards. */
    private record Applied(Group group, List<Command> steps, List<WidthChange> changes) {
    }

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private final NetlistCompiler compiler;
    private final Netlist before;
    private final Set<ElementId> placedIds;
    private final Map<ElementId, List<Integer>> widthPinsById = new HashMap<>();

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
     * the parts the connection placed or moved (empty for a wire). If it fails part-way it undoes its own steps
     * before the exception leaves.
     */
    public static Followed follow(CircuitDocument document, ComponentTypeRegistry registry, Netlist before,
                                  Set<ElementId> placedIds) {
        Objects.requireNonNull(before, "before");
        return new ConnectedWidths(document, registry, before, placedIds).follow();
    }

    /**
     * The role of a part of {@code type} in rule 2 of the class comment, read from its pins; empty when the type has no
     * width that may follow.
     */
    public static Optional<Role> roleOf(ComponentType type) {
        if (!mayFollow(type)) {
            return Optional.empty();
        }
        boolean reads = type.pins().stream().anyMatch(PinSpec::isInput);
        boolean drives = type.pins().stream().anyMatch(PinSpec::isDriver);
        if (!drives) {
            return Optional.of(Role.SINK);
        }
        return Optional.of(reads ? Role.BLOCK : Role.SOURCE);
    }

    private Followed follow() {
        Netlist after = compiler.compile(document);
        List<Component> candidates = candidates(after);
        if (candidates.isEmpty()) {
            return Followed.NOTHING;
        }
        List<Applied> applied = new ArrayList<>();
        try {
            for (Group group : groups(candidates, after)) {
                decide(group, after).ifPresent(targets -> apply(group, targets).ifPresent(applied::add));
            }
            keepOnlyThoseThatHold(applied, after);
        } catch (RuntimeException exception) {
            revertAll(applied);
            throw exception;
        }
        List<WidthChange> changes = new ArrayList<>();
        List<Command> steps = new ArrayList<>();
        for (Applied group : applied) {
            changes.addAll(group.changes());
            steps.addAll(group.steps());
        }
        return changes.isEmpty() ? Followed.NOTHING : new Followed(changes, steps);
    }

    /** Whether {@code component} has an «Ancho (bits)» it may change (its data pins decide, {@link #widthPins}). */
    private static boolean hasWidth(ComponentTypeRegistry registry, Component component) {
        return registry.findTypeOf(component).filter(ConnectedWidths::mayFollow).isPresent();
    }

    private static boolean mayFollow(ComponentType type) {
        return !NEVER_FOLLOWING.contains(type.id()) && type.parameterNamed(WIDTH)
                .filter(parameter -> parameter.kind() == ParameterKind.INTEGER)
                .isPresent();
    }

    /**
     * The parts that may change: with a width, placed or moved by the edit (any, for a wire), with a data pin joined
     * to something new. Only the nets the connection changed are looked at, so a wire far from everything costs one
     * compile and a pass over the nets.
     */
    private List<Component> candidates(Netlist after) {
        Map<ElementId, Component> candidates = new LinkedHashMap<>();
        Set<ElementId> looked = new HashSet<>();
        for (Net net : after.nets()) {
            if (!changedByTheConnection(net)) {
                continue;
            }
            for (PinRef pin : net.pins()) {
                if (!looked.add(pin.componentId())) {
                    continue;
                }
                Component component = document.component(pin.componentId()).orElseThrow();
                if (mayChange(component) && widthPins(component).stream()
                        .anyMatch(pinIndex -> newlyJoined(new PinRef(component.id(), pinIndex), after))) {
                    candidates.put(component.id(), component);
                }
            }
        }
        return List.copyOf(candidates.values());
    }

    /** Whether {@code net}'s pins are not the pins of one net before the connection. */
    private boolean changedByTheConnection(Net net) {
        if (net.pins().isEmpty()) {
            return false;
        }
        PinRef anyPin = net.pins().iterator().next();
        return before.netForPin(anyPin).map(old -> !old.pins().equals(net.pins())).orElse(true);
    }

    private boolean mayChange(Component component) {
        return (placedIds.isEmpty() || placedIds.contains(component.id())) && hasWidth(registry, component);
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

    /** The candidates joined through the nets of their data pins, each group with every net those pins touch. */
    private List<Group> groups(List<Component> candidates, Netlist after) {
        Map<ElementId, Set<NetId>> netsById = new LinkedHashMap<>();
        for (Component candidate : candidates) {
            netsById.put(candidate.id(), widthPinNets(candidate, after));
        }
        List<Group> groups = new ArrayList<>();
        Set<ElementId> grouped = new HashSet<>();
        for (Component seed : candidates) {
            if (grouped.contains(seed.id())) {
                continue;
            }
            List<Component> members = new ArrayList<>(List.of(seed));
            Set<NetId> nets = new LinkedHashSet<>(netsById.get(seed.id()));
            grouped.add(seed.id());
            boolean grew = true;
            while (grew) {
                grew = false;
                for (Component other : candidates) {
                    if (!grouped.contains(other.id())
                            && netsById.get(other.id()).stream().anyMatch(nets::contains)) {
                        members.add(other);
                        nets.addAll(netsById.get(other.id()));
                        grouped.add(other.id());
                        grew = true;
                    }
                }
            }
            groups.add(new Group(members, nets));
        }
        return groups;
    }

    private Set<NetId> widthPinNets(Component component, Netlist after) {
        Set<NetId> nets = new LinkedHashSet<>();
        for (int pinIndex : widthPins(component)) {
            after.netForPin(new PinRef(component.id(), pinIndex)).ifPresent(net -> nets.add(net.id()));
        }
        return nets;
    }

    /** The new width of each part of {@code group} that changes (rules 1 to 3 of the class comment); empty for none. */
    private Optional<Map<ElementId, Integer>> decide(Group group, Netlist after) {
        Set<PinRef> loosePins = new HashSet<>();
        for (Component member : group.members()) {
            if (!wasConnected(member)) {
                loosePins.addAll(ownWidthPins(member));
            }
        }
        Set<Integer> fixedWidths = widthsOn(group, after, loosePins);
        if (fixedWidths.size() == 1) {
            return everyLoosePartTakes(group, fixedWidths.iterator().next());
        }
        if (fixedWidths.isEmpty()) {
            return byRole(group);
        }
        return theOnlyConnectedPartThatSeesOneWidth(group, after);
    }

    /** Rule 1: the loose parts of {@code group} take {@code width}. */
    private Optional<Map<ElementId, Integer>> everyLoosePartTakes(Group group, int width) {
        Map<ElementId, Integer> targets = new LinkedHashMap<>();
        for (Component member : group.members()) {
            if (widthOf(member) != width) {
                targets.put(member.id(), width);
            }
        }
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets);
    }

    /** Rule 2: only loose parts and nothing else; the highest {@link Role} gives its width when it has only one. */
    private Optional<Map<ElementId, Integer>> byRole(Group group) {
        Role highest = Role.SINK;
        for (Component member : group.members()) {
            Role role = roleOf(registry.typeOf(member)).orElseThrow();
            if (role.compareTo(highest) > 0) {
                highest = role;
            }
        }
        Set<Integer> widths = new HashSet<>();
        for (Component member : group.members()) {
            if (roleOf(registry.typeOf(member)).orElseThrow() == highest) {
                widths.add(widthOf(member));
            }
        }
        return widths.size() == 1 ? everyLoosePartTakes(group, widths.iterator().next()) : Optional.empty();
    }

    /** Rule 3: the one part of {@code group} for which every other pin in the group's nets carries one other width. */
    private Optional<Map<ElementId, Integer>> theOnlyConnectedPartThatSeesOneWidth(Group group, Netlist after) {
        Map<ElementId, Integer> targets = new LinkedHashMap<>();
        for (Component member : group.members()) {
            Set<Integer> seen = widthsOn(group, after, ownWidthPins(member));
            if (seen.size() == 1 && seen.iterator().next() != widthOf(member)) {
                targets.put(member.id(), seen.iterator().next());
            }
        }
        return targets.size() == 1 ? Optional.of(targets) : Optional.empty();
    }

    /** The widths of the pins in {@code group}'s nets other than {@code leftOut}. */
    private Set<Integer> widthsOn(Group group, Netlist after, Set<PinRef> leftOut) {
        Set<Integer> widths = new HashSet<>();
        for (NetId netId : group.nets()) {
            for (PinRef pin : after.net(netId).map(Net::pins).orElse(Set.of())) {
                PinSpec spec = specOf(pin);
                if (!leftOut.contains(pin) && !spec.isAdaptiveWidth()) {
                    widths.add(spec.width());
                }
            }
        }
        return widths;
    }

    private Set<PinRef> ownWidthPins(Component component) {
        Set<PinRef> pins = new HashSet<>();
        for (int pinIndex : widthPins(component)) {
            pins.add(new PinRef(component.id(), pinIndex));
        }
        return pins;
    }

    /** Gives each part its target width, wires following its pins; nothing when one of them refuses its width. */
    private Optional<Applied> apply(Group group, Map<ElementId, Integer> targets) {
        List<Command> steps = new ArrayList<>();
        List<WidthChange> changes = new ArrayList<>();
        for (Map.Entry<ElementId, Integer> target : targets.entrySet()) {
            Component component = document.component(target.getKey()).orElseThrow();
            Map<String, Object> parameters = withWidth(component, target.getValue());
            if (parameters.isEmpty()) {
                revert(steps);
                return Optional.empty();
            }
            Command step = ChangeParametersWithWiresCommand.of(document, registry, component.id(), parameters);
            step.apply(document);
            steps.add(step);
            changes.add(new WidthChange(component.id(), component.typeId(), widthOf(component), target.getValue()));
        }
        return Optional.of(new Applied(group, steps, changes));
    }

    /**
     * Undoes every applied group whose parts no longer meet exactly what they met (a wider constant's pin moved) or
     * whose data pins still sit on a mismatch, until the rest hold; one compile when nothing has to go.
     */
    private void keepOnlyThoseThatHold(List<Applied> applied, Netlist after) {
        while (!applied.isEmpty()) {
            Netlist changed = compiler.compile(document);
            Optional<Applied> broken = applied.stream().filter(group -> !holds(group, after, changed)).findFirst();
            if (broken.isEmpty()) {
                return;
            }
            revert(broken.get().steps());
            applied.remove(broken.get());
        }
    }

    private boolean holds(Applied applied, Netlist after, Netlist changed) {
        for (Component member : applied.group().members()) {
            Component resized = document.component(member.id()).orElseThrow();
            if (registry.typeOf(resized).pinCount() != registry.typeOf(member).pinCount()
                    || !sameMates(resized, after, changed)) {
                return false;
            }
            for (int pinIndex : widthPins(member)) {
                if (changed.netForPin(new PinRef(member.id(), pinIndex)).map(Net::widthMismatch).orElse(false)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void revertAll(List<Applied> applied) {
        for (int index = applied.size() - 1; index >= 0; index--) {
            revert(applied.get(index).steps());
        }
    }

    private void revert(List<Command> steps) {
        for (int index = steps.size() - 1; index >= 0; index--) {
            steps.get(index).revert(document);
        }
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

    /**
     * The pins whose width is the part's «Ancho» (a register's D and Q, not its clock): those whose width changes
     * when the width does, found by trying another width the part accepts. Worked out once per part and follow.
     */
    private List<Integer> widthPins(Component component) {
        return widthPinsById.computeIfAbsent(component.id(), id -> findWidthPins(component));
    }

    private List<Integer> findWidthPins(Component component) {
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
