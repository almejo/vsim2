package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.command.EditMachineCommand;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineChecks;
import cl.almejo.vsim.core.machine.MachineFinding;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.core.simulation.MachineProblem;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineCanvas;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineMode;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
import cl.almejo.vsim.ui.machine.MachineProblemNavigation;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/** Entering a machine copy opens its definition's tab in machine mode; the circuit's own tab stays a circuit tab. */
class MachineTabEnterTest {

    private final RecordingWindowHost host = new RecordingWindowHost();
    private final AppPreferences preferences = AppPreferences.inMemory();
    private WindowTabs tabs;
    private OpenDocument document;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new MachineModeExtension()));
        // the window's showPlace, as CircuitWindow gives it: a Problemas row reaches the machine's real tab
        WindowCommands window = new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }

            @Override
            public Optional<EditorView> showPlace(OpenDocument shown, Optional<String> definitionId,
                                                  Optional<String> contextPath) {
                return Optional.of(tabs.showPlace(shown, definitionId, contextPath));
            }
        };
        tabs = new WindowTabs(host, place -> new EditorView(place, services, window),
                new SplitLayoutStore(preferences), () -> false);
        document = MachineFixtures.circuitWithMachines(2);
    }

    @AfterEach
    void close() {
        tabs.disposeAll();
        document.close();
    }

    @Test
    void enteringAMachineCopyOpensItsTabInMachineMode() {
        EditorView circuit = tabs.addTab(document, Optional.empty());
        String copy = MachineFixtures.copies(document).get(1);

        EditorView entered = tabs.enter(document, MachineFixtures.DEFINITION_ID, Optional.of(copy), Optional.empty());

        assertThat(tabs.activeView()).isSameAs(entered);
        assertThat(entered.openDocument().contextPath()).contains(copy);
        MachineMode mode = MachineMode.of(entered).orElseThrow();
        assertThat(SwingUtilities.isDescendingFrom(mode.canvas(), entered)).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(mode.palette(), entered)).isTrue();
        assertThat(MachineMode.of(circuit)).isEmpty();
        assertThat(SwingUtilities.isDescendingFrom(circuit.canvas(), circuit)).isTrue();
    }

    /**
     * Hand test of hito 2: a click on a machine's row in Problemas, through the window's real tabs, opens the machine's
     * tab and shows that problem there: selected and, once the diagram has its size, in the middle of it.
     */
    @Test
    void aProblemsRowOpensTheMachinesTabAndShowsThatProblemThere() {
        List<MachineState> states = new ArrayList<>(MachineFixtures.detector().states());
        states.add(new MachineState("lost", "LOST", new DiagramPoint(600, 400), List.of()));
        StateMachine withALostState = MachineFixtures.detector().withStates(states);
        document.execute(new EditMachineCommand(MachineFixtures.DEFINITION_ID, withALostState));
        MachineFinding lost = MachineChecks.check(withALostState).stream()
                .filter(finding -> finding.kind() == MachineFinding.Kind.UNREACHABLE_STATE).findFirst().orElseThrow();
        EditorView circuit = tabs.addTab(document, Optional.empty());

        boolean shown = MachineProblemNavigation.show(circuit,
                MachineProblem.of(MachineFixtures.DEFINITION_ID, Optional.empty(), lost));

        assertThat(shown).isTrue();
        MachineMode mode = MachineMode.of(tabs.activeView()).orElseThrow();
        assertThat(mode.selection().stateIds()).containsExactly("lost");
        MachineCanvas canvas = mode.canvas();
        canvas.setBounds(0, 0, 800, 600); // the real window lays the new tab out after the click
        Point2D centre = canvas.toScreen(new DiagramPoint(600, 400));
        assertThat(centre.getX()).isCloseTo(400, offset(2.0));
        assertThat(centre.getY()).isCloseTo(300, offset(2.0));
    }

    @Test
    void theMachineTabsFocusGoesToTheDiagram() {
        tabs.addTab(document, Optional.empty());
        EditorView entered = tabs.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(0)), Optional.empty());

        assertThat(entered.focusTarget()).isSameAs(MachineMode.of(entered).orElseThrow().canvas());
    }
}
