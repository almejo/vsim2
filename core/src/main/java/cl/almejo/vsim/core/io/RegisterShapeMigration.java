package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.BlockSignals;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Up to format 4 the register was 12 x 7 with EN, CLR and CLK at y = 7; from format 5 it is 12 x 4 (12 x 5 over 16
 * bits) and those pins sit on its new bottom edge. This rewrites the JSON of an old file before it is read, like
 * {@link LegacyGateMigration} and in the same place (circuits, library components, clipboard fragments and every
 * definition they carry): for each register, a wire from each moved pin's new position to its old one, turned with
 * the register, only where a wire or another part's pin touched the old position. So an old circuit opens connected
 * and behaves the same; an unconnected pin gets no stub. Pin indices do not change, so probes stay put.
 */
public final class RegisterShapeMigration {

    /** The format that introduced the thin register; older files are migrated. */
    static final int FIRST_VERSION_WITH_THIN_REGISTER = 5;
    /** The y of EN, CLR and CLK in the old 12 x 7 register. */
    private static final int OLD_BOTTOM = 7;
    /** The x of EN, CLR and CLK, the same before and after. */
    private static final List<Integer> CONTROL_COLUMNS = List.of(6, 8, 10);
    /** Every pin of the old register (D, EN, CLR, CLK, Q), for a register that touches another one. */
    private static final List<GridPoint> OLD_REGISTER_PINS = List.of(new GridPoint(0, 2), new GridPoint(6, OLD_BOTTOM),
            new GridPoint(8, OLD_BOTTOM), new GridPoint(10, OLD_BOTTOM), new GridPoint(12, 2));
    private static final String COMPONENTS = "components";
    private static final String WIRES = "wires";
    private static final String DEFINITIONS = "definitions";
    private static final String ID = "id";
    private static final String TYPE = "type";
    private static final String PARAMETERS = "parameters";
    private static final String WIRE_PREFIX = "w";
    /** The lists whose entries have ids that share the document's numbering ({@code c1}, {@code w2}, …). */
    private static final List<String> NUMBERED_LISTS = List.of(COMPONENTS, WIRES, "labels", "frames", "stickyNotes");

    private RegisterShapeMigration() {
    }

    /** Whether a file of format {@code version} has the old 12 x 7 register. */
    public static boolean needsMigration(int version) {
        return version < FIRST_VERSION_WITH_THIN_REGISTER;
    }

    /**
     * Adds, in place, the stubs every register of {@code fileRoot} (a circuit, component or fragment file) and of every
     * definition it carries needs, when its {@code formatVersion} is older than 5; a newer file is left alone. Running
     * it twice adds nothing more.
     */
    public static void migrate(ObjectNode fileRoot) {
        JsonNode version = fileRoot.path(FileHeader.FORMAT_VERSION_KEY);
        if (!version.isIntegralNumber() || !needsMigration(version.intValue())) {
            return;
        }
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        migrateBody(fileRoot, registry);
        for (JsonNode definition : fileRoot.path(DEFINITIONS)) {
            if (definition instanceof ObjectNode body) {
                migrateBody(body, registry);
            }
        }
    }

    /** A straight wire as written in the file. */
    private record Segment(GridPoint start, GridPoint end) {

        boolean contains(GridPoint point) {
            if (start.x() == end.x() && point.x() == start.x()) {
                return between(point.y(), start.y(), end.y());
            }
            if (start.y() == end.y() && point.y() == start.y()) {
                return between(point.x(), start.x(), end.x());
            }
            return point.equals(start) || point.equals(end);
        }

        boolean joins(GridPoint first, GridPoint second) {
            return start.equals(first) && end.equals(second) || start.equals(second) && end.equals(first);
        }

        private static boolean between(int value, int first, int second) {
            return Math.min(first, second) <= value && value <= Math.max(first, second);
        }
    }

    /** A placed part: its id and where its pins are. */
    private record PlacedPins(String id, List<GridPoint> pins) {
    }

    private static void migrateBody(ObjectNode body, ComponentTypeRegistry registry) {
        List<ObjectNode> registers = new ArrayList<>();
        for (JsonNode component : body.path(COMPONENTS)) {
            if (component instanceof ObjectNode entry && isRegister(entry)) {
                registers.add(entry);
            }
        }
        if (registers.isEmpty()) {
            return;
        }
        List<Segment> wires = new ArrayList<>();
        body.path(WIRES).forEach(wire -> wires.add(segment(wire)));
        List<PlacedPins> placed = new ArrayList<>();
        body.path(COMPONENTS).forEach(component -> placed.add(placedPins(component, registry)));
        long nextNumber = highestIdNumber(body) + 1;
        for (ObjectNode register : registers) {
            for (Segment stub : stubsFor(register, wires, placed)) {
                ArrayNode list = body.path(WIRES) instanceof ArrayNode existing ? existing : body.putArray(WIRES);
                list.addObject().put(ID, WIRE_PREFIX + nextNumber++)
                        .put("x1", stub.start().x()).put("y1", stub.start().y())
                        .put("x2", stub.end().x()).put("y2", stub.end().y());
                wires.add(stub);
            }
        }
    }

    /** The stubs one register needs: from each moved pin to its old place, where something touched that place. */
    private static List<Segment> stubsFor(ObjectNode register, List<Segment> wires, List<PlacedPins> placed) {
        GridPoint position = position(register);
        Rotation rotation = rotation(register);
        int newBottom = StandardComponentTypes.registerHeight(width(register));
        String id = register.path(ID).asText("");
        List<Segment> stubs = new ArrayList<>();
        for (int column : CONTROL_COLUMNS) {
            GridPoint oldPoint = position.plus(rotation.apply(new GridPoint(column, OLD_BOTTOM)));
            GridPoint newPoint = position.plus(rotation.apply(new GridPoint(column, newBottom)));
            boolean alreadyStubbed = wires.stream().anyMatch(wire -> wire.joins(newPoint, oldPoint));
            if (!alreadyStubbed && touched(oldPoint, id, wires, placed)) {
                stubs.add(new Segment(newPoint, oldPoint));
            }
        }
        return stubs;
    }

    private static boolean touched(GridPoint point, String registerId, List<Segment> wires, List<PlacedPins> placed) {
        return wires.stream().anyMatch(wire -> wire.contains(point))
                || placed.stream().filter(other -> !other.id().equals(registerId))
                .anyMatch(other -> other.pins().contains(point));
    }

    /**
     * Where a part's pins are: an (old) register's from {@link #OLD_REGISTER_PINS}, a built-in part's from its type;
     * none for a user component or anything that does not resolve (such a pin on an old position gets no stub).
     */
    private static PlacedPins placedPins(JsonNode component, ComponentTypeRegistry registry) {
        String id = component.path(ID).asText("");
        GridPoint position = position(component);
        Rotation rotation = rotation(component);
        List<GridPoint> locals;
        if (isRegister(component)) {
            locals = OLD_REGISTER_PINS;
        } else {
            locals = localPins(component, registry);
        }
        return new PlacedPins(id, locals.stream().map(local -> position.plus(rotation.apply(local))).toList());
    }

    private static List<GridPoint> localPins(JsonNode component, ComponentTypeRegistry registry) {
        Optional<ComponentType> type = registry.find(component.path(TYPE).asText(""));
        if (type.isEmpty()) {
            return List.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = new ObjectMapper().convertValue(component.path(PARAMETERS), Map.class);
            return type.get().specializedFor(parameters == null ? Map.of() : parameters).pins().stream()
                    .map(PinSpec::position).toList();
        } catch (IllegalArgumentException exception) {
            // A part the reader will refuse anyway: it touches nothing here.
            return List.of();
        }
    }

    private static boolean isRegister(JsonNode component) {
        return StandardComponentTypes.REGISTER.equals(component.path(TYPE).asText(""));
    }

    private static int width(JsonNode register) {
        return register.path(PARAMETERS).path(StandardComponentTypes.WIDTH)
                .asInt(BlockSignals.WIDTH.defaultValue() instanceof Integer width ? width : 8);
    }

    private static GridPoint position(JsonNode component) {
        return new GridPoint(component.path("x").asInt(), component.path("y").asInt());
    }

    private static Rotation rotation(JsonNode component) {
        return Rotation.fromQuarterTurns(component.path("rotation").asInt());
    }

    private static Segment segment(JsonNode wire) {
        return new Segment(new GridPoint(wire.path("x1").asInt(), wire.path("y1").asInt()),
                new GridPoint(wire.path("x2").asInt(), wire.path("y2").asInt()));
    }

    /** The highest number in the ids of the body's elements ({@code w12} → 12), 0 when there is none. */
    private static long highestIdNumber(ObjectNode body) {
        long highest = 0;
        for (String list : NUMBERED_LISTS) {
            for (JsonNode element : body.path(list)) {
                String digits = element.path(ID).asText("").replaceAll("\\D", "");
                if (!digits.isEmpty() && digits.length() < 18) {
                    highest = Math.max(highest, Long.parseLong(digits));
                }
            }
        }
        return highest;
    }
}
