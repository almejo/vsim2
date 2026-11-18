package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.ui.app.OpenDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * The machines being tested now, one test mode per machine (review of hito 4, finding 4): two tabs testing the same
 * machine would each write their own tests on leaving, the second over the first. «Probar…» in another tab of a machine
 * being tested goes to the tab that tests it instead ({@link MachineMode#startTest()}). Keyed by the circuit (the
 * document whose definition the machine is) and the machine's definition id; a circuit that is gone is forgotten.
 * EDT only.
 */
final class MachineTestsUnderWay {

    private static final Map<OpenDocument, Map<String, MachineTestMode>> UNDER_WAY = new WeakHashMap<>();

    private MachineTestsUnderWay() {
    }

    /** The test mode of {@code definitionId} in {@code circuit}, while there is one. */
    static Optional<MachineTestMode> of(OpenDocument circuit, String definitionId) {
        return Optional.ofNullable(UNDER_WAY.get(circuit)).map(modes -> modes.get(definitionId));
    }

    /**
     * {@code mode} tests {@code definitionId} of {@code circuit} from now on; returns what says it no longer does
     * (running it after another mode took the machine does nothing).
     */
    static Runnable add(OpenDocument circuit, String definitionId, MachineTestMode mode) {
        Objects.requireNonNull(mode, "mode");
        UNDER_WAY.computeIfAbsent(circuit, key -> new HashMap<>()).put(definitionId, mode);
        return () -> {
            Map<String, MachineTestMode> modes = UNDER_WAY.get(circuit);
            if (modes != null && modes.remove(definitionId, mode) && modes.isEmpty()) {
                UNDER_WAY.remove(circuit);
            }
        };
    }

    /** The circuit {@code place} belongs to: its parent for a body place, itself for a circuit. */
    static OpenDocument circuitOf(OpenDocument place) {
        return place.parent().orElse(place);
    }
}
