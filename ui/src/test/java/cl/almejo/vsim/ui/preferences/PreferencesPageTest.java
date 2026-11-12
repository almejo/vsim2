package cl.almejo.vsim.ui.preferences;

import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesPageTest {

    private static final String SESSION_TEXT = "Al iniciar, abrir los circuitos de la última sesión";

    private final JComboBox<String> themeCombo = new JComboBox<>(new String[]{"Claro", "Oscuro"});
    private final JCheckBox sessionBox = new JCheckBox(SESSION_TEXT);

    private PreferencesPage page() {
        return PreferencesPage.builder()
                .section("preferences.section.window")
                .row(PreferenceRow.labeled("theme", "preferences.theme", themeCombo, ""))
                .section("preferences.section.startup")
                .row(PreferenceRow.checkBox("restoreSession", sessionBox, "skip 1"))
                .row(PreferenceRow.labeled("language", "preferences.language", new JComboBox<>(), "",
                        new PreferenceRow.Placed(new JLabel("Se aplica al reiniciar"), "skip 1")))
                .build();
    }

    @Test
    void aPageListsItsRowsUnderTheirSectionsInOrder() {
        assertThat(page().sectionRows()).containsExactly(
                Map.entry("preferences.section.window", List.of("theme")),
                Map.entry("preferences.section.startup", List.of("restoreSession", "language")));
    }

    @Test
    void anEmptyQueryShowsEveryRowAndCountsThem() {
        PreferencesPage page = page();

        assertThat(page.filter("")).isEqualTo(3);
        assertThat(page.visibleRowIds()).containsExactly("theme", "restoreSession", "language");
        assertThat(page.visibleSectionKeys()).containsExactly("preferences.section.window",
                "preferences.section.startup");
    }

    @Test
    void aQueryHidesTheOtherRowsAndTheSectionsLeftEmpty() {
        PreferencesPage page = page();

        assertThat(page.filter("oscuro")).isEqualTo(1);

        assertThat(page.visibleRowIds()).containsExactly("theme");
        assertThat(page.visibleSectionKeys()).containsExactly("preferences.section.window");
    }

    @Test
    void aQueryThatOnlyMatchesASectionTitleShowsNothing() {
        PreferencesPage page = page();

        assertThat(page.filter("ventana")).isZero();

        assertThat(page.visibleRowIds()).isEmpty();
        assertThat(page.visibleSectionKeys()).isEmpty();
    }

    @Test
    void aRowMatchesOnItsNotesAndIsFoundIgnoringAccents() {
        PreferencesPage page = page();

        assertThat(page.filter("REINICIAR")).isEqualTo(1);
        assertThat(page.visibleRowIds()).containsExactly("language");
        assertThat(page.filter("ultima sesion")).isEqualTo(1);
        assertThat(page.visibleRowIds()).containsExactly("restoreSession");
    }

    @Test
    void clearingTheQueryRestoresEverythingAndThePlainTexts() {
        PreferencesPage page = page();
        page.filter("sesión");
        assertThat(sessionBox.getText()).contains("<span");

        assertThat(page.filter("")).isEqualTo(3);

        assertThat(page.visibleRowIds()).containsExactly("theme", "restoreSession", "language");
        assertThat(page.visibleSectionKeys()).hasSize(2);
        assertThat(sessionBox.getText()).isEqualTo(SESSION_TEXT);
        assertThat(sessionBox.isVisible()).isTrue();
    }

    @Test
    void theMatchedTextOfACheckBoxIsHighlightedAndRestoredWhenTheQueryIsCleared() {
        PreferenceRow row = PreferenceRow.checkBox("restoreSession", sessionBox, "skip 1");

        row.highlight("SESION");

        assertThat(sessionBox.getText()).startsWith("<html>").contains("<span style=\"background:")
                .contains(">sesión</span>");
        row.highlight("");
        assertThat(sessionBox.getText()).isEqualTo(SESSION_TEXT);
    }

    @Test
    void highlightingEscapesTheTextAsHtml() {
        JCheckBox box = new JCheckBox("a < b & c");
        PreferenceRow row = PreferenceRow.checkBox("odd", box, "skip 1");

        row.highlight("b");

        assertThat(box.getText()).contains("a &lt; ").contains(">b</span>").contains(" &amp; c");
    }

    @Test
    void theKeywordsOfARowAreSearchedToo() {
        PreferenceRow row = PreferenceRow.labeled("library", "preferences.library", new JLabel("x"), "");

        assertThat(row.words()).contains("Directorio de la librería").contains("carpeta");
    }

    @Test
    void aRowCanIgnoreTheValuesOfItsControl() {
        JComboBox<String> families = new JComboBox<>(new String[]{"DejaVu Sans"});
        PreferenceRow row = PreferenceRow.labeled("textFont", "preferences.textFont", families, "").ignoringValues();

        assertThat(row.words()).doesNotContain("DejaVu");
    }
}
