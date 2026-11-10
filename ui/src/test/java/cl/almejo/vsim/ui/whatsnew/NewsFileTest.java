package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Application;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.library.LibraryService;
import cl.almejo.vsim.ui.library.SwingLibraryDialogs;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Action;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.ResourceBundle;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Each release lists its bubbles in a file: target, title, text and the action whose key or place they show. */
class NewsFileTest {

    @TempDir
    Path directory;

    @Test
    void aFileListsItsBubblesInOrderSkippingCommentsAndBlankLines() {
        NewsFile news = NewsFile.parse("0.8", List.of(
                "# a comment",
                "",
                "toolbar.select | whatsNew.wires.title | whatsNew.wires.text | -",
                "toolbar.capture | whatsNew.capture.title | whatsNew.capture.text | edit.copyCapture",
                "menu.file | whatsNew.animations.title | whatsNew.animations.text | file.preferences#0"));

        assertThat(news.version()).isEqualTo("0.8");
        assertThat(news.steps()).extracting(NewsStep::targetId)
                .containsExactly("toolbar.select", "toolbar.capture", "menu.file");
        assertThat(news.steps().get(0).action()).isEmpty();
        assertThat(news.steps().get(1).action())
                .contains(new NewsStep.ActionReference("edit.copyCapture", OptionalInt.empty()));
        assertThat(news.steps().get(2).action())
                .contains(new NewsStep.ActionReference("file.preferences", OptionalInt.of(0)));
    }

    @Test
    void aBrokenLineSaysWhichLineItIs() {
        assertThatThrownBy(() -> NewsFile.parse("0.8", List.of("# ok", "toolbar.select | only.two")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void keysInsideATextAreCutOutOfIt() {
        assertThat(NewsText.parse("{space} cambia el codo y {escape} cancela."))
                .containsExactly(new NewsText.Key("space"), new NewsText.Plain(" cambia el codo y "),
                        new NewsText.Key("escape"), new NewsText.Plain(" cancela."));
    }

    @Test
    void theRunningVersionHasItsNewsOrADevelopmentBuildShowsTheNextReleasesOnes() {
        assertThat(NewsFiles.forRunning("0.8")).map(NewsFile::version).contains("0.8");
        assertThat(NewsFiles.forRunning("0.7")).map(NewsFile::version).contains("0.8");
        assertThat(NewsFiles.forRunning("0.1")).isEmpty();
        assertThat(NewsFiles.forRunning("unknown")).isEmpty();
    }

    @Test
    void everyNewsFileNamesRegisteredTargetsKeysOfBothBundlesAndKnownKeys() {
        Messages.initialize(Messages.SPANISH);
        AppPreferences preferences = AppPreferences.inMemory();
        preferences.setLibraryDirectory(Optional.of(directory.resolve("library")));
        ComponentLibrary library = new ComponentLibrary();
        LibraryService service = new LibraryService(preferences, library, StandardComponentTypes.registry(),
                Runnable::run);
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, library, Application.editorExtensions(service, SwingLibraryDialogs.INSTANCE));
        OpenDocument openDocument = OpenDocument.createEmpty(StandardComponentTypes.registry());
        EditorView view = new EditorView(openDocument, services, NO_WINDOW);
        try {
            NewsFile news = NewsFiles.load("0.8").orElseThrow();
            assertThat(news.steps()).hasSize(6);
            for (NewsStep step : news.steps()) {
                assertThat(NewsTargets.find(view, step.targetId())).as(step.targetId()).isPresent();
                for (Locale locale : List.of(Locale.forLanguageTag("es"), Locale.ENGLISH)) {
                    ResourceBundle bundle = Messages.bundle(locale);
                    assertThat(bundle.containsKey(step.titleKey())).as(locale + " " + step.titleKey()).isTrue();
                    assertThat(bundle.containsKey(step.textKey())).as(locale + " " + step.textKey()).isTrue();
                    NewsText.parse(bundle.getString(step.textKey())).stream()
                            .filter(NewsText.Key.class::isInstance).map(NewsText.Key.class::cast)
                            .forEach(key -> assertThat(NewsText.isKnown(key.name()))
                                    .as(locale + " " + step.textKey() + " {" + key.name() + "}").isTrue());
                }
                step.action().ifPresent(reference -> {
                    assertThat(ShortcutCatalog.find(reference.shortcutId())).as(reference.shortcutId()).isPresent();
                    Action action = view.shortcutActions().get(reference.shortcutId());
                    assertThat(action).as(reference.shortcutId()).isNotNull();
                    reference.paletteEntry().ifPresent(entry -> assertThat(
                            action.getValue(Actions.PALETTE_ENTRIES) instanceof List<?> entries
                                    && entry < entries.size()).as(reference.shortcutId() + "#" + entry).isTrue());
                });
            }
        } finally {
            view.dispose();
            openDocument.close();
            service.close();
        }
    }
}
