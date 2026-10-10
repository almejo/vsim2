package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class TopLevelNetsTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());

    @AfterEach
    void close() {
        openDocument.close();
    }

    @Test
    void bothSidesOfAPassThroughAreOneGroupHeadedByTheReportedRoot() {
        PassThroughCircuit circuit = PassThroughCircuit.addTo(openDocument);
        String left = circuit.leftNetId(openDocument);
        String right = circuit.rightNetId(openDocument);
        String root = circuit.rootNetId(openDocument);

        TopLevelNets merged = TopLevelNets.of(openDocument.document(), openDocument.netlist(),
                openDocument.registry());

        assertThat(merged.membersOf(left)).containsExactlyInAnyOrder(left, right).startsWith(root);
        assertThat(merged.membersOf(right)).isEqualTo(merged.membersOf(left));
        assertThat(merged.rootOf(left)).isEqualTo(root);
        assertThat(merged.rootOf(right)).isEqualTo(root);
        assertThat(merged.membersOf("n:elsewhere")).containsExactly("n:elsewhere");
    }

    @Test
    void aCircuitWithoutTemplatesMergesNothing() {
        place(openDocument, StandardComponentTypes.NOT, 0, 0, Map.of());

        TopLevelNets merged = TopLevelNets.of(openDocument.document(), openDocument.netlist(),
                openDocument.registry());

        assertThat(merged).isSameAs(TopLevelNets.NONE);
        assertThat(merged.rootOf("n:p:c1#0")).isEqualTo("n:p:c1#0");
    }
}
