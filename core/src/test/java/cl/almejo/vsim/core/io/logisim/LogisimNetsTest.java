package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogisimNetsTest {

    private static LogisimProject project;

    @BeforeAll
    static void readTheFixture() throws Exception {
        try (InputStream input = LogisimNetsTest.class.getResourceAsStream("nets.circ")) {
            project = new LogisimReader().read(input);
        }
    }

    @Test
    void aSplitterWhoseBusIsDrivenSplits() {
        LogisimNetAnalysis analysis = analyze("split");

        assertThat(analysis.splitterDirection(first("split", "Splitter"))).isEqualTo(SplitterDirection.SPLIT);
        assertThat(analysis.notes()).isEmpty();
    }

    @Test
    void aSplitterWhoseEndsAreDrivenJoins() {
        LogisimNetAnalysis analysis = analyze("join");

        assertThat(analysis.splitterDirection(first("join", "Splitter"))).isEqualTo(SplitterDirection.JOIN);
        assertThat(analysis.notes()).isEmpty();
    }

    @Test
    void aSplitterDrivenOnBothSidesJoinsWithANote() {
        LogisimNetAnalysis analysis = analyze("both");

        assertThat(analysis.splitterDirection(first("both", "Splitter"))).isEqualTo(SplitterDirection.JOIN);
        assertThat(analysis.notes()).singleElement().satisfies(note -> {
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.SPLITTER_BOTH_SIDES);
            assertThat(note.where()).contains(new LogisimPoint(150, 100));
        });
    }

    @Test
    void theDriverIsFoundThroughTunnels() {
        LogisimNetAnalysis analysis = analyze("through tunnels");

        assertThat(analysis.splitterDirection(first("through tunnels", "Splitter")))
                .isEqualTo(SplitterDirection.SPLIT);
    }

    @Test
    void wiresJoinWhereTheyShareAnEndLikeLogisimNotWhereOneEndsOnTheOthersMiddle() {
        LogisimNetAnalysis analysis = analyze("wires");

        assertThat(analysis.netOf(new LogisimPoint(150, 150))).isNotEqualTo(analysis.netOf(new LogisimPoint(100, 100)));
        assertThat(analysis.netOf(new LogisimPoint(150, 350))).isEqualTo(analysis.netOf(new LogisimPoint(100, 300)))
                .isEqualTo(analysis.netOf(new LogisimPoint(200, 300)));
    }

    @Test
    void crossingWiresDoNotJoin() {
        LogisimNetAnalysis analysis = analyze("wires");

        assertThat(analysis.netOf(new LogisimPoint(150, 450))).isNotEqualTo(analysis.netOf(new LogisimPoint(100, 500)));
        assertThat(analysis.netOf(new LogisimPoint(150, 450))).isEqualTo(analysis.netOf(new LogisimPoint(150, 550)));
    }

    @Test
    void aPointOnNoWireNorEndIsOnNoNet() {
        assertThat(analyze("wires").netOf(new LogisimPoint(1000, 1000))).isEqualTo(-1);
    }

    @Test
    void tunnelsGetNoNotesWhicheverOfThemIsDrivenSinceNoDirectionIsGuessed() {
        // «tunnels» has a name driven from one side, one driven from two and one nothing drives; «lone tunnels» has
        // names with a single tunnel. They all become tunnels without direction: nothing to note.
        assertThat(analyze("tunnels").notes()).isEmpty();
        assertThat(analyze("lone tunnels").notes()).isEmpty();
    }

    @Test
    void aConnectedEndVsimLacksIsNotedAndAnUnconnectedOneIsNot() {
        LogisimNetAnalysis analysis = analyze("lacking");

        assertThat(analysis.notes()).singleElement().satisfies(note -> {
            assertThat(note.messageKey()).isEqualTo(LogisimNotes.MISSING_PIN);
            assertThat(note.arguments()).containsExactly("RAM", "OE");
            assertThat(note.where()).contains(new LogisimPoint(300, 300));
        });
    }

    @Test
    void aOneBitSplitterIsAJunctionJoiningItsTwoEnds() {
        LogisimNetAnalysis analysis = analyze("junction");
        LogisimComponent splitter = first("junction", "Splitter");

        assertThat(analysis.isJunction(splitter)).isTrue();
        assertThat(analysis.netOf(new LogisimPoint(50, 100))).isEqualTo(analysis.netOf(new LogisimPoint(150, 90)));
    }

    @Test
    void endAtGivesWhereLogisimPutsAnEnd() {
        LogisimNetAnalysis analysis = analyze("split");

        assertThat(analysis.endAt(first("split", "Splitter"), 2)).isEqualTo(new LogisimPoint(170, 90));
    }

    @Test
    void theMappingContextCarriesTheCircuitAndTheSplitterDirection() {
        LogisimNetAnalysis analysis = analyze("tunnels");

        MappingContext context = analysis.mappingContext(tunnelAt("tunnels", 150, 100),
                StandardComponentTypes.registry());

        assertThat(context.circuit()).isEqualTo("tunnels");
        assertThat(context.splitterDirection()).isEqualTo(SplitterDirection.SPLIT);
    }

    private static LogisimNetAnalysis analyze(String circuit) {
        return LogisimNets.analyze(project.circuit(circuit).orElseThrow(), project);
    }

    private static LogisimComponent first(String circuit, String name) {
        return components(circuit).stream().filter(component -> component.name().equals(name)).findFirst()
                .orElseThrow();
    }

    private static LogisimComponent tunnelAt(String circuit, int x, int y) {
        return components(circuit).stream()
                .filter(component -> component.name().equals("Tunnel")
                        && component.location().equals(new LogisimPoint(x, y)))
                .findFirst().orElseThrow();
    }

    private static List<LogisimComponent> components(String circuit) {
        return project.circuit(circuit).orElseThrow().components();
    }
}
