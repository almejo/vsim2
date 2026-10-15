package cl.almejo.vsim.ui.frames;

import cl.almejo.vsim.core.command.CreateFrameCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Frame;
import cl.almejo.vsim.core.document.FrameContents;
import cl.almejo.vsim.core.document.FrameTint;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.actions.SelectionActions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A click selects a locked frame alone, but the operations on the selection (Copiar, Cortar, Duplicar, rotate,
 * Supr) act on its block: the frame at (0, 0) 20 x 12 with a NOT gate at (4, 4) inside and one at (30, 4) outside.
 */
class LockedBlockOperationsTest {

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };

    private final FragmentClipboard clipboard = new FragmentClipboard(new Clipboard("test"));
    private OpenDocument openDocument;
    private EditorView view;
    private FramesFeatures features;
    private SelectionActions selectionActions;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, new EditorServices(PainterRegistry.standard(),
                new Appearance(ColorScheme.LIGHT), AppPreferences.inMemory(), new ComponentLibrary(), List.of()),
                NO_WINDOW);
        view.canvas().setSize(400, 300);
        features = new FramesFeatures(view);
        selectionActions = new SelectionActions(openDocument, view.canvas(), view.actions(), clipboard);
    }

    @AfterEach
    void close() {
        features.dispose();
        view.dispose();
        openDocument.close();
    }

    private Frame frame(boolean locked) {
        Frame frame = new Frame(openDocument.document().newFrameId(), new GridPoint(0, 0), 20, 12, "Sumador",
                FrameTint.NONE, locked);
        openDocument.execute(new CreateFrameCommand(frame));
        return frame;
    }

    private Component gate(int x, int y) {
        return place(openDocument, StandardComponentTypes.NOT, x, y, Map.of());
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
    }

    private CircuitFragment copied() throws Exception {
        return new FragmentFormat(openDocument.registry()).read(clipboard.read().orElseThrow());
    }

    @Test
    void copyingASelectedLockedFrameCopiesItsContentsToo() throws Exception {
        Frame frame = frame(true);
        gate(4, 4);
        gate(30, 4);
        view.canvas().selection().select(frame.id());
        perform(selectionActions.copy());
        assertThat(copied().frames()).hasSize(1);
        assertThat(copied().components()).hasSize(1);
        assertThat(view.canvas().selection().ids()).containsExactly(frame.id());
    }

    @Test
    void copyingASelectedUnlockedFrameCopiesTheFrameAlone() throws Exception {
        Frame frame = frame(false);
        gate(4, 4);
        view.canvas().selection().select(frame.id());
        perform(selectionActions.copy());
        assertThat(copied().frames()).hasSize(1);
        assertThat(copied().components()).isEmpty();
    }

    @Test
    void cuttingASelectedLockedFrameCutsTheBlock() throws Exception {
        Frame frame = frame(true);
        gate(4, 4);
        Component outside = gate(30, 4);
        view.canvas().selection().select(frame.id());
        perform(selectionActions.cut());
        assertThat(openDocument.document().elements()).containsExactly(outside);
        assertThat(copied().components()).hasSize(1);
    }

    @Test
    void duplicatingASelectedLockedFrameFloatsTheBlock() {
        Frame frame = frame(true);
        gate(4, 4);
        view.canvas().selection().select(frame.id());
        perform(selectionActions.duplicate());
        assertThat(view.canvas().tool()).isInstanceOf(FloatingSelectionTool.class);
        CircuitFragment floating = ((FloatingSelectionTool) view.canvas().tool()).fragment();
        assertThat(floating.frames()).hasSize(1);
        assertThat(floating.components()).hasSize(1);
    }

    @Test
    void duplicatingASelectedUnlockedFrameFloatsTheFrameAlone() {
        Frame frame = frame(false);
        gate(4, 4);
        view.canvas().selection().select(frame.id());
        perform(selectionActions.duplicate());
        CircuitFragment floating = ((FloatingSelectionTool) view.canvas().tool()).fragment();
        assertThat(floating.frames()).hasSize(1);
        assertThat(floating.components()).isEmpty();
    }

    @Test
    void rotatingASelectedLockedFrameTurnsTheBlockAndUndoIsExact() {
        Frame frame = frame(true);
        Component inside = gate(4, 4);
        List<DocumentElement> before = openDocument.document().elements();
        view.canvas().selection().select(frame.id());
        perform(view.actions().rotateClockwise());
        Frame turned = openDocument.document().frame(frame.id()).orElseThrow();
        assertThat(turned.width()).isEqualTo(12);
        assertThat(turned.height()).isEqualTo(20);
        assertThat(openDocument.document().component(inside.id()).orElseThrow()).isNotEqualTo(inside);
        assertThat(FrameContents.of(openDocument.document(), openDocument.registry(), turned)).contains(inside.id());
        openDocument.history().undo();
        assertThat(openDocument.document().elements()).isEqualTo(before);
    }

    @Test
    void rotatingASelectedUnlockedFrameLeavesItsContentsAlone() {
        Frame frame = frame(false);
        Component inside = gate(4, 4);
        view.canvas().selection().select(frame.id());
        perform(view.actions().rotateClockwise());
        assertThat(openDocument.document().frame(frame.id()).orElseThrow().width()).isEqualTo(12);
        assertThat(openDocument.document().component(inside.id()).orElseThrow()).isEqualTo(inside);
    }

    @Test
    void deletingASelectedLockedFrameDeletesTheBlockWithTheNoticeAndUndoRestoresExactly() {
        Frame frame = frame(true);
        gate(4, 4);
        gate(10, 4);
        Component outside = gate(30, 4);
        List<DocumentElement> before = openDocument.document().elements();
        view.canvas().selection().select(frame.id());
        perform(view.actions().delete());
        assertThat(openDocument.document().elements()).containsExactly(outside);
        assertThat(view.statusBar().text()).contains("Se borró el bloque «Sumador» (2 elementos)");
        openDocument.history().undo();
        assertThat(openDocument.document().elements()).isEqualTo(before);
    }

    @Test
    void deletingASelectedUnlockedFrameDeletesTheFrameAlone() {
        Frame frame = frame(false);
        Component inside = gate(4, 4);
        view.canvas().selection().select(frame.id());
        perform(view.actions().delete());
        assertThat(openDocument.document().elements()).containsExactly(inside);
        assertThat(view.statusBar().text()).doesNotContain("bloque");
    }

    @Test
    void quitarMarcoStillRemovesOnlyTheFrame() {
        Frame frame = frame(true);
        Component inside = gate(4, 4);
        view.canvas().selection().select(frame.id());
        JPopupMenu menu = features.frameMenu().build(frame);
        for (java.awt.Component child : menu.getComponents()) {
            if (child instanceof JMenuItem item && "Quitar marco".equals(item.getText())) {
                item.doClick();
            }
        }
        assertThat(openDocument.document().elements()).containsExactly(inside);
    }

    @Test
    void afterDisposeASelectedLockedFrameActsAlone() {
        Frame frame = frame(true);
        Component inside = gate(4, 4);
        features.dispose();
        view.canvas().selection().select(frame.id());
        perform(view.actions().delete());
        assertThat(openDocument.document().elements()).containsExactly(inside);
    }
}
