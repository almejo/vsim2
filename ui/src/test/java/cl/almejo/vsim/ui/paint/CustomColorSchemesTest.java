package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomColorSchemesTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final CustomColorSchemes schemes = new CustomColorSchemes(preferences);

    /** A scheme whose every role has its own colour and its own alpha. */
    private static ColorScheme distinctColors() {
        return ColorRole.scheme(role -> new Color(10 * role.ordinal(), 255 - role.ordinal(), 7 * role.ordinal(),
                40 + role.ordinal()));
    }

    @Test
    void aSavedSchemeComesBackWithAllTwentyFourRolesAndTheirAlpha() {
        NamedColorScheme saved = schemes.save("Mío", distinctColors());
        assertThat(saved.id()).isEqualTo("custom-1");
        assertThat(saved.name()).isEqualTo("Mío");

        CustomColorSchemes reread = new CustomColorSchemes(preferences);
        NamedColorScheme found = reread.byId("custom-1").orElseThrow();
        assertThat(found).isEqualTo(saved);
        for (ColorRole role : ColorRole.values()) {
            assertThat(role.get(found.scheme()).getAlpha()).isEqualTo(40 + role.ordinal());
        }
        assertThat(found.scheme()).isEqualTo(distinctColors());
    }

    @Test
    void colorsAreStoredAsHexWithAlpha() {
        schemes.save("Mío", ColorSchemes.DARK);
        assertThat(preferences.colorSchemesNode().node("custom-1").get("name", null)).isEqualTo("Mío");
        assertThat(preferences.colorSchemesNode().node("custom-1").get("background", null)).isEqualTo("#1E1F22FF");
        assertThat(preferences.colorSchemesNode().node("custom-1").get("shadow", null)).isEqualTo("#0000005A");
    }

    @Test
    void anUnreadableColorFallsBackToTheLightSchemeAndAMissingNameToTheId() {
        schemes.save("Mío", ColorSchemes.DARK);
        preferences.colorSchemesNode().node("custom-1").put("grid", "verde");
        preferences.colorSchemesNode().node("custom-1").remove("name");
        NamedColorScheme found = schemes.byId("custom-1").orElseThrow();
        assertThat(found.name()).isEqualTo("custom-1");
        assertThat(found.scheme().grid()).isEqualTo(ColorScheme.LIGHT.grid());
        assertThat(found.scheme().background()).isEqualTo(ColorSchemes.DARK.background());
    }

    @Test
    void aSchemeStoredBeforeTheGridLinesDerivesThemFromItsOwnDots() {
        NamedColorScheme saved = schemes.save("Antiguo", ColorSchemes.DARK.with(ColorRole.GRID, new Color(0x405060)));
        preferences.colorSchemesNode().node(saved.id()).remove("gridLine");

        ColorScheme found = schemes.byId(saved.id()).orElseThrow().scheme();

        assertThat(found.gridLine()).isEqualTo(ColorScheme.derivedGridLine(new Color(0x405060)));
        assertThat(found.grid()).isEqualTo(new Color(0x405060));
        assertThat(found.background()).isEqualTo(ColorSchemes.DARK.background());
    }

    @Test
    void aSchemeStoredBeforeThePinsColourPaintsItsPinsInItsOwnOutlineColour() {
        NamedColorScheme saved = schemes.save("Antiguo", ColorSchemes.DARK.with(ColorRole.COMPONENT_STROKE,
                new Color(0x405060)));
        preferences.colorSchemesNode().node(saved.id()).remove("pad");

        ColorScheme found = schemes.byId(saved.id()).orElseThrow().scheme();

        assertThat(found.pad()).isEqualTo(new Color(0x405060));
        assertThat(found.componentStroke()).isEqualTo(new Color(0x405060));
    }

    @Test
    void aSchemeStoredBeforeTheMemoriesGetsTheActivityColoursThatSuitItsBoard() {
        NamedColorScheme darkBoard = schemes.save("Oscuro antiguo", ColorSchemes.DARK);
        NamedColorScheme lightBoard = schemes.save("Claro antiguo", ColorScheme.LIGHT);
        for (NamedColorScheme saved : List.of(darkBoard, lightBoard)) {
            preferences.colorSchemesNode().node(saved.id()).remove("memoryRead");
            preferences.colorSchemesNode().node(saved.id()).remove("memoryWrite");
        }

        ColorScheme dark = schemes.byId(darkBoard.id()).orElseThrow().scheme();
        ColorScheme light = schemes.byId(lightBoard.id()).orElseThrow().scheme();

        assertThat(dark.memoryRead()).isEqualTo(ColorSchemes.DARK.memoryRead());
        assertThat(dark.memoryWrite()).isEqualTo(ColorSchemes.DARK.memoryWrite());
        assertThat(light.memoryRead()).isEqualTo(ColorScheme.LIGHT.memoryRead());
        assertThat(light.memoryWrite()).isEqualTo(ColorScheme.LIGHT.memoryWrite());
        assertThat(dark.background()).isEqualTo(ColorSchemes.DARK.background());
    }

    @Test
    void aSavedSchemeKeepsItsOwnPinsColour() {
        NamedColorScheme saved = schemes.save("Placa (copia)", ColorSchemes.PLACA);

        assertThat(preferences.colorSchemesNode().node(saved.id()).get("pad", null))
                .isEqualTo(CustomColorSchemes.format(ColorSchemes.PLACA.pad()));
        assertThat(schemes.byId(saved.id()).orElseThrow().scheme()).isEqualTo(ColorSchemes.PLACA);
    }

    @Test
    void anUnreadableGridLineIsDerivedFromTheDotsToo() {
        NamedColorScheme saved = schemes.save("Roto", ColorSchemes.DARK);
        preferences.colorSchemesNode().node(saved.id()).put("gridLine", "gris");

        assertThat(schemes.byId(saved.id()).orElseThrow().scheme().gridLine())
                .isEqualTo(ColorScheme.derivedGridLine(ColorSchemes.DARK.grid()));
    }

    @Test
    void idsAreNeverReusedWhileStored() {
        NamedColorScheme first = schemes.save("A", ColorSchemes.DARK);
        NamedColorScheme second = schemes.save("B", ColorSchemes.DARK);
        NamedColorScheme third = schemes.save("C", ColorSchemes.DARK);
        assertThat(first.id()).isEqualTo("custom-1");
        assertThat(second.id()).isEqualTo("custom-2");
        assertThat(third.id()).isEqualTo("custom-3");
        schemes.delete(first.id());
        assertThat(schemes.save("D", ColorSchemes.DARK).id()).isEqualTo("custom-4");
    }

    @Test
    void theListIsInNameOrder() {
        schemes.save("zeta", ColorSchemes.DARK);
        schemes.save("Alfa", ColorSchemes.LIGHT);
        schemes.save("beta", ColorSchemes.CLASSIC);
        assertThat(schemes.list()).extracting(NamedColorScheme::name).containsExactly("Alfa", "beta", "zeta");
    }

    @Test
    void updateKeepsTheIdAndDeleteRemovesIt() {
        NamedColorScheme saved = schemes.save("Mío", ColorSchemes.DARK);
        schemes.update(new NamedColorScheme(saved.id(), "Otro", ColorSchemes.CSIM));
        assertThat(schemes.list()).containsExactly(new NamedColorScheme("custom-1", "Otro", ColorSchemes.CSIM));
        schemes.delete(saved.id());
        assertThat(schemes.list()).isEmpty();
        assertThat(schemes.byId(saved.id())).isEmpty();
        assertThatThrownBy(() -> schemes.update(saved)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingTheActiveSchemeGoesBackToTheTheme() {
        NamedColorScheme saved = schemes.save("Mío", ColorSchemes.DARK);
        preferences.setColorScheme(Optional.of(saved.id()));
        schemes.delete(saved.id());
        assertThat(preferences.colorScheme()).isEmpty();
    }

    @Test
    void everyChangeTellsThePreferenceListeners() {
        AtomicInteger changes = new AtomicInteger();
        preferences.addListener(changes::incrementAndGet);
        NamedColorScheme saved = schemes.save("Mío", ColorSchemes.DARK);
        assertThat(changes).hasValue(1);
        schemes.update(new NamedColorScheme(saved.id(), "Otro", ColorSchemes.DARK));
        assertThat(changes).hasValue(2);
        schemes.delete(saved.id());
        assertThat(changes).hasValue(3);
    }

    @Test
    void theLookupSeesBuiltInAndCustomSchemesAndNothingElse() {
        NamedColorScheme saved = schemes.save("Mío", ColorSchemes.CSIM.with(ColorRole.TEXT, Color.PINK));
        assertThat(schemes.lookup("classic")).containsSame(ColorSchemes.CLASSIC);
        assertThat(schemes.lookup(saved.id())).contains(saved.scheme());
        assertThat(schemes.lookup("custom-9")).isEmpty();
        assertThat(schemes.lookup("neon")).isEmpty();
        assertThat(schemes.lookup("custom-1/../x")).isEmpty();
        assertThat(schemes.lookup("")).isEmpty();
        assertThat(CustomColorSchemes.isCustomId(saved.id())).isTrue();
        assertThat(CustomColorSchemes.isCustomId("classic")).isFalse();
    }
}
