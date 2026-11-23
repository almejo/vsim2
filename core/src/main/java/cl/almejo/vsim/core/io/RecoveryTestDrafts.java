package cl.almejo.vsim.core.io;

import cl.almejo.vsim.core.machine.StateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The machines' test drafts a recovery copy carries (fix round 2 of hito 4): while a machine is being tested, the
 * editor's copy of the circuit keeps the draft's diagram apart, in a top-level field {@value #FIELD} only recovery
 * copies have, one entry per machine definition: {@code {"definition": id, "machine": {…}}}, the machine written as
 * in a definition. The circuit itself is untouched: every reader ignores the field (format 7 reads unknown fields as
 * nothing), so a saved file never has it and an older VSim opens a copy as the circuit it is.
 */
public final class RecoveryTestDrafts {

    /** The top-level field of a recovery copy that holds the drafts. */
    public static final String FIELD = "testDrafts";
    private static final String DEFINITION = "definition";
    private static final String MACHINE = "machine";
    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryTestDrafts.class);
    private static final ObjectMapper MAPPER = CircuitFormat.fileMapper();

    private RecoveryTestDrafts() {
    }

    /**
     * {@code circuit} (a circuit file's text) with {@code drafts} (definition id → the draft machine) in the field;
     * {@code circuit} itself when there are none. Entries are written by definition id, so the same drafts give the
     * same text.
     */
    public static String withDrafts(String circuit, Map<String, StateMachine> drafts) {
        if (drafts.isEmpty()) {
            return circuit;
        }
        try {
            ObjectNode root = (ObjectNode) MAPPER.readTree(circuit);
            ArrayNode entries = root.putArray(FIELD);
            new TreeMap<>(drafts).forEach((definitionId, machine) -> {
                ObjectNode entry = entries.addObject();
                entry.put(DEFINITION, definitionId);
                entry.set(MACHINE, MAPPER.valueToTree(MachineEntries.entry(machine)));
            });
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException | ClassCastException exception) {
            throw new IllegalStateException("cannot write the test drafts into the recovery copy", exception);
        }
    }

    /**
     * The drafts {@code copy} carries, by definition id, in the order written; empty for a circuit without them or
     * a text that is not one. An entry that cannot be read is left out (and logged): the circuit still recovers.
     */
    public static Map<String, StateMachine> drafts(String copy) {
        JsonNode entries;
        try {
            entries = MAPPER.readTree(copy).path(FIELD);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
        Map<String, StateMachine> drafts = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            String definitionId = entry.path(DEFINITION).asText("");
            try {
                MachineEntries.MachineEntry machine = MAPPER.treeToValue(entry.path(MACHINE),
                        MachineEntries.MachineEntry.class);
                if (!definitionId.isEmpty() && machine != null) {
                    drafts.put(definitionId, MachineEntries.machine(machine, definitionId));
                }
            } catch (JsonProcessingException | IllegalArgumentException | CircuitFormatException exception) {
                LOGGER.warn("Could not read the test draft of {} in a recovery copy", definitionId, exception);
            }
        }
        return Collections.unmodifiableMap(drafts);
    }
}
