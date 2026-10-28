package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.LogicOperation;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Up to format 3 a gate's type named its operation and inputs ({@code and2} … {@code nor4}) and the inverter was
 * {@code not}; from format 4 they are the parameterized {@code and}, {@code or}, {@code xor} and {@code buffer}. This
 * rewrites the JSON of an old file before it is read, in the one place every VSim 2 file goes through (circuits,
 * library components, clipboard fragments, and the definitions any of them carries), so the user's files open
 * unchanged: pin indices and positions are the same (2..4 inputs keep their rows), so wires and probes stay put. The
 * VSim 1 importer uses the same table ({@link #replacement}).
 */
public final class LegacyGateMigration {

    /** The format that introduced the parameterized gates; older files are migrated. */
    static final int FIRST_VERSION_WITH_PARAMETERIZED_GATES = 4;
    private static final String COMPONENTS = "components";
    private static final String DEFINITIONS = "definitions";
    private static final String TYPE = "type";
    private static final String PARAMETERS = "parameters";

    /** An old type id as a new type with the parameters it implies. */
    public record Replacement(String typeId, Map<String, Object> parameters) {

        public Replacement {
            parameters = Map.copyOf(parameters);
        }
    }

    private static final Map<String, Replacement> REPLACEMENTS = replacements();

    private LegacyGateMigration() {
    }

    private static Map<String, Replacement> replacements() {
        Map<String, Replacement> table = new LinkedHashMap<>();
        for (LogicOperation operation : LogicOperation.values()) {
            for (int inputs = 2; inputs <= 4; inputs++) {
                table.put(operation.typeId() + inputs, gate(operation.typeId(), inputs, false));
            }
        }
        for (int inputs = 2; inputs <= 4; inputs++) {
            table.put("nand" + inputs, gate(StandardComponentTypes.AND, inputs, true));
            table.put("nor" + inputs, gate(StandardComponentTypes.OR, inputs, true));
        }
        table.put("not", new Replacement(StandardComponentTypes.BUFFER, Map.of(StandardComponentTypes.NEGATE, true)));
        return Map.copyOf(table);
    }

    private static Replacement gate(String typeId, int inputs, boolean negated) {
        return new Replacement(typeId, Map.of(StandardComponentTypes.GATE_INPUTS, inputs,
                StandardComponentTypes.NEGATE, negated));
    }

    /** The new type and parameters of an old gate id ({@code nand3} → {@code and}, inputs 3, negate), if it is one. */
    public static Optional<Replacement> replacement(String oldTypeId) {
        return Optional.ofNullable(REPLACEMENTS.get(oldTypeId));
    }

    /**
     * The palette id (a type or a preset) that stands for an old gate id in the preferences: {@code and3} →
     * {@code and}, {@code nand2} → {@code nand}, {@code not} → {@code not}; empty for any other id.
     */
    public static Optional<String> paletteIdFor(String oldTypeId) {
        return replacement(oldTypeId).map(found -> StandardComponentTypes.nameId(found.typeId(), found.parameters()));
    }

    /** Whether a file of format {@code version} names its gates the old way. */
    public static boolean needsMigration(int version) {
        return version < FIRST_VERSION_WITH_PARAMETERIZED_GATES;
    }

    /**
     * Rewrites, in place, the old gates of {@code fileRoot} (a circuit, component or fragment file) and of every
     * definition it carries, when its {@code formatVersion} is older than 4; a newer file is left alone. Running it
     * twice changes nothing more.
     */
    public static void migrate(ObjectNode fileRoot) {
        JsonNode version = fileRoot.path(FileHeader.FORMAT_VERSION_KEY);
        if (!version.isIntegralNumber() || !needsMigration(version.intValue())) {
            return;
        }
        migrateComponents(fileRoot.path(COMPONENTS));
        for (JsonNode definition : fileRoot.path(DEFINITIONS)) {
            migrateComponents(definition.path(COMPONENTS));
        }
    }

    private static void migrateComponents(JsonNode components) {
        for (JsonNode component : components) {
            if (component instanceof ObjectNode entry) {
                replacement(entry.path(TYPE).asText("")).ifPresent(found -> rewrite(entry, found));
            }
        }
    }

    /** The new type; the old parameters (the delay) are kept and the replacement's are added. */
    private static void rewrite(ObjectNode entry, Replacement replacement) {
        entry.put(TYPE, replacement.typeId());
        ObjectNode parameters = entry.path(PARAMETERS) instanceof ObjectNode existing ? existing
                : entry.putObject(PARAMETERS);
        replacement.parameters().forEach((name, value) -> {
            switch (value) {
                case Boolean flag -> parameters.put(name, flag);
                case Integer number -> parameters.put(name, number);
                default -> throw new IllegalStateException("unexpected parameter " + name + ": " + value);
            }
        });
    }
}
