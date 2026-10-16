package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.command.ChangeFrameCommand;
import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.command.SetLabelCommand;
import cl.almejo.vsim.core.geometry.Bounds;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.canvas.LabelLayout;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.TestToolContext;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class FramesFeaturesTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private OpenDocument openDocument;
    private EditorView view;
    private FramesFeatures features;
    private final List<Frame> titleEdited = new ArrayList<>();

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        features = new FramesFeatures(view);
        features.setTitleEditor(titleEdited::add);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private void frameSelection() {
        features.frameSelectionAction().actionPerformed(
                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void theActionIsDisabledWithoutASelectionAndEnabledWithOne() {
        assertThat(features.frameSelectionAction().isEnabled()).isFalse();
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());
        assertThat(features.frameSelectionAction().isEnabled()).isTrue();
        view.canvas().selection().clear();
        assertThat(features.frameSelectionAction().isEnabled()).isFalse();
    }

    @Test
    void theFrameIsTheSelectionBoundsPlusAMarginOfTwo() {
        Component first = place(openDocument, StandardComponentTypes.SWITCH, 0, 0, Map.of());
        Component second = place(openDocument, StandardComponentTypes.LED, 40, 20, Map.of());
        view.canvas().selection().set(List.of(first.id(), second.id()));
        Bounds bounds = ElementBounds.union(List.of(first, second), openDocument.registry()).orElseThrow();

        frameSelection();

        assertThat(openDocument.document().frames()).hasSize(1);
        Frame frame = openDocument.document().frames().get(0);
        assertThat(frame.topLeft().x()).isEqualTo(bounds.x() - 2);
        assertThat(frame.topLeft().y()).isEqualTo(bounds.y() - 4);
        assertThat(frame.width()).isEqualTo(bounds.width() + 4);
        assertThat(frame.height()).isEqualTo(bounds.height() + 6);
    }

    @Test
    void theTitleBarGetsItsOwnRoomAboveTheTopMargin() {
        Component led = place(openDocument, StandardComponentTypes.LED, 20, 20, Map.of());
        view.canvas().selection().select(led.id());
        Bounds bounds = ElementBounds.union(List.of(led), openDocument.registry()).orElseThrow();

        frameSelection();

        Frame frame = openDocument.document().frames().get(0);
        int titleBarUnits = (int) Math.ceil(FramePainter.TITLE_BAR_HEIGHT / GridPoint.PIXELS_PER_UNIT);
        assertThat(frame.topLeft().y()).isEqualTo(bounds.y() - FramesFeatures.MARGIN - titleBarUnits);
    }

    @Test
    void aComponentLabelWiderThanItsBodyFitsInsideTheFrame() {
        Component led = place(openDocument, StandardComponentTypes.LED, 20, 20, Map.of());
        Component labelled = led.withLabel("Acarreo de salida (Cout)");
        openDocument.execute(new SetLabelCommand(led.id(), labelled.label()));
        view.canvas().selection().select(led.id());
        Rectangle2D label = LabelLayout.componentLabel(openDocument.registry().typeOf(labelled), labelled)
                .orElseThrow().bounds();

        frameSelection();

        Frame frame = openDocument.document().frames().get(0);
        double unit = GridPoint.PIXELS_PER_UNIT;
        assertThat((frame.topLeft().x() + FramesFeatures.MARGIN) * unit).isLessThanOrEqualTo(label.getMinX());
        assertThat((frame.topLeft().x() + frame.width() - FramesFeatures.MARGIN) * unit)
                .isGreaterThanOrEqualTo(label.getMaxX());
        assertThat(frame.topLeft().y() * unit + FramePainter.TITLE_BAR_HEIGHT).isLessThanOrEqualTo(label.getMinY());
    }

    @Test
    void aTinySelectionGrowsTheFrameToTheMinimumSize() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());
        Bounds bounds = ElementBounds.union(List.of(led), openDocument.registry()).orElseThrow();
        assertThat(bounds.width() + 4).isLessThan(Frame.MINIMUM_WIDTH + 1);

        frameSelection();

        Frame frame = openDocument.document().frames().get(0);
        assertThat(frame.width()).isGreaterThanOrEqualTo(Frame.MINIMUM_WIDTH);
        assertThat(frame.height()).isGreaterThanOrEqualTo(Frame.MINIMUM_HEIGHT);
        assertThat(frame.topLeft().x()).isEqualTo(bounds.x() - 2);
    }

    @Test
    void theFrameIsBornUnlockedTitledBloqueAndSelected() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());

        frameSelection();

        Frame frame = openDocument.document().frames().get(0);
        assertThat(frame.locked()).isFalse();
        assertThat(frame.title()).isEqualTo("Bloque");
        assertThat(frame.tint()).isEqualTo(FrameTint.NONE);
        assertThat(view.canvas().selection().ids()).containsExactly(frame.id());
    }

    @Test
    void oneUndoRemovesTheFrameAndRedoBringsItBack() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());
        frameSelection();
        Frame frame = openDocument.document().frames().get(0);

        openDocument.history().undo();
        assertThat(openDocument.document().frames()).isEmpty();
        assertThat(openDocument.document().element(led.id())).isPresent();

        openDocument.history().redo();
        assertThat(openDocument.document().frames()).containsExactly(frame);
    }

    @Test
    void theEditMenuHasTheItemRightAfterSelectAllAndDisposeTakesItBack() {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : view.menu(MenuId.EDIT).getMenuComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        assertThat(texts).containsSubsequence("Seleccionar todo", "Enmarcar selección");
        assertThat(texts.indexOf("Enmarcar selección")).isEqualTo(texts.indexOf("Seleccionar todo") + 1);
        assertThat(view.shortcutActions()).containsKey("edit.frameSelection");

        features.dispose();

        assertThat(view.shortcutActions()).doesNotContainKey("edit.frameSelection");
        assertThat(view.menu(MenuId.EDIT).getMenuComponents())
                .noneMatch(child -> child instanceof JMenuItem item && "Enmarcar selección".equals(item.getText()));
    }

    @Test
    void theContextMenuOffersItOverASelectionAndNotOverAnEmptyCanvas() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());
        List<String> withSelection = menuTexts(view.contextMenu(Optional.of(led), new java.awt.geom.Point2D.Double(10, 10)));
        assertThat(withSelection).contains("Enmarcar selección");
        List<String> onEmpty = menuTexts(view.contextMenu(Optional.empty(), new java.awt.geom.Point2D.Double(50, 50)));
        assertThat(onEmpty).doesNotContain("Enmarcar selección");
    }

    private Frame addFrame(boolean locked) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 12, "Sumador",
                FrameTint.NONE, locked);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    private void delete() {
        view.actions().delete().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void deletingALockedBlockSaysSoInTheStatusBarAndUndoBringsItBack() {
        Frame frame = addFrame(true);
        Component first = place(openDocument, StandardComponentTypes.NOT, 4, 4, Map.of());
        Component second = place(openDocument, StandardComponentTypes.NOT, 10, 4, Map.of());
        view.canvas().selection().set(List.of(frame.id(), first.id(), second.id()));

        delete();

        assertThat(openDocument.document().elements()).isEmpty();
        assertThat(view.statusBar().text()).contains("Se borró el bloque «Sumador» (2 elementos)");
        openDocument.history().undo();
        assertThat(openDocument.document().elements()).hasSize(3);
    }

    @Test
    void deletingAnUnlockedFrameOrPartOfABlockShowsNoNotice() {
        Frame unlocked = addFrame(false);
        Component inside = place(openDocument, StandardComponentTypes.NOT, 4, 4, Map.of());
        view.canvas().selection().set(List.of(unlocked.id(), inside.id()));
        delete();
        assertThat(view.statusBar().text()).doesNotContain("bloque");

        // part of a locked block's contents, without its frame
        Frame locked = addFrame(true);
        Component deleted = place(openDocument, StandardComponentTypes.NOT, 4, 4, Map.of());
        place(openDocument, StandardComponentTypes.NOT, 10, 4, Map.of());
        view.canvas().selection().set(List.of(deleted.id()));
        delete();
        assertThat(openDocument.document().frame(locked.id())).isPresent();
        assertThat(view.statusBar().text()).doesNotContain("bloque");
    }

    @Test
    void deletingAnEmptyLockedFrameShowsNoBlockNotice() {
        Frame empty = addFrame(true);
        view.canvas().selection().set(List.of(empty.id()));
        delete();
        assertThat(openDocument.document().elements()).isEmpty();
        assertThat(view.statusBar().text()).doesNotContain("bloque");
    }

    @Test
    void disposeTakesBackTheSelectionListenerAndTheContextMenuContributor() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        Frame frame = addFrame(false);
        features.dispose();

        view.canvas().selection().select(led.id());
        assertThat(features.frameSelectionAction().isEnabled()).isFalse();
        List<String> overFrame = menuTexts(view.contextMenu(Optional.of(frame), new java.awt.geom.Point2D.Double(1, 1)));
        assertThat(overFrame).doesNotContain("Enmarcar selección", "Cerrar candado", "Quitar marco");
    }

    @Test
    void theFaintPadlockOfALockedFrameShowsWhateverToolIsActive() {
        Frame frame = addFrame(true);
        view.canvas().setTool(new FrameTool());
        List<Integer> locked = lockPixels(frame);
        openDocument.execute(new ChangeFrameCommand(frame, frame.withLocked(false)));
        List<Integer> unlocked = lockPixels(frame);
        assertThat(locked).isNotEqualTo(unlocked);
    }

    private List<Integer> lockPixels(Frame frame) {
        view.canvas().setSize(240, 120);
        BufferedImage image = new BufferedImage(240, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        view.canvas().paint(graphics);
        graphics.dispose();
        Rectangle2D lock = FramePainter.lockBounds(frame);
        java.awt.geom.Point2D topLeft = view.canvas().viewport().gridToScreen(
                lock.getMinX() / GridPoint.PIXELS_PER_UNIT, lock.getMinY() / GridPoint.PIXELS_PER_UNIT);
        List<Integer> pixels = new ArrayList<>();
        for (int x = 0; x < (int) lock.getWidth(); x++) {
            for (int y = 0; y < (int) lock.getHeight(); y++) {
                pixels.add(image.getRGB((int) topLeft.getX() + x, (int) topLeft.getY() + y));
            }
        }
        return pixels;
    }

    @Test
    void theCanvasSelectToolGetsTheFrameGesturesUntilDispose() {
        Frame frame = addFrame(true);
        Component inside = place(openDocument, StandardComponentTypes.NOT, 4, 4, Map.of());
        Tool select = view.canvas().defaultTool();
        select.mousePressed(view.canvas(), TestToolContext.at(10, 1));
        select.mouseReleased(view.canvas(), TestToolContext.at(10, 1));
        assertThat(view.canvas().selection().ids()).containsExactly(frame.id());
        assertThat(view.canvas().selection().effectiveIds()).containsExactlyInAnyOrder(frame.id(), inside.id());

        features.dispose();

        select.mousePressed(view.canvas(), TestToolContext.at(10, 1));
        select.mouseReleased(view.canvas(), TestToolContext.at(10, 1));
        assertThat(view.canvas().selection().isEmpty()).isTrue();
        view.canvas().selection().select(frame.id());
        assertThat(view.canvas().selection().effectiveIds()).containsExactly(frame.id());
    }

    private static List<String> menuTexts(javax.swing.JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item) {
                texts.add(item.getText());
            }
        }
        return texts;
    }

    @Test
    void enmarcarSeleccionOpensTheTitleEditorForTheNewFrame() {
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());

        frameSelection();

        assertThat(titleEdited).containsExactly(openDocument.document().frames().get(0));
    }

    @Test
    void theRealTitleEditorOpensInlineOverTheNewFrameWithBloqueSelected() {
        features.setTitleEditor(view::editLabel);
        Component led = place(openDocument, StandardComponentTypes.LED, 10, 10, Map.of());
        view.canvas().selection().select(led.id());

        frameSelection();

        assertThat(view.inlineEditor().isOpen()).isTrue();
        assertThat(view.inlineEditor().field().getText()).isEqualTo("Bloque");
        assertThat(view.inlineEditor().field().getSelectedText()).isEqualTo("Bloque");
        view.inlineEditor().field().setText("Suma");
        view.inlineEditor().confirm();
        assertThat(openDocument.document().frames().get(0).title()).isEqualTo("Suma");
    }

    @Test
    void theMarcoToolHasAToolbarButtonBetweenCableAndManoAndAViewMenuItem() {
        List<String> names = new ArrayList<>();
        for (java.awt.Component child : view.toolBar().getComponents()) {
            if (child instanceof javax.swing.JToggleButton button) {
                names.add(button.getAction().getValue(javax.swing.Action.NAME).toString());
            }
        }
        assertThat(names).containsExactly("Selección", "Cable", "Marco", "Mover vista");
        List<String> viewTexts = new ArrayList<>();
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenuItem item) {
                viewTexts.add(item.getText());
            }
        }
        assertThat(viewTexts.subList(0, 4)).containsExactly("Selección", "Cable", "Marco", "Mover vista");
    }

    @Test
    void choosingTheToolSelectsItsButtonAndDisposeTakesButtonAndItemBack() {
        features.frameToolAction().actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""));
        assertThat(view.canvas().tool()).isInstanceOf(FrameTool.class);
        assertThat(view.toolButton(FrameTool.class).orElseThrow().isSelected()).isTrue();

        features.dispose();

        assertThat(view.toolButton(FrameTool.class)).isEmpty();
        for (java.awt.Component child : view.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenuItem item) {
                assertThat(item.getText()).isNotEqualTo("Marco");
            }
        }
    }
}
