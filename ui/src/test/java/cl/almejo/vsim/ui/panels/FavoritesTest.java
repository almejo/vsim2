package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.favorites.PaletteFavorites;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FavoritesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> chosen = new ArrayList<>();
    private final List<PaletteFavorites> owners = new ArrayList<>();
    private PalettePanel palette;

    @BeforeEach
    void createPalette() {
        palette = createPalette(chosen);
    }

    private PalettePanel createPalette(List<String> choices) {
        PalettePanel result = new PalettePanel(StandardComponentTypes.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT, type -> choices.add(type.id()));
        PaletteFavorites favorites = new PaletteFavorites(result, preferences);
        favorites.install();
        owners.add(favorites);
        return result;
    }

    @Test
    void favoritesAreCopiedInMarkingOrderAndFollowOtherWindowsWithoutStartingPlacement() {
        PalettePanel second = createPalette(new ArrayList<>());
        preferences.setPaletteFavorites(List.of("led", "and", "led"));

        assertThat(preferences.paletteFavorites()).containsExactly("led", "and");
        assertThat(palette.visibleSections()).startsWith("Favoritos");
        assertThat(palette.visibleTypeIds()).startsWith("led", "and");
        assertThat(palette.visibleTypeIds().stream().filter("led"::equals)).hasSize(2);
        assertThat(second.visibleTypeIds()).startsWith("led", "and");
        assertThat(chosen).isEmpty();

        Object model = palette.treeModel();
        preferences.setStepUnits(20);
        assertThat(palette.treeModel()).isSameAs(model);
        owners.forEach(PaletteFavorites::dispose);
        assertThat(preferences.listenerCount()).isZero();
    }

    @Test
    void unavailableUserFavoritesStayStoredAndReturnWithTheDefinition() {
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        preferences.setPaletteFavorites(List.of("subcircuit:inverter"));
        palette.setUserComponents(List.of(bundle), List.of());
        assertThat(palette.visibleTypeIds()).startsWith("subcircuit:inverter");
        palette.setUserComponents(List.of(), List.of());
        assertThat(palette.visibleTypeIds()).doesNotContain("subcircuit:inverter");
        assertThat(preferences.paletteFavorites()).containsExactly("subcircuit:inverter");
        palette.setUserComponents(List.of(bundle), List.of());
        List<String> users = new ArrayList<>();
        palette.setUserComponentChooser(component -> users.add(component.bundle().root().id()));
        palette.choose("subcircuit:inverter");
        assertThat(users).containsExactly("inverter");
    }

    @Test
    void markingAnUnrelatedComponentKeepsTheCurrentPlacementHighlightWithoutChoosingAgain() {
        palette.choose("xor");
        Object selected = palette.tree().getSelectionPath().getLastPathComponent();
        preferences.setPaletteFavorites(List.of("led"));
        assertThat(palette.tree().getSelectionPath().getLastPathComponent().toString())
                .isEqualTo(selected.toString());
        assertThat(chosen).containsExactly("xor");
    }

    @Test
    void aFavoritesChangeUpdatesOnlyItsSectionAndKeepsWhatTheUserFolded() {
        Object model = palette.treeModel();
        palette.tree().collapseRow(rowOfSection("GATES"));

        preferences.setPaletteFavorites(List.of("led", "xor"));
        preferences.setPaletteFavorites(List.of("xor"));
        preferences.setPaletteFavorites(List.of());

        assertThat(palette.treeModel()).isSameAs(model);
        assertThat(palette.tree().isCollapsed(rowOfSection("GATES"))).isTrue();
        assertThat(palette.tree().isExpanded(rowOfSection("INPUT_OUTPUT"))).isTrue();
        assertThat(palette.visibleSections()).startsWith("Favoritos");
    }

    @Test
    void unmarkingTheFavoriteBeingPlacedMovesItsHighlightToItsOwnCategory() {
        preferences.setPaletteFavorites(List.of("led"));
        palette.tree().setSelectionPath(palette.pathOf("led"));
        assertThat(palette.tree().getSelectionPath().getPathComponent(1).toString()).isEqualTo("FAVORITES");

        preferences.setPaletteFavorites(List.of());

        TreePath highlighted = palette.tree().getSelectionPath();
        assertThat(highlighted).isNotNull();
        assertThat(highlighted.getPathComponent(1).toString()).isEqualTo("INPUT_OUTPUT");
        assertThat(highlighted.getLastPathComponent().toString()).contains("id=led");
        assertThat(chosen).containsExactly("led");
    }

    @Test
    void theHoverStarSurvivesAFavoritesChange() {
        JTree tree = palette.tree();
        tree.setSize(300, 2000);
        Rectangle entry = tree.getPathBounds(palette.pathOf("xor"));
        tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_MOVED, 0, 0, entry.x + 4,
                entry.y + entry.height / 2, 0, false, MouseEvent.NOBUTTON));
        assertThat(palette.hoveredPath()).hasValue(palette.pathOf("xor"));

        preferences.setPaletteFavorites(List.of("led"));

        assertThat(palette.hoveredPath()).isPresent();
        assertThat(palette.tree().getRowForPath(palette.hoveredPath().get())).as("a path of the tree shown now")
                .isNotNegative();
        assertThat(palette.hoveredPath().get().getLastPathComponent().toString()).contains("id=xor");
    }

    @Test
    void filteringHidesEmptyFavoritesAndTheHintCannotBeChosen() {
        palette.tree().setSelectionRow(1);
        assertThat(palette.hasSelection()).isFalse();
        palette.setFilter("and");
        assertThat(palette.visibleSections()).doesNotContain("Favoritos");
        preferences.setPaletteFavorites(List.of("clock"));
        assertThat(palette.visibleSections()).doesNotContain("Favoritos");
        palette.setFilter("clock");
        assertThat(palette.visibleSections()).startsWith("Favoritos");
        assertThat(palette.visibleTypeIds()).containsExactly("clock", "clock");
    }

    @Test
    void aMarkedRowSaysItIsAFavoriteToAScreenReaderAndAnUnmarkedOneOnlyItsName() {
        preferences.setPaletteFavorites(List.of("led"));

        assertThat(palette.accessibleNameOf("led")).hasValue("LED, favorito");
        assertThat(palette.accessibleNameOf("xor")).hasValue("XOR");
    }

    @Test
    void theArrowKeysWalkPastTheEmptyHintBothWays() {
        JTree tree = palette.tree();
        tree.setSelectionRow(0);

        press(tree, "selectNext");
        assertThat(tree.getSelectionRows()).containsExactly(2);
        assertThat(tree.getPathForRow(2).getLastPathComponent().toString()).isEqualTo("GATES");
        press(tree, "selectPrevious");
        assertThat(tree.getSelectionRows()).containsExactly(0);
        assertThat(chosen).isEmpty();
    }

    @Test
    void aClickOnTheEmptyHintKeepsWhatIsBeingPlacedHighlighted() {
        palette.choose("xor");
        TreePath placing = palette.tree().getSelectionPath();

        palette.tree().setSelectionRow(1);

        assertThat(palette.tree().getSelectionPath()).isEqualTo(placing);
        assertThat(chosen).containsExactly("xor");
    }

    private int rowOfSection(String section) {
        for (int row = 0; row < palette.tree().getRowCount(); row++) {
            if (palette.tree().getPathForRow(row).getLastPathComponent().toString().equals(section)) {
                return row;
            }
        }
        throw new AssertionError("no section " + section);
    }

    @Test
    void markingAUserComponentWithTheStarSavesItsKeyAndCopiesIt() {
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        palette.setUserComponents(List.of(bundle), List.of());
        List<String> users = new ArrayList<>();
        palette.setUserComponentChooser(component -> users.add(component.bundle().root().id()));
        layOut();

        clickStar(palette.pathOf("subcircuit:inverter"));

        assertThat(preferences.paletteFavorites()).containsExactly("subcircuit:inverter");
        assertThat(palette.visibleSections()).startsWith("Favoritos");
        assertThat(palette.visibleTypeIds().stream().filter("subcircuit:inverter"::equals)).hasSize(2);
        assertThat(users).isEmpty();
    }

    @Test
    void aPressOnTheNameGoesOnToTheTreeWhichSelectsItAndStartsPlacing() {
        layOut();
        JTree tree = palette.tree();
        // BasicTreeUI's own press handling asks the toolkit for the menu key, which a headless toolkit refuses: a
        // recorder takes its place and shows the press reaches the tree unconsumed (the press on the star does not).
        for (MouseListener listener : tree.getMouseListeners()) {
            if (listener.getClass().getName().contains("BasicTreeUI")) {
                tree.removeMouseListener(listener);
            }
        }
        List<MouseEvent> presses = new ArrayList<>();
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                presses.add(event);
            }
        });
        Rectangle entry = tree.getPathBounds(palette.pathOf("xor"));

        click(entry.x + 4, entry.y + entry.height / 2);
        clickStar(palette.pathOf("xor"));

        assertThat(presses).singleElement().satisfies(press -> assertThat(press.isConsumed()).isFalse());
        assertThat(preferences.paletteFavorites()).containsExactly("xor");
        tree.setSelectionPath(tree.getPathForLocation(entry.x + 4, entry.y + entry.height / 2));
        assertThat(chosen).containsExactly("xor");
    }

    @Test
    void theFilledStarInTheOwnCategoryShowsWithoutHoverAndUnmarks() {
        preferences.setPaletteFavorites(List.of("led"));
        layOut();
        TreePath own = pathInSection("INPUT_OUTPUT", "led");
        int ownRow = palette.tree().getRowForPath(own);

        assertThat(palette.hoveredPath()).isEmpty();
        assertThat(palette.starState(ownRow)).isEqualTo(PalettePanel.StarState.FILLED);
        assertThat(palette.starState(palette.tree().getRowForPath(palette.pathOf("led"))))
                .isEqualTo(PalettePanel.StarState.FILLED);
        assertThat(palette.starState(palette.tree().getRowForPath(palette.pathOf("xor"))))
                .isEqualTo(PalettePanel.StarState.NONE);

        clickStar(own);

        assertThat(preferences.paletteFavorites()).isEmpty();
        assertThat(chosen).isEmpty();
        assertThat(palette.starState(palette.tree().getRowForPath(pathInSection("INPUT_OUTPUT", "led"))))
                .isNotEqualTo(PalettePanel.StarState.FILLED);
    }

    @Test
    void theOutlineStarShowsOnlyUnderThePointer() {
        layOut();
        Rectangle entry = palette.tree().getPathBounds(palette.pathOf("xor"));
        int row = palette.tree().getRowForPath(palette.pathOf("xor"));
        assertThat(palette.starState(row)).isEqualTo(PalettePanel.StarState.NONE);

        palette.tree().dispatchEvent(new MouseEvent(palette.tree(), MouseEvent.MOUSE_MOVED, 0, 0, entry.x + 4,
                entry.y + entry.height / 2, 0, false, MouseEvent.NOBUTTON));

        assertThat(palette.starState(row)).isEqualTo(PalettePanel.StarState.OUTLINE);
    }

    @Test
    void aUserComponentsMenuFromTheLibraryEndsWithTheFavoriteItemAfterASeparator() {
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        palette.setUserComponents(List.of(bundle), List.of());
        JMenuItem importer = new JMenuItem("Importar componente…");
        palette.setUserMenuProvider(component -> {
            JPopupMenu menu = new JPopupMenu();
            component.ifPresent(present -> {
                menu.add(new JMenuItem("Renombrar…"));
                menu.addSeparator();
            });
            menu.add(importer);
            return Optional.of(menu);
        });

        JPopupMenu menu = palette.popupFor(palette.pathOf("subcircuit:inverter")).orElseThrow();

        int count = menu.getComponentCount();
        assertThat(menu.getComponent(count - 3)).isSameAs(importer);
        assertThat(menu.getComponent(count - 2)).isInstanceOf(JPopupMenu.Separator.class);
        JMenuItem favorite = (JMenuItem) menu.getComponent(count - 1);
        assertThat(favorite.getText()).isEqualTo("Agregar a favoritos");
        favorite.doClick();
        assertThat(preferences.paletteFavorites()).containsExactly("subcircuit:inverter");
        JMenuItem unmark = (JMenuItem) palette.popupFor(palette.pathOf("subcircuit:inverter")).orElseThrow()
                .getComponent(count - 1);
        assertThat(unmark.getText()).isEqualTo("Quitar de favoritos");
    }

    @Test
    void aStandardTypeGetsAMenuHoldingOnlyTheFavoriteItem() {
        JPopupMenu menu = palette.popupFor(palette.pathOf("xor")).orElseThrow();

        assertThat(menu.getComponentCount()).isEqualTo(1);
        assertThat(((JMenuItem) menu.getComponent(0)).getText()).isEqualTo("Agregar a favoritos");
    }

    /** Gives the palette a size, so the star column has a place at the visible right edge. */
    private void layOut() {
        palette.setSize(340, 2000);
        palette.doLayout();
        JScrollPane scroll = (JScrollPane) palette.getComponent(1);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        palette.tree().setSize(300, 2000);
    }

    private void clickStar(TreePath path) {
        Rectangle entry = palette.tree().getPathBounds(path);
        Rectangle visible = palette.tree().getVisibleRect();
        click(visible.x + visible.width - 12, entry.y + entry.height / 2);
    }

    private void click(int x, int y) {
        JTree tree = palette.tree();
        for (int eventId : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            tree.dispatchEvent(new MouseEvent(tree, eventId, 0, eventId == MouseEvent.MOUSE_PRESSED
                    ? InputEvent.BUTTON1_DOWN_MASK : 0, x, y, 1, false, MouseEvent.BUTTON1));
        }
    }

    private TreePath pathInSection(String section, String typeId) {
        TreePath sectionPath = palette.tree().getPathForRow(rowOfSection(section));
        for (int row = 0; row < palette.tree().getRowCount(); row++) {
            TreePath path = palette.tree().getPathForRow(row);
            if (path.getParentPath() != null && path.getParentPath().equals(sectionPath)
                    && path.getLastPathComponent().toString().contains("id=" + typeId + ",")) {
                return path;
            }
        }
        throw new AssertionError(typeId + " not in " + section);
    }

    private static void press(JTree tree, String actionName) {
        tree.getActionMap().get(actionName).actionPerformed(new ActionEvent(tree, ActionEvent.ACTION_PERFORMED,
                actionName));
    }

    @Test
    void clickingTheStarConsumesTheWholeGestureAndTheContextMenuCanUnmark() {
        JTree tree = palette.tree();
        palette.setSize(340, 2000);
        palette.doLayout();
        JScrollPane scroll = (JScrollPane) palette.getComponent(1);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        tree.setSize(300, 2000);
        Rectangle entry = tree.getPathBounds(palette.pathOf("led"));
        int starX = tree.getVisibleRect().x + tree.getVisibleRect().width - 12;
        List<Integer> drags = new ArrayList<>();
        tree.setTransferHandler(new TransferHandler() {
            @Override
            public void exportAsDrag(JComponent source, InputEvent event, int action) {
                drags.add(action);
            }
        });
        for (int eventId : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED)) {
            tree.dispatchEvent(new MouseEvent(tree, eventId, 0, 0, starX, entry.y + entry.height / 2,
                    1, false, MouseEvent.BUTTON1));
            if (eventId == MouseEvent.MOUSE_PRESSED) {
                tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_DRAGGED, 0,
                        InputEvent.BUTTON1_DOWN_MASK, starX - 30, entry.y + 20,
                        0, false, MouseEvent.NOBUTTON));
            }
        }
        assertThat(preferences.paletteFavorites()).containsExactly("led");
        assertThat(chosen).isEmpty();
        assertThat(palette.hasSelection()).isFalse();
        assertThat(drags).isEmpty();

        JMenuItem unmark = (JMenuItem) palette.popupFor(palette.pathOf("led")).orElseThrow().getComponent(0);
        assertThat(unmark.getText()).isEqualTo("Quitar de favoritos");
        unmark.doClick();
        assertThat(preferences.paletteFavorites()).isEmpty();
    }
}
