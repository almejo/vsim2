package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.favorites.PaletteFavorites;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** NAND, NOR, XNOR and NOT as palette shortcuts of AND, OR, XOR and Buffer with «Negar salida» on. */
class PalettePresetsTest {

    private static final List<String> GATES = List.of("and", "or", "xor", "nand", "nor", "xnor", "buffer", "not");

    private final ComponentTypeRegistry registry = StandardComponentTypes.registry();
    private final List<PaletteEntry> chosen = new ArrayList<>();
    private PalettePanel palette;

    @BeforeEach
    void create() {
        palette = new PalettePanel(registry, PainterRegistry.standard(), ColorScheme.LIGHT, chosen::add);
    }

    @Test
    void theGatesComeFirstInTheirOrderWithOneEntryPerGate() {
        assertThat(palette.visibleTypeIds().subList(0, GATES.size())).containsExactlyElementsOf(GATES);
        assertThat(palette.entries().stream().map(PaletteEntry::id).toList()).containsSubsequence(GATES);
    }

    @Test
    void aShortcutRowShowsTheGateNameAndAnIconOfItsOwn() {
        assertThat(palette.accessibleNameOf("nand")).contains("NAND");
        assertThat(palette.accessibleNameOf("not")).contains("NOT");
        assertThat(palette.accessibleNameOf("buffer")).contains("Buffer");
        PaletteEntry nand = palette.entries().stream().filter(entry -> entry.id().equals("nand")).findFirst()
                .orElseThrow();
        assertThat(palette.icon(nand)).isNotSameAs(palette.icon(registry.require(StandardComponentTypes.AND)));
    }

    @Test
    void theControlledInverterSitsRightAfterTheTriStateBufferAndIsNamedInversorControlado() {
        List<String> ids = palette.visibleTypeIds();
        assertThat(ids.indexOf("inverter-controlled")).isEqualTo(ids.indexOf(StandardComponentTypes.TRI_STATE) + 1);
        assertThat(palette.accessibleNameOf("inverter-controlled")).contains("Inversor controlado");

        palette.choose("inverter-controlled");

        assertThat(chosen).singleElement().satisfies(entry -> {
            assertThat(entry.type().id()).isEqualTo(StandardComponentTypes.TRI_STATE);
            assertThat(entry.parameters()).isEqualTo(Map.of(StandardComponentTypes.NEGATE, true));
        });
    }

    @Test
    void choosingNandHandsOverTheAndTypeWithNegateOn() {
        palette.choose("nand");

        assertThat(chosen).singleElement().satisfies(entry -> {
            assertThat(entry.id()).isEqualTo("nand");
            assertThat(entry.type().id()).isEqualTo(StandardComponentTypes.AND);
            assertThat(entry.parameters()).isEqualTo(Map.of(StandardComponentTypes.NEGATE, true));
        });
    }

    @Test
    void draggingNorCarriesItsShortcutId() throws Exception {
        palette.choose("nor");

        Transferable transferable = palette.dragTransferable();

        assertThat(PaletteTransfer.decode((String) transferable.getTransferData(DataFlavor.stringFlavor)))
                .contains("nor");
    }

    @Test
    void theSearchFindsTheShortcutsByName() {
        palette.setFilter("xnor");
        assertThat(palette.visibleTypeIds()).containsExactly("xnor");
        palette.setFilter("not");
        assertThat(palette.visibleTypeIds()).containsExactly("not");
    }

    @Test
    void aShortcutIsMarkedAsFavouriteByItsOwnId() {
        AppPreferences preferences = AppPreferences.inMemory();
        new PaletteFavorites(palette, preferences).install();

        palette.toggleFavorite("xnor");

        assertThat(preferences.paletteFavorites()).containsExactly("xnor");
        assertThat(palette.favoriteEntries()).extracting(PaletteEntry::id).containsExactly("xnor");
        assertThat(palette.favoriteEntries().getFirst().parameters()).containsEntry(StandardComponentTypes.NEGATE,
                true);
    }

    @Test
    void favouritesSavedWithTheOldGateIdsComeBackAsTheNewEntries() {
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setPaletteFavorites(List.of("nand2", "and3", "not", "and2", "led", "gone"));

        assertThat(preferences.paletteFavorites()).containsExactly("nand", "and", "not", "led", "gone");
    }

    @Test
    void anEntryIsFoundByTheIdOfAShortcutOrOfAType() {
        assertThat(PaletteEntry.forId("xnor", registry)).hasValueSatisfying(entry -> {
            assertThat(entry.type().id()).isEqualTo(StandardComponentTypes.XOR);
            assertThat(entry.parameters()).containsEntry(StandardComponentTypes.NEGATE, true);
        });
        assertThat(PaletteEntry.forId("led", registry)).hasValueSatisfying(entry ->
                assertThat(entry.parameters()).isEmpty());
        assertThat(PaletteEntry.forId("nand2", registry)).isEmpty();
    }
}
