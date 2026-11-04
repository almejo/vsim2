package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.TextLabel;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.GridSize;
import cl.almejo.vsim.core.geometry.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimLayoutTest {

    private static final String FAKE_SUBCIRCUIT = "fake-subcircuit";
    private static final ComponentTypeRegistry REGISTRY = registryWithAFakeSubcircuit();

    private static LogisimProject project;

    @BeforeAll
    static void readTheFixture() throws Exception {
        try (InputStream input = LogisimLayoutTest.class.getResourceAsStream("layout.circ")) {
            project = new LogisimReader().read(input);
        }
    }

    @Test
    void aGateKeepsLogisimsNets() {
        LaidOutCircuit laidOut = layOut("gate");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(component(laidOut, "and").rotation()).isEqualTo(Rotation.R0);
        assertThat(laidOut.statistics().unrouted()).isZero();
    }

    @Test
    void logisimPointsSpreadByOneAndAHalfRoundedHalfUp() {
        // 10 Logisim pixels are 1.5 units: Logisim's 10-pixel grid lands on distinct VSim points, spread out.
        assertThat(LogisimLayout.scaled(new LogisimPoint(100, 90))).isEqualTo(new GridPoint(15, 14));
        assertThat(LogisimLayout.scaled(new LogisimPoint(170, 110))).isEqualTo(new GridPoint(26, 17));
        assertThat(LogisimLayout.scaled(new LogisimPoint(0, 10))).isEqualTo(new GridPoint(0, 2));
        assertThat(LogisimLayout.scaled(new LogisimPoint(-10, -30))).isEqualTo(new GridPoint(-1, -4));
    }

    @Test
    void wiresAreSpreadLikeTheirEnds() {
        LaidOutCircuit laidOut = layOut("gate");

        assertThat(laidOut.document().wires()).anySatisfy(wire -> {
            assertThat(wire.start()).isEqualTo(new GridPoint(15, 14));
            assertThat(wire.end().y()).isEqualTo(14);
        });
    }

    @Test
    void theWiresStayAsLogisimDrewThem() {
        LaidOutCircuit laidOut = layOut("untidy");

        // The stub and the two halves it split are kept: «Limpiar cables» is the user's to run.
        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(laidOut.document().wires()).hasSize(3);
    }

    @Test
    void aMultiplexerWithItsSelectOnTopKeepsItsRotationAndItsNets() {
        LaidOutCircuit laidOut = layOut("mux");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(component(laidOut, "mux").rotation()).isEqualTo(Rotation.R0);
    }

    @Test
    void splittersFacingEachWayKeepTheirNets() {
        LaidOutCircuit laidOut = layOut("splitters");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(laidOut.document().components()).filteredOn(part -> part.typeId().equals("bus-splitter"))
                .hasSize(4);
    }

    @Test
    void aWireJunctionFeedsBothGates() {
        LaidOutCircuit laidOut = layOut("tee");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(laidOut.document().components()).filteredOn(part -> part.typeId().equals("buffer")).hasSize(2);
    }

    @Test
    void aSingleNegatedInputGetsANotInFrontOfIt() {
        LaidOutCircuit laidOut = layOut("not in front");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(component(laidOut, "buffer").parameters()).containsEntry("negate", true)
                .containsEntry("delay", LogisimTiming.GATE);
        assertThat(laidOut.notes()).anyMatch(note -> note.messageKey().equals(LogisimNotes.NEGATED_INPUT));
    }

    @Test
    void textsAndLabelledProbesBecomeTextLabelsAtTheirScaledPlace() {
        LaidOutCircuit laidOut = layOut("texts");

        assertThat(laidOut.document().labels()).extracting(TextLabel::text).containsExactlyInAnyOrder("hola", "pc");
        assertThat(laidOut.document().components()).isEmpty();
    }

    @Test
    void aOneBitSplitterIsOnlyAWire() {
        LaidOutCircuit laidOut = layOut("junction");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(laidOut.document().components()).noneMatch(part -> part.typeId().startsWith("bus-"));
        assertThat(laidOut.notes()).noneMatch(note -> note.messageKey().equals(LogisimNotes.NARROW_SPLITTER));
    }

    @Test
    void aSubcircuitInstanceIsItsVsimPartWithItsPinsOnItsEnds() {
        LaidOutCircuit laidOut = layOut("instance");

        assertThat(NetEquivalence.mismatches(laidOut, REGISTRY)).isEmpty();
        assertThat(component(laidOut, FAKE_SUBCIRCUIT).rotation()).isEqualTo(Rotation.R0);
    }

    private static LaidOutCircuit layOut(String circuit) {
        LogisimNetAnalysis analysis = LogisimNets.analyze(project.circuit(circuit).orElseThrow(), project);
        return new LogisimLayout(REGISTRY).layOut(analysis,
                component -> analysis.mappingContext(component, REGISTRY),
                name -> name.equals("inner")
                        ? Optional.of(new SubcircuitPart(FAKE_SUBCIRCUIT, Map.of(),
                        Map.of(new LogisimPoint(100, 100), 0, new LogisimPoint(300, 100), 1)))
                        : Optional.empty());
    }

    private static Component component(LaidOutCircuit laidOut, String typeId) {
        List<Component> found = laidOut.document().components().stream()
                .filter(part -> part.typeId().equals(typeId)).toList();
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    /** The standard parts plus a stand-in for a subcircuit's part: input {@code a} on the left, output on the right. */
    private static ComponentTypeRegistry registryWithAFakeSubcircuit() {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.register(new ComponentType(FAKE_SUBCIRCUIT, ComponentCategory.GATES, new GridSize(4, 2),
                List.of(PinSpec.input("a", 0, 1), PinSpec.output("y", 4, 1)), List.of(), parameters -> null));
        return registry;
    }
}
