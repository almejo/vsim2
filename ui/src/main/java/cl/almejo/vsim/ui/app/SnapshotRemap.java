package cl.almejo.vsim.ui.app;

import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.simulation.DriverRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The document's snapshot seen from inside one instance (spec §10.3): the flattener names a body's parts and nets
 * {@code <path>/<id>}, so the instance's values are the entries under {@code <path>/}, with that prefix taken off —
 * the ids a body tab's canvas knows. Pure and cheap: run it on the caller's thread on the immutable snapshot.
 */
final class SnapshotRemap {

    private SnapshotRemap() {
    }

    /**
     * The entries of {@code snapshot} under {@code path} with the prefix stripped: net values, component states and
     * the short circuits and width mismatches of nets inside it (their parts outside dropped); other problems are the
     * document's (Problemas lists them). Time, revision, running state, token and failure are kept. An empty path
     * («Sin valores») keeps no values and no problems.
     */
    static SimulationSnapshot under(SimulationSnapshot snapshot, Optional<String> path) {
        if (path.isEmpty()) {
            return new SimulationSnapshot(snapshot.revision(), snapshot.token(), snapshot.time(), snapshot.running(),
                    Map.of(), Map.of(), List.of(), snapshot.failure());
        }
        String prefix = path.get() + InstancePath.SEPARATOR;
        return new SimulationSnapshot(snapshot.revision(), snapshot.token(), snapshot.time(), snapshot.running(),
                stripped(snapshot.netValues(), prefix), stripped(snapshot.componentStates(), prefix),
                problemsUnder(snapshot.problems(), prefix), snapshot.failure());
    }

    /** {@code snapshot} with {@code token}: the netlist its ids belong to is the body's, not the document's. */
    static SimulationSnapshot withToken(SimulationSnapshot snapshot, long token) {
        return new SimulationSnapshot(snapshot.revision(), token, snapshot.time(), snapshot.running(),
                snapshot.netValues(), snapshot.componentStates(), snapshot.problems(), snapshot.failure());
    }

    private static <T> Map<String, T> stripped(Map<String, T> entries, String prefix) {
        Map<String, T> result = new HashMap<>();
        entries.forEach((id, value) -> {
            if (id.startsWith(prefix)) {
                result.put(id.substring(prefix.length()), value);
            }
        });
        return result;
    }

    private static List<Problem> problemsUnder(List<Problem> problems, String prefix) {
        List<Problem> result = new ArrayList<>();
        for (Problem problem : problems) {
            switch (problem) {
                case ShortCircuit shortCircuit when shortCircuit.netId().startsWith(prefix) ->
                        result.add(new ShortCircuit(shortCircuit.netId().substring(prefix.length()),
                                drivers(shortCircuit.drivers(), prefix), shortCircuit.sinceTime()));
                case WidthMismatch mismatch when mismatch.netId().startsWith(prefix) ->
                        result.add(new WidthMismatch(mismatch.netId().substring(prefix.length()),
                                pins(mismatch.pins(), prefix)));
                default -> {
                    // the document's problem, or one outside this instance
                }
            }
        }
        return result;
    }

    private static List<DriverRef> drivers(List<DriverRef> drivers, String prefix) {
        return drivers.stream().filter(driver -> driver.componentId().startsWith(prefix))
                .map(driver -> new DriverRef(driver.componentId().substring(prefix.length()), driver.pinIndex(),
                        driver.driven()))
                .toList();
    }

    private static List<FlatPinRef> pins(List<FlatPinRef> pins, String prefix) {
        return pins.stream().filter(pin -> pin.componentId().startsWith(prefix))
                .map(pin -> new FlatPinRef(pin.componentId().substring(prefix.length()), pin.pinIndex()))
                .toList();
    }
}
