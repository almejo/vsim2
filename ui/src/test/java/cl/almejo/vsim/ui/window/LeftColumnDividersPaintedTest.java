package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.fullscreen.EdgeBarsForTests;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.layOut;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The left column's width and the circuit tree's height on a real, painted editor view: only a drag of their dividers
 * by the user (mouse or keyboard) stores them; the layout's own moves (a window resize once the split was painted,
 * presentation mode, the interface size) never do, and leaving presentation mode gives the column back its width.
 */
class LeftColumnDividersPaintedTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Appearance appearance = new Appearance(ColorScheme.LIGHT);
    private final List<OpenDocument> documents = new ArrayList<>();
    private final List<EditorView> views = new ArrayList<>();
    private EditorView view;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        view = newView();
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        documents.forEach(OpenDocument::close);
    }

    private EditorView newView() {
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        documents.add(document);
        EditorServices services = new EditorServices(PainterRegistry.standard(), appearance, preferences,
                new cl.almejo.vsim.core.library.ComponentLibrary(), List.of());
        EditorView created = new EditorView(document, services, NO_WINDOW);
        views.add(created);
        return created;
    }

    private static JSplitPane outerSplit(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JSplitPane pane && pane.getLeftComponent() instanceof LeftColumn) {
                return pane;
            }
            if (child instanceof Container nested) {
                JSplitPane found = outerSplit(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Lays {@code shown} out at {@code width} × {@code height} and paints it, as a shown window would. */
    private static void settle(EditorView shown, int width, int height) {
        shown.setSize(width, height);
        layOut(shown);
        flushEvents();
        layOut(shown);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            shown.paint(graphics);
        } finally {
            graphics.dispose();
        }
        layOut(shown);
    }

    private static void flushEvents() {
        try {
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException | InvocationTargetException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Presses the divider of {@code split}, drags it {@code pixels} along the split and releases it. */
    static void dragDivider(JSplitPane split, int pixels) {
        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
        boolean horizontal = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        int endX = horizontal ? 2 + pixels : 2;
        int endY = horizontal ? 2 : 2 + pixels;
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
                2, 2, 1, false, MouseEvent.BUTTON1));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
                endX, endY, 0, false));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 0, 0, endX, endY, 1, false,
                MouseEvent.BUTTON1));
    }

    @Test
    void narrowingThePaintedWindowStoresNoWidth() {
        settle(view, 1000, 700);
        JSplitPane split = outerSplit(view);
        assertThat(split.getDividerLocation()).isEqualTo(230);

        settle(view, 700, 700);
        settle(view, 500, 700);
        settle(view, 1200, 700);

        assertThat(preferences.leftColumnWidth()).isEmpty();
    }

    @Test
    void shorteningThePaintedWindowAtALargeSizeStoresNoTreeHeight() {
        view.addAbovePalette("Circuito", new JLabel("árbol"));
        appearance.setInterfaceZoom(200);
        settle(view, 1000, 700);

        settle(view, 1000, 300);
        settle(view, 1000, 200);

        assertThat(preferences.circuitTreeSplit()).isEmpty();
    }

    @Test
    void aMouseDragOfTheColumnsDividerStoresItsWidthAt100() {
        appearance.setInterfaceZoom(150);
        settle(view, 2000, 700);
        JSplitPane split = outerSplit(view);
        assertThat(split.getDividerLocation()).isEqualTo(345);

        dragDivider(split, 105);

        assertThat(split.getDividerLocation()).isEqualTo(450);
        assertThat(preferences.leftColumnWidth()).hasValue(300);
    }

    @Test
    void aMouseDragOfTheTreesDividerStoresItsHeightAt100() {
        JLabel tree = new JLabel("árbol");
        view.addAbovePalette("Circuito", tree);
        appearance.setInterfaceZoom(200);
        settle(view, 1000, 900);
        JSplitPane split = (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, tree);
        assertThat(split.getDividerLocation()).isEqualTo(340);

        dragDivider(split, 60);

        assertThat(split.getDividerLocation()).isEqualTo(400);
        assertThat(preferences.circuitTreeSplit()).hasValue(200);
    }

    @Test
    void theKeyboardMovesOfTheColumnsDividerStore() {
        settle(view, 2000, 700);
        JSplitPane split = outerSplit(view);
        // F8 gives the split the focus, which turns the arrows into divider moves
        for (var listener : split.getFocusListeners()) {
            listener.focusGained(new FocusEvent(split, FocusEvent.FOCUS_GAINED));
        }

        split.getActionMap().get("positiveIncrement").actionPerformed(
                new ActionEvent(split, ActionEvent.ACTION_PERFORMED, "positiveIncrement"));

        assertThat(split.getDividerLocation()).isGreaterThan(230);
        assertThat(preferences.leftColumnWidth()).hasValue(split.getDividerLocation());
    }

    @Test
    void aDragSurvivesAChangeOfTheLookAndFeelsUi() {
        settle(view, 2000, 700);
        JSplitPane split = outerSplit(view);
        SwingUtilities.updateComponentTreeUI(view);
        settle(view, 2000, 700);

        dragDivider(split, 70);

        assertThat(preferences.leftColumnWidth()).hasValue(300);
    }

    @Test
    void leavingPresentationModeGivesBackTheDraggedWidthAfterTheSizeGoesBack() {
        settle(view, 1000, 700);
        JSplitPane split = outerSplit(view);
        dragDivider(split, 110);
        settle(view, 1000, 700);
        // over the 30 % cap (288 px of the 960 px split), so an application of the stored width would not give it back
        assertThat(split.getDividerLocation()).isEqualTo(340);

        // the order of the real mode: the view is laid out, then the interface takes the presentation size; on leaving,
        // the view is put back, then the size goes back
        Runnable restore = view.layOutForFullScreen(EdgeBarsForTests.over(1000, 700));
        appearance.setInterfaceZoom(150);
        restore.run();
        appearance.setInterfaceZoom(100);
        settle(view, 1000, 700);

        assertThat(split.getDividerLocation()).isEqualTo(340);
        assertThat(preferences.leftColumnWidth()).hasValue(340);
    }

    @Test
    void leavingPresentationModeWithoutASizeChangeGivesBackTheDraggedWidth() {
        settle(view, 1000, 700);
        JSplitPane split = outerSplit(view);
        dragDivider(split, 110);
        settle(view, 1000, 700);
        assertThat(split.getDividerLocation()).isEqualTo(340);

        Runnable restore = view.layOutForFullScreen(EdgeBarsForTests.over(1000, 700));
        settle(view, 1000, 700);
        restore.run();
        settle(view, 1000, 700);

        assertThat(split.getDividerLocation()).isEqualTo(340);
    }

    @Test
    void eachViewGetsItsOwnWidthBackAfterPresentationMode() {
        settle(view, 1000, 700);
        JSplitPane split = outerSplit(view);
        EditorView other = newView();
        settle(other, 1000, 700);

        Runnable restore = view.layOutForFullScreen(EdgeBarsForTests.over(1000, 700));
        dragDivider(outerSplit(other), 50);
        assertThat(preferences.leftColumnWidth()).hasValue(280);
        restore.run();
        settle(view, 1000, 700);

        assertThat(split.getDividerLocation()).isEqualTo(230);
    }

    @Test
    void aDisposedViewNoLongerFollowsTheInterfaceSize() {
        settle(view, 2000, 700);
        JSplitPane split = outerSplit(view);
        view.dispose();
        views.remove(view);

        appearance.setInterfaceZoom(200);

        assertThat(split.getDividerLocation()).isEqualTo(230);
    }
}
