package cl.almejo.vsim.core.tunnel;

import cl.almejo.vsim.core.command.ChangeParametersCommand;
import cl.almejo.vsim.core.command.Command;
import cl.almejo.vsim.core.command.CompositeCommand;
import cl.almejo.vsim.core.command.MoveElementsCommand;
import cl.almejo.vsim.core.component.ComponentShape;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.ParameterSpec;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.io.CircuitFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
    /** A name renamed apart from another keeps it and adds this and a number: CLK_2, CLK_3, … */
    private static final String VARIANT_SEPARATOR = "_";
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
     * The shape of a tunnel of {@code kind} with these (resolved) parameters: a tag {@link #lengthInGridUnits} long
     * and two units thick, and the pin one grid unit outside it on the wire's side, in the middle of a short side. At
     * rotation 0 the wire arrives at an entrada from the left (pin at (0, 1), tag from x = 1) and leaves a salida to
     * the right (tag from x = 0, pin at the right end), so the two kinds look different. The pin has no width of its
     * own ({@link PinSpec#adaptive}).
     */
    public static ComponentShape shape(TunnelKind kind, Map<String, Object> parameters) {
        int length = lengthInGridUnits((String) parameters.get(NAME));
        int pinX = kind == TunnelKind.ENTRANCE ? 0 : 1 + length;
        return new ComponentShape(new GridSize(1 + length, TAG_THICKNESS),
                List.of(PinSpec.adaptive("pin", pinX, TAG_THICKNESS / 2)));
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

    /**
     * The names, sorted, that have tunnels both among {@code ids} and outside them in {@code document}: replacing
     * {@code ids} by a component would cut those names in two, since names never cross a component's boundary.
     */
    public static List<String> namesSplitBy(CircuitDocument document, Collection<ElementId> ids) {
        Set<ElementId> inside = Set.copyOf(ids);
        Set<String> namesInside = new TreeSet<>();
        Set<String> namesOutside = new HashSet<>();
        for (Component component : document.components()) {
            if (isTunnel(component.typeId())) {
                (inside.contains(component.id()) ? namesInside : namesOutside).add(nameOf(component));
            }
        }
        namesInside.retainAll(namesOutside);
        return List.copyOf(namesInside);
    }

    /**
     * A fragment after {@link #renamedApartFrom}: {@code renames} maps each name that changed to its new name, sorted
     * by the old name; empty when nothing changed (then {@code fragment} is the one given).
     */
    public record RenamedFragment(CircuitFragment fragment, Map<String, String> renames) {
        public RenamedFragment {
            renames = Collections.unmodifiableMap(new TreeMap<>(renames));
        }
    }

    /**
     * {@code fragment} ready to be pasted into {@code document} without joining anything through a name: each name of
     * its tunnels that a tunnel of {@code document} already uses becomes the first free {@code NAME_2}, {@code NAME_3},
     * … (free in both), on every tunnel of that name in the fragment, each kept at its pin ({@link #renamedInPlace}).
     * «Desarmar» uses it so a component's inner tunnels stay its own once pasted.
     */
    public static RenamedFragment renamedApartFrom(CircuitDocument document, CircuitFragment fragment,
                                                   ComponentTypeRegistry registry) {
        Set<String> namesOutside = tunnelNames(document.components());
        Set<String> namesInside = new TreeSet<>(tunnelNames(fragment.components()));
        Set<String> taken = new HashSet<>(namesOutside);
        taken.addAll(namesInside);
        Map<String, String> renames = new TreeMap<>();
        for (String name : namesInside) {
            if (namesOutside.contains(name)) {
                String free = freeVariantOf(name, taken);
                taken.add(free);
                renames.put(name, free);
            }
        }
        if (renames.isEmpty()) {
            return new RenamedFragment(fragment, renames);
        }
        List<Component> components = fragment.components().stream()
                .map(component -> isTunnel(component.typeId()) && renames.containsKey(nameOf(component))
                        ? renamedInPlace(component, renames.get(nameOf(component)), registry)
                        : component)
                .toList();
        return new RenamedFragment(new CircuitFragment(components, fragment.wires(), fragment.labels(),
                fragment.definitions(), fragment.frames()), renames);
    }

    /** The first of {@code name_2}, {@code name_3}, … not in {@code taken}. */
    private static String freeVariantOf(String name, Set<String> taken) {
        int number = 2;
        while (taken.contains(name + VARIANT_SEPARATOR + number)) {
            number++;
        }
        return name + VARIANT_SEPARATOR + number;
    }

    private static Set<String> tunnelNames(List<Component> components) {
        return components.stream()
                .filter(component -> isTunnel(component.typeId()))
                .map(Tunnels::nameOf)
                .collect(Collectors.toSet());
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
        return colorsByName(document).getOrDefault(name, NO_COLOR);
    }

    /**
     * The colour of every name that has an entrada (the first entrada's, in document order), in one pass over the
     * document: what a frame needs to colour each salida like its entrada. Names without entrada are absent.
     */
    public static Map<String, Integer> colorsByName(CircuitDocument document) {
        Map<String, Integer> colors = new HashMap<>();
        for (Component entrance : entrances(document)) {
            colors.putIfAbsent(nameOf(entrance), colorParameter(entrance));
        }
        return Collections.unmodifiableMap(colors);
    }

    /**
     * The colours by name once {@code added} (a pasted fragment, a component being placed) joins {@code document}:
     * its entradas come after the circuit's, so a name the circuit already has keeps the circuit's colour and only
     * new names take a pasted entrada's. What the ghost of a salida is drawn in.
     */
    public static Map<String, Integer> colorsByName(CircuitDocument document, Collection<Component> added) {
        Map<String, Integer> colors = new HashMap<>(colorsByName(document));
        for (Component component : added) {
            if (kindOf(component).orElse(null) == TunnelKind.ENTRANCE) {
                colors.putIfAbsent(nameOf(component), colorParameter(component));
            }
        }
        return Collections.unmodifiableMap(colors);
    }

    /** The palette colour of an entrada component (its parameter), or {@link #NO_COLOR} for a salida. */
    public static int colorParameterOf(Component tunnel) {
        return kindOf(tunnel).orElse(null) == TunnelKind.ENTRANCE ? colorParameter(tunnel) : NO_COLOR;
    }

    /**
     * The command (one undo step) that renames tunnel {@code id} to {@code newName} (trimmed). The only entrada of its
     * name takes its salidas along, unless another entrada already has {@code newName}: then only this one changes and
     * the duplicate shows as a problem. One of two or more entradas of a name (a pasted or duplicated copy) renames
     * only itself, so renaming the copy fixes the duplicate instead of carrying the original along. A salida renames
     * only itself. Throws IllegalArgumentException when {@code id} is not a tunnel or the name is blank.
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
        List<Command> changes = new ArrayList<>();
        for (Component component : renamed) {
            changes.add(new ChangeParametersCommand(component.id(), registry.typeOf(component),
                    withName(component, name)));
            GridPoint shift = positionShift(component, renamedInPlace(component, name, registry));
            if (!shift.equals(GridPoint.ORIGIN)) {
                changes.add(new MoveElementsCommand(List.of(component.id()), shift.x(), shift.y()));
            }
        }
        return new CompositeCommand("command.renameTunnel", changes);
    }

    /**
     * Tunnel {@code tunnel} named {@code newName} (trimmed), moved so its pin stays where its wire is: the tag grows
     * with the name, and a salida's pin sits at the far end of the tag, so a longer name would otherwise move it.
     */
    public static Component renamedInPlace(Component tunnel, String newName, ComponentTypeRegistry registry) {
        Component renamed = tunnel.withParameters(withName(tunnel, normalizeName(newName)));
        GridPoint before = registry.typeOf(tunnel).pinLocation(tunnel, 0);
        GridPoint after = registry.typeOf(renamed).pinLocation(renamed, 0);
        return renamed.translate(before.x() - after.x(), before.y() - after.y());
    }

    /** How far {@code moved} lies from {@code original}. */
    private static GridPoint positionShift(Component original, Component moved) {
        return new GridPoint(moved.position().x() - original.position().x(),
                moved.position().y() - original.position().y());
    }

    /**
     * True when renaming {@code tunnel} to {@code newName} renames every tunnel of its current name: it is the only
     * entrada of that name and no other entrada has {@code newName}.
     */
    private static boolean carriesItsName(CircuitDocument document, Component tunnel, String newName) {
        if (kindOf(tunnel).orElseThrow() != TunnelKind.ENTRANCE) {
            return false;
        }
        String oldName = nameOf(tunnel);
        return entrances(document).stream()
                .filter(entrance -> !entrance.id().equals(tunnel.id()))
                .map(Tunnels::nameOf)
                .noneMatch(name -> name.equals(oldName) || name.equals(newName));
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
