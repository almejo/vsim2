package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.theme.ReadableColors;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static Color chipOf(String html) {
        Matcher matcher = Pattern.compile("background:#([0-9A-Fa-f]{6})").matcher(html);
        assertThat(matcher.find()).as(html).isTrue();
        return new Color(Integer.parseInt(matcher.group(1), 16));
    }

    @Test
    void theHighlightFollowsTheThemeColoursAndKeepsTheTextReadable() {
        Object before = UIManager.get("Component.warning.focusedBorderColor");
        try {
            JCheckBox box = new JCheckBox("a b");
            PreferenceRow row = PreferenceRow.checkBox("odd", box, "skip 1");
            UIManager.put("Component.warning.focusedBorderColor", new Color(0xFF0000));
            row.highlight("b");
            Color red = chipOf(box.getText());
            UIManager.put("Component.warning.focusedBorderColor", new Color(0x0000FF));
            row.highlight("b");
            Color blue = chipOf(box.getText());

            assertThat(red.getRed()).isGreaterThan(red.getBlue());
            assertThat(blue.getBlue()).isGreaterThan(blue.getRed());
            Color text = UIManager.getColor("Label.foreground");
            if (text != null) {
                assertThat(ReadableColors.contrast(text, red)).isGreaterThanOrEqualTo(
                        ReadableColors.MINIMUM_TEXT_CONTRAST);
            }
        } finally {
            UIManager.put("Component.warning.focusedBorderColor", before);
        }
    }

    @Test
    void theLabelColumnKeepsItsWidthWhileFiltering() {
        PreferencesPage page = page();
        page.setSize(new Dimension(700, 400));
        page.doLayout();
        int before = sessionBox.getX();

        page.filter("sesion");
        page.setSize(new Dimension(700, 400));
        page.doLayout();

        assertThat(page.visibleRowIds()).containsExactly("restoreSession");
        assertThat(sessionBox.getX()).isEqualTo(before).isPositive();
    }

    @Test
    void aDisabledCheckBoxKeepsItsDisabledTextColourWhileItsTextIsHighlighted() {
        Object before = UIManager.get("CheckBox.disabledText");
        try {
            UIManager.put("CheckBox.disabledText", new Color(0x123456));
            JCheckBox box = new JCheckBox("Animar los movimientos");
            PreferenceRow row = PreferenceRow.checkBox("animate", box, "skip 1");
            box.setEnabled(false);
            row.highlight("movi");

            assertThat(box.getText()).containsIgnoringCase("color:#123456").contains("background:");

            box.setEnabled(true);

            assertThat(box.getText()).doesNotContain("color:#123456").contains("background:");
            UIManager.put("CheckBox.disabledText", new Color(0x654321));
            box.setEnabled(false);
            assertThat(box.getText()).containsIgnoringCase("color:#654321");
        } finally {
            UIManager.put("CheckBox.disabledText", before);
        }
    }

    @Test
    void theLabelColumnIsMeasuredAgainWhenTheFontChanges() {
        Object before = UIManager.get("Label.font");
        PreferencesPage page = page();
        try {
            page.setSize(new Dimension(900, 400));
            page.doLayout();
            int small = themeCombo.getX();

            UIManager.put("Label.font", new FontUIResource("Dialog", Font.PLAIN, 40));
            SwingUtilities.updateComponentTreeUI(page);
            page.setSize(new Dimension(900, 400));
            page.getPreferredSize();
            page.doLayout();

            assertThat(themeCombo.getX()).isGreaterThan(small);
        } finally {
            UIManager.put("Label.font", before);
        }
    }
}
