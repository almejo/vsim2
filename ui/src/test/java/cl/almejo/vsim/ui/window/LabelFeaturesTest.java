package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

class LabelFeaturesTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    @Test
    void propertiesTabIsInTheInspector() {
        assertThat(view.inspector().title(InspectorPanel.PROPERTIES)).contains("Propiedades");
        assertThat(view.inspector().selectedTab()).contains(InspectorPanel.PROPERTIES);
        assertThat(SwingUtilities.isDescendingFrom(view.labelFeatures().properties(), view.inspector())).isTrue();
    }

    @Test
    void f2EditsTheLabelFromTheCanvas() {
        Object key = view.canvas().getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        assertThat(key).isEqualTo("editLabel");
        assertThat(view.canvas().getActionMap().get("editLabel")).isSameAs(view.labelFeatures().actions().editLabel());
    }

    @Test
    void editMenuOffersLabel() {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : view.menu(MenuId.EDIT).getMenuComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        assertThat(texts).contains("Etiqueta…");
    }

    @Test
    void emptyCanvasContextMenuOffersAddTextHere() {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : view.contextMenu(Optional.empty(), new Point2D.Double(4.4, 6.6)).getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        assertThat(texts).contains("Agregar texto aquí");
    }
}
