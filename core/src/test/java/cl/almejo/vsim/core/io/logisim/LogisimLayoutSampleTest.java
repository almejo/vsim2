package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The geometry step on the real Logisim file of {@link LogisimSampleTest} (manual, skipped without
 * {@code -Dvsim.logisim.sample}): every circuit laid out, how its pins met their ends, and whether VSim's nets are
 * Logisim's. Subcircuit instances use stand-in parts whose pins sit where the instance's Logisim ends are (the
 * subcircuit step makes the real ones).
 */
class LogisimLayoutSampleTest {

    @Test
    void everyCircuitOfTheSampleKeepsItsNets() throws Exception {
        LogisimProject project = LogisimSampleTest.readSample();
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        Map<String, SubcircuitPart> standIns = registerStandIns(project, registry);

        int totalMismatches = 0;
        System.out.println("== layout (circuit: pins on their end, routed, added wires, unrouted, turned parts |"
                + " parts, wires, texts | net mismatches)");
        List<ImportNote> layoutNotes = new ArrayList<>();
        for (LogisimCircuit circuit : project.circuits()) {
            LogisimNetAnalysis analysis = LogisimNets.analyze(circuit, project);
            LaidOutCircuit laidOut = new LogisimLayout(registry).layOut(analysis,
                    component -> analysis.mappingContext(component, registry),
                    name -> Optional.ofNullable(standIns.get(name)));
            List<String> mismatches = NetEquivalence.mismatches(laidOut, registry);
            totalMismatches += mismatches.size();
            LaidOutCircuit.Statistics statistics = laidOut.statistics();
            System.out.println("   " + circuit.name() + ": " + statistics.pinsOnTheirEnd() + " on their end, "
                    + statistics.pinsRouted() + " routed, " + statistics.addedWires() + " added wires, "
                    + statistics.unrouted() + " unrouted, " + statistics.turnedParts() + " turned | "
                    + laidOut.document().components().size() + " parts, "
                    + laidOut.document().wires().size() + " wires, " + laidOut.document().labels().size()
                    + " texts | " + mismatches.size() + " mismatches");
            mismatches.forEach(mismatch -> System.out.println("      " + mismatch));
            laidOut.notes().stream()
                    .filter(note -> note.messageKey().equals(LogisimNotes.UNROUTED_PIN)
                            || note.messageKey().equals(LogisimNotes.CROWDED_PART))
                    .forEach(layoutNotes::add);
        }
        System.out.println("== layout notes: " + layoutNotes.size());
        layoutNotes.forEach(note -> System.out.println("   " + note.circuit() + " " + note.where().orElseThrow() + " "
                + note.messageKey() + " " + note.arguments()));

        assertThat(totalMismatches).isZero();
    }

    /**
     * For each circuit a stand-in part: its pins where an instance's Logisim ends are when it faces east at the
     * origin, named and numbered as the ends.
     */
    private static Map<String, SubcircuitPart> registerStandIns(LogisimProject project,
                                                                 ComponentTypeRegistry registry) {
        Map<String, SubcircuitPart> standIns = new HashMap<>();
        for (LogisimCircuit circuit : project.circuits()) {
            LogisimComponent instance = new LogisimComponent("", circuit.name(), new LogisimPoint(0, 0),
                    LogisimAttributes.of(Map.of("facing", "east")));
            List<PinSpec> pins = new ArrayList<>();
            Map<LogisimPoint, Integer> pinOfInnerPin = new LinkedHashMap<>();
            for (LogisimEnd end : LogisimEnds.of(instance, project)) {
                GridPoint point = LogisimLayout.scaled(end.point());
                pinOfInnerPin.put(LogisimPoint.parse(end.name()), pins.size());
                pins.add(end.role() == LogisimEnd.Role.OUTPUT
                        ? PinSpec.output(end.name(), point.x(), point.y(), end.width())
                        : PinSpec.input(end.name(), point.x(), point.y(), end.width()));
            }
            String typeId = "stand-in:" + circuit.name();
            registry.register(new ComponentType(typeId, ComponentCategory.GATES, new GridSize(4, 4), pins,
                    List.of(), parameters -> null));
            standIns.put(circuit.name(), new SubcircuitPart(typeId, Map.of(), pinOfInnerPin));
        }
        return standIns;
    }
}
