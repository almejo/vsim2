package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Wire tunnels («Túnel (entrada)» / «Túnel (salida)»): their parameters and shape, how they group by name in one
 * circuit, the names and colours a new tunnel proposes, and the rename that carries the salidas along. Names are
 * trimmed and case-sensitive ({@code clk} is not {@code CLK}); they never cross a component boundary.
 */
public final class Tunnels {

    /** The tunnel's name parameter, trimmed and never blank. */
    public static final String NAME = "name";
    /** The entrada's colour parameter: an index into the tunnel palette (0 .. {@link #COLOR_COUNT} - 1). */
    public static final String COLOR = "color";
    /** How many colours the tunnel palette has. */
    public static final int COLOR_COUNT = 8;
    /** {@link #colorOf} for a name without entrada: its salidas are drawn grey. */
    public static final int NO_COLOR = -1;

    /** Proposed names are this prefix and a number: T1, T2, … */
    private static final String NAME_PREFIX = "T";
    private static final String DEFAULT_NAME = NAME_PREFIX + 1;
    /** Room for the hole and the arrow next to it, in grid units. */
    private static final int HOLE_AND_ARROW_UNITS = 3;
    /** Room around the name, in grid units; the name itself takes one unit per character (like a constant's text). */
    private static final int TEXT_MARGIN_UNITS = 1;
    /** The tag is two grid units thick, with the pin in the middle of its short side. */
    private static final int TAG_THICKNESS = 2;

    private static final ParameterSpec NAME_PARAMETER = ParameterSpec.text(NAME, DEFAULT_NAME, Tunnels::normalizeName);
    private static final ParameterSpec COLOR_PARAMETER = ParameterSpec.integer(COLOR, 0, 0, COLOR_COUNT - 1);

    private Tunnels() {
    }

    /** The tunnels of one name in one circuit, in document order. */
    public record TunnelGroup(String name, List<ElementId> entrances, List<ElementId> exits) {
        public TunnelGroup {
            entrances = List.copyOf(entrances);
            exits = List.copyOf(exits);
        }
    }

    /** The parameters of {@code kind}: the name, and the colour for an entrada only (salidas take its colour). */
    public static List<ParameterSpec> parameters(TunnelKind kind) {
        return kind == TunnelKind.ENTRANCE ? List.of(NAME_PARAMETER, COLOR_PARAMETER) : List.of(NAME_PARAMETER);
    }

    /**
     * The shape of a tunnel with these (resolved) parameters: the pin at (0, 1), one grid unit outside the tag on the
     * wire's side, and the tag from x = 1, {@link #lengthInGridUnits} long and two units thick. The pin has no width
     * of its own ({@link PinSpec#adaptive}).
     */
    public static ComponentShape shape(Map<String, Object> parameters) {
        int length = lengthInGridUnits((String) parameters.get(NAME));
        return new ComponentShape(new GridSize(1 + length, TAG_THICKNESS),
                List.of(PinSpec.adaptive("pin", 0, TAG_THICKNESS / 2)));
    }

    /**
     * How long the tag of a tunnel named {@code name} is, in whole grid units so the pin stays on the grid: the hole
     * and the arrow, then one unit per character of the name and a margin. Shared with the painter so the hit area
     * and the drawing agree.
     */
    public static int lengthInGridUnits(String name) {
        String trimmed = name.strip();
        int characters = Math.max(1, trimmed.codePointCount(0, trimmed.length()));
        return HOLE_AND_ARROW_UNITS + characters + TEXT_MARGIN_UNITS;
    }

    /** The stored form of a name: trimmed. A blank name throws IllegalArgumentException. */
    public static String normalizeName(String raw) {
        String name = raw.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a tunnel name must not be blank");
        }
        return name;
    }

    public static boolean isTunnel(String typeId) {
        return TunnelKind.of(typeId).isPresent();
    }

    public static Optional<TunnelKind> kindOf(Component component) {
        return TunnelKind.of(component.typeId());
    }

    /** The trimmed name of tunnel {@code component} (the default name when the parameter is missing). */
    public static String nameOf(Component component) {
        Object name = component.parameters().get(NAME);
        return name == null ? DEFAULT_NAME : name.toString().strip();
    }

    /** The tunnels of {@code document} grouped by name, sorted by name. */
    public static Map<String, TunnelGroup> groups(CircuitDocument document) {
        Map<String, List<ElementId>> entrances = new HashMap<>();
        Map<String, List<ElementId>> exits = new HashMap<>();
        for (Component component : document.components()) {
            Optional<TunnelKind> kind = kindOf(component);
            if (kind.isPresent()) {
                Map<String, List<ElementId>> byName = kind.get() == TunnelKind.ENTRANCE ? entrances : exits;
                byName.computeIfAbsent(nameOf(component), name -> new ArrayList<>()).add(component.id());
            }
        }
        Map<String, TunnelGroup> groups = new TreeMap<>();
        for (String name : unionOfKeys(entrances, exits)) {
            groups.put(name, new TunnelGroup(name, entrances.getOrDefault(name, List.of()),
                    exits.getOrDefault(name, List.of())));
        }
        return Collections.unmodifiableMap(groups);
    }

    private static Set<String> unionOfKeys(Map<String, ?> first, Map<String, ?> second) {
        Set<String> names = new HashSet<>(first.keySet());
        names.addAll(second.keySet());
        return names;
    }

    /**
     * The name a new entrada proposes: the first of T1, T2, … that no entrada uses. Salidas do not take a name, so an
     * entrada placed after a lone salida «T1» links to it.
     */
    public static String proposedEntranceName(CircuitDocument document) {
        Set<String> used = entranceNames(document);
        int number = 1;
        while (used.contains(NAME_PREFIX + number)) {
            number++;
        }
        return NAME_PREFIX + number;
    }

    /**
     * The name a new salida proposes: {@code lastEntranceName} (the last entrada placed or renamed in this circuit)
     * while some entrada still has it, else T1.
     */
    public static String proposedExitName(CircuitDocument document, Optional<String> lastEntranceName) {
        return lastEntranceName.map(String::strip)
                .filter(entranceNames(document)::contains)
                .orElse(DEFAULT_NAME);
    }

    /** The colour of a new entrada: the first palette colour no entrada uses, else the entrada count mod 8. */
    public static int freeColor(CircuitDocument document) {
        List<Integer> used = entrances(document).stream().map(Tunnels::colorParameter).toList();
        for (int color = 0; color < COLOR_COUNT; color++) {
            if (!used.contains(color)) {
                return color;
            }
        }
        return used.size() % COLOR_COUNT;
    }

    /** The colour of {@code name}: its entrada's (the first in document order), or {@link #NO_COLOR} without one. */
    public static int colorOf(CircuitDocument document, String name) {
        return entrances(document).stream()
                .filter(entrance -> nameOf(entrance).equals(name))
                .findFirst()
                .map(Tunnels::colorParameter)
                .orElse(NO_COLOR);
    }

    /**
     * The command (one undo step) that renames tunnel {@code id} to {@code newName} (trimmed). An entrada takes every
     * tunnel of its old name along (its salidas, and any other entrada of that name), unless another entrada already
     * has {@code newName}: then only this one changes and the duplicate shows as a problem. A salida renames only
     * itself. Throws IllegalArgumentException when {@code id} is not a tunnel or the name is blank.
     */
    public static Command renameCommand(CircuitDocument document, ComponentTypeRegistry registry, ElementId id,
                                        String newName) {
        Component tunnel = document.component(id)
                .filter(component -> isTunnel(component.typeId()))
                .orElseThrow(() -> new IllegalArgumentException(id + " is not a tunnel"));
        String name = normalizeName(newName);
        List<Component> renamed = carriesItsName(document, tunnel, name)
                ? tunnelsNamed(document, nameOf(tunnel))
                : List.of(tunnel);
        List<Command> changes = renamed.stream()
                .<Command>map(component -> new ChangeParametersCommand(component.id(), registry.typeOf(component),
                        withName(component, name)))
                .toList();
        return new CompositeCommand("command.renameTunnel", changes);
    }

    /** True when renaming {@code tunnel} to {@code newName} renames every tunnel of its current name. */
    private static boolean carriesItsName(CircuitDocument document, Component tunnel, String newName) {
        return kindOf(tunnel).orElseThrow() == TunnelKind.ENTRANCE
                && entrances(document).stream()
                .noneMatch(entrance -> !entrance.id().equals(tunnel.id()) && nameOf(entrance).equals(newName));
    }

    private static List<Component> tunnelsNamed(CircuitDocument document, String name) {
        return document.components().stream()
                .filter(component -> isTunnel(component.typeId()) && nameOf(component).equals(name))
                .toList();
    }

    private static Map<String, Object> withName(Component component, String name) {
        Map<String, Object> parameters = new HashMap<>(component.parameters());
        parameters.put(NAME, name);
        return parameters;
    }

    private static List<Component> entrances(CircuitDocument document) {
        return document.components().stream()
                .filter(component -> kindOf(component).orElse(null) == TunnelKind.ENTRANCE)
                .toList();
    }

    private static Set<String> entranceNames(CircuitDocument document) {
        return entrances(document).stream().map(Tunnels::nameOf).collect(Collectors.toSet());
    }

    private static int colorParameter(Component entrance) {
        Object color = entrance.parameters().get(COLOR);
        return color instanceof Number number ? number.intValue() : 0;
    }
}
