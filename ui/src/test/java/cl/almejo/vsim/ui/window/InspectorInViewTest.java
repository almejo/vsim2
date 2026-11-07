package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** The panel inside a real editor view: the strip on the right edge, the split and its divider. */
class InspectorInViewTest {

    private final OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        EditorServices services = new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(),
                new cl.almejo.vsim.core.library.ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        view.setSize(1280, 760);
        layoutTree(view);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private JSplitPane split() {
        return (JSplitPane) view.inspector().getParent();
    }

    @Test
    void theStripSitsOnTheRightEdgeOfTheViewOutsideTheSplit() {
        Component strip = view.inspector().strip();

        assertThat(strip.isShowing() || strip.isVisible()).isTrue();
        assertThat(SwingUtilities.isDescendingFrom(strip, split())).isFalse();
        assertThat(SwingUtilities.isDescendingFrom(strip, view)).isTrue();
        Point corner = SwingUtilities.convertPoint(strip, strip.getWidth(), 0, view);
        assertThat(corner.x).isEqualTo(view.getWidth());
        assertThat(strip.getWidth()).isEqualTo(InspectorStrip.WIDTH);
    }

    @Test
    void theSplitHasNoOneTouchArrows() {
        assertThat(split().isOneTouchExpandable()).isFalse();
    }

    @Test
    void closingGivesTheCanvasTheWholeSplitAndHidesTheDivider() {
        view.inspector().close();
        layoutTree(view);

        assertThat(view.inspector().isVisible()).isFalse();
        assertThat(split().getDividerSize()).isZero();
        assertThat(view.bottomDock().getWidth()).isEqualTo(split().getWidth() - split().getInsets().left
                - split().getInsets().right);
        assertThat(view.inspector().strip().isVisible()).isTrue();
    }

    @Test
    void doubleClickOnTheDividerClosesThePanel() {
        Component divider = ((BasicSplitPaneUI) split().getUI()).getDivider();

        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 2, false,
                MouseEvent.BUTTON1));

        assertThat(view.inspector().isOpen()).isFalse();
    }

    @Test
    void reopeningRestoresTheDraggedWidth() {
        JSplitPane split = split();
        split.setDividerLocation(split.getDividerLocation() - 120);
        layoutTree(view);
        int dragged = view.inspector().getWidth();
        assertThat(dragged).isEqualTo(InspectorPanel.PREFERRED_WIDTH + 120);
        assertThat(view.inspector().state().width()).isEqualTo(dragged);

        view.inspector().close();
        layoutTree(view);
        view.inspector().showTab("problems");
        layoutTree(view);

        assertThat(view.inspector().getWidth()).isEqualTo(dragged);
    }

    @Test
    void resizingTheWindowKeepsTheRememberedWidth() {
        view.setSize(1000, 760);
        layoutTree(view);
        assertThat(view.inspector().getWidth()).as("a narrower window squeezes the canvas, not the panel")
                .isEqualTo(InspectorPanel.PREFERRED_WIDTH);
        view.setSize(1400, 760);
        layoutTree(view);

        assertThat(view.inspector().state().width()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);
        assertThat(view.inspector().getWidth()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);
    }

    @Test
    void aRestoredWidthIsWhatTheOpenPanelGets() {
        view.inspector().restore(new InspectorState(true, "problems", 400));
        layoutTree(view);

        assertThat(view.inspector().getWidth()).isEqualTo(400);
    }

    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }
}
