package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.component.standard.KeyboardTypes;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Container;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** A multi-line parameter (the Teclado's «Texto inicial») shows a text area of at least three lines. */
class MultilineParameterTest {

    private static JScrollPane scrollPaneOf(java.awt.Component editor) {
        Container parent = editor.getParent();
        while (parent != null && !(parent instanceof JScrollPane)) {
            parent = parent.getParent();
        }
        return (JScrollPane) parent;
    }

    private static void assertShowsThreeLines(JTextArea area) {
        int lineHeight = area.getFontMetrics(area.getFont()).getHeight();
        JScrollPane scroll = scrollPaneOf(area);
        assertThat(scroll.getViewport().getHeight()).isGreaterThanOrEqualTo(3 * lineHeight);
    }

    @Test
    void theFormShowsThreeLines() {
        ParameterForm form = new ParameterForm(
                StandardComponentTypes.registry().require(StandardComponentTypes.KEYBOARD), Map.of());
        form.setSize(300, 900);
        layOut(form);
        assertShowsThreeLines((JTextArea) form.editor(KeyboardTypes.INITIAL_TEXT));
    }

    @Test
    void thePropertiesTabShowsThreeLines() {
        OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        Selection selection = new Selection();
        PropertiesPanel panel = new PropertiesPanel(openDocument, selection);
        try {
            Component keyboard = place(openDocument, StandardComponentTypes.KEYBOARD, 0, 0, Map.of());
            selection.select(keyboard.id());
            panel.setSize(300, 900);
            layOut(panel);
            assertShowsThreeLines((JTextArea) panel.parameterForm().editor(KeyboardTypes.INITIAL_TEXT));
        } finally {
            panel.dispose();
            openDocument.close();
        }
    }
}
