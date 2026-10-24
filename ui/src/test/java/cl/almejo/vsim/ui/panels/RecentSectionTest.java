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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentSectionTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<String> chosen = new ArrayList<>();
    private PalettePanel palette;

    @BeforeEach
    void createPalette() {
        Messages.initialize(Messages.SPANISH);
        palette = new PalettePanel(StandardComponentTypes.registry(), PainterRegistry.standard(), ColorScheme.LIGHT,
                type -> chosen.add(type.id()));
        new PaletteFavorites(palette, preferences).install();
    }

    @Test
    void withNothingUsedThereIsNoSection() {
        assertThat(palette.visibleSections()).doesNotContain("Recientes");
        assertThat(palette.recentEntries()).isEmpty();
    }

    @Test
    void theSectionComesRightBelowFavoritesInUseOrder() {
        palette.setRecents(List.of("and3", "led"));

        assertThat(palette.visibleSections()).startsWith("Favoritos", "Recientes", "Básicas");
        assertThat(palette.visibleTypeIds()).startsWith("and3", "led");
        assertThat(chosen).isEmpty();
    }

    @Test
    void aFavoriteIsNotRepeatedAndComesBackWhenUnmarked() {
        palette.setRecents(List.of("and3", "led"));
        preferences.setPaletteFavorites(List.of("led"));

        assertThat(palette.recentEntries()).extracting(entry -> entry.type().id()).containsExactly("and3");
        assertThat(palette.visibleTypeIds()).startsWith("led", "and3", "and2");

        preferences.setPaletteFavorites(List.of());

        assertThat(palette.recentEntries()).extracting(entry -> entry.type().id()).containsExactly("and3", "led");
    }

    @Test
    void whenEveryRecentIsAFavoriteTheSectionHides() {
        palette.setRecents(List.of("led"));
        preferences.setPaletteFavorites(List.of("led"));

        assertThat(palette.visibleSections()).doesNotContain("Recientes");
    }

    @Test
    void theSectionIsFilteredLikeTheOthers() {
        palette.setRecents(List.of("and3", "led"));

        palette.setFilter("led");
        assertThat(palette.visibleSections()).startsWith("Recientes");
        palette.setFilter("xor");
        assertThat(palette.visibleSections()).doesNotContain("Recientes");
    }

    @Test
    void withoutFavoritesTheSectionIsFirst() {
        PalettePanel plain = new PalettePanel(StandardComponentTypes.registry(), PainterRegistry.standard(),
                ColorScheme.LIGHT, type -> { });
        plain.setRecents(List.of("or2"));

        assertThat(plain.visibleSections()).startsWith("Recientes", "Básicas");
    }

    @Test
    void aRecentChangeKeepsTheModelAndWhatTheUserFolded() {
        Object model = palette.treeModel();
        int basic = rowOf("BASIC");
        palette.tree().collapseRow(basic);

        palette.setRecents(List.of("and3"));
        palette.setRecents(List.of("led", "and3"));

        assertThat(palette.treeModel()).isSameAs(model);
        assertThat(palette.tree().isCollapsed(rowOf("BASIC"))).isTrue();
        assertThat(palette.tree().isExpanded(rowOf("RECENT"))).isTrue();
    }

    @Test
    void anUnavailableUserComponentIsHiddenAndComesBack() {
        DefinitionBundle bundle = new DefinitionBundle(ExampleDefinitions.inverter(), List.of());
        palette.setRecents(List.of("subcircuit:inverter"));
        assertThat(palette.visibleSections()).doesNotContain("Recientes");

        palette.setUserComponents(List.of(bundle), List.of());

        assertThat(palette.recentEntries()).extracting(entry -> entry.type().id())
                .containsExactly("subcircuit:inverter");
    }

    private int rowOf(String section) {
        for (int row = 0; row < palette.tree().getRowCount(); row++) {
            if (palette.tree().getPathForRow(row).getLastPathComponent().toString().equals(section)) {
                return row;
            }
        }
        throw new AssertionError("no section " + section);
    }
}
