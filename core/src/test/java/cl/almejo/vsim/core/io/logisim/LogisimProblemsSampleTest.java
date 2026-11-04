package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionPin;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.tunnel.Tunnels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where every problem of the imported real Logisim file of {@link LogisimSampleTest} comes from (manual, skipped
 * without {@code -Dvsim.logisim.sample}): each circuit keeps Logisim's nets, and every
 * problem left is one the Logisim file has itself — a circuit's pin that reaches nothing inside, a tunnel its name has
 * alone; none is the importer's (every tunnel comes without direction, so a name two places drive is no problem). Inside a component the canvas rule holds (a loose pin of an
 * inner part is no problem), so there are no others. Prints the problems by origin.
 */
class LogisimProblemsSampleTest {

    /** Where a problem comes from. */
    private enum Origin {
        /** A circuit's {@code Pin} that touches nothing inside the circuit in the Logisim file too. */
        INTERFACE_LOOSE_IN_LOGISIM,
        /** A tunnel whose name no other tunnel of its circuit has, in the Logisim file. */
        LONE_TUNNEL_IN_LOGISIM,
        /** Anything else: the importer's fault. */
        IMPORTER
    }

    private record Circuit(LogisimNetAnalysis analysis, Map<ElementId, LogisimComponent> origins) {
    }

    @Test
    void everyProblemLeftComesFromTheLogisimFile() throws Exception {
        LogisimProject project = LogisimSampleTest.readSample();
        ComponentTypeRegistry baseRegistry = StandardComponentTypes.registry();
        Map<String, Circuit> circuits = new HashMap<>();
        List<String> mismatches = new ArrayList<>();
        CircuitDocument document = LogisimImporter.importProject(project, baseRegistry,
                (analysis, laidOut, registry) -> {
                    Map<ElementId, LogisimComponent> origins = new HashMap<>();
                    laidOut.parts().forEach((component, id) -> origins.put(id, component));
                    circuits.put(analysis.circuit().name(), new Circuit(analysis, origins));
                    NetEquivalence.mismatches(laidOut, registry)
                            .forEach(mismatch -> mismatches.add(analysis.circuit().name() + ": " + mismatch));
                }).document();
        assertThat(mismatches).as("VSim's nets are Logisim's").isEmpty();

        ComponentTypeRegistry registry = baseRegistry.extendedWith(SubcircuitTypes.resolverFor(document));
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry)
                .flatten(document, new NetlistCompiler(registry).compile(document)));
        Map<String, Integer> byOrigin = new TreeMap<>();
        List<String> importerFaults = new ArrayList<>();
        for (Problem problem : engine.snapshot().problems()) {
            String line = describe(problem, document, project, circuits);
            String origin = line.substring(0, line.indexOf(' '));
            byOrigin.merge(origin, 1, Integer::sum);
            System.out.println("   " + line);
            if (origin.equals(Origin.IMPORTER.name())) {
                importerFaults.add(line);
            }
        }
        System.out.println("== problems by origin: " + byOrigin);
        assertThat(importerFaults).isEmpty();
    }

    /** The problem's origin, then where it is and what Logisim has there. */
    private static String describe(Problem problem, CircuitDocument document,
                                   LogisimProject project, Map<String, Circuit> circuits) {
        if (problem instanceof ConnectivityProblem connectivity
                && connectivity.cause() == ConnectivityProblem.Cause.UNCONNECTED_INTERFACE) {
            return describeInterface(connectivity, document, project, circuits);
        }
        if (problem instanceof TunnelProblem tunnel) {
            Level level = levelOf(document, project, tunnel.tunnels().getFirst());
            List<LogisimComponent> named = circuits.get(level.circuit()).analysis().circuit().components().stream()
                    .filter(component -> component.name().equals("Tunnel")
                            && component.attributes().text("label").trim().equals(tunnel.name()))
                    .toList();
            Origin origin = switch (tunnel.kind()) {
                case LONE_TUNNEL -> named.size() == 1 ? Origin.LONE_TUNNEL_IN_LOGISIM : Origin.IMPORTER;
                default -> Origin.IMPORTER;
            };
            return origin + " " + level.circuit() + " › " + Tunnels.class.getSimpleName() + " «" + tunnel.name()
                    + "» " + tunnel.kind() + " (" + named.size() + " in Logisim)";
        }
        return Origin.IMPORTER + " " + problem;
    }

    /** An outer terminal whose interface pin reaches nothing inside: from the file when Logisim's Pin is loose too. */
    private static String describeInterface(ConnectivityProblem problem, CircuitDocument document,
                                            LogisimProject project, Map<String, Circuit> circuits) {
        Level level = levelOf(document, project, problem.elementId());
        Component instance = level.document().component(new ElementId(level.lastSegment())).orElseThrow();
        ComponentDefinition definition = SubcircuitTypes.definitionIdOf(instance.typeId())
                .flatMap(document::definition).orElseThrow();
        DefinitionPin terminal = definition.pinout().get(problem.pinIndex());
        Circuit inside = circuits.get(definition.name());
        LogisimComponent pin = inside.origins().get(terminal.source());
        String where = level.circuit() + " › " + definition.name() + " pin «" + terminal.spec().name() + "»";
        if (pin == null) {
            return Origin.IMPORTER + " " + where + " (no Logisim pin)";
        }
        boolean connected = inside.analysis().ends(pin).stream()
                .anyMatch(end -> inside.analysis().isConnected(end.point()));
        return (connected ? Origin.IMPORTER : Origin.INTERFACE_LOOSE_IN_LOGISIM) + " " + where
                + " (Logisim's Pin at " + pin.location().x() + "," + pin.location().y() + ")";
    }

    /** The circuit (by Logisim name) and the document a flat id's last segment is in. */
    private record Level(String circuit, CircuitDocument document, String lastSegment) {
    }

    private static Level levelOf(CircuitDocument document, LogisimProject project, String flatId) {
        List<String> segments = InstancePath.segments(flatId);
        String circuit = project.mainCircuit();
        CircuitDocument level = document;
        for (String segment : segments.subList(0, segments.size() - 1)) {
            Component instance = level.component(new ElementId(segment)).orElseThrow();
            ComponentDefinition definition = SubcircuitTypes.definitionIdOf(instance.typeId())
                    .flatMap(document::definition).orElseThrow();
            circuit = definition.name();
            level = definition.bodyDocument();
        }
        return new Level(circuit, level, segments.getLast());
    }
}
