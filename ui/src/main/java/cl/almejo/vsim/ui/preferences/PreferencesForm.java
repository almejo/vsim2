package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.CustomColorSchemes;
import cl.almejo.vsim.ui.paint.NamedColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import com.formdev.flatlaf.FlatLaf;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.Component;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

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
    private final JComboBox<Choice<String>> fontFamilyChoice = new JComboBox<>();
    private final JSpinner fontSizeSpinner = new JSpinner();
    private final CustomColorSchemes customSchemes;
    private final SchemeEditorDialog.Opener schemeEditorOpener;
    private final SchemeEditorPanel.ColorChooser colorChooser;
    private final BooleanSupplier themeIsDark;
    private final JButton customizeSchemeButton = new JButton(Messages.text("preferences.scheme.customize"));
    private final JButton deleteSchemeButton = new JButton(Messages.text("preferences.scheme.delete"));
    private boolean refreshingSchemes;

    public PreferencesForm(AppPreferences preferences, DirectoryChooser directoryChooser) {
        this(preferences, directoryChooser, SchemeEditorDialog::open, SchemeEditorDialog::chooseColor,
                FlatLaf::isLafDark);
    }

    /**
     * @param schemeEditorOpener shows «Personalizar…» and tells whether the user saved (tests answer without a dialog)
     * @param colorChooser       what the editor's colour buttons open
     * @param themeIsDark        whether the installed theme is dark: «Según el tema» is then a copy of «Oscuro»
     */
    PreferencesForm(AppPreferences preferences, DirectoryChooser directoryChooser,
                    SchemeEditorDialog.Opener schemeEditorOpener, SchemeEditorPanel.ColorChooser colorChooser,
                    BooleanSupplier themeIsDark) {
        super(new MigLayout("insets dialog, wrap 2", "[right][grow, fill]"));
        this.preferences = preferences;
        this.directoryChooser = directoryChooser;
        this.customSchemes = new CustomColorSchemes(preferences);
        this.schemeEditorOpener = schemeEditorOpener;
        this.colorChooser = colorChooser;
        this.themeIsDark = themeIsDark;

        for (AppPreferences.Theme theme : List.of(AppPreferences.Theme.SYSTEM, AppPreferences.Theme.LIGHT,
                AppPreferences.Theme.DARK)) {
            themeChoice.addItem(new Choice<>(theme, themeName(theme)));
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
        refreshSchemes();
        select(languageChoice, preferences.language()
                .map(tag -> Locale.forLanguageTag(tag).getLanguage())
                .filter(LANGUAGES::containsKey));

        themeChoice.addActionListener(event -> preferences.setTheme(selected(themeChoice)));
        schemeChoice.addActionListener(event -> {
            if (!refreshingSchemes) {
                preferences.setColorScheme(selected(schemeChoice));
                deleteSchemeButton.setEnabled(selectedCustomScheme().isPresent());
            }
        });
        customizeSchemeButton.addActionListener(event -> customizeScheme());
        deleteSchemeButton.addActionListener(event -> deleteScheme());
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
        add(customizeSchemeButton, "skip 1, split 2, growx 0");
        add(deleteSchemeButton, "growx 0");
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
        addTextFontRows();
    }

    /**
     * «Letra de etiquetas y textos» (M6.8): the family («Predeterminada» = SansSerif first, then the installed ones)
     * and the size, stored at once. A stored family that is not installed here is listed under its own name, so
     * opening the form never changes it.
     */
    private void addTextFontRows() {
        TextFont stored = preferences.textFont();
        fontFamilyChoice.addItem(new Choice<>(TextFont.DEFAULT.family(),
                Messages.text("preferences.textFont.default")));
        if (!stored.family().equalsIgnoreCase(TextFont.DEFAULT.family()) && !TextFont.isInstalled(stored.family())) {
            fontFamilyChoice.addItem(new Choice<>(stored.family(), stored.family()));
        }
        for (String family : TextFont.installedFamilies()) {
            if (!family.equalsIgnoreCase(TextFont.DEFAULT.family())) {
                fontFamilyChoice.addItem(new Choice<>(family, family));
            }
        }
        select(fontFamilyChoice, stored.family());
        fontSizeSpinner.setModel(new SpinnerNumberModel(stored.size(), TextFont.MINIMUM_SIZE, TextFont.MAXIMUM_SIZE,
                1));
        fontFamilyChoice.addActionListener(event -> storeTextFont());
        fontSizeSpinner.addChangeListener(event -> storeTextFont());

        add(new JLabel(Messages.text("preferences.textFont")), "gaptop 12");
        add(fontFamilyChoice, "gaptop 12, split 2");
        add(fontSizeSpinner, "growx 0");
    }

    private void storeTextFont() {
        preferences.setTextFont(new TextFont(selected(fontFamilyChoice), (Integer) fontSizeSpinner.getValue()));
    }

    /**
     * Fills the scheme combo («Según el tema», the built-ins, then the custom schemes by name) and selects the stored
     * choice; an id that names no scheme (a deleted custom one) shows «Según el tema». Stores nothing.
     */
    private void refreshSchemes() {
        refreshingSchemes = true;
        try {
            schemeChoice.removeAllItems();
            schemeChoice.addItem(new Choice<>(Optional.empty(), Messages.text("preferences.scheme.byTheme")));
            for (String id : ColorSchemes.ids()) {
                schemeChoice.addItem(new Choice<>(Optional.of(id), ColorSchemes.displayName(id)));
            }
            for (NamedColorScheme custom : customSchemes.list()) {
                schemeChoice.addItem(new Choice<>(Optional.of(custom.id()), custom.name()));
            }
            select(schemeChoice, Optional.empty());
            select(schemeChoice, preferences.colorScheme().filter(id -> customSchemes.lookup(id).isPresent()));
        } finally {
            refreshingSchemes = false;
        }
        deleteSchemeButton.setEnabled(selectedCustomScheme().isPresent());
    }

    private Optional<NamedColorScheme> selectedCustomScheme() {
        return selected(schemeChoice).flatMap(customSchemes::byId);
    }

    /**
     * «Personalizar…»: edits the selected custom scheme in place, or saves a copy of the selected built-in one (by
     * theme: the scheme the theme gives) as a new scheme and selects it.
     */
    private void customizeScheme() {
        Optional<NamedColorScheme> custom = selectedCustomScheme();
        NamedColorScheme start = custom.orElseGet(() -> {
            String id = selected(schemeChoice).filter(chosen -> ColorSchemes.byId(chosen).isPresent())
                    .orElse(themeIsDark.getAsBoolean() ? ColorSchemes.DARK_ID : ColorSchemes.LIGHT_ID);
            ColorScheme scheme = ColorSchemes.byId(id).orElseThrow();
            return new NamedColorScheme(id, Messages.format("preferences.scheme.copyName",
                    ColorSchemes.displayName(id)), scheme);
        });
        SchemeEditorPanel editor = new SchemeEditorPanel(start, colorChooser);
        if (!schemeEditorOpener.open(this, editor)) {
            return;
        }
        if (custom.isPresent()) {
            customSchemes.update(new NamedColorScheme(start.id(), editor.name(), editor.scheme()));
        } else {
            NamedColorScheme saved = customSchemes.save(editor.name(), editor.scheme());
            preferences.setColorScheme(Optional.of(saved.id()));
        }
        refreshSchemes();
    }

    /** «Borrar»: removes the selected custom scheme; when it was in use, the choice goes back to «Según el tema». */
    private void deleteScheme() {
        selectedCustomScheme().ifPresent(custom -> {
            customSchemes.delete(custom.id());
            refreshSchemes();
        });
    }

    JButton customizeSchemeButton() {
        return customizeSchemeButton;
    }

    JButton deleteSchemeButton() {
        return deleteSchemeButton;
    }

    JComboBox<Choice<String>> fontFamilyChoice() {
        return fontFamilyChoice;
    }

    JSpinner fontSizeSpinner() {
        return fontSizeSpinner;
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
