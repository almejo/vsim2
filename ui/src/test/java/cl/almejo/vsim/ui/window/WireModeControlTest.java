package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.toolbar.SplitButton;
import cl.almejo.vsim.ui.tools.PanTool;
import cl.almejo.vsim.ui.tools.WireMode;
import cl.almejo.vsim.ui.tools.WireTool;
import cl.almejo.vsim.core.library.ComponentLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.perform;
import static org.assertj.core.api.Assertions.assertThat;

/** The Cable's split button and Editar ▸ «Cable continuo»: one mode for the whole app, kept in the preferences. */
class WireModeControlTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument firstDocument;
    private OpenDocument secondDocument;
    private EditorView first;
    private EditorView second;

    @BeforeEach
    void twoViewsSharingThePreferences() {
        Messages.initialize(Messages.SPANISH);
        firstDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        secondDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        first = viewOn(firstDocument);
        second = viewOn(secondDocument);
    }

    @AfterEach
    void close() {
        first.dispose();
        second.dispose();
        firstDocument.close();
        secondDocument.close();
    }

    private EditorView viewOn(OpenDocument document) {
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        return new EditorView(document, services, NO_WINDOW);
    }

    private static SplitButton cable(EditorView view) {
        List<SplitButton> found = new ArrayList<>();
        collect(view.toolBar(), found);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    private static void collect(Component component, List<SplitButton> found) {
        if (component instanceof SplitButton button) {
            found.add(button);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, found);
            }
        }
    }

    private static JCheckBoxMenuItem continuousItem(EditorView view) {
        JMenu edit = view.menu(MenuId.EDIT);
        for (int index = 0; index < edit.getItemCount(); index++) {
            JMenuItem item = edit.getItem(index);
            if (item instanceof JCheckBoxMenuItem check && Messages.text("action.wireContinuous").equals(check.getText())) {
                return check;
            }
        }
        throw new AssertionError("no «Cable continuo» item in Editar");
    }

    @Test
    void theCableIsContinuousByDefaultAndTheItemIsChecked() {
        assertThat(cable(first).mode().id()).isEqualTo(WireMode.CONTINUOUS.id());
        assertThat(continuousItem(first).isSelected()).isTrue();
        assertThat(cable(first).mainButton().getToolTipText()).isEqualTo(Messages.text("wire.mode.continuous.description"));
    }

    @Test
    void theButtonsMainPartActivatesTheWireToolAndShowsItPressed() {
        cable(first).mainButton().doClick();
        assertThat(first.canvas().tool()).isInstanceOf(WireTool.class);
        assertThat(cable(first).mainButton().isSelected()).isTrue();
        perform(first.actions().panTool());
        assertThat(cable(first).mainButton().isSelected()).isFalse();
        perform(first.actions().wireTool());
        assertThat(cable(first).mainButton().isSelected()).isTrue();
    }

    @Test
    void theItemTogglesTheModeWithoutChangingTheActiveToolAndTheButtonFollows() {
        perform(first.actions().panTool());
        continuousItem(first).doClick();
        assertThat(first.canvas().tool()).isInstanceOf(PanTool.class);
        assertThat(cable(first).mode().id()).isEqualTo(WireMode.PIECES.id());
        assertThat(preferences.splitButtonMode(WireMode.PREFERENCE_ID)).contains(WireMode.PIECES.id());
        assertThat(continuousItem(first).isSelected()).isFalse();
        assertThat(cable(first).mainButton().getToolTipText()).isEqualTo(Messages.text("wire.mode.pieces.description"));
        continuousItem(first).doClick();
        assertThat(cable(first).mode().id()).isEqualTo(WireMode.CONTINUOUS.id());
        assertThat(continuousItem(first).isSelected()).isTrue();
    }

    @Test
    void theButtonsModeChangeChecksTheItem() {
        cable(first).setMode(WireMode.PIECES.id());
        assertThat(continuousItem(first).isSelected()).isFalse();
        cable(first).setMode(WireMode.CONTINUOUS.id());
        assertThat(continuousItem(first).isSelected()).isTrue();
    }

    @Test
    void aModeChosenInOneWindowShowsInTheOthersButtonAndItem() {
        continuousItem(first).doClick();
        assertThat(cable(second).mode().id()).isEqualTo(WireMode.PIECES.id());
        assertThat(continuousItem(second).isSelected()).isFalse();
        cable(second).setMode(WireMode.CONTINUOUS.id());
        assertThat(cable(first).mode().id()).isEqualTo(WireMode.CONTINUOUS.id());
        assertThat(continuousItem(first).isSelected()).isTrue();
    }

    @Test
    void theItemHasNoShortcutAndIsInTheShortcutCatalog() {
        assertThat(ShortcutCatalog.find("edit.wireContinuous").orElseThrow().defaultStroke()).isEmpty();
        assertThat(first.shortcutActions()).containsKey("edit.wireContinuous");
        assertThat(first.shortcutActions().get("edit.wireContinuous").getValue(Action.ACCELERATOR_KEY)).isNull();
    }

    @Test
    void disposingAViewTakesItsListenerBack() {
        int before = preferences.listenerCount();
        second.dispose();
        assertThat(preferences.listenerCount()).isLessThan(before);
        second = viewOn(secondDocument); // so that close() has something to dispose
    }

    @Test
    void theToolbarKeepsTheCableBetweenSelectionAndPan() {
        List<Component> components = List.of(first.toolBar().getComponents());
        int cableIndex = components.indexOf(cable(first));
        assertThat(cableIndex).isPositive();
        assertThat(cable(first).getParent()).isSameAs(first.toolBar());
    }

    @Test
    void choosingAModeOrPressingTheButtonWhileAPathIsInProgressKeepsThePath() {
        perform(first.actions().wireTool());
        first.canvas().setSize(400, 300);
        cl.almejo.vsim.ui.CanvasClicks.click(first.canvas(), 40, 40);
        WireTool before = (WireTool) first.canvas().tool();
        assertThat(before.holdsGesture()).isTrue();

        cable(first).mainButton().doClick();
        cable(first).setMode(WireMode.PIECES.id());
        cable(first).setMode(WireMode.CONTINUOUS.id());
        continuousItem(first).doClick();

        assertThat(first.canvas().tool()).isSameAs(before);
        assertThat(before.holdsGesture()).isTrue();
    }
}
