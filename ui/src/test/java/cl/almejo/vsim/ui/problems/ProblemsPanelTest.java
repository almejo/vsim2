package cl.almejo.vsim.ui.problems;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class ProblemsPanelTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final List<Problem> navigated = new ArrayList<>();
    private ProblemsPanel panel;
    private ShortCircuit shortCircuit;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        Component high = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of("value", "1"));
        Component low = place(openDocument, StandardComponentTypes.SWITCH, 0, 6, Map.of("value", "0"));
        Wire joint = new Wire(openDocument.document().newWireId(), new GridPoint(4, 1), new GridPoint(4, 7));
        openDocument.execute(new AddWireCommand(joint));
        String netId = openDocument.netlist().netForWire(joint.id()).orElseThrow().id().value();
        shortCircuit = new ShortCircuit(netId, List.of(new DriverRef(high.id().value(), 0, Value.ONE),
                new DriverRef(low.id().value(), 0, Value.ZERO)), 5);
        panel = new ProblemsPanel(openDocument, navigated::add);
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private static SimulationSnapshot snapshot(long time, Problem... problems) {
        return new SimulationSnapshot(1, 1, time, true, Map.of(), Map.of(), List.of(problems), Optional.empty());
    }

    private String rowText(int index) {
        Problem problem = panel.problemList().getModel().getElementAt(index);
        return ((JLabel) panel.problemList().getCellRenderer()
                .getListCellRendererComponent(panel.problemList(), problem, index, false, false)).getText();
    }

    @Test
    void startsEmpty() {
        assertThat(panel.problemList().getModel().getSize()).isZero();
        assertThat(panel.summaryText()).isEqualTo("Sin problemas");
    }

    @Test
    void listsEachProblemWithItsDescription() {
        panel.showSnapshot(snapshot(10, new WidthMismatch("n:zz", List.of()), shortCircuit));
        assertThat(panel.problemList().getModel().getSize()).isEqualTo(2);
        assertThat(rowText(0)).isEqualTo("⚠ Cortocircuito desde t = 5: Interruptor → 1 · Interruptor → 0");
        assertThat(rowText(1)).isEqualTo("⚠ Anchos incompatibles: ");
        assertThat(panel.summaryText()).isEqualTo("2 problemas");
    }

    @Test
    void refreshesOnlyWhenTheProblemSetChangesAndKeepsTheSelectedNet() {
        panel.showSnapshot(snapshot(10, shortCircuit));
        int refreshes = panel.refreshCount();
        panel.problemList().setSelectedIndex(0);
        panel.showSnapshot(snapshot(20, shortCircuit));
        assertThat(panel.refreshCount()).isEqualTo(refreshes);

        panel.showSnapshot(snapshot(30, shortCircuit, new ShortCircuit("n:early", List.of(), 1)));
        assertThat(panel.refreshCount()).isEqualTo(refreshes + 1);
        assertThat(panel.problemList().getSelectedValue()).isEqualTo(shortCircuit);
        assertThat(panel.summaryText()).isEqualTo("2 problemas");
    }

    @Test
    void activatingARowNavigatesToItsProblem() {
        panel.showSnapshot(snapshot(10, shortCircuit));
        panel.activate(0);
        panel.activate(5);
        panel.activate(-1);
        assertThat(navigated).containsExactly(shortCircuit);
    }

    @Test
    void libraryProblemsHaveTheirOwnSectionThatHidesWhenEmpty() {
        panel.showLibraryProblems(List.of("roto.vsimcomponent.json no es un componente válido: …"));
        assertThat(panel.libraryProblemTexts()).containsExactly("roto.vsimcomponent.json no es un componente válido: …");

        panel.showLibraryProblems(List.of());

        assertThat(panel.libraryProblemTexts()).isEmpty();
    }
}
