package cl.almejo.vsim.ui.wiring;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.export.ExportIcons;
import cl.almejo.vsim.ui.export.ExportImageExtension;
import cl.almejo.vsim.ui.icons.ActionIcons;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;

/** The icons of «Exportar imagen…» and «Ordenar cables» and the toolbar button of the latter (M6.2). */
class TidyToolbarTest {

    private OpenDocument openDocument;
    private EditorView view;

    @BeforeEach
    void create() {
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = EditorServices.of(PainterRegistry.standard(), ColorScheme.LIGHT)
                .withExtensions(List.of(new ExportImageExtension(), new TidyWiresExtension(), new ActionIcons(),
                        new ShortcutsExtension()));
        view = new EditorView(openDocument, services, NO_WINDOW);
    }

    @AfterEach
    void close() {
        view.dispose();
        openDocument.close();
    }

    private List<AbstractButton> buttons() {
        List<AbstractButton> buttons = new ArrayList<>();
        for (Component child : view.toolBar().getComponents()) {
            if (child instanceof AbstractButton button) {
                buttons.add(button);
            }
        }
        return buttons;
    }

    private static JMenuItem item(javax.swing.JMenu menu, String text) {
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenuItem item && text.equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no menu item " + text);
    }

    @Test
    void bothActionsGetTheirIconsInTheMenuAndTheToolbarSizes() {
        Action export = item(view.menu(MenuId.FILE), "Exportar imagen…").getAction();
        Action tidy = item(view.menu(MenuId.EDIT), "Ordenar cables").getAction();
        assertThat(export.getValue(Action.SMALL_ICON)).isSameAs(Icons.get(ExportIcons.EXPORT_IMAGE, Icons.MENU_SIZE));
        assertThat(export.getValue(Action.LARGE_ICON_KEY))
                .isSameAs(Icons.get(ExportIcons.EXPORT_IMAGE, Icons.TOOLBAR_SIZE));
        assertThat(tidy.getValue(Action.SMALL_ICON)).isSameAs(Icons.get(TidyIcons.TIDY_WIRES, Icons.MENU_SIZE));
        assertThat(tidy.getValue(Action.LARGE_ICON_KEY)).isSameAs(Icons.get(TidyIcons.TIDY_WIRES, Icons.TOOLBAR_SIZE));
    }

    @Test
    void theToolbarHasTheTidyButtonRightAfterRedoAndNoneForExport() {
        List<Component> components = List.of(view.toolBar().getComponents());
        int redo = -1;
        for (int index = 0; index < components.size(); index++) {
            if (components.get(index) instanceof AbstractButton button && button.getAction() == view.actions().redo()) {
                redo = index;
            }
        }
        assertThat(redo).isPositive();
        AbstractButton tidy = (AbstractButton) components.get(redo + 1);
        assertThat(tidy.getAction().getValue(Action.NAME)).isEqualTo("Ordenar cables");
        assertThat(tidy.getToolTipText()).isEqualTo("Ordenar cables (Ctrl+Alt+L)");
        assertThat(tidy.getHideActionText()).isTrue();
        assertThat(buttons()).noneMatch(button -> "Exportar imagen…".equals(button.getAction().getValue(Action.NAME)));
    }

    @Test
    void disposingTheViewTakesTheButtonBack() {
        EditorView second = new EditorView(openDocument, EditorServices.of(PainterRegistry.standard(),
                ColorScheme.LIGHT).withExtensions(List.of(new TidyWiresExtension())), NO_WINDOW);
        int withButton = second.toolBar().getComponentCount();
        second.dispose();
        assertThat(second.toolBar().getComponentCount()).isEqualTo(withButton - 1);
        assertThat(buttons()).anyMatch(button -> "Ordenar cables".equals(button.getAction().getValue(Action.NAME)));
    }
}
