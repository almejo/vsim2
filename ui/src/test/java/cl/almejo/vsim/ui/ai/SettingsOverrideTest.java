package cl.almejo.vsim.ui.ai;

import cl.almejo.vsim.automation.AutomationError;
import cl.almejo.vsim.automation.AutomationException;
import cl.almejo.vsim.automation.EditorSetting;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.WindowCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsOverrideTest {

    private final AppPreferences saved = AppPreferences.inMemory();
    private final AppPreferences aiWindow = saved.layered();
    private final AppPreferences otherWindow = saved.layered();
    private final SettingsOverride override = SettingsOverride.of(aiWindow).orElseThrow();

    @TempDir
    Path directory;

    @BeforeEach
    void spanish() {
        Messages.initialize(Messages.SPANISH);
    }

    @Test
    void theSavedPreferencesHaveNoOverride() {
        assertThat(SettingsOverride.of(saved)).isEmpty();
    }

    /** The AI's pace and pause for its task (vsim_set_presentation): this window only, given back at the end. */
    @Test
    void thePaceAndPauseOfTheAiAreLayeredForTheTaskAndGivenBack() {
        override.layerPresentation(Optional.of(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS),
                Optional.of(java.time.Duration.ofMillis(2000)));

        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS);
        assertThat(aiWindow.aiPauseMillis()).isEqualTo(2000);
        assertThat(saved.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        assertThat(otherWindow.aiPauseMillis()).isEqualTo(1000);
        assertThat(override.changes()).isEmpty(); // not an editor setting in «Ajustes que tocó la IA»

        override.drop();

        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        assertThat(aiWindow.aiPauseMillis()).isEqualTo(1000);
    }

    /** Review M3: «Mantener» / «Volver a los míos» are about the editor settings; the AI's pace stays for the task. */
    @Test
    void keepingOrGivingBackTheEditorSettingsLeavesTheAisPaceForTheTask() {
        override.layerPresentation(Optional.of(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS),
                Optional.of(java.time.Duration.ofMillis(2000)));
        override.set(EditorSetting.SHOW_GUIDES, "false");

        override.dropEditorSettings();

        assertThat(aiWindow.showGuides()).isTrue();
        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS);
        override.set(EditorSetting.SHOW_GUIDES, "false");

        override.keep();

        assertThat(saved.showGuides()).isFalse();
        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS);
        assertThat(aiWindow.aiPauseMillis()).isEqualTo(2000);
        assertThat(saved.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        override.drop();
        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
    }

    /** Review M4: the pace the user picks in a second window of the circuit wins over the AI's in the first. */
    @Test
    void thePaceTheUserPicksInAnyWindowReplacesTheAisInEveryWindow() {
        override.layerPresentation(Optional.of(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS),
                Optional.of(java.time.Duration.ofMillis(2000)));
        List<String> heard = new ArrayList<>();
        saved.addListener(() -> heard.add("changed"));

        otherWindow.setAiPace(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        otherWindow.setAiPauseMillis(500);

        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        assertThat(aiWindow.aiPauseMillis()).isEqualTo(500);
        assertThat(heard).isNotEmpty();
    }

    @Test
    void theUsersOwnPaceInThatWindowReplacesTheAisAndIsSaved() {
        override.layerPresentation(Optional.of(cl.almejo.vsim.automation.present.PresentationPace.CONTINUOUS),
                Optional.empty());

        aiWindow.setAiPace(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        override.drop();

        assertThat(saved.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
        assertThat(aiWindow.aiPace()).isEqualTo(cl.almejo.vsim.automation.present.PresentationPace.STEP_BY_STEP);
    }

    @Test
    void anOverrideChangesOneWindowOnlyAndNeverWritesThePreferences() {
        AtomicInteger heard = new AtomicInteger();
        aiWindow.addListener(heard::incrementAndGet);

        override.set(EditorSetting.SHOW_GUIDES, "false");
        override.set(EditorSetting.GRID_STYLE, "LINES");
        override.set(EditorSetting.GRID_SPACING, "4");
        override.set(EditorSetting.SNAP, "false");
        override.set(EditorSetting.SNAP_TO_PARTS, "false");
        override.set(EditorSetting.SNAP_TO_GUIDES, "false");
        override.set(EditorSetting.SNAP_TO_GRID_LINES, "true");
        override.set(EditorSetting.WIRES_FOLLOW, "false");

        assertThat(aiWindow.showGuides()).isFalse();
        assertThat(aiWindow.gridStyle()).isEqualTo(AppPreferences.GridStyle.LINES);
        assertThat(aiWindow.gridLineSpacing()).isEqualTo(4);
        assertThat(aiWindow.snapToAlignments()).isFalse();
        assertThat(aiWindow.snapToParts()).isFalse();
        assertThat(aiWindow.snapToGuides()).isFalse();
        assertThat(aiWindow.snapToGridLines()).isTrue();
        assertThat(aiWindow.wiresFollow()).isFalse();
        for (AppPreferences untouched : List.of(saved, otherWindow)) {
            assertThat(untouched.showGuides()).isTrue();
            assertThat(untouched.gridStyle()).isEqualTo(AppPreferences.GridStyle.DOTS);
            assertThat(untouched.gridLineSpacing()).isEqualTo(AppPreferences.DEFAULT_GRID_LINE_SPACING);
            assertThat(untouched.snapToAlignments()).isTrue();
            assertThat(untouched.snapToGridLines()).isFalse();
            assertThat(untouched.wiresFollow()).isTrue();
        }
        assertThat(heard).hasValue(8);
        assertThat(override.changes()).extracting(SettingsOverride.Change::setting).containsExactly(
                EditorSetting.SHOW_GUIDES, EditorSetting.GRID_STYLE, EditorSetting.GRID_SPACING, EditorSetting.SNAP,
                EditorSetting.SNAP_TO_PARTS, EditorSetting.SNAP_TO_GUIDES, EditorSetting.SNAP_TO_GRID_LINES,
                EditorSetting.WIRES_FOLLOW);
    }

    @Test
    void droppingTheOverrideBringsBackTheSavedValues() {
        saved.setGridStyle(AppPreferences.GridStyle.NONE);
        override.set(EditorSetting.GRID_STYLE, "LINES");
        override.set(EditorSetting.SHOW_GUIDES, "false");

        override.drop();

        assertThat(aiWindow.gridStyle()).isEqualTo(AppPreferences.GridStyle.NONE);
        assertThat(aiWindow.showGuides()).isTrue();
        assertThat(override.changes()).isEmpty();
        assertThat(override.isEmpty()).isTrue();
    }

    @Test
    void keepingWritesTheValuesAsPreferencesForEveryWindow() {
        override.set(EditorSetting.GRID_STYLE, "LINES");
        override.set(EditorSetting.WIRES_FOLLOW, "false");

        override.keep();

        assertThat(override.isEmpty()).isTrue();
        assertThat(saved.gridStyle()).isEqualTo(AppPreferences.GridStyle.LINES);
        assertThat(otherWindow.wiresFollow()).isFalse();
        assertThat(aiWindow.gridStyle()).isEqualTo(AppPreferences.GridStyle.LINES);
    }

    @Test
    void aValueThatIsNotAllowedIsRejectedAndChangesNothing() {
        for (String wrong : List.of("maybe", "", "TRUE ")) {
            assertThatThrownBy(() -> override.set(EditorSetting.SHOW_GUIDES, wrong))
                    .isInstanceOfSatisfying(AutomationException.class, exception ->
                            assertThat(exception.error()).isEqualTo(AutomationError.INVALID_PARAMETER));
        }
        assertThatThrownBy(() -> override.set(EditorSetting.GRID_SPACING, "3"))
                .isInstanceOf(AutomationException.class);
        assertThatThrownBy(() -> override.set(EditorSetting.GRID_STYLE, "lines"))
                .isInstanceOf(AutomationException.class);

        assertThat(override.changes()).isEmpty();
    }

    @Test
    void theAllowedSpacingsAreTheOnesTheGridMenuOffers() {
        assertThat(EditorSetting.GRID_SPACING.allowedValues())
                .containsExactlyElementsOf(AppPreferences.GRID_LINE_SPACINGS.stream().map(String::valueOf).toList());
        assertThat(EditorSetting.GRID_STYLE.allowedValues()).containsExactlyElementsOf(
                java.util.Arrays.stream(AppPreferences.GridStyle.values()).map(Enum::name).toList());
    }

    @Test
    void theUsersOwnChoiceInThatWindowReplacesTheAiValueAndIsSaved() {
        override.set(EditorSetting.SHOW_GUIDES, "false");

        aiWindow.setShowGuides(true); // the user turns the guides back on in the AI's window

        assertThat(aiWindow.showGuides()).isTrue();
        assertThat(override.changes()).isEmpty();
        assertThat(saved.showGuides()).isTrue();

        override.set(EditorSetting.SHOW_GUIDES, "false");
        aiWindow.setShowGuides(!aiWindow.showGuides()); // the menu's toggle
        assertThat(saved.showGuides()).isTrue();
        assertThat(aiWindow.showGuides()).isTrue();
    }

    @Test
    void theWindowsCanvasFollowsItsOverrideAndTheOtherWindowDoesNot() {
        LibraryService libraryService = new LibraryService(saved, new ComponentLibrary(),
                StandardComponentTypes.registry(), Runnable::run);
        saved.setLibraryDirectory(Optional.of(directory.resolve("library")));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                saved, new ComponentLibrary(), Application.editorExtensions(libraryService,
                SwingLibraryDialogs.INSTANCE));
        OpenDocument document = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorView aiView = new EditorView(document, services.withPreferences(aiWindow), NO_WINDOW);
        EditorView otherView = new EditorView(document, services.withPreferences(otherWindow), NO_WINDOW);
        try {
            override.set(EditorSetting.GRID_STYLE, "LINES");

            assertThat(aiView.canvas().gridStyle()).isEqualTo(AppPreferences.GridStyle.LINES);
            assertThat(otherView.canvas().gridStyle()).isEqualTo(AppPreferences.GridStyle.DOTS);

            override.drop();

            assertThat(aiView.canvas().gridStyle()).isEqualTo(AppPreferences.GridStyle.DOTS);
        } finally {
            aiView.dispose();
            otherView.dispose();
            document.close();
            libraryService.close();
        }
    }

    private static final WindowCommands NO_WINDOW = new WindowCommands() {
        @Override public void newDocument() { }
        @Override public void open() { }
        @Override public void save() { }
        @Override public void saveAs() { }
        @Override public void closeWindow() { }
        @Override public void exit() { }
    };
}
