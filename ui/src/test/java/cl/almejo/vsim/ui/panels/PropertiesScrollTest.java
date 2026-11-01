package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.Selection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** The «Propiedades» tab scrolls when its form is taller than the inspector. */
class PropertiesScrollTest {

    private static final int ROWS = 40;

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private final Selection selection = new Selection();
    private PropertiesPanel panel;
    private JScrollPane scroll;
    private JTextArea quiet;

    @BeforeEach
    void create() {
        panel = new PropertiesPanel(openDocument, selection);
        panel.addComponentRows((component, rows) -> {
            for (int index = 0; index < ROWS; index++) {
                rows.add("Fila " + index, new JLabel("valor " + index));
            }
            quiet = new JTextArea("una línea");
            rows.add("Texto", new JScrollPane(quiet));
        });
        scroll = PropertiesPanel.inScrollPane(panel);
        Component gate = place(openDocument, StandardComponentTypes.BUFFER, 0, 0, Map.of());
        selection.select(gate.id());
        scroll.setSize(new Dimension(280, 200));
        layOutTree(scroll);
    }

    @AfterEach
    void close() {
        panel.dispose();
        openDocument.close();
    }

    private static void layOutTree(java.awt.Container root) {
        root.doLayout();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof java.awt.Container container) {
                layOutTree(container);
            }
        }
    }

    @Test
    void aTallFormInAShortPaneScrollsVerticallyAndNeverHorizontally() {
        assertThat(scroll.getVerticalScrollBarPolicy()).isEqualTo(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        assertThat(scroll.getHorizontalScrollBarPolicy()).isEqualTo(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        assertThat(panel.getPreferredSize().height).isGreaterThan(scroll.getViewport().getHeight());
        assertThat(scroll.getVerticalScrollBar().getMaximum()).isGreaterThan(scroll.getVerticalScrollBar()
                .getVisibleAmount());
    }

    @Test
    void theFormTracksTheViewportWidth() {
        assertThat(panel.getScrollableTracksViewportWidth()).isTrue();
        assertThat(panel.getScrollableTracksViewportHeight()).isFalse();
        assertThat(panel.getWidth()).isEqualTo(scroll.getViewport().getWidth());
    }

    @Test
    void aRowIsOneUnitIncrement() {
        assertThat(scroll.getVerticalScrollBar().getUnitIncrement()).isBetween(8, 40);
    }

    @Test
    void theWheelOverATextAreaThatNeedsNoScrollingScrollsThePane() {
        int before = scroll.getViewport().getViewPosition().y;
        JScrollPane inner = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, quiet);
        // Swing hands a wheel over the text to the nearest scroll pane around it: this one
        MouseWheelEvent wheel = new MouseWheelEvent(inner, MouseWheelEvent.MOUSE_WHEEL, 0, 0, 1, 1, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 3);
        inner.dispatchEvent(wheel);
        assertThat(scroll.getViewport().getViewPosition().y).isGreaterThan(before);
    }
}
