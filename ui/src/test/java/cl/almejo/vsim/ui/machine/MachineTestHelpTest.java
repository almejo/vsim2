package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The help bubble of the machine test mode (hito 4, fix round 2): the first time the user tests a machine a bubble
 * over the table explains what the table is for; it is shown once, and Ayuda ▸ «Ayuda del modo prueba» shows it again.
 */
class MachineTestHelpTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<Runnable> endings = new ArrayList<>();
    private int opened;
    private int taken;
    private OpenDocument document;
    private OpenDocument place;
    private EditorView view;
    private MachineMode mode;

    @BeforeEach
    void aMachineTab() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(new MachineModeExtension()));
        document = MachineFixtures.circuitWithGeneratedMachines(1);
        place = document.openBody(MachineFixtures.DEFINITION_ID,
                Optional.of(MachineFixtures.copies(document).getFirst()));
        view = new EditorView(place, services, NO_WINDOW);
        mode = MachineMode.of(view).orElseThrow();
        mode.helpWith((shownIn, ended) -> {
            opened++;
            endings.add(ended);
            return () -> taken++;
        });
    }

    @AfterEach
    void close() {
        view.dispose();
        place.close();
        document.close();
    }

    private JMenuItem helpItem() {
        JMenu help = view.menu(MenuId.HELP);
        for (int index = 0; index < help.getItemCount(); index++) {
            JMenuItem item = help.getItem(index);
            if (item != null && Messages.text("action.machineTestHelp").equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no «Ayuda del modo prueba» in Ayuda");
    }

    @Test
    void theFirstTimeTheUserTestsABubbleOpens() {
        mode.startTest();

        assertThat(opened).isEqualTo(1);
    }

    @Test
    void onceTheUserEndedItItNeverComesByItself() {
        mode.startTest();
        endings.getFirst().run(); // «Listo»
        MachineTestModes.leaveTheTest(view);

        mode.startTest();

        assertThat(opened).as("not again").isEqualTo(1);
        assertThat(preferences.machineTestHelpSeen()).isTrue();
    }

    @Test
    void ifTheTestEndsBeforeTheUserEndedItTheBubbleIsTakenAwayAndComesAgain() {
        mode.startTest();

        MachineTestModes.leaveTheTest(view);

        assertThat(taken).isEqualTo(1);
        assertThat(preferences.machineTestHelpSeen()).as("never read").isFalse();
        mode.startTest();
        assertThat(opened).isEqualTo(2);
    }

    @Test
    void ayudaShowsItAgainEvenWhenSeenAndOnlyWhileTesting() {
        preferences.setMachineTestHelpSeen(true);
        JMenuItem item = helpItem();
        assertThat(item.isEnabled()).isFalse();
        assertThat(item.getToolTipText()).isEqualTo(Messages.text("machine.test.help.onlyTesting"));
        assertThat(item.getAction().isEnabled()).as("the action, which «Acciones» reads").isFalse();
        assertThat(cl.almejo.vsim.ui.actions.Actions.disabledReason(item.getAction()))
                .contains(Messages.text("machine.test.help.onlyTesting"));

        mode.startTest();
        assertThat(opened).as("seen: not by itself").isZero();
        assertThat(item.isEnabled()).isTrue();
        assertThat(item.getAction().isEnabled()).isTrue();
        assertThat(item.getToolTipText()).isNull();

        item.doClick();
        assertThat(opened).isEqualTo(1);

        MachineTestModes.leaveTheTest(view);
        assertThat(item.isEnabled()).isFalse();
    }

    @Test
    void theHelpTextSaysWhatTheTableIsFor() {
        String text = Messages.text("machine.test.help.text");

        assertThat(text).contains("esperas", "✓", "✗", "Aplicar").containsIgnoringCase("borrador");
        assertThat(Messages.text("machine.test.help.title")).isNotBlank();
    }

    @Test
    void theItemIsFoundInTheCommandPaletteByItsWords() {
        assertThat(String.valueOf(helpItem().getClientProperty(cl.almejo.vsim.ui.actions.Actions.SEARCH_TERMS)))
                .contains("burbuja", "prueba");
    }
}
