package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComboBox;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesFormTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<Path> offeredDirectories = new ArrayList<>();
    private Optional<Path> chooserAnswer = Optional.empty();

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    private PreferencesForm form() {
        return new PreferencesForm(preferences, (parent, current) -> {
            offeredDirectories.add(current);
            return chooserAnswer;
        });
    }

    private static <T> List<String> labels(JComboBox<PreferencesForm.Choice<T>> combo) {
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < combo.getItemCount(); index++) {
            labels.add(combo.getItemAt(index).label());
        }
        return labels;
    }

    private static <T> T selected(JComboBox<PreferencesForm.Choice<T>> combo) {
        return combo.getItemAt(combo.getSelectedIndex()).value();
    }

    private static <T> void choose(JComboBox<PreferencesForm.Choice<T>> combo, T value) {
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).value().equals(value)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
        throw new AssertionError("no item " + value);
    }

    @Test
    void choicesAreNamedInSpanish() {
        PreferencesForm form = form();
        assertThat(labels(form.themeChoice())).containsExactly("Seguir sistema", "Claro", "Oscuro");
        assertThat(labels(form.schemeChoice())).containsExactly("Según el tema", "VSim clásico", "Claro", "Oscuro",
                "Pastel (VSim)", "CSim (VSim)", "Twilight (VSim)");
        assertThat(labels(form.languageChoice())).containsExactly("Sistema", "Español", "English");
    }

    @Test
    void aFreshProfileShowsTheDefaults() {
        PreferencesForm form = form();
        assertThat(selected(form.themeChoice())).isEqualTo(AppPreferences.Theme.SYSTEM);
        assertThat(selected(form.schemeChoice())).isEmpty();
        assertThat(selected(form.languageChoice())).isEmpty();
        assertThat(form.libraryField().getText()).isEqualTo(preferences.libraryDirectory().toString());
        assertThat(form.libraryField().isEditable()).isFalse();
        assertThat(form.restoreLibraryButton().isEnabled()).isFalse();
    }

    @Test
    void storedValuesAreShown(@TempDir Path directory) {
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setColorScheme(Optional.of("classic"));
        preferences.setLanguage(Optional.of("en"));
        preferences.setLibraryDirectory(Optional.of(directory));
        PreferencesForm form = form();
        assertThat(selected(form.themeChoice())).isEqualTo(AppPreferences.Theme.DARK);
        assertThat(selected(form.schemeChoice())).contains("classic");
        assertThat(selected(form.languageChoice())).contains("en");
        assertThat(form.libraryField().getText()).isEqualTo(directory.toAbsolutePath().toString());
        assertThat(form.restoreLibraryButton().isEnabled()).isTrue();
    }

    @Test
    void anUnknownStoredSchemeShowsByTheme() {
        preferences.setColorScheme(Optional.of("neon"));
        preferences.setLanguage(Optional.of("fr"));
        PreferencesForm form = form();
        assertThat(selected(form.schemeChoice())).isEmpty();
        assertThat(selected(form.languageChoice())).isEmpty();
    }

    @Test
    void everyChoiceIsStoredAtOnce() {
        PreferencesForm form = form();
        choose(form.themeChoice(), AppPreferences.Theme.DARK);
        choose(form.schemeChoice(), Optional.of("classic"));
        choose(form.languageChoice(), Optional.of("en"));
        assertThat(preferences.theme()).isEqualTo(AppPreferences.Theme.DARK);
        assertThat(preferences.colorScheme()).contains("classic");
        assertThat(preferences.language()).contains("en");

        choose(form.schemeChoice(), Optional.empty());
        choose(form.languageChoice(), Optional.empty());
        assertThat(preferences.colorScheme()).isEmpty();
        assertThat(preferences.language()).isEmpty();
    }

    @Test
    void choosingADirectoryStoresItAndRestoreGoesBackToTheDefault(@TempDir Path directory) {
        PreferencesForm form = form();
        Path defaultDirectory = preferences.libraryDirectory();
        chooserAnswer = Optional.of(directory);
        form.chooseLibraryButton().doClick(0);
        assertThat(offeredDirectories).containsExactly(defaultDirectory);
        assertThat(preferences.libraryDirectory()).isEqualTo(directory.toAbsolutePath());
        assertThat(form.libraryField().getText()).isEqualTo(directory.toAbsolutePath().toString());
        assertThat(form.restoreLibraryButton().isEnabled()).isTrue();

        form.restoreLibraryButton().doClick(0);
        assertThat(preferences.usesDefaultLibraryDirectory()).isTrue();
        assertThat(form.libraryField().getText()).isEqualTo(defaultDirectory.toString());
        assertThat(form.restoreLibraryButton().isEnabled()).isFalse();
    }

    @Test
    void cancellingTheChooserChangesNothing() {
        PreferencesForm form = form();
        AtomicInteger changes = new AtomicInteger();
        preferences.addListener(changes::incrementAndGet);
        chooserAnswer = Optional.empty();
        form.chooseLibraryButton().doClick(0);
        assertThat(changes).hasValue(0);
        assertThat(preferences.usesDefaultLibraryDirectory()).isTrue();
    }

    @Test
    void theRestoreSessionBoxShowsAndChangesThePreference() {
        PreferencesForm form = form();
        assertThat(form.restoreSessionBox().getText()).isEqualTo("Al iniciar, abrir los circuitos de la última sesión");
        assertThat(form.restoreSessionBox().isSelected()).isTrue();

        form.restoreSessionBox().doClick(0);
        assertThat(preferences.restoreSession()).isFalse();
        assertThat(form().restoreSessionBox().isSelected()).isFalse();
    }
}
