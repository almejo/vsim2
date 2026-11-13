package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorRole;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ColorSchemes;
import cl.almejo.vsim.ui.paint.CustomColorSchemes;
import cl.almejo.vsim.ui.paint.NamedColorScheme;
import cl.almejo.vsim.ui.paint.TextFont;
import cl.almejo.vsim.ui.theme.SwitchBox;
import cl.almejo.vsim.ui.theme.ThemeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComboBox;
import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class PreferencesFormTest {

    /** «Según el tema» and every built-in scheme: the scheme choice without custom schemes. */
    private static final int BUILT_IN_CHOICES = 1 + ColorSchemes.ids().size();

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final List<Path> offeredDirectories = new ArrayList<>();
    private Optional<Path> chooserAnswer = Optional.empty();

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
    void theFourPagesHoldTheRowsOfTheGroupsUnderTheirSectionsInOrder() {
        PreferencesForm form = form();

        assertThat(form.general().sectionRows()).containsExactly(
                Map.entry("preferences.section.language", List.of("language")),
                Map.entry("preferences.section.startup", List.of("restoreSession", "showTipAtStart")),
                Map.entry("preferences.section.editing", List.of("invertModifier")));
        assertThat(form.appearance().sectionRows()).containsExactly(
                Map.entry("preferences.section.window",
                        List.of("theme", "interfaceZoom", "interfaceFont", "presentationZoom")),
                Map.entry("preferences.section.circuit", List.of("scheme", "textFont")));
        assertThat(form.accessibility().sectionRows()).containsExactly(
                Map.entry("preferences.section.motion", List.of("animations", "animateViewMoves")),
                Map.entry("preferences.section.lens", List.of("lensShape", "lensSize")));
        assertThat(form.files().sectionRows()).containsExactly(
                Map.entry("preferences.section.library", List.of("library")),
                Map.entry("preferences.section.memories", List.of("externalEditor")));
    }

    @Test
    void searchingFindsTheOptionsByTheWordsOfTheirValuesAndKeywords() {
        PreferencesForm form = form();

        assertThat(form.appearance().filter("oscuro")).isEqualTo(1);
        assertThat(form.appearance().visibleRowIds()).containsExactly("theme");
        assertThat(form.files().filter("carpeta")).isEqualTo(1);
        assertThat(form.files().visibleRowIds()).containsExactly("library");
        assertThat(form.general().filter("mayús")).isEqualTo(1);
        assertThat(form.general().visibleRowIds()).containsExactly("invertModifier");
        assertThat(form.accessibility().filter("movimiento")).isEqualTo(2);
        assertThat(form.accessibility().filter("rectangular")).isEqualTo(1);
        assertThat(form.accessibility().visibleRowIds()).containsExactly("lensShape");
        assertThat(form.accessibility().filter("grande")).isEqualTo(1);
        assertThat(form.accessibility().visibleRowIds()).containsExactly("lensSize");
    }

    @Test
    void theLensRowsStoreTheChoiceAndFollowChangesMadeElsewhere() {
        PreferencesForm form = form();
        assertThat(form.lensShapeChoice().getItemCount()).isEqualTo(2);
        assertThat(form.lensSizeChoice().getItemCount()).isEqualTo(3);
        assertThat(selected(form.lensShapeChoice()))
                .isEqualTo(AppPreferences.LensShape.ROUND);
        assertThat(selected(form.lensSizeChoice()))
                .isEqualTo(AppPreferences.LensSize.MEDIUM);

        choose(form.lensShapeChoice(), AppPreferences.LensShape.RECTANGLE);
        choose(form.lensSizeChoice(), AppPreferences.LensSize.SMALL);
        assertThat(preferences.lensShape()).isEqualTo(AppPreferences.LensShape.RECTANGLE);
        assertThat(preferences.lensSize()).isEqualTo(AppPreferences.LensSize.SMALL);

        preferences.setLensSize(AppPreferences.LensSize.LARGE);
        assertThat(selected(form.lensSizeChoice()))
                .isEqualTo(AppPreferences.LensSize.LARGE);
        form.dispose();
    }

    @Test
    void liveChangesStillShowWhileAFilterHidesRows() {
        PreferencesForm form = form();
        form.accessibility().filter("vista");

        preferences.setAnimations(false);

        assertThat(form.accessibility().visibleRowIds()).containsExactly("animateViewMoves");
        assertThat(form.animateViewMovesBox().isEnabled()).isFalse();
        form.dispose();
    }

    @Test
    void theExternalEditorCommandRoundTripsAndEmptyMeansTheSystems() {
        preferences.setExternalEditor(Optional.of("gedit"));
        PreferencesForm form = form();
        assertThat(form.externalEditorField().getText()).isEqualTo("gedit");

        form.externalEditorField().setText("  code --wait ");
        form.externalEditorField().postActionEvent();
        assertThat(preferences.externalEditor()).contains("code --wait");
        assertThat(form().externalEditorField().getText()).isEqualTo("code --wait");

        form.externalEditorField().setText(" ");
        form.externalEditorField().postActionEvent();
        assertThat(preferences.externalEditor()).isEmpty();
    }

    @Test
    void choicesAreNamedInSpanish() {
        PreferencesForm form = form();
        assertThat(labels(form.themeChoice())).containsExactly("Seguir sistema", "Claro", "Oscuro");
        assertThat(labels(form.schemeChoice())).containsExactly("Según el tema", "VSim clásico", "Claro", "Oscuro",
                "Pastel (VSim)", "CSim (VSim)", "Twilight (VSim)", "Placa", "Logisim", "Chipmunk");
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

    @Test
    void theTipOfTheDayBoxIsTheCardsMostrarAlIniciarBothWays() {
        PreferencesForm form = form();
        assertThat(form.showTipAtStartBox().getText()).isEqualTo("Mostrar el consejo del día al iniciar");
        assertThat(form.showTipAtStartBox().isSelected()).isTrue();

        form.showTipAtStartBox().doClick(0);
        assertThat(preferences.showTipAtStart()).isFalse();

        preferences.setShowTipAtStart(true); // the card's «Mostrar al iniciar», with the dialog open
        assertThat(form.showTipAtStartBox().isSelected()).isTrue();
    }

    @Test
    void theAnimateViewMovesBoxShowsAndChangesThePreference() {
        PreferencesForm form = form();
        assertThat(form.animateViewMovesBox().getText()).isEqualTo("Animar los movimientos de la vista");
        assertThat(form.animateViewMovesBox().isSelected()).isTrue();

        form.animateViewMovesBox().doClick(0);
        assertThat(preferences.animateViewMoves()).isFalse();
        assertThat(form().animateViewMovesBox().isSelected()).isFalse();
    }

    @Test
    void animationsAreASwitchOnSiByDefaultAndNoTurnsThemOffAndDisablesTheViewMovesBox() {
        PreferencesForm form = form();
        SwitchBox animations = form.animationsSwitch();
        assertThat(animations).as("the same switch as the boolean properties").isInstanceOf(SwitchBox.class);
        assertThat(animations.isSelected()).isTrue();
        assertThat(animations.getText()).isEqualTo("Sí");
        assertThat(form.animateViewMovesBox().isEnabled()).isTrue();

        animations.doClick(0);

        assertThat(preferences.animations()).isFalse();
        assertThat(animations.getText()).isEqualTo("No");
        assertThat(form.animateViewMovesBox().isEnabled()).isFalse();
        assertThat(form.animateViewMovesBox().isSelected()).as("the choice is kept for later").isTrue();
        assertThat(form().animationsSwitch().isSelected()).isFalse();
        assertThat(form().animateViewMovesBox().isEnabled()).isFalse();

        animations.doClick(0);

        assertThat(preferences.animations()).isTrue();
        assertThat(animations.getText()).isEqualTo("Sí");
        assertThat(form.animateViewMovesBox().isEnabled()).isTrue();
    }

    @Test
    void theAnimationsChoiceFollowsChangesMadeElsewhere() {
        PreferencesForm form = form();

        preferences.setAnimations(false);

        assertThat(form.animationsSwitch().isSelected()).isFalse();
        assertThat(form.animationsSwitch().getText()).isEqualTo("No");
        assertThat(form.animateViewMovesBox().isEnabled()).isFalse();
        preferences.setAnimations(true);
        assertThat(form.animationsSwitch().isSelected()).isTrue();
        assertThat(form.animateViewMovesBox().isEnabled()).isTrue();
    }

    @Test
    void theAnimateViewMovesBoxFollowsChangesMadeElsewhereUntilDisposed() {
        int listeners = preferences.listenerCount();
        PreferencesForm form = form();

        preferences.setAnimateViewMoves(false); // the find bar's ↷ while the dialog is open
        assertThat(form.animateViewMovesBox().isSelected()).isFalse();
        preferences.setAnimateViewMoves(true);
        assertThat(form.animateViewMovesBox().isSelected()).isTrue();

        form.dispose();
        assertThat(preferences.listenerCount()).isEqualTo(listeners);
    }

    @Test
    void theInvertKeyComboOffersShiftAltAndNoneAndSavesTheChoice() {
        PreferencesForm form = form();
        assertThat(labels(form.invertModifierChoice())).containsExactly("Mayús", "Alt", "Ninguna");
        assertThat(selected(form.invertModifierChoice())).isEqualTo(AppPreferences.InvertModifier.SHIFT);

        choose(form.invertModifierChoice(), AppPreferences.InvertModifier.ALT);
        assertThat(preferences.invertModifier()).isEqualTo(AppPreferences.InvertModifier.ALT);

        preferences.setInvertModifier(AppPreferences.InvertModifier.NONE);
        assertThat(selected(form().invertModifierChoice())).isEqualTo(AppPreferences.InvertModifier.NONE);
    }

    @Test
    void theInterfaceRowsShowTheDefaultsAndStoreEachChoiceAtOnce() {
        PreferencesForm form = form();
        JComboBox<PreferencesForm.Choice<Integer>> zoom = form.interfaceZoomChoice();
        JComboBox<PreferencesForm.Choice<Optional<String>>> font = form.interfaceFontChoice();
        JComboBox<PreferencesForm.Choice<Integer>> presentation = form.presentationZoomChoice();

        assertThat(labels(zoom)).containsExactly("100 %", "125 %", "150 %", "175 %", "200 %");
        assertThat(((PreferencesForm.Choice<?>) zoom.getSelectedItem()).value()).isEqualTo(100);
        assertThat(font.getItemAt(0).label()).isEqualTo("Predeterminada");
        assertThat(font.getSelectedIndex()).isZero();
        assertThat(labels(presentation)).containsExactly("Sin cambio", "125 %", "150 %", "175 %", "200 %");
        assertThat(((PreferencesForm.Choice<?>) presentation.getSelectedItem()).value()).isEqualTo(150);

        choose(zoom, 175);
        choose(presentation, AppPreferences.PRESENTATION_ZOOM_UNCHANGED);
        font.setSelectedIndex(font.getItemCount() - 1);

        assertThat(preferences.interfaceZoom()).isEqualTo(175);
        assertThat(preferences.presentationInterfaceZoom()).isEqualTo(AppPreferences.PRESENTATION_ZOOM_UNCHANGED);
        assertThat(preferences.interfaceFontFamily()).isEqualTo(font.getItemAt(font.getItemCount() - 1).value());
    }

    @Test
    void aStoredInterfaceFontThatIsNotInstalledIsShownAndKept() {
        preferences.setInterfaceFontFamily(Optional.of("Fuente Que No Existe"));
        PreferencesForm form = form();
        JComboBox<PreferencesForm.Choice<Optional<String>>> font = form.interfaceFontChoice();

        assertThat(((PreferencesForm.Choice<?>) font.getSelectedItem()).label()).isEqualTo("Fuente Que No Existe");
        assertThat(preferences.interfaceFontFamily()).contains("Fuente Que No Existe");
    }


    @Test
    void theTextFontListsTheDefaultFirstAndSavesTheFamilyAndTheSize() {
        PreferencesForm form = form();
        assertThat(labels(form.fontFamilyChoice()).get(0)).isEqualTo("Predeterminada");
        assertThat(form.fontFamilyChoice().getItemAt(0).value()).isEqualTo(Font.SANS_SERIF);
        assertThat(labels(form.fontFamilyChoice())).contains(Font.SERIF).doesNotContain(Font.SANS_SERIF);
        assertThat(selected(form.fontFamilyChoice())).isEqualTo(Font.SANS_SERIF);
        assertThat(form.fontSizeSpinner().getValue()).isEqualTo(10);

        choose(form.fontFamilyChoice(), Font.SERIF);
        form.fontSizeSpinner().setValue(14);

        assertThat(preferences.textFont()).isEqualTo(new TextFont(Font.SERIF, 14));
        assertThat(form.fontSizeSpinner().getValue()).isEqualTo(14);
    }

    @Test
    void theSizeStaysBetweenEightAndTwentyFour() {
        PreferencesForm form = form();
        javax.swing.SpinnerNumberModel model = (javax.swing.SpinnerNumberModel) form.fontSizeSpinner().getModel();
        assertThat(model.getMinimum()).isEqualTo(TextFont.MINIMUM_SIZE);
        assertThat(model.getMaximum()).isEqualTo(TextFont.MAXIMUM_SIZE);
    }

    @Test
    void aStoredFamilyInAnotherCaseIsShownUnderItsInstalledNameAndKeptOnASizeChange() {
        preferences.setTextFont(new TextFont("serif", 12));
        PreferencesForm form = form();
        assertThat(form.fontFamilyChoice().getSelectedItem().toString()).isEqualTo(Font.SERIF);
        assertThat(labels(form.fontFamilyChoice())).doesNotContain("serif");
        form.fontSizeSpinner().setValue(13);
        assertThat(preferences.textFont().family()).isEqualToIgnoringCase(Font.SERIF);
        assertThat(preferences.textFont().size()).isEqualTo(13);
    }

    @Test
    void aStoredFamilyThatIsNotInstalledIsShownAndKept() {
        preferences.setTextFont(new TextFont("Fuente De Otro Computador", 12));
        PreferencesForm form = form();
        assertThat(selected(form.fontFamilyChoice())).isEqualTo("Fuente De Otro Computador");
        assertThat(form.fontSizeSpinner().getValue()).isEqualTo(12);
        form.fontSizeSpinner().setValue(13);
        assertThat(preferences.textFont()).isEqualTo(new TextFont("Fuente De Otro Computador", 13));
    }

    // --- M6.8 custom colour schemes ---

    private final List<SchemeEditorPanel> openedEditors = new ArrayList<>();
    private Consumer<SchemeEditorPanel> editing = panel -> { };
    private boolean editorSaves = true;
    private boolean themeIsDark;
    private final List<String> deleteQuestions = new ArrayList<>();
    private boolean deleteAnswer = true;

    private PreferencesForm schemeForm() {
        return new PreferencesForm(preferences, (parent, current) -> Optional.empty(), (parent, panel) -> {
            openedEditors.add(panel);
            editing.accept(panel);
            return editorSaves;
        }, (parent, title, initial) -> {
            throw new AssertionError("the colour chooser must not open");
        }, () -> themeIsDark, (parent, schemeName) -> {
            deleteQuestions.add(schemeName);
            return deleteAnswer;
        });
    }

    @Test
    void deleteAsksFirstAndANoKeepsTheScheme() {
        CustomColorSchemes customSchemes = new CustomColorSchemes(preferences);
        NamedColorScheme mine = customSchemes.save("Mío", ColorSchemes.CSIM);
        preferences.setColorScheme(Optional.of(mine.id()));
        PreferencesForm form = schemeForm();
        deleteAnswer = false;

        form.deleteSchemeButton().doClick(0);

        assertThat(deleteQuestions).containsExactly("Mío");
        assertThat(customSchemes.list()).containsExactly(mine);
        assertThat(preferences.colorScheme()).contains(mine.id());
        assertThat(selected(form.schemeChoice())).contains(mine.id());
        assertThat(form.deleteSchemeButton().isEnabled()).isTrue();
    }

    @Test
    void theDeleteQuestionNamesTheScheme() {
        assertThat(Messages.format("preferences.scheme.deleteConfirm", "Mío")).isEqualTo("¿Borrar el esquema «Mío»?");
    }

    @Test
    void customSchemesAreListedAfterTheBuiltInsInNameOrder() {
        CustomColorSchemes customSchemes = new CustomColorSchemes(preferences);
        customSchemes.save("Zafiro", ColorSchemes.DARK);
        NamedColorScheme amber = customSchemes.save("Ámbar", ColorSchemes.LIGHT);
        preferences.setColorScheme(Optional.of(amber.id()));
        PreferencesForm form = schemeForm();
        assertThat(labels(form.schemeChoice())).containsExactly("Según el tema", "VSim clásico", "Claro", "Oscuro",
                "Pastel (VSim)", "CSim (VSim)", "Twilight (VSim)", "Placa", "Logisim", "Chipmunk", "Zafiro", "Ámbar");
        assertThat(selected(form.schemeChoice())).contains(amber.id());
        assertThat(form.customizeSchemeButton().getText()).isEqualTo("Personalizar…");
        assertThat(form.deleteSchemeButton().getText()).isEqualTo("Borrar");
    }

    @Test
    void deleteIsEnabledOnlyForACustomScheme() {
        NamedColorScheme mine = new CustomColorSchemes(preferences).save("Mío", ColorSchemes.DARK);
        PreferencesForm form = schemeForm();
        assertThat(form.deleteSchemeButton().isEnabled()).isFalse();
        choose(form.schemeChoice(), Optional.of(ColorSchemes.CLASSIC_ID));
        assertThat(form.deleteSchemeButton().isEnabled()).isFalse();
        choose(form.schemeChoice(), Optional.of(mine.id()));
        assertThat(form.deleteSchemeButton().isEnabled()).isTrue();
        assertThat(form.customizeSchemeButton().isEnabled()).isTrue();
    }

    @Test
    void savingACopyOfDarkAddsItAndSelectsIt() {
        PreferencesForm form = schemeForm();
        choose(form.schemeChoice(), Optional.of(ColorSchemes.DARK_ID));
        editing = panel -> panel.setColor(ColorRole.WIRE_ONE, Color.MAGENTA);
        form.customizeSchemeButton().doClick(0);

        assertThat(openedEditors).hasSize(1);
        NamedColorScheme saved = new CustomColorSchemes(preferences).list().get(0);
        assertThat(saved.name()).isEqualTo("Oscuro (copia)");
        assertThat(saved.scheme()).isEqualTo(ColorSchemes.DARK.with(ColorRole.WIRE_ONE, Color.MAGENTA));
        assertThat(preferences.colorScheme()).contains(saved.id());
        assertThat(labels(form.schemeChoice())).endsWith("Oscuro (copia)");
        assertThat(selected(form.schemeChoice())).contains(saved.id());
        assertThat(form.deleteSchemeButton().isEnabled()).isTrue();
    }

    @Test
    void byThemeStartsFromTheSchemeTheThemeGives() {
        themeIsDark = true;
        PreferencesForm form = schemeForm();
        form.customizeSchemeButton().doClick(0);
        assertThat(openedEditors.get(0).name()).isEqualTo("Oscuro (copia)");
        assertThat(openedEditors.get(0).scheme()).isSameAs(ColorSchemes.DARK);

        themeIsDark = false;
        editorSaves = false;
        choose(form.schemeChoice(), Optional.empty());
        form.customizeSchemeButton().doClick(0);
        assertThat(openedEditors.get(1).name()).isEqualTo("Claro (copia)");
        assertThat(openedEditors.get(1).scheme()).isSameAs(ColorSchemes.LIGHT);
    }

    @Test
    void cancellingTheEditorStoresNothing() {
        PreferencesForm form = schemeForm();
        editorSaves = false;
        AtomicInteger changes = new AtomicInteger();
        preferences.addListener(changes::incrementAndGet);
        editing = panel -> panel.setColor(ColorRole.BACKGROUND, Color.RED);
        form.customizeSchemeButton().doClick(0);
        assertThat(changes).hasValue(0);
        assertThat(new CustomColorSchemes(preferences).list()).isEmpty();
        assertThat(form.schemeChoice().getItemCount()).isEqualTo(BUILT_IN_CHOICES);
    }

    @Test
    void editingTheActiveCustomSchemeSavesItInPlaceAndTheAppearanceFollowsLive() {
        CustomColorSchemes customSchemes = new CustomColorSchemes(preferences);
        NamedColorScheme mine = customSchemes.save("Mío", ColorSchemes.CSIM);
        preferences.setTheme(AppPreferences.Theme.LIGHT);
        preferences.setColorScheme(Optional.of(mine.id()));
        Appearance appearance = new Appearance(ColorSchemes.CLASSIC);
        ThemeManager manager = new ThemeManager(preferences, appearance, () -> false, dark -> true);
        manager.start();
        assertThat(appearance.scheme()).isEqualTo(ColorSchemes.CSIM);

        PreferencesForm form = schemeForm();
        editing = panel -> {
            panel.nameField().setText("Mío 2");
            panel.setColor(ColorRole.BACKGROUND, Color.ORANGE);
        };
        form.customizeSchemeButton().doClick(0);

        assertThat(openedEditors.get(0).name()).isEqualTo("Mío 2");
        ColorScheme edited = ColorSchemes.CSIM.with(ColorRole.BACKGROUND, Color.ORANGE);
        assertThat(customSchemes.list()).containsExactly(new NamedColorScheme(mine.id(), "Mío 2", edited));
        assertThat(appearance.scheme()).isEqualTo(edited);
        assertThat(labels(form.schemeChoice())).endsWith("Mío 2");
        assertThat(selected(form.schemeChoice())).contains(mine.id());
        manager.dispose();
    }

    @Test
    void deletingTheActiveSchemeSelectsByThemeAndTheAppearanceFollowsTheTheme() {
        CustomColorSchemes customSchemes = new CustomColorSchemes(preferences);
        NamedColorScheme mine = customSchemes.save("Mío", ColorSchemes.CSIM);
        preferences.setTheme(AppPreferences.Theme.DARK);
        preferences.setColorScheme(Optional.of(mine.id()));
        Appearance appearance = new Appearance(ColorSchemes.CLASSIC);
        ThemeManager manager = new ThemeManager(preferences, appearance, () -> false, dark -> true);
        manager.start();
        assertThat(appearance.scheme()).isEqualTo(ColorSchemes.CSIM);

        PreferencesForm form = schemeForm();
        form.deleteSchemeButton().doClick(0);

        assertThat(customSchemes.list()).isEmpty();
        assertThat(preferences.colorScheme()).isEmpty();
        assertThat(selected(form.schemeChoice())).isEmpty();
        assertThat(form.schemeChoice().getItemCount()).isEqualTo(BUILT_IN_CHOICES);
        assertThat(form.deleteSchemeButton().isEnabled()).isFalse();
        assertThat(appearance.scheme()).isSameAs(ColorSchemes.DARK);
        manager.dispose();
    }

    @Test
    void aStoredIdOfADeletedCustomSchemeShowsByTheme() {
        preferences.setColorScheme(Optional.of("custom-4"));
        PreferencesForm form = schemeForm();
        assertThat(selected(form.schemeChoice())).isEmpty();
        assertThat(form.deleteSchemeButton().isEnabled()).isFalse();
    }
}
