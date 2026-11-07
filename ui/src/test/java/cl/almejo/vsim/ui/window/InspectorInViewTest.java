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

import javax.swing.JComponent;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
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
        dragTheDivider(-120);
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
    void aWidthCappedByANarrowWindowIsNotRememberedAsTheUsersWidth() {
        giveTheSplitWidth(600);
        layoutTree(view);
        layoutTree(view);
        assertThat(view.inspector().getWidth()).as("the canvas keeps its minimum")
                .isLessThan(InspectorPanel.PREFERRED_WIDTH);
        view.revalidate();
        layoutTree(view);
        assertThat(view.inspector().state().width()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);

        view.setSize(1400, 760);
        layoutTree(view);

        assertThat(view.inspector().getWidth()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);
    }

    @Test
    void aDividerMovedByCodeIsNotADrag() {
        split().setDividerLocation(split().getDividerLocation() - 120);
        layoutTree(view);

        assertThat(view.inspector().state().width()).isEqualTo(InspectorPanel.PREFERRED_WIDTH);
    }

    @Test
    void aRealDragInANarrowWindowIsKept() {
        giveTheSplitWidth(600);
        layoutTree(view);
        layoutTree(view);
        int capped = view.inspector().getWidth();

        dragTheDivider(20);
        int dragged = view.inspector().getWidth();
        assertThat(dragged).isEqualTo(capped - 20);
        assertThat(view.inspector().state().width()).isEqualTo(dragged);
        view.setSize(1400, 760);
        layoutTree(view);

        assertThat(view.inspector().getWidth()).isEqualTo(dragged);
    }

    @Test
    void aRestoredWidthIsWhatTheOpenPanelGets() {
        view.inspector().restore(new InspectorState(true, "problems", 400));
        layoutTree(view);

        assertThat(view.inspector().getWidth()).isEqualTo(400);
    }

    /** Resizes the view so that its split (canvas + panel) is {@code width} pixels wide. */
    private void giveTheSplitWidth(int width) {
        view.setSize(view.getWidth() - split().getWidth() + width, view.getHeight());
        layoutTree(view);
    }

    /** Presses the divider, drags it by {@code distance} pixels (negative: to the left) and releases it. */
    private void dragTheDivider(int distance) {
        // off screen a component without a peer reports its size as its minimum: on screen the canvas can shrink
        ((JComponent) split().getLeftComponent()).setMinimumSize(new Dimension(1, 1));
        Component divider = ((BasicSplitPaneUI) split().getUI()).getDivider();
        int x = divider.getWidth() / 2;
        int y = divider.getHeight() / 2;
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK, x, y,
                1, false, MouseEvent.BUTTON1));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 0, MouseEvent.BUTTON1_DOWN_MASK,
                x + distance, y, 1, false, MouseEvent.BUTTON1));
        layoutTree(view);
        // the divider moved with the mouse: the release comes at the same point of the divider
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false,
                MouseEvent.BUTTON1));
        layoutTree(view);
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
