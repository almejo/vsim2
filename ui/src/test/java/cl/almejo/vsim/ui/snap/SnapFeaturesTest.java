package cl.almejo.vsim.ui.snap;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.Guide;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.grid.GridFeatures;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.tools.Tool;
import cl.almejo.vsim.ui.tools.ToolContext;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static cl.almejo.vsim.ui.UiTestSupport.place;
import static cl.almejo.vsim.ui.UiTestSupport.texts;
import static org.assertj.core.api.Assertions.assertThat;

class SnapFeaturesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private OpenDocument openDocument;
    private EditorView view;
    private EditorView otherView;
    private GridFeatures grid;
    private SnapFeatures features;
    private SnapFeatures otherFeatures;

    @BeforeEach
    void create() {
        Messages.initialize(Messages.SPANISH);
        openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of());
        view = new EditorView(openDocument, services, NO_WINDOW);
        otherView = new EditorView(openDocument, services, NO_WINDOW);
        grid = new GridFeatures(view);
        features = new SnapFeatures(view);
        otherFeatures = new SnapFeatures(otherView);
    }

    @AfterEach
    void close() {
        features.dispose();
        otherFeatures.dispose();
        grid.dispose();
        view.dispose();
        otherView.dispose();
        openDocument.close();
    }

    private static JMenu snapToMenu(EditorView target) {
        for (java.awt.Component child : target.menu(MenuId.VIEW).getMenuComponents()) {
            if (child instanceof JMenu menu && "Ajustar a".equals(menu.getText())) {
                return menu;
            }
        }
        throw new AssertionError("no «Ajustar a» submenu");
    }

    private static List<AbstractButton> snapButtons(EditorView target, Action toggle) {
        List<AbstractButton> found = new ArrayList<>();
        for (java.awt.Component child : target.toolBar().getComponents()) {
            if (child instanceof AbstractButton button && button.getAction() == toggle) {
                found.add(button);
            }
        }
        return found;
    }

    @Test
    void theItemsSitRightAfterTheGridSubmenuWithTheDefaults() {
        List<String> viewTexts = texts(view.menu(MenuId.VIEW));
        int gridIndex = viewTexts.indexOf("Cuadrícula");
        assertThat(viewTexts.subList(gridIndex, gridIndex + 3)).containsExactly("Cuadrícula", "Ajustar",
                "Ajustar a");
        JMenuItem toggleItem = view.menu(MenuId.VIEW).getItem(gridIndex + 1);
        assertThat(toggleItem).isInstanceOf(JCheckBoxMenuItem.class);
        assertThat(toggleItem.isSelected()).isTrue();
        JMenu snapTo = snapToMenu(view);
        assertThat(texts(snapTo)).containsExactly("Piezas", "Guías", "Líneas de la cuadrícula");
        assertThat(List.of(snapTo.getItem(0).isSelected(), snapTo.getItem(1).isSelected(),
                snapTo.getItem(2).isSelected())).containsExactly(true, true, false);
    }

    @Test
    void theToolbarMagnetComesRightAfterTheToolsPressedWithItsIconAndTooltip() {
        List<java.awt.Component> components = List.of(view.toolBar().getComponents());
        AbstractButton button = snapButtons(view, features.toggleAction()).getFirst();
        int index = components.indexOf(button);
        assertThat(((AbstractButton) components.get(index - 1)).getAction()).isSameAs(view.actions().zoomTool());
        assertThat(button.isSelected()).isTrue();
        assertThat(button.getToolTipText()).isEqualTo("Ajustar a piezas y guías al mover");
        assertThat(features.toggleAction().getValue(Action.LARGE_ICON_KEY))
                .isSameAs(Icons.get(SnapIcons.SNAP, Icons.TOOLBAR_SIZE));
        assertThat(features.toggleAction().getValue(Action.NAME)).isEqualTo("Ajustar");
    }

    @Test
    void theTooltipNamesWhatIsChosenOrSaysNothingIs() {
        preferences.setSnapToGridLines(true);
        assertThat(features.toggleAction().getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo("Ajustar a piezas, guías y líneas de la cuadrícula al mover");
        preferences.setSnapToParts(false);
        preferences.setSnapToGuides(false);
        assertThat(features.toggleAction().getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo("Ajustar a líneas de la cuadrícula al mover");
        preferences.setSnapToGridLines(false);
        assertThat(features.toggleAction().getValue(Action.SHORT_DESCRIPTION)).isEqualTo("Ajustar: nada elegido");
    }

    @Test
    void clickingInOneViewChangesThePreferenceAndTheOtherViewFollows() {
        snapButtons(view, features.toggleAction()).getFirst().doClick();
        assertThat(preferences.snapToAlignments()).isFalse();
        assertThat(snapButtons(otherView, otherFeatures.toggleAction()).getFirst().isSelected()).isFalse();
        snapToMenu(otherView).getItem(2).doClick();
        assertThat(preferences.snapToGridLines()).isTrue();
        assertThat(snapToMenu(view).getItem(2).isSelected()).isTrue();
        // the submenu stays editable while «Ajustar» is off
        assertThat(snapToMenu(view).getItem(0).isEnabled()).isTrue();
    }

    @Test
    void theCanvasSnapsWithTheDefaultSnapperAndTheShortcutsAreRegistered() {
        assertThat(view.canvas().snapper()).isSameAs(features.snapper());
        assertThat(view.shortcutActions()).containsEntry("view.snap", features.toggleAction())
                .containsEntry("view.snapToParts", features.partsAction())
                .containsEntry("view.snapToGuides", features.guidesAction())
                .containsEntry("view.snapToGridLines", features.gridLinesAction());
    }

    /** A tool that counts the snap refreshes it is asked for. */
    private static final class CountingTool implements Tool {
        int refreshes;

        @Override
        public boolean refreshSnap(ToolContext context) {
            refreshes++;
            return true;
        }
    }

    @Test
    void aSnapPreferenceTheViewportOrTheGuidesChangingRefreshesTheActiveTool() {
        CountingTool tool = new CountingTool();
        view.canvas().setTool(tool);
        preferences.setSnapToAlignments(false);
        assertThat(tool.refreshes).isEqualTo(1);
        preferences.setSnapToGridLines(true);
        preferences.setShowGuides(false);
        assertThat(tool.refreshes).isEqualTo(3);
        preferences.setWiresFollow(false); // not a snap preference
        assertThat(tool.refreshes).isEqualTo(3);
        view.canvas().viewport().zoomAt(0, 0, 2);
        assertThat(tool.refreshes).isEqualTo(4);
        openDocument.document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 5)));
        assertThat(tool.refreshes).isEqualTo(5);
        preferences.setGridLineSpacing(10); // the grid lines now pull every 10 units
        assertThat(tool.refreshes).isEqualTo(6);
    }

    @Test
    void aDocumentChangeRefreshesTheActiveToolOnceTheChangeIsOver() throws Exception {
        CountingTool tool = new CountingTool();
        view.canvas().setTool(tool);
        SwingUtilities.invokeAndWait(() -> { });
        int before = tool.refreshes;
        int[] rightAfterTheChange = new int[1];

        SwingUtilities.invokeAndWait(() -> {
            place(openDocument, StandardComponentTypes.NOT, 10, 10, Map.of());
            rightAfterTheChange[0] = tool.refreshes;
        });
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(rightAfterTheChange[0]).isEqualTo(before); // not inside the change
        assertThat(tool.refreshes).isEqualTo(before + 1);
    }

    @Test
    void disposeTakesBackEverythingAndLeavesTheOtherViewAlone() {
        CountingTool tool = new CountingTool();
        view.canvas().setTool(tool);
        features.dispose();
        features.dispose();

        assertThat(texts(view.menu(MenuId.VIEW))).doesNotContain("Ajustar", "Ajustar a").contains("Cuadrícula");
        assertThat(snapButtons(view, features.toggleAction())).isEmpty();
        assertThat(view.shortcutActions()).doesNotContainKeys("view.snap", "view.snapToParts", "view.snapToGuides",
                "view.snapToGridLines");
        assertThat(view.canvas().snapper()).isNotSameAs(features.snapper());
        preferences.setSnapToAlignments(false);
        view.canvas().viewport().zoomAt(0, 0, 2);
        openDocument.document().setGuides(List.of(new Guide(Guide.Orientation.VERTICAL, 5)));
        assertThat(tool.refreshes).isZero();
        assertThat(snapButtons(otherView, otherFeatures.toggleAction()).getFirst().isSelected()).isFalse();
    }
}
