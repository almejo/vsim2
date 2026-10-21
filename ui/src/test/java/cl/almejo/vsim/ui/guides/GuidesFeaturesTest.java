package cl.almejo.vsim.ui.guides;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.document.Guide.Orientation;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.grid.GridFeatures;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.Scope;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutEntry;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Rulers, guide gestures, the Ver items and the context menu (spec §4.2–§4.5, canvas-edges §1–§4). */
class GuidesFeaturesTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
    private static final Guide HORIZONTAL_10 = new Guide(Orientation.HORIZONTAL, 10);
    private static final Guide HORIZONTAL_20 = new Guide(Orientation.HORIZONTAL, 20);

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<JPopupMenu> shownMenus = new ArrayList<>();
    private OpenDocument openDocument;
    private EditorServices services;
    private EditorView view;
    private GuidesFeatures features;
    private final List<Runnable> cleanUps = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT), preferences,
                new ComponentLibrary(), List.of());
        view = newView();
        features = newFeatures(view);
    }

    private EditorView newView() {
        EditorView created = new EditorView(openDocument, services, NO_WINDOW);
        created.canvas().setSize(400, 300);
        cleanUps.add(created::dispose);
        return created;
    }

    private GuidesFeatures newFeatures(EditorView target) {
        GuidesFeatures created = new GuidesFeatures(target,
                (menu, invoker, x, y) -> shownMenus.add(menu));
        cleanUps.add(0, created::dispose);
        return created;
    }

    @AfterEach
    void close() {
        cleanUps.forEach(Runnable::run);
        openDocument.close();
    }

    private List<Guide> guides() {
        return openDocument.document().guides();
    }

    private void setGuides(Guide... guides) {
        openDocument.document().setGuides(List.of(guides));
    }

    private static MouseEvent mouse(CircuitCanvas canvas, int id, int x, int y, int modifiers, int button) {
        return new MouseEvent(canvas, id, 0, modifiers, x, y, 1, false, button);
    }

    private static void press(EditorView target, int x, int y) {
        target.canvas().getMouseListeners()[0].mousePressed(mouse(target.canvas(), MouseEvent.MOUSE_PRESSED, x, y,
                InputEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1));
    }

    private static void drag(EditorView target, int x, int y) {
        target.canvas().getMouseMotionListeners()[0].mouseDragged(mouse(target.canvas(), MouseEvent.MOUSE_DRAGGED,
                x, y, InputEvent.BUTTON1_DOWN_MASK, MouseEvent.NOBUTTON));
    }

    private static void release(EditorView target, int x, int y) {
        target.canvas().getMouseListeners()[0].mouseReleased(mouse(target.canvas(), MouseEvent.MOUSE_RELEASED, x, y,
                0, MouseEvent.BUTTON1));
    }

    private static void move(EditorView target, int x, int y) {
        target.canvas().getMouseMotionListeners()[0].mouseMoved(mouse(target.canvas(), MouseEvent.MOUSE_MOVED, x, y,
                0, MouseEvent.NOBUTTON));
    }

    private void pull(int fromX, int fromY, int toX, int toY) {
        press(view, fromX, fromY);
        drag(view, (fromX + toX) / 2, (fromY + toY) / 2);
        drag(view, toX, toY);
        release(view, toX, toY);
    }

    private static List<String> texts(java.awt.Component[] children) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : children) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private static void click(JPopupMenu menu, String text) {
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                item.getAction().actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, ""));
                return;
            }
        }
        throw new AssertionError("no item " + text + " in " + texts(menu.getComponents()));
    }

    // --- creating, moving, removing -----------------------------------------------------------------------------

    @Test
    void pullingFromTheTopRulerAddsOneHorizontalGuideInOneUndoStep() {
        pull(100, 5, 100, 83);

        assertThat(guides()).containsExactly(HORIZONTAL_10);
        assertThat(view.statusBar().text()).contains("Guía agregada");
        assertThat(openDocument.history().undoCommand().orElseThrow().description()).isEqualTo("command.addGuide");
        openDocument.history().undo();
        assertThat(guides()).isEmpty();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void pullingFromTheLeftRulerAddsAVerticalGuide() {
        pull(5, 100, 83, 100);

        assertThat(guides()).containsExactly(new Guide(Orientation.VERTICAL, 10));
    }

    @Test
    void releasingOverItsRulerOverTheOtherRulerOrOutsideThePaneCreatesNothing() {
        pull(100, 5, 100, 4);
        pull(100, 5, 4, 120);
        pull(100, 5, 100, 400);
        pull(100, 5, -20, 100);

        assertThat(guides()).isEmpty();
        assertThat(openDocument.history().canUndo()).isFalse();
    }

    @Test
    void escapeCancelsAGuideBeingPulled() {
        press(view, 100, 5);
        drag(view, 100, 80);
        view.canvas().getKeyListeners()[0].keyPressed(new KeyEvent(view.canvas(), KeyEvent.KEY_PRESSED, 0, 0,
                KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        drag(view, 100, 90);
        release(view, 100, 90);

        assertThat(guides()).isEmpty();
        assertThat(view.canvas().isEdgeGestureActive()).isFalse();
    }

    @Test
    void draggingAGuideBackOntoItsRulerRemovesItInOneUndoStep() {
        setGuides(HORIZONTAL_10);

        pull(300, 80, 300, 4);

        assertThat(guides()).isEmpty();
        assertThat(view.statusBar().text()).contains("Guía quitada");
        openDocument.history().undo();
        assertThat(guides()).containsExactly(HORIZONTAL_10);
    }

    @Test
    void aGuideMovesByItsLineOrByItsMarkerAndAClickOnTheMarkerChangesNothing() {
        setGuides(HORIZONTAL_10);

        pull(300, 81, 300, 120);
        assertThat(guides()).containsExactly(new Guide(Orientation.HORIZONTAL, 15));
        assertThat(openDocument.history().undoCommand().orElseThrow().description()).isEqualTo("command.moveGuide");

        press(view, 5, 120);
        release(view, 5, 120);
        assertThat(guides()).containsExactly(new Guide(Orientation.HORIZONTAL, 15));

        pull(5, 120, 200, 160);
        assertThat(guides()).containsExactly(HORIZONTAL_20);
        openDocument.history().undo();
        openDocument.history().undo();
        assertThat(guides()).containsExactly(HORIZONTAL_10);
    }

    @Test
    void movingAGuideOntoAnotherMergesThemInOneStep() {
        setGuides(HORIZONTAL_10, HORIZONTAL_20);

        pull(300, 80, 300, 160);

        assertThat(guides()).containsExactly(HORIZONTAL_20);
        openDocument.history().undo();
        assertThat(guides()).containsExactly(HORIZONTAL_10, HORIZONTAL_20);
    }

    @Test
    void aGuideUnderAnElementIsNotGrabbedTheElementWins() {
        Component gate = place(openDocument, StandardComponentTypes.AND2, 8, 8, Map.of());
        assertThat(view.canvas().hitTester().elementAt(openDocument.document(), new Point2D.Double(10, 10)))
                .isPresent();
        setGuides(HORIZONTAL_10);

        pull(80, 80, 80, 120);

        assertThat(guides()).containsExactly(HORIZONTAL_10);
        assertThat(openDocument.document().element(gate.id()).orElseThrow()).isNotEqualTo(gate);
    }

    @Test
    void onlyTheSelectToolGrabsAGuide() {
        setGuides(HORIZONTAL_10);
        view.canvas().setTool(new PanTool());

        pull(300, 80, 300, 120);

        assertThat(guides()).containsExactly(HORIZONTAL_10);
    }

    @Test
    void theRulersAndAGrabbableGuideShowTheirCursorAndTooltip() {
        setGuides(HORIZONTAL_10);

        move(view, 100, 5);
        assertThat(view.canvas().getCursor()).isEqualTo(GuideCursors.forOrientation(Orientation.HORIZONTAL));
        assertThat(view.canvas().getToolTipText(mouse(view.canvas(), MouseEvent.MOUSE_MOVED, 100, 5, 0,
                MouseEvent.NOBUTTON))).isEqualTo("Arrastra para crear una guía");
        move(view, 300, 81);
        assertThat(view.canvas().getCursor()).isEqualTo(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        move(view, 300, 120);
        assertThat(view.canvas().getCursor()).isEqualTo(Cursor.getDefaultCursor());
    }

    @Test
    void theTickSpacingKeepsTicksAtLeastSixteenPixelsApart() {
        assertThat(RulerHandle.tickSpacing(2)).isEqualTo(10);
        assertThat(RulerHandle.tickSpacing(4)).isEqualTo(5);
        assertThat(RulerHandle.tickSpacing(8)).isEqualTo(2);
        assertThat(RulerHandle.tickSpacing(16)).isEqualTo(1);
        assertThat(RulerHandle.tickSpacing(32)).isEqualTo(1);
        assertThat(RulerHandle.tickSpacing(0.25)).isEqualTo(100);
    }

    // --- two views, undo ------------------------------------------------------------------------------------------

    @Test
    void twoViewsShareTheGuidesAndARemovalInOneCancelsTheDragInTheOther() {
        EditorView other = newView();
        newFeatures(other);
        pull(100, 5, 100, 80);
        assertThat(guides()).containsExactly(HORIZONTAL_10);

        press(view, 300, 80);
        drag(view, 300, 120);
        assertThat(view.canvas().isEdgeGestureActive()).isTrue();
        JPopupMenu otherMenu = other.contextMenu(Optional.empty(), new Point2D.Double(30, 10.1));
        click(otherMenu, "Quitar guía");

        assertThat(view.canvas().isEdgeGestureActive()).isFalse();
        drag(view, 300, 130);
        release(view, 300, 130);
        assertThat(guides()).isEmpty();
    }

    @Test
    void anUndoDuringAPreviewCancelsIt() {
        pull(100, 5, 100, 80);
        press(view, 100, 5);
        drag(view, 100, 120);

        openDocument.history().undo();
        release(view, 100, 120);

        assertThat(guides()).isEmpty();
        assertThat(view.canvas().isEdgeGestureActive()).isFalse();
    }

    // --- menus ---------------------------------------------------------------------------------------------------

    @Test
    void theVerItemsComeRightAfterTheGridSubmenu() {
        GridFeatures grid = new GridFeatures(view);
        try {
            features.dispose();
            GuidesFeatures afterGrid = newFeatures(view);
            List<String> texts = texts(view.menu(MenuId.VIEW).getMenuComponents());
            int gridIndex = texts.indexOf("Cuadrícula");
            assertThat(texts.subList(gridIndex, gridIndex + 6)).containsExactly("Cuadrícula", "Mostrar guías",
                    "Agregar guía horizontal", "Agregar guía vertical", "Borrar guías", "---");
            assertThat(view.shortcutActions()).containsEntry("view.showGuides", afterGrid.showGuidesAction())
                    .containsEntry("view.addHorizontalGuide", afterGrid.addHorizontalAction())
                    .containsEntry("view.addVerticalGuide", afterGrid.addVerticalAction())
                    .containsEntry("view.clearGuides", afterGrid.clearAction());
        } finally {
            grid.dispose();
        }
        for (String id : List.of("view.showGuides", "view.addHorizontalGuide", "view.addVerticalGuide",
                "view.clearGuides")) {
            assertThat(ShortcutCatalog.find(id)).as(id).hasValueSatisfying(entry -> {
                assertThat(entry.group()).isEqualTo(MenuId.VIEW);
                assertThat(entry.scope()).isEqualTo(Scope.WINDOW);
                assertThat(entry.defaultStroke()).isEmpty();
            });
        }
        assertThat(ShortcutCatalog.find("view.showGuides").map(ShortcutEntry::nameKey)).contains("action.showGuides");
    }

    @Test
    void clearGuidesIsDisabledWithoutGuidesAndClearsInOneStep() {
        Action clear = features.clearAction();
        assertThat(clear.isEnabled()).isFalse();
        setGuides(HORIZONTAL_10, new Guide(Orientation.VERTICAL, 4));
        assertThat(clear.isEnabled()).isTrue();

        clear.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));

        assertThat(guides()).isEmpty();
        assertThat(clear.isEnabled()).isFalse();
        assertThat(openDocument.history().undoCommand().orElseThrow().description()).isEqualTo("command.clearGuides");
        openDocument.history().undo();
        assertThat(guides()).hasSize(2);
    }

    @Test
    void addingFromVerGoesToThePointerOrElseToTheMiddleOfThePane() {
        move(view, 120, 64);
        features.addHorizontalAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
        view.canvas().getMouseListeners()[0].mouseExited(mouse(view.canvas(), MouseEvent.MOUSE_EXITED, 500, 500, 0,
                MouseEvent.NOBUTTON));
        features.addVerticalAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));

        assertThat(guides()).containsExactly(new Guide(Orientation.HORIZONTAL, 8), new Guide(Orientation.VERTICAL, 25));
        assertThat(view.statusBar().text()).contains("Guía agregada");
    }

    @Test
    void addingFromVerWhileTheGuidesAreHiddenShowsThem() {
        preferences.setShowGuides(false);

        features.addVerticalAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));

        assertThat(preferences.showGuides()).isTrue();
        assertThat(guides()).hasSize(1);
    }

    @Test
    void theContextMenuAddsGuidesOnTheEmptyCanvasAndRemovesTheOneClicked() {
        JPopupMenu empty = view.contextMenu(Optional.empty(), new Point2D.Double(12.3, 7.6));
        assertThat(texts(empty.getComponents())).contains("Agregar guía horizontal aquí", "Agregar guía vertical aquí")
                .doesNotContain("Quitar guía");
        click(empty, "Agregar guía horizontal aquí");
        click(view.contextMenu(Optional.empty(), new Point2D.Double(12.3, 20)), "Agregar guía vertical aquí");
        assertThat(guides()).containsExactly(new Guide(Orientation.HORIZONTAL, 8),
                new Guide(Orientation.VERTICAL, 12));

        JPopupMenu onGuide = view.contextMenu(Optional.empty(), new Point2D.Double(30, 8.3));
        assertThat(texts(onGuide.getComponents())).contains("Quitar guía")
                .doesNotContain("Agregar guía horizontal aquí");
        click(onGuide, "Quitar guía");

        assertThat(guides()).containsExactly(new Guide(Orientation.VERTICAL, 12));
        assertThat(view.statusBar().text()).contains("Guía quitada");
        assertThat(openDocument.history().undoCommand().orElseThrow().description()).isEqualTo("command.removeGuide");
    }

    @Test
    void aClickOnTheCornerOpensTheGuidesMenuAndADragFromItDoesNothing() {
        press(view, 5, 5);
        drag(view, 60, 60);
        release(view, 60, 60);
        assertThat(shownMenus).isEmpty();

        press(view, 5, 5);
        release(view, 5, 5);

        assertThat(shownMenus).hasSize(1);
        assertThat(texts(shownMenus.get(0).getComponents())).containsExactly("Agregar guía horizontal",
                "Agregar guía vertical", "---", "Mostrar guías", "Borrar guías");
        assertThat(guides()).isEmpty();
    }

    // --- showing, hiding, painting, dispose ----------------------------------------------------------------------

    @Test
    void hidingTheGuidesTakesAwayTheRulersAndCancelsAGestureInProgress() {
        CircuitCanvas canvas = view.canvas();
        int overlaysShown = canvas.overlayCount();
        assertThat(canvas.edgeHandles()).hasSize(4);
        press(view, 100, 5);
        drag(view, 100, 80);

        preferences.setShowGuides(false);

        assertThat(canvas.isEdgeGestureActive()).isFalse();
        assertThat(canvas.edgeHandles()).isEmpty();
        assertThat(canvas.overlayCount()).isEqualTo(overlaysShown - 1);
        assertThat(features.showGuidesAction().getValue(Action.SELECTED_KEY)).isEqualTo(false);
        release(view, 100, 80);
        assertThat(guides()).isEmpty();

        features.showGuidesAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(preferences.showGuides()).isTrue();
        assertThat(canvas.edgeHandles()).hasSize(4);
    }

    @Test
    void guidesAreDashedOneLogicalPixelLinesAndTheRulersCoverTheEdges() {
        BufferedImage without = paint(view.canvas());
        setGuides(HORIZONTAL_10);
        BufferedImage with = paint(view.canvas());

        assertThat(differingPixels(without, with, 20, 80)).isPositive();
        assertThat(differingPixels(without, with, 20, 79)).isZero();
        assertThat(differingPixels(without, with, 20, 81)).isZero();
        assertThat(with.getRGB(203, 5)).isNotEqualTo(with.getRGB(203, 43)); // the top ruler

        preferences.setShowGuides(false);
        BufferedImage hidden = paint(view.canvas());
        assertThat(differingPixels(without, hidden, 20, 80)).isZero(); // the guide is not drawn…
        assertThat(hidden.getRGB(203, 5)).isEqualTo(hidden.getRGB(203, 43)); // …and the canvas reaches the edge
        assertThat(hidden.getRGB(5, 83)).isEqualTo(hidden.getRGB(5, 43));
    }

    @Test
    void theGuideTheActiveGestureSnappedToIsDrawnSolidAndTheOthersStayDashed() {
        BufferedImage without = paint(view.canvas());
        setGuides(HORIZONTAL_10, HORIZONTAL_20);
        BufferedImage dashed = paint(view.canvas());
        view.canvas().setTool(new cl.almejo.vsim.ui.tools.Tool() {
            @Override
            public List<Guide> snappedGuides() {
                return List.of(HORIZONTAL_10);
            }
        });
        BufferedImage highlighted = paint(view.canvas());

        int width = without.getWidth() - 20;
        assertThat(differingPixels(without, dashed, 20, 80)).isLessThan(width * 3 / 4);
        assertThat(differingPixels(without, highlighted, 20, 80)).isEqualTo(width); // solid across the canvas
        assertThat(differingPixels(dashed, highlighted, 20, 160)).isZero(); // the other guide is still dashed
        view.canvas().setTool(view.canvas().defaultTool());
        assertThat(differingPixels(dashed, paint(view.canvas()), 20, 80)).isZero();
    }

    private static BufferedImage paint(CircuitCanvas canvas) {
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        canvas.paint(graphics);
        graphics.dispose();
        return image;
    }

    /** Pixels of row {@code y} from {@code fromX} to the right edge that differ. */
    private static int differingPixels(BufferedImage first, BufferedImage second, int fromX, int y) {
        int count = 0;
        for (int x = fromX; x < first.getWidth(); x++) {
            if (first.getRGB(x, y) != second.getRGB(x, y)) {
                count++;
            }
        }
        return count;
    }

    @Test
    void disposeTakesBackEverythingItAdded() {
        CircuitCanvas canvas = view.canvas();
        int overlaysShown = canvas.overlayCount();
        int preferenceListeners = preferences.listenerCount();
        int guideListeners = openDocument.document().guideListenerCount();
        int viewItems = view.menu(MenuId.VIEW).getMenuComponentCount();
        press(view, 100, 5);
        drag(view, 100, 80);

        features.dispose();
        features.dispose();

        assertThat(canvas.isEdgeGestureActive()).isFalse();
        assertThat(canvas.edgeHandles()).isEmpty();
        assertThat(canvas.overlayCount()).isEqualTo(overlaysShown - 1);
        assertThat(preferences.listenerCount()).isEqualTo(preferenceListeners - 1);
        assertThat(openDocument.document().guideListenerCount()).isEqualTo(guideListeners - 1);
        assertThat(view.menu(MenuId.VIEW).getMenuComponentCount()).isEqualTo(viewItems - 5); // with its separator
        assertThat(view.shortcutActions()).doesNotContainKeys("view.showGuides", "view.addHorizontalGuide",
                "view.addVerticalGuide", "view.clearGuides");
        assertThat(texts(view.contextMenu(Optional.empty(), new Point2D.Double(3, 3)).getComponents()))
                .doesNotContain("Agregar guía horizontal aquí");
    }

    @Test
    void theGuideColourIsCyanAndPaintedFromTheCurrentScheme() {
        assertThat(ColorScheme.LIGHT.guide().getBlue()).isGreaterThan(ColorScheme.LIGHT.guide().getRed());
        assertThat(ColorScheme.LIGHT.guide().getGreen()).isGreaterThan(ColorScheme.LIGHT.guide().getRed());
    }
}
