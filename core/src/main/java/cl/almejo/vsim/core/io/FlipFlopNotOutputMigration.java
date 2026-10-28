package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Format 5 gave the D flip-flop Q̅ at (4, 3), one unit under Q. A pin joins any wire that passes over its point, so an
 * old circuit whose Q wire runs straight down would get Q and Q̅ on one net: a short. This rewrites the JSON of an old
 * file before it is read, in the same place as {@link RegisterShapeMigration} (circuits, library components, clipboard
 * fragments and every definition they carry): every D flip-flop gets «Mostrar Q̅» off, so it keeps the three pins it
 * always had and the circuit behaves the same. The user can turn Q̅ on afterwards.
 */
public final class FlipFlopNotOutputMigration {

    private static final String COMPONENTS = "components";
    private static final String DEFINITIONS = "definitions";
    private static final String PARAMETERS = "parameters";

    private FlipFlopNotOutputMigration() {
    }

    /**
     * Turns «Mostrar Q̅» off, in place, on every D flip-flop of {@code fileRoot} and of every definition it carries, when
     * its {@code formatVersion} is older than 5; a newer file is left alone. Running it twice changes nothing more.
     */
    public static void migrate(ObjectNode fileRoot) {
        JsonNode version = fileRoot.path(FileHeader.FORMAT_VERSION_KEY);
        if (!version.isIntegralNumber() || !RegisterShapeMigration.needsMigration(version.intValue())) {
            return;
        }
        migrateBody(fileRoot);
        for (JsonNode definition : fileRoot.path(DEFINITIONS)) {
            migrateBody(definition);
        }
    }

    private static void migrateBody(JsonNode body) {
        for (JsonNode component : body.path(COMPONENTS)) {
            if (component instanceof ObjectNode entry
                    && StandardComponentTypes.FLIP_FLOP_DATA.equals(entry.path("type").asText(""))) {
                JsonNode parameters = entry.path(PARAMETERS);
                ObjectNode writable = parameters instanceof ObjectNode existing ? existing : entry.putObject(PARAMETERS);
                writable.put(StandardComponentTypes.SHOW_INVERTED, false);
            }
        }
    }
}
