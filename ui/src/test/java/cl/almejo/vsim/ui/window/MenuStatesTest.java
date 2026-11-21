package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.clipboard.FragmentClipboard;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static org.assertj.core.api.Assertions.assertThat;

/** Items that do nothing now are off, re-checked when their menu opens, and say why (audit I14). */
class MenuStatesTest {

    private OpenDocument openDocument;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
    }

    @AfterEach
    void close() {
        openDocument.close();
    }

    private static void open(JMenu menu) {
        for (MenuListener listener : menu.getMenuListeners()) {
            listener.menuSelected(new MenuEvent(menu));
        }
    }

    @Test
    void openingEditarChecksAgainWhetherTheClipboardHoldsSomethingToPaste() {
        EditorView view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, NO_WINDOW);
        try {
            view.selection().select(place(openDocument, StandardComponentTypes.BUFFER, 0, 0,
                    Map.of(StandardComponentTypes.NEGATE, true)).id());
            perform(view.selectionActions().copy());
            assertThat(view.selectionActions().paste().isEnabled()).isTrue();

            // something that is no circuit fragment reached the clipboard every view shares
            FragmentClipboard.system().put("hola", GridPoint.ORIGIN, null);
            open(view.menu(MenuId.EDIT));

            assertThat(view.selectionActions().paste().isEnabled()).isFalse();
        } finally {
            view.dispose();
        }
    }

    @Test
    void moveTheGroupIsOffWhileTheWindowHoldsASingleGroupAndSaysWhy() {
        boolean[] otherGroups = {false};
        WindowCommands window = new WindowCommands() {
            @Override public void newDocument() { }
            @Override public void open() { }
            @Override public void save() { }
            @Override public void saveAs() { }
            @Override public void closeWindow() { }
            @Override public void exit() { }
            @Override public void newView() { }
            @Override public boolean holdsOtherGroups() { return otherGroups[0]; }
        };
        EditorView view = new EditorView(openDocument, PainterRegistry.standard(), ColorScheme.LIGHT, window);
        try {
            open(view.menu(MenuId.WINDOW));
            assertThat(view.actions().moveGroup().isEnabled()).isFalse();
            assertThat(Actions.disabledReason(view.actions().moveGroup()))
                    .contains("Esta ventana tiene un solo circuito");

            otherGroups[0] = true;
            open(view.menu(MenuId.WINDOW));
            assertThat(view.actions().moveGroup().isEnabled()).isTrue();
        } finally {
            view.dispose();
        }
    }
}
