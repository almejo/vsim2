package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Content of the preferences dialog (spec §6.7, §6.10, §5.6). Every change is stored at once; the theme manager
 * and the library follow the preferences live, the language applies at the next start.
 */
public final class PreferencesForm extends JPanel {

    /** A combo item: the stored value and the text shown for it. */
    record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    /** Asks the user for a directory, starting at {@code current}; empty when cancelled. */
    @FunctionalInterface
    public interface DirectoryChooser {
        Optional<Path> choose(Component parent, Path current);
    }

    /** The selectable languages: tag and the message key of its name. */
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("es", "preferences.language.spanish");
        LANGUAGES.put("en", "preferences.language.english");
    }

    private final AppPreferences preferences;
    private final DirectoryChooser directoryChooser;
    private final JComboBox<Choice<AppPreferences.Theme>> themeChoice = new JComboBox<>();
    private final JComboBox<Choice<Optional<String>>> schemeChoice = new JComboBox<>();
    private final JComboBox<Choice<Optional<String>>> languageChoice = new JComboBox<>();
    private final JComboBox<Choice<AppPreferences.InvertModifier>> invertModifierChoice = new JComboBox<>();
    private final JTextField libraryField = new JTextField(32);
    private final JButton chooseLibraryButton = new JButton(Messages.text("preferences.library.choose"));
    private final JButton restoreLibraryButton = new JButton(Messages.text("preferences.library.restore"));
    private final JCheckBox restoreSessionBox = new JCheckBox(Messages.text("preferences.restoreSession"));

    public PreferencesForm(AppPreferences preferences, DirectoryChooser directoryChooser) {
        super(new MigLayout("insets dialog, wrap 2", "[right][grow, fill]"));
        this.preferences = preferences;
        this.directoryChooser = directoryChooser;

        for (AppPreferences.Theme theme : List.of(AppPreferences.Theme.SYSTEM, AppPreferences.Theme.LIGHT,
                AppPreferences.Theme.DARK)) {
            themeChoice.addItem(new Choice<>(theme, themeName(theme)));
        }
        schemeChoice.addItem(new Choice<>(Optional.empty(), Messages.text("preferences.scheme.byTheme")));
        for (String id : ColorSchemes.ids()) {
            schemeChoice.addItem(new Choice<>(Optional.of(id), ColorSchemes.displayName(id)));
        }
        languageChoice.addItem(new Choice<>(Optional.empty(), Messages.text("preferences.language.system")));
        LANGUAGES.forEach((tag, nameKey) ->
                languageChoice.addItem(new Choice<>(Optional.of(tag), Messages.text(nameKey))));

        for (AppPreferences.InvertModifier modifier : AppPreferences.InvertModifier.values()) {
            invertModifierChoice.addItem(new Choice<>(modifier, invertModifierName(modifier)));
        }
        select(invertModifierChoice, preferences.invertModifier());
        invertModifierChoice.addActionListener(
                event -> preferences.setInvertModifier(selected(invertModifierChoice)));

        select(themeChoice, preferences.theme());
        select(schemeChoice, preferences.colorScheme().filter(id -> ColorSchemes.byId(id).isPresent()));
        select(languageChoice, preferences.language()
                .map(tag -> Locale.forLanguageTag(tag).getLanguage())
                .filter(LANGUAGES::containsKey));

        themeChoice.addActionListener(event -> preferences.setTheme(selected(themeChoice)));
        schemeChoice.addActionListener(event -> preferences.setColorScheme(selected(schemeChoice)));
        languageChoice.addActionListener(event -> preferences.setLanguage(selected(languageChoice)));
        libraryField.setEditable(false);
        chooseLibraryButton.addActionListener(event -> chooseLibraryDirectory());
        restoreLibraryButton.addActionListener(event -> {
            preferences.setLibraryDirectory(Optional.empty());
            refreshLibrary();
        });
        refreshLibrary();
        restoreSessionBox.setSelected(preferences.restoreSession());
        restoreSessionBox.addActionListener(event -> preferences.setRestoreSession(restoreSessionBox.isSelected()));

        add(new JLabel(Messages.text("preferences.theme")));
        add(themeChoice);
        add(new JLabel(Messages.text("preferences.scheme")));
        add(schemeChoice);
        add(new JLabel(Messages.text("preferences.language")));
        add(languageChoice);
        add(new JLabel(Messages.text("preferences.language.note")), "skip 1");
        add(new JLabel(Messages.text("preferences.library")));
        add(libraryField);
        add(chooseLibraryButton, "skip 1, split 2, growx 0");
        add(restoreLibraryButton, "growx 0");
        add(restoreSessionBox, "skip 1, gaptop 12");
        add(new JLabel(Messages.text("preferences.invertModifier")), "gaptop 12");
        add(invertModifierChoice, "gaptop 12");
    }

    JComboBox<Choice<AppPreferences.Theme>> themeChoice() {
        return themeChoice;
    }

    JComboBox<Choice<Optional<String>>> schemeChoice() {
        return schemeChoice;
    }

    JComboBox<Choice<Optional<String>>> languageChoice() {
        return languageChoice;
    }

    JComboBox<Choice<AppPreferences.InvertModifier>> invertModifierChoice() {
        return invertModifierChoice;
    }

    JTextField libraryField() {
        return libraryField;
    }

    JButton chooseLibraryButton() {
        return chooseLibraryButton;
    }

    JButton restoreLibraryButton() {
        return restoreLibraryButton;
    }

    JCheckBox restoreSessionBox() {
        return restoreSessionBox;
    }

    /** The text of an invert key, also used by the follow toggle's tooltip. */
    public static String invertModifierName(AppPreferences.InvertModifier modifier) {
        return switch (modifier) {
            case SHIFT -> Messages.text("preferences.invertModifier.SHIFT");
            case ALT -> Messages.text("preferences.invertModifier.ALT");
            case NONE -> Messages.text("preferences.invertModifier.NONE");
        };
    }

    static String themeName(AppPreferences.Theme theme) {
        return switch (theme) {
            case LIGHT -> Messages.text("preferences.theme.LIGHT");
            case DARK -> Messages.text("preferences.theme.DARK");
            case SYSTEM -> Messages.text("preferences.theme.SYSTEM");
        };
    }

    private void chooseLibraryDirectory() {
        directoryChooser.choose(this, preferences.libraryDirectory()).ifPresent(directory -> {
            preferences.setLibraryDirectory(Optional.of(directory));
            refreshLibrary();
        });
    }

    private void refreshLibrary() {
        libraryField.setText(preferences.libraryDirectory().toString());
        restoreLibraryButton.setEnabled(!preferences.usesDefaultLibraryDirectory());
    }

    private static <T> void select(JComboBox<Choice<T>> combo, T value) {
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).value().equals(value)) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    private static <T> T selected(JComboBox<Choice<T>> combo) {
        return combo.getItemAt(combo.getSelectedIndex()).value();
    }
}
