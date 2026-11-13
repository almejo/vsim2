package cl.almejo.vsim.ui.theme;

import cl.almejo.vsim.ui.app.AppPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceLookTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<InterfaceLook> looks = new ArrayList<>();
    private final ThemeManager manager = new ThemeManager(preferences,
            new cl.almejo.vsim.ui.paint.Appearance(cl.almejo.vsim.ui.paint.ColorSchemes.CLASSIC), () -> false,
            new LookAndFeelInstaller() {
                @Override
                public boolean install(boolean dark) {
                    throw new AssertionError("the manager installs with the interface's look");
                }

                @Override
                public boolean install(boolean dark, InterfaceLook look) {
                    looks.add(look);
                    return true;
                }
            });

    @AfterEach
    void stop() {
        manager.dispose();
    }

    @Test
    void aFreshProfileIsTheStandardSizeAndFontAndPresentsAtOneHundredFifty() {
        assertThat(InterfaceLook.of(preferences, false)).isEqualTo(InterfaceLook.STANDARD);
        assertThat(InterfaceLook.of(preferences, true)).isEqualTo(new InterfaceLook(150, Optional.empty()));
    }

    @Test
    void presentingNeverMakesTheInterfaceSmallerAndSinCambioKeepsIt() {
        preferences.setInterfaceZoom(175);
        assertThat(InterfaceLook.of(preferences, true).zoomPercent()).isEqualTo(175);
        preferences.setInterfaceZoom(125);
        preferences.setPresentationInterfaceZoom(200);
        assertThat(InterfaceLook.of(preferences, true).zoomPercent()).isEqualTo(200);
        preferences.setPresentationInterfaceZoom(AppPreferences.PRESENTATION_ZOOM_UNCHANGED);
        assertThat(InterfaceLook.of(preferences, true).zoomPercent()).isEqualTo(125);
    }

    @Test
    void changingTheSizeOrTheFontReinstallsLiveAndOtherPreferencesDoNot() {
        manager.start();
        preferences.setInterfaceZoom(150);
        preferences.setInterfaceFontFamily(Optional.of("DejaVu Sans"));
        preferences.setShowTipAtStart(false);

        assertThat(looks).containsExactly(InterfaceLook.STANDARD, new InterfaceLook(150, Optional.empty()),
                new InterfaceLook(150, Optional.of("DejaVu Sans")));
        assertThat(manager.installedLook()).isEqualTo(new InterfaceLook(150, Optional.of("DejaVu Sans")));
    }

    @Test
    void presentationModeRaisesTheInterfaceAndLeavingItGoesBack() {
        manager.start();
        manager.setPresenting(true);
        manager.setPresenting(true);
        manager.setPresenting(false);

        assertThat(looks).extracting(InterfaceLook::zoomPercent).containsExactly(100, 150, 100);
    }

    @Test
    void presentingWithSinCambioInstallsNothing() {
        preferences.setPresentationInterfaceZoom(AppPreferences.PRESENTATION_ZOOM_UNCHANGED);
        manager.start();
        manager.setPresenting(true);

        assertThat(looks).containsExactly(InterfaceLook.STANDARD);
    }

    @Test
    void changingThePresentationSizeWhilePresentingAppliesItAtOnce() {
        manager.start();
        manager.setPresenting(true);
        preferences.setPresentationInterfaceZoom(200);

        assertThat(looks).extracting(InterfaceLook::zoomPercent).containsExactly(100, 150, 200);
    }

    @Test
    void flatLafGetsTheSizeAsAPercentOfItsDefaultFontAndNothingAtTheStandardSize() {
        assertThat(FlatLafInstaller.defaultsFor(InterfaceLook.STANDARD)).isNull();
        assertThat(FlatLafInstaller.defaultsFor(new InterfaceLook(175, Optional.empty())))
                .isEqualTo(Map.of("defaultFont", "175%"));
    }

    @Test
    void onlyTheOfferedSizesAreStoredAndAnythingElseReadsAsTheDefault() {
        assertThatThrownBy(() -> preferences.setInterfaceZoom(110)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> preferences.setInterfaceZoom(AppPreferences.PRESENTATION_ZOOM_UNCHANGED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
