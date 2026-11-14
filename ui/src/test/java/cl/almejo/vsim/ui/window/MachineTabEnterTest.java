package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.machine.MachineFixtures;
import cl.almejo.vsim.ui.machine.MachineMode;
import cl.almejo.vsim.ui.machine.MachineModeExtension;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.panes.SplitLayoutStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

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
        tabs = new WindowTabs(host, place -> new EditorView(place, services, NO_WINDOW),
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

    @Test
    void theMachineTabsFocusGoesToTheDiagram() {
        tabs.addTab(document, Optional.empty());
        EditorView entered = tabs.enter(document, MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).get(0)), Optional.empty());

        assertThat(entered.focusTarget()).isSameAs(MachineMode.of(entered).orElseThrow().canvas());
    }
}
