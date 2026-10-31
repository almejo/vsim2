package cl.almejo.vsim.ui.terminal;

import cl.almejo.vsim.core.command.AddElementsCommand;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import java.awt.Container;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The Teclado's «Terminal enlazado» row: it lists the Terminal labels and writes the link as one command. */
class KeyboardInspectorRowsTest {

    private final OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;

    @AfterEach
    void close() {
        if (view != null) {
            view.dispose();
        }
        document.close();
    }

    private void placeTerminal(String label, int x) {
        ComponentType type = document.registry().require(StandardComponentTypes.TERMINAL);
        document.execute(new AddElementsCommand(List.of(type.newComponent(document.document().newComponentId(),
                new GridPoint(x, 0), Rotation.R0, Map.of(), label))));
    }

    @SuppressWarnings("unchecked")
    private JComboBox<String> comboShowing(Component keyboard) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                AppPreferences.inMemory(), new ComponentLibrary(), List.of());
        view = new EditorView(document, services, NO_WINDOW);
        view.selection().select(keyboard.id());
        return find(view.labelFeatures().properties());
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<String> find(Container root) {
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof JComboBox<?> combo && KeyboardInspectorRows.COMBO_NAME.equals(combo.getName())) {
                return (JComboBox<String>) combo;
            }
            if (child instanceof Container container) {
                JComboBox<String> nested = find(container);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static List<String> items(JComboBox<String> combo) {
        return java.util.stream.IntStream.range(0, combo.getItemCount()).mapToObj(combo::getItemAt).toList();
    }

    private Object linkOf(Component keyboard) {
        return document.document().component(keyboard.id()).orElseThrow().parameters()
                .get(KeyboardTypes.LINKED_TERMINAL);
    }

    @Test
    void theComboListsTheTerminalLabelsAndNoneAndSkipsBlankOnes() {
        placeTerminal("pantalla", 0);
        placeTerminal("", 100);
        placeTerminal("consola", 200);
        placeTerminal("pantalla", 300);
        Component keyboard = place(document, StandardComponentTypes.KEYBOARD, 0, 100, Map.of());

        JComboBox<String> combo = comboShowing(keyboard);

        assertThat(combo).isNotNull();
        assertThat(items(combo)).hasSize(3).containsSubsequence("pantalla", "consola");
        assertThat(combo.getSelectedIndex()).isZero();
    }

    @Test
    void choosingATerminalWritesTheLinkAsOneUndoStepAndNoneClearsIt() {
        placeTerminal("pantalla", 0);
        Component keyboard = place(document, StandardComponentTypes.KEYBOARD, 0, 100, Map.of());
        JComboBox<String> combo = comboShowing(keyboard);

        combo.setSelectedItem("pantalla");

        assertThat(linkOf(keyboard)).isEqualTo("pantalla");
        document.history().undo();
        assertThat(linkOf(keyboard)).isEqualTo("");
        document.history().redo();
        assertThat(linkOf(keyboard)).isEqualTo("pantalla");
    }

    @Test
    void aLinkToALabelNoTerminalHasIsStillListedAndSelected() {
        Component keyboard = place(document, StandardComponentTypes.KEYBOARD, 0, 100,
                Map.of(KeyboardTypes.LINKED_TERMINAL, "borrada"));

        JComboBox<String> combo = comboShowing(keyboard);

        assertThat(items(combo)).contains("borrada");
        assertThat(combo.getSelectedItem()).isEqualTo("borrada");
        assertThat(linkOf(keyboard)).isEqualTo("borrada");
    }
}
