package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.CustomColorSchemes;
import cl.almejo.vsim.ui.paint.NamedColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.theme.SwitchBox;
import cl.almejo.vsim.ui.wiring.InvertModifierNames;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The controls of the preferences dialog's four option groups (spec §6.7, §6.10, §5.6) and their wiring: it owns the
 * controls and lays them out as {@link PreferencesPage}s, one per group. Every change is stored at once; the theme
 * manager and the library follow the preferences live, the language applies at the next start.
 */
public final class PreferencesForm {

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

    /** Asks whether to delete the custom scheme named {@code schemeName}; tests answer without a dialog. */
    @FunctionalInterface
    interface DeleteConfirmer {
        boolean confirm(Component parent, String schemeName);
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
    private final JComboBox<Choice<AppPreferences.LensShape>> lensShapeChoice = new JComboBox<>();
    private final JComboBox<Choice<AppPreferences.LensSize>> lensSizeChoice = new JComboBox<>();
    private final JTextField libraryField = new JTextField(32);
    private final JTextField externalEditorField = new JTextField(32);
    private final JButton chooseLibraryButton = new JButton(Messages.text("preferences.library.choose"));
    private final JButton restoreLibraryButton = new JButton(Messages.text("preferences.library.restore"));
    private final JCheckBox restoreSessionBox = new JCheckBox(Messages.text("preferences.restoreSession"));
    /** The same preference as the tip of the day's own «Mostrar al iniciar». */
    private final JCheckBox showTipAtStartBox = new JCheckBox(Messages.text("preferences.showTipAtStart"));
    /** «Animaciones»: the same Sí/No switch as the boolean properties. */
    private final SwitchBox animationsSwitch = new SwitchBox(true);
    private final JCheckBox animateViewMovesBox = new JCheckBox(Messages.text("preferences.animateViewMoves"));
    private final Runnable preferenceListener = this::followPreferences;
    private final JComboBox<Choice<String>> fontFamilyChoice = new JComboBox<>();
    private final JComboBox<Choice<Integer>> interfaceZoomChoice = new JComboBox<>();
    private final JComboBox<Choice<Optional<String>>> interfaceFontChoice = new JComboBox<>();
    private final JComboBox<Choice<Integer>> presentationZoomChoice = new JComboBox<>();
    private final JSpinner fontSizeSpinner = new JSpinner();
    private final CustomColorSchemes customSchemes;
    private final SchemeEditorDialog.Opener schemeEditorOpener;
    private final SchemeEditorPanel.ColorChooser colorChooser;
    private final BooleanSupplier themeIsDark;
    private final DeleteConfirmer deleteConfirmer;
    private final JButton customizeSchemeButton = new JButton(Messages.text("preferences.scheme.customize"));
    private final JButton deleteSchemeButton = new JButton(Messages.text("preferences.scheme.delete"));
    private boolean refreshingSchemes;
    private final PreferencesPage general;
    private final PreferencesPage appearance;
    private final PreferencesPage accessibility;
    private final PreferencesPage files;

    public PreferencesForm(AppPreferences preferences, DirectoryChooser directoryChooser) {
        this(preferences, directoryChooser, SchemeEditorDialog::open, SchemeEditorDialog::chooseColor,
                FlatLaf::isLafDark, PreferencesForm::confirmDelete);
    }

    /**
     * @param schemeEditorOpener shows «Personalizar…» and tells whether the user saved (tests answer without a dialog)
     * @param colorChooser       what the editor's colour buttons open
     * @param themeIsDark        whether the installed theme is dark: «Según el tema» is then a copy of «Oscuro»
     * @param deleteConfirmer    asks before «Borrar» removes a custom scheme (it cannot be undone)
     */
    PreferencesForm(AppPreferences preferences, DirectoryChooser directoryChooser,
                    SchemeEditorDialog.Opener schemeEditorOpener, SchemeEditorPanel.ColorChooser colorChooser,
                    BooleanSupplier themeIsDark, DeleteConfirmer deleteConfirmer) {
        this.preferences = preferences;
        this.directoryChooser = directoryChooser;
        this.customSchemes = new CustomColorSchemes(preferences);
        this.schemeEditorOpener = schemeEditorOpener;
        this.colorChooser = colorChooser;
        this.themeIsDark = themeIsDark;
        this.deleteConfirmer = deleteConfirmer;

        for (AppPreferences.Theme theme : List.of(AppPreferences.Theme.SYSTEM, AppPreferences.Theme.LIGHT,
                AppPreferences.Theme.DARK)) {
            themeChoice.addItem(new Choice<>(theme, themeName(theme)));
        }
        languageChoice.addItem(new Choice<>(Optional.empty(), Messages.text("preferences.language.system")));
        LANGUAGES.forEach((tag, nameKey) ->
                languageChoice.addItem(new Choice<>(Optional.of(tag), Messages.text(nameKey))));

        for (AppPreferences.InvertModifier modifier : AppPreferences.InvertModifier.values()) {
            invertModifierChoice.addItem(new Choice<>(modifier, InvertModifierNames.of(modifier)));
        }
        select(invertModifierChoice, preferences.invertModifier());
        invertModifierChoice.addActionListener(
                event -> preferences.setInvertModifier(selected(invertModifierChoice)));

        for (AppPreferences.LensShape shape : AppPreferences.LensShape.values()) {
            lensShapeChoice.addItem(new Choice<>(shape, lensShapeName(shape)));
        }
        for (AppPreferences.LensSize size : AppPreferences.LensSize.values()) {
            lensSizeChoice.addItem(new Choice<>(size, lensSizeName(size)));
        }
        showLens();
        lensShapeChoice.addActionListener(event -> preferences.setLensShape(selected(lensShapeChoice)));
        lensSizeChoice.addActionListener(event -> preferences.setLensSize(selected(lensSizeChoice)));

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
        showTipAtStartBox.setSelected(preferences.showTipAtStart());
        showTipAtStartBox.addActionListener(event -> preferences.setShowTipAtStart(showTipAtStartBox.isSelected()));
        wireAnimations();
        // the find bar's ↷ changes it too, also while this dialog is open
        preferences.addListener(preferenceListener);

        wireExternalEditor();
        wireTextFont();
        wireInterface();
        general = buildGeneral();
        appearance = buildAppearance();
        accessibility = buildAccessibility();
        files = buildFiles();
    }

    /** «General»: Idioma, Al iniciar, Edición. */
    private PreferencesPage buildGeneral() {
        return PreferencesPage.builder()
                .section("preferences.section.language")
                .row(PreferenceRow.labeled("language", "preferences.language", languageChoice, "",
                        new PreferenceRow.Placed(new JLabel(Messages.text("preferences.language.note")), "skip 1")))
                .section("preferences.section.startup")
                .row(PreferenceRow.checkBox("restoreSession", restoreSessionBox, "skip 1"))
                .row(PreferenceRow.checkBox("showTipAtStart", showTipAtStartBox, "skip 1"))
                .section("preferences.section.editing")
                .row(PreferenceRow.labeled("invertModifier", "preferences.invertModifier", invertModifierChoice, ""))
                .build();
    }

    /** «Aspecto»: Ventana (theme, the interface's size and font) and Circuito. */
    private PreferencesPage buildAppearance() {
        return PreferencesPage.builder()
                .section("preferences.section.window")
                .row(PreferenceRow.labeled("theme", "preferences.theme", themeChoice, ""))
                .row(PreferenceRow.labeled("interfaceZoom", "preferences.interfaceZoom", interfaceZoomChoice, ""))
                .row(PreferenceRow.labeled("interfaceFont", "preferences.interfaceFont", interfaceFontChoice, "")
                        .ignoringValues())
                .row(PreferenceRow.labeled("presentationZoom", "preferences.presentationZoom",
                        presentationZoomChoice, ""))
                .section("preferences.section.circuit")
                .row(PreferenceRow.labeled("scheme", "preferences.scheme", schemeChoice, "",
                        new PreferenceRow.Placed(customizeSchemeButton, "skip 1, split 2, growx 0"),
                        new PreferenceRow.Placed(deleteSchemeButton, "growx 0")).ignoringValues())
                .row(PreferenceRow.labeled("textFont", "preferences.textFont", fontFamilyChoice, "split 2",
                        new PreferenceRow.Placed(fontSizeSpinner, "growx 0")).ignoringValues())
                .build();
    }

    /** «Accesibilidad»: Movimiento and Lupa. */
    private PreferencesPage buildAccessibility() {
        return PreferencesPage.builder()
                .section("preferences.section.motion")
                .row(PreferenceRow.labeled("animations", "preferences.animations", animationsSwitch, "growx 0")
                        .ignoringValues())
                .row(PreferenceRow.checkBox("animateViewMoves", animateViewMovesBox, "skip 1"))
                .section("preferences.section.lens")
                .row(PreferenceRow.labeled("lensShape", "preferences.lens.shape", lensShapeChoice, ""))
                .row(PreferenceRow.labeled("lensSize", "preferences.lens.size", lensSizeChoice, ""))
                .build();
    }

    /** «Archivos»: Librería and Memorias. */
    private PreferencesPage buildFiles() {
        return PreferencesPage.builder()
                .section("preferences.section.library")
                .row(PreferenceRow.labeled("library", "preferences.library", libraryField, "",
                        new PreferenceRow.Placed(chooseLibraryButton, "skip 1, split 2, growx 0"),
                        new PreferenceRow.Placed(restoreLibraryButton, "growx 0")))
                .section("preferences.section.memories")
                .row(PreferenceRow.labeled("externalEditor", "preferences.externalEditor", externalEditorField, "",
                        new PreferenceRow.Placed(new JLabel(Messages.text("preferences.externalEditor.note")),
                                "skip 1")))
                .build();
    }

    PreferencesPage general() {
        return general;
    }

    PreferencesPage appearance() {
        return appearance;
    }

    PreferencesPage accessibility() {
        return accessibility;
    }

    PreferencesPage files() {
        return files;
    }

    /**
     * «Animaciones», a Sí/No switch, and below it «Animar los movimientos de la vista», which only counts (and is only
     * enabled) with animations on; turning them off keeps its choice for when they come back.
     */
    private void wireAnimations() {
        animationsSwitch.addActionListener(event -> preferences.setAnimations(animationsSwitch.isSelected()));
        animateViewMovesBox.addActionListener(
                event -> preferences.setAnimateViewMoves(animateViewMovesBox.isSelected()));
        showAnimations();
    }

    private void showAnimations() {
        boolean animations = preferences.animations();
        animationsSwitch.setSelected(animations);
        animateViewMovesBox.setSelected(preferences.animateViewMoves());
        animateViewMovesBox.setEnabled(animations);
    }

    /**
     * «Editor externo» (memory spec §8.2): the command that opens a memory's linked file, stored on Enter and when the
     * field loses the focus; empty means the system's editor.
     */
    private void wireExternalEditor() {
        externalEditorField.setText(preferences.externalEditor().orElse(""));
        Runnable store = () -> preferences.setExternalEditor(Optional.of(externalEditorField.getText().strip())
                .filter(command -> !command.isEmpty()));
        externalEditorField.addActionListener(event -> store.run());
        externalEditorField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                store.run();
            }
        });
    }

    /**
     * «Letra de etiquetas y textos» (M6.8): the family («Predeterminada» = SansSerif first, then the installed ones)
     * and the size, stored at once. A stored family that is not installed here is listed under its own name, so
     * opening the form never changes it.
     */
    private void wireTextFont() {
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
        selectFamily(stored.family());
        fontSizeSpinner.setModel(new SpinnerNumberModel(stored.size(), TextFont.MINIMUM_SIZE, TextFont.MAXIMUM_SIZE,
                1));
        fontFamilyChoice.addActionListener(event -> storeTextFont());
        fontSizeSpinner.addChangeListener(event -> storeTextFont());
    }

    /**
     * The interface's size («Tamaño de la interfaz», 100 % to 200 %), its font («Predeterminada» = the look and feel's
     * own, then the installed families; a stored family not installed here is listed under its own name) and its size
     * in «Modo presentación» («Sin cambio» or a size). Stored at once; the theme manager applies them live.
     */
    private void wireInterface() {
        for (int percent : AppPreferences.INTERFACE_ZOOMS) {
            interfaceZoomChoice.addItem(new Choice<>(percent, percentText(percent)));
        }
        presentationZoomChoice.addItem(new Choice<>(AppPreferences.PRESENTATION_ZOOM_UNCHANGED,
                Messages.text("preferences.presentationZoom.unchanged")));
        for (int percent : AppPreferences.INTERFACE_ZOOMS) {
            if (percent > 100) {
                presentationZoomChoice.addItem(new Choice<>(percent, percentText(percent)));
            }
        }
        Optional<String> storedFamily = preferences.interfaceFontFamily();
        interfaceFontChoice.addItem(new Choice<>(Optional.empty(), Messages.text("preferences.interfaceFont.default")));
        storedFamily.filter(family -> !TextFont.isInstalled(family))
                .ifPresent(family -> interfaceFontChoice.addItem(new Choice<>(Optional.of(family), family)));
        for (String family : TextFont.installedFamilies()) {
            interfaceFontChoice.addItem(new Choice<>(Optional.of(family), family));
        }
        select(interfaceZoomChoice, preferences.interfaceZoom());
        select(presentationZoomChoice, preferences.presentationInterfaceZoom());
        for (int index = 0; index < interfaceFontChoice.getItemCount(); index++) {
            Optional<String> listed = interfaceFontChoice.getItemAt(index).value();
            if (listed.isEmpty() ? storedFamily.isEmpty()
                    : storedFamily.filter(family -> family.equalsIgnoreCase(listed.get())).isPresent()) {
                interfaceFontChoice.setSelectedIndex(index);
                break;
            }
        }
        storeWhenChosen(interfaceZoomChoice, () -> preferences.setInterfaceZoom(selected(interfaceZoomChoice)));
        storeWhenChosen(interfaceFontChoice,
                () -> preferences.setInterfaceFontFamily(selected(interfaceFontChoice)));
        storeWhenChosen(presentationZoomChoice,
                () -> preferences.setPresentationInterfaceZoom(selected(presentationZoomChoice)));
    }

    /**
     * Stores {@code combo}'s choice once it is made, not on each entry the arrow keys pass through with the list open:
     * every stored size or font reinstalls the look and feel of every window. With the list closed, a change stores at
     * once; with it open, it stores when the list closes, after the combo's own dispatch.
     */
    static void storeWhenChosen(JComboBox<?> combo, Runnable store) {
        combo.addActionListener(event -> {
            if (!combo.isPopupVisible()) {
                store.run();
            }
        });
        combo.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                SwingUtilities.invokeLater(store);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent event) {
            }
        });
    }

    private static String percentText(int percent) {
        return Messages.format("preferences.interfaceZoom.percent", percent);
    }

    /** Selects the entry of {@code family}, whatever its case («dejavu sans» stored, «DejaVu Sans» listed). */
    private void selectFamily(String family) {
        for (int index = 0; index < fontFamilyChoice.getItemCount(); index++) {
            if (fontFamilyChoice.getItemAt(index).value().equalsIgnoreCase(family)) {
                fontFamilyChoice.setSelectedIndex(index);
                return;
            }
        }
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
        if (!schemeEditorOpener.open(appearance, editor)) {
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

    /**
     * «Borrar»: after a yes, removes the selected custom scheme; when it was in use, the choice goes back to «Según el
     * tema».
     */
    private void deleteScheme() {
        selectedCustomScheme().filter(custom -> deleteConfirmer.confirm(appearance, custom.name())).ifPresent(custom -> {
            customSchemes.delete(custom.id());
            refreshSchemes();
        });
    }

    private static boolean confirmDelete(Component parent, String schemeName) {
        return JOptionPane.showConfirmDialog(parent, Messages.format("preferences.scheme.deleteConfirm", schemeName),
                Messages.text("preferences.scheme.delete"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
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

    JComboBox<Choice<Integer>> interfaceZoomChoice() {
        return interfaceZoomChoice;
    }

    JComboBox<Choice<Optional<String>>> interfaceFontChoice() {
        return interfaceFontChoice;
    }

    JComboBox<Choice<Integer>> presentationZoomChoice() {
        return presentationZoomChoice;
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

    JTextField externalEditorField() {
        return externalEditorField;
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

    JCheckBox showTipAtStartBox() {
        return showTipAtStartBox;
    }

    /** Stops following the preferences: the dialog closed. */
    public void dispose() {
        preferences.removeListener(preferenceListener);
    }

    /**
     * Shows the options that can change elsewhere while the dialog is open (setSelected fires no action): the find
     * bar's ↷, the tip of the day's «Mostrar al iniciar», the lens's shape and size.
     */
    private void followPreferences() {
        showAnimations();
        showLens();
        showTipAtStartBox.setSelected(preferences.showTipAtStart());
    }

    private static String lensShapeName(AppPreferences.LensShape shape) {
        return switch (shape) {
            case ROUND -> Messages.text("preferences.lens.shape.ROUND");
            case RECTANGLE -> Messages.text("preferences.lens.shape.RECTANGLE");
        };
    }

    private static String lensSizeName(AppPreferences.LensSize size) {
        return switch (size) {
            case SMALL -> Messages.text("preferences.lens.size.SMALL");
            case MEDIUM -> Messages.text("preferences.lens.size.MEDIUM");
            case LARGE -> Messages.text("preferences.lens.size.LARGE");
        };
    }

    private void showLens() {
        select(lensShapeChoice, preferences.lensShape());
        select(lensSizeChoice, preferences.lensSize());
    }

    JComboBox<Choice<AppPreferences.LensShape>> lensShapeChoice() {
        return lensShapeChoice;
    }

    JComboBox<Choice<AppPreferences.LensSize>> lensSizeChoice() {
        return lensSizeChoice;
    }

    SwitchBox animationsSwitch() {
        return animationsSwitch;
    }

    JCheckBox animateViewMovesBox() {
        return animateViewMovesBox;
    }

    static String themeName(AppPreferences.Theme theme) {
        return switch (theme) {
            case LIGHT -> Messages.text("preferences.theme.LIGHT");
            case DARK -> Messages.text("preferences.theme.DARK");
            case SYSTEM -> Messages.text("preferences.theme.SYSTEM");
        };
    }

    private void chooseLibraryDirectory() {
        directoryChooser.choose(files, preferences.libraryDirectory()).ifPresent(directory -> {
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
