package cl.almejo.vsim.ui.machine;

import cl.almejo.vsim.core.command.PutDefinitionsCommand;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.machine.DiagramPoint;
import cl.almejo.vsim.core.machine.MachineDefinitions;
import cl.almejo.vsim.core.machine.MachineState;
import cl.almejo.vsim.core.machine.MachineTest;
import cl.almejo.vsim.core.machine.MachineTests;
import cl.almejo.vsim.core.machine.MachineTransition;
import cl.almejo.vsim.core.machine.StateMachine;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.theme.FlatLafInstaller;
import cl.almejo.vsim.ui.window.EditorExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JViewport;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.atDoubleUiScale;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fix round 4 (M3): a machine's Propiedades fits its panel at the panel's default width, at 100 % and at 200 %: the
 * rows take the panel's width (no horizontal scrolling, so nothing may stick out on the right) and no text is cut.
 */
class MachinePropertiesRoomTest {

    @TempDir
    Path directory;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
    }

    /** Two inputs and an output used, an input and an output in no arrow: «en 2 flechas», «en ninguna flecha», «Quitar». */
    private static StateMachine detector() {
        List<MachineState> states = List.of(
                new MachineState("a", "INICIO", new DiagramPoint(0, 0), List.of()),
                new MachineState("b", "VI 1", new DiagramPoint(200, 0), List.of()));
        List<MachineTransition> transitions = List.of(
                new MachineTransition("t1", "a", "b", "x", List.of()),
                new MachineTransition("t2", "b", "a", "x' + y", List.of()));
        return new StateMachine(states, transitions, Optional.of("a"), List.of("x", "y", "sensor"),
                List.of("Z", "alarma"), false, false, StateMachine.EDGE_FALLING,
                List.of(MachineTest.empty("p1", "Prueba 1", MachineTests.DEFAULT_COLUMNS),
                        MachineTest.empty("p2", "Prueba 2", MachineTests.DEFAULT_COLUMNS)));
    }

    /** Builds the machine's tab {@code scale} times the 100 % window and checks its Propiedades. */
    private void checkRoom(double scale) {
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library" + scale)));
        ComponentLibrary library = new ComponentLibrary();
        LibraryService service = new LibraryService(preferences, library, registry, Runnable::run);
        List<EditorExtension> extensions = new ArrayList<>(
                Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE).stream()
                        .filter(extension -> !(extension instanceof MachineModeExtension)).toList());
        extensions.add(new MachineModeExtension((snapshot, instancePath) -> Optional.empty()));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, extensions);
        OpenDocument document = OpenDocument.createEmpty(registry);
        document.execute(new PutDefinitionsCommand(List.of(
                MachineDefinitions.regenerated(MachineFixtures.definition(detector())))));
        Component piece = place(document, SubcircuitTypes.typeIdOf(MachineFixtures.DEFINITION_ID), 0, 0, Map.of());
        OpenDocument place = document.openBody(MachineFixtures.DEFINITION_ID, Optional.of(piece.id().value()));
        EditorView view = new EditorView(place, services, NO_WINDOW);
        try {
            SwingUtilities.updateComponentTreeUI(view);
            view.setSize((int) (1400 * scale), (int) (900 * scale));
            layOut(view);
            layOut(view);
            MachineInspectorRows rows = MachineMode.of(view).orElseThrow().inspectorRows();
            JViewport viewport = (JViewport) rows.getParent();
            String where = Math.round(scale * 100) + " %";
            assertThat(rows.getWidth()).as("the rows take the panel's width at %s", where)
                    .isLessThanOrEqualTo(viewport.getWidth());
            SoftAssertions softly = new SoftAssertions();
            for (java.awt.Component component : texts(rows)) {
                softly.assertThat(component.getWidth()).as("«%s» whole at %s", textOf(component), where)
                        .isGreaterThanOrEqualTo(component.getPreferredSize().width);
                softly.assertThat(SwingUtilities.convertPoint(component, component.getWidth(), 0, rows).x)
                        .as("«%s» inside at %s", textOf(component), where).isLessThanOrEqualTo(rows.getWidth());
            }
            softly.assertAll();
        } finally {
            view.dispose();
            place.close();
            document.close();
            service.close();
        }
    }

    @Test
    void nothingIsCutAtOneHundredPercent() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", false));
            checkRoom(1);
        } finally {
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    void nothingIsCutAtOneHundredFiftyPercent() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            System.setProperty("flatlaf.uiScale", "1.5");
            UIManager.setLookAndFeel(FlatLafInstaller.lookAndFeel("linux", true));
            checkRoom(1.5);
        } finally {
            System.clearProperty("flatlaf.uiScale");
            UIManager.setLookAndFeel(original);
        }
    }

    @Test
    void nothingIsCutAtTwoHundredPercent() throws Exception {
        atDoubleUiScale(() -> checkRoom(2));
    }

    /** The labels, buttons and lists that show a text, visible in the rows. */
    private static List<java.awt.Component> texts(Container container) {
        List<java.awt.Component> found = new ArrayList<>();
        for (java.awt.Component child : container.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if ((child instanceof JLabel label && !label.getText().isBlank())
                    || (child instanceof AbstractButton button && !button.getText().isBlank())
                    || child instanceof JComboBox<?>) {
                found.add(child);
            } else if (child instanceof Container nested) {
                found.addAll(texts(nested));
            }
        }
        return found;
    }

    private static String textOf(java.awt.Component component) {
        return switch (component) {
            case JLabel label -> label.getText();
            case AbstractButton button -> button.getText();
            case JComboBox<?> combo -> String.valueOf(combo.getSelectedItem());
            default -> component.toString();
        };
    }
}
