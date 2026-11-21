package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The machines being tested now, one test per machine (review of hito 4, finding 4): two drafts of the same machine
 * would each write their own tests on leaving, the second over the first. «Probar…» in another view of a machine
 * being tested joins its test instead, and a new view of it tests from the start (fix round 2,
 * {@link MachineMode#startTest()}). Keyed by the circuit (the document whose definition the machine is) and the
 * machine's definition id; a circuit that is gone is forgotten. EDT only.
 */
final class MachineTestsUnderWay {

    private static final Map<OpenDocument, Map<String, MachineTestUnderWay>> UNDER_WAY = new WeakHashMap<>();

    private MachineTestsUnderWay() {
    }

    /** The test of {@code definitionId} in {@code circuit}, while there is one. */
    static Optional<MachineTestUnderWay> of(OpenDocument circuit, String definitionId) {
        return Optional.ofNullable(UNDER_WAY.get(circuit)).map(tests -> tests.get(definitionId));
    }

    /**
     * {@code test} tests {@code definitionId} of {@code circuit} from now on; returns what says it no longer does
     * (running it after another test took the machine does nothing).
     */
    static Runnable add(OpenDocument circuit, String definitionId, MachineTestUnderWay test) {
        Objects.requireNonNull(test, "test");
        UNDER_WAY.computeIfAbsent(circuit, key -> new HashMap<>()).put(definitionId, test);
        return () -> {
            Map<String, MachineTestUnderWay> tests = UNDER_WAY.get(circuit);
            if (tests != null && tests.remove(definitionId, test) && tests.isEmpty()) {
                UNDER_WAY.remove(circuit);
            }
        };
    }

    /** The circuit {@code place} belongs to: its parent for a body place, itself for a circuit. */
    static OpenDocument circuitOf(OpenDocument place) {
        return place.parent().orElse(place);
    }
}
