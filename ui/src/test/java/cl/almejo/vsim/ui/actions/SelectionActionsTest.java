package cl.almejo.vsim.ui.actions;

import cl.almejo.vsim.core.command.AddWireCommand;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.Wire;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.FloatingSelectionTool;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Optional;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

class SelectionActionsTest {

    private final Clipboard systemClipboard = new Clipboard("test");
    private final FragmentClipboard clipboard = new FragmentClipboard(systemClipboard);
    private OpenDocument openDocument;
    private EditorView view;
    private SelectionActions actions;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
        });
        view.canvas().setSize(400, 300);
        actions = new SelectionActions(openDocument, view.canvas(), view.actions(), clipboard);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private Component gateAt(int x, int y) {
        return place(openDocument, StandardComponentTypes.NOT, x, y, Map.of());
    }

    private static void perform(Action action) {
        action.actionPerformed(new ActionEvent(new Object(), ActionEvent.ACTION_PERFORMED, ""));
    }

    private void moveMouseTo(int screenX, int screenY) {
        view.canvas().getMouseMotionListeners()[0].mouseMoved(new MouseEvent(view.canvas(), MouseEvent.MOUSE_MOVED,
                0, 0, screenX, screenY, 0, false, MouseEvent.NOBUTTON));
    }

    private void moveMouseOut() {
        view.canvas().getMouseListeners()[0].mouseExited(new MouseEvent(view.canvas(), MouseEvent.MOUSE_EXITED,
                0, 0, -5, -5, 0, false, MouseEvent.NOBUTTON));
    }

    private FloatingSelectionTool floating() {
        assertThat(view.canvas().tool()).isInstanceOf(FloatingSelectionTool.class);
        return (FloatingSelectionTool) view.canvas().tool();
    }

    private void copyGateAt(int x, int y) {
        Component gate = gateAt(x, y);
        view.canvas().selection().select(gate.id());
        perform(actions.copy());
    }

    @Test
    void cutCopyAndDuplicateFollowTheSelection() {
        Component gate = gateAt(0, 0);
        assertThat(List.of(actions.cut(), actions.copy(), actions.duplicate())).noneMatch(Action::isEnabled);
        assertThat(actions.paste().isEnabled()).isTrue();
        assertThat(actions.selectAll().isEnabled()).isTrue();
        view.canvas().selection().select(gate.id());
        assertThat(List.of(actions.cut(), actions.copy(), actions.duplicate())).allMatch(Action::isEnabled);
    }

    @Test
    void copyPutsTheSelectionOnTheClipboardWithoutChangingTheDocument() throws Exception {
        copyGateAt(10, 4);
        String json = clipboard.read().orElseThrow();
        CircuitFragment fragment = new FragmentFormat(openDocument.registry()).read(json);
        assertThat(fragment.components()).singleElement().satisfies(component -> {
            assertThat(component.typeId()).isEqualTo(StandardComponentTypes.NOT);
            assertThat(component.position()).isEqualTo(GridPoint.ORIGIN);
        });
        assertThat(systemClipboard.isDataFlavorAvailable(FragmentTransferable.FRAGMENT_FLAVOR)).isTrue();
        assertThat(systemClipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)).isTrue();
        assertThat(clipboard.originOf(json)).contains(new GridPoint(10, 4));
        assertThat(openDocument.document().components()).hasSize(1);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void cutRemovesTheSelectionAsOneUndoStep() {
        Component first = gateAt(0, 0);
        Component second = gateAt(10, 0);
        view.canvas().selection().set(List.of(first.id(), second.id()));
        perform(actions.cut());
        assertThat(openDocument.document().isEmpty()).isTrue();
        assertThat(clipboard.read()).isPresent();
        assertThat(openDocument.history().undoDescription()).contains("command.remove");
        openDocument.history().undo();
        assertThat(openDocument.document().components()).hasSize(2);
    }

    @Test
    void copyAndCutWithoutASelectionDoNothing() {
        gateAt(0, 0);
        systemClipboard.setContents(new StringSelection("before"), null);
        perform(actions.copy());
        perform(actions.cut());
        assertThat(clipboard.read()).contains("before");
        assertThat(openDocument.document().components()).hasSize(1);
        assertThat(openDocument.history().undoDescription()).contains("command.add");
    }

    @Test
    void pasteWithTheMouseOutsideFloatsTheFragmentNextToTheOriginal() {
        copyGateAt(10, 4);
        perform(actions.paste());
        assertThat(floating().position()).isEqualTo(new GridPoint(12, 6));
        assertThat(floating().fragment().components()).hasSize(1);
        assertThat(openDocument.document().components()).hasSize(1);
    }

    @Test
    void pasteWithTheMouseOverTheCanvasFloatsTheFragmentThere() {
        copyGateAt(10, 4);
        moveMouseTo(80, 48);
        perform(actions.paste());
        assertThat(floating().position()).isEqualTo(new GridPoint(10, 6));
    }

    @Test
    void enterFixesThePastedFragmentAsOneUndoStep() {
        copyGateAt(10, 4);
        perform(actions.paste());
        view.canvas().tool().keyPressed(view.canvas(), KeyEvent.VK_ENTER, false);
        assertThat(openDocument.document().components()).hasSize(2);
        assertThat(openDocument.history().undoDescription()).contains("command.paste");
        assertThat(view.canvas().tool()).isSameAs(view.canvas().defaultTool());
    }

    @Test
    void pastingAgainFixesThePreviousFloatingFragment() {
        copyGateAt(10, 4);
        perform(actions.paste());
        moveMouseTo(160, 160);
        perform(actions.paste());
        assertThat(openDocument.document().components()).hasSize(2);
        assertThat(openDocument.history().undoDescription()).contains("command.paste");
        assertThat(floating().position()).isEqualTo(new GridPoint(20, 20));
    }

    @Test
    void pasteOfAFragmentCopiedElsewhereFloatsItAtTheCenterOfTheView() {
        Component gate = gateAt(10, 4);
        String json = new FragmentFormat(openDocument.registry())
                .write(CircuitFragment.of(openDocument.document(), List.of(gate.id()), openDocument.registry()));
        systemClipboard.setContents(new StringSelection(json), null);
        moveMouseOut();
        perform(actions.paste());
        assertThat(floating().position()).isEqualTo(view.canvas().viewport().snap(200, 150));
    }

    @Test
    void pastingSomethingThatIsNotAFragmentDoesNothing() {
        gateAt(0, 0);
        perform(actions.paste());
        assertThat(view.canvas().tool()).isSameAs(view.canvas().defaultTool());
        for (String text : List.of("hola", "{\"formatVersion\":2}", "", "{\"formatVersion\":2,\"kind\":\"fragment\"")) {
            systemClipboard.setContents(new StringSelection(text), null);
            perform(actions.paste());
            assertThat(view.canvas().tool()).as("pasting %s", text).isSameAs(view.canvas().defaultTool());
        }
        assertThat(openDocument.document().components()).hasSize(1);
    }

    @Test
    void duplicateFloatsACopyOffsetFromTheOriginalAndLeavesTheClipboardAlone() {
        Component gate = gateAt(10, 4);
        systemClipboard.setContents(new StringSelection("sentinel"), null);
        view.canvas().selection().select(gate.id());
        moveMouseTo(80, 48);
        perform(actions.duplicate());
        assertThat(floating().position()).isEqualTo(new GridPoint(12, 6));
        assertThat(floating().fragment().components()).hasSize(1);
        assertThat(clipboard.read()).contains("sentinel");
        assertThat(openDocument.document().components()).hasSize(1);
    }

    @Test
    void selectAllSelectsEveryElement() {
        Component first = gateAt(0, 0);
        Component second = gateAt(10, 0);
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 8), new GridPoint(6, 8));
        openDocument.execute(new AddWireCommand(wire));
        perform(actions.selectAll());
        assertThat(view.canvas().selection().ids()).containsExactlyInAnyOrder(first.id(), second.id(), wire.id());
    }

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (java.awt.Component child : menu.getComponents()) {
            texts.add(child instanceof JMenuItem item ? item.getText() : "---");
        }
        return texts;
    }

    private JPopupMenu contribute(JPopupMenu menu, Optional<DocumentElement> target) {
        return contribute(menu, target, new Point2D.Double());
    }

    private JPopupMenu contribute(JPopupMenu menu, Optional<DocumentElement> target, Point2D gridPosition) {
        actions.contributor().contribute(menu, target, view.canvas().selection(), gridPosition);
        return menu;
    }

    @Test
    void emptyCanvasMenuPastesWhereItWasOpened() {
        copyGateAt(10, 4);
        view.canvas().selection().clear();
        moveMouseOut();
        JPopupMenu menu = contribute(new JPopupMenu(), Optional.empty(), new Point2D.Double(9.8, 6.2));
        assertThat(texts(menu)).containsExactly("Pegar", "Seleccionar todo");
        perform(((JMenuItem) menu.getComponent(0)).getAction());
        assertThat(floating().position()).isEqualTo(new GridPoint(10, 6));
    }

    @Test
    void componentMenuOffersCutCopyAndDuplicate() {
        Component gate = gateAt(0, 0);
        view.canvas().selection().select(gate.id());
        assertThat(texts(contribute(new JPopupMenu(), Optional.of(gate))))
                .containsExactly("Cortar", "Copiar", "Duplicar");
    }

    @Test
    void aLoneWireGetsNoClipboardItems() {
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 8), new GridPoint(6, 8));
        openDocument.execute(new AddWireCommand(wire));
        view.canvas().selection().select(wire.id());
        assertThat(texts(contribute(new JPopupMenu(), Optional.of(wire)))).isEmpty();
    }

    @Test
    void multiSelectionMenuOffersEveryGroupOperationOnce() {
        Component gate = gateAt(0, 0);
        Wire wire = new Wire(openDocument.document().newWireId(), new GridPoint(0, 8), new GridPoint(6, 8));
        openDocument.execute(new AddWireCommand(wire));
        view.canvas().selection().set(List.of(gate.id(), wire.id()));
        assertThat(texts(contribute(new JPopupMenu(), Optional.of(wire))))
                .containsExactly("Cortar", "Copiar", "Duplicar", "Borrar", "Rotar ↻", "Rotar ↺");
        JPopupMenu withM2Entries = new JPopupMenu();
        withM2Entries.add(new JMenuItem(view.actions().rotateClockwise()));
        withM2Entries.add(new JMenuItem(view.actions().rotateCounterClockwise()));
        withM2Entries.add(new JMenuItem(view.actions().delete()));
        assertThat(texts(contribute(withM2Entries, Optional.of(gate))))
                .containsExactly("Rotar ↻", "Rotar ↺", "Borrar", "Cortar", "Copiar", "Duplicar");
    }
}
