package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.ExampleDefinitions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.favorites.PaletteFavorites;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoritesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> chosen = new ArrayList<>();
    private final List<PaletteFavorites> owners = new ArrayList<>();
    private PalettePanel palette;

    @BeforeEach
    void createPalette() {
        Messages.initialize(Messages.SPANISH);
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
        preferences.setPaletteFavorites(List.of("led", "and2", "led"));

        assertThat(preferences.paletteFavorites()).containsExactly("led", "and2");
        assertThat(palette.visibleSections()).startsWith("Favoritos");
        assertThat(palette.visibleTypeIds()).startsWith("led", "and2");
        assertThat(palette.visibleTypeIds().stream().filter("led"::equals)).hasSize(2);
        assertThat(second.visibleTypeIds()).startsWith("led", "and2");
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
        palette.choose("and3");
        Object selected = palette.tree().getSelectionPath().getLastPathComponent();
        preferences.setPaletteFavorites(List.of("led"));
        assertThat(palette.tree().getSelectionPath().getLastPathComponent().toString())
                .isEqualTo(selected.toString());
        assertThat(chosen).containsExactly("and3");
    }

    @Test
    void filteringHidesEmptyFavoritesAndTheHintCannotBeChosen() {
        palette.tree().setSelectionRow(1);
        assertThat(palette.hasSelection()).isFalse();
        palette.setFilter("and2");
        assertThat(palette.visibleSections()).doesNotContain("Favoritos");
        preferences.setPaletteFavorites(List.of("led"));
        assertThat(palette.visibleSections()).doesNotContain("Favoritos");
        palette.setFilter("led");
        assertThat(palette.visibleSections()).startsWith("Favoritos");
        assertThat(palette.visibleTypeIds()).containsExactly("led", "led");
    }

    @Test
    void theArrowKeysWalkPastTheEmptyHintBothWays() {
        JTree tree = palette.tree();
        tree.setSelectionRow(0);

        press(tree, "selectNext");
        assertThat(tree.getSelectionRows()).containsExactly(2);
        assertThat(tree.getPathForRow(2).getLastPathComponent().toString()).isEqualTo("BASIC");
        press(tree, "selectPrevious");
        assertThat(tree.getSelectionRows()).containsExactly(0);
        assertThat(chosen).isEmpty();
    }

    @Test
    void aClickOnTheEmptyHintKeepsWhatIsBeingPlacedHighlighted() {
        palette.choose("and3");
        TreePath placing = palette.tree().getSelectionPath();

        palette.tree().setSelectionRow(1);

        assertThat(palette.tree().getSelectionPath()).isEqualTo(placing);
        assertThat(chosen).containsExactly("and3");
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
        javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) palette.getComponent(1);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        tree.setSize(300, 2000);
        Rectangle entry = tree.getPathBounds(palette.pathOf("led"));
        int starX = tree.getVisibleRect().x + tree.getVisibleRect().width - 12;
        List<Integer> drags = new ArrayList<>();
        tree.setTransferHandler(new javax.swing.TransferHandler() {
            @Override
            public void exportAsDrag(javax.swing.JComponent source, java.awt.event.InputEvent event, int action) {
                drags.add(action);
            }
        });
        for (int eventId : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED,
                MouseEvent.MOUSE_CLICKED)) {
            tree.dispatchEvent(new MouseEvent(tree, eventId, 0, 0, starX, entry.y + entry.height / 2,
                    1, false, MouseEvent.BUTTON1));
            if (eventId == MouseEvent.MOUSE_PRESSED) {
                tree.dispatchEvent(new MouseEvent(tree, MouseEvent.MOUSE_DRAGGED, 0,
                        java.awt.event.InputEvent.BUTTON1_DOWN_MASK, starX - 30, entry.y + 20,
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
