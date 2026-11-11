package cl.almejo.vsim.ui.whatsnew;

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
import cl.almejo.vsim.ui.shortcuts.KeyBindings;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.Action;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static cl.almejo.vsim.ui.UiTestSupport.NO_WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The tips of the day are listed in one file, in the order they are shown. */
class TipsFileTest {

    @TempDir
    Path directory;

    @Test
    void aFileListsItsTipsInOrderSkippingCommentsAndBlankLines() {
        List<Tip> tips = TipsFile.parse(List.of(
                "# a comment",
                "",
                "look-inside | circuit | tip.a.title | tip.a.text | look-inside.vsim | alt | circuit",
                "tidy | before-after | tip.b.title | tip.b.text | before.vsim, after.vsim | edit.tidyWires | "
                        + "circuit simulation.truthTable",
                "split-view | image | tip.c.title | tip.c.text | split-view.png | - | -"));

        assertThat(tips).extracting(Tip::id).containsExactly("look-inside", "tidy", "split-view");
        assertThat(tips).extracting(Tip::kind)
                .containsExactly(TipKind.CIRCUIT, TipKind.BEFORE_AFTER, TipKind.IMAGE);
        assertThat(tips.get(1).pictures()).containsExactly("before.vsim", "after.vsim");
        assertThat(tips.get(0).pictureKey()).contains("alt");
        assertThat(tips.get(2).pictureKey()).isEmpty();
        assertThat(tips.get(0).trial()).contains(new Tip.Trial(Optional.of("look-inside.vsim"), Optional.empty()));
        assertThat(tips.get(1).trial())
                .contains(new Tip.Trial(Optional.of("before.vsim"), Optional.of("simulation.truthTable")));
        assertThat(tips.get(2).trial()).isEmpty();
    }

    @Test
    void anImageTipMayOpenACircuitOfItsOwnOrRunAnActionOnANewEmptyOne() {
        List<Tip> tips = TipsFile.parse(List.of(
                "a | image | t | x | a.png | - | a.vsim",
                "b | image | t | x | b.png | - | edit.quickInsert",
                "c | image | t | x | c.png | - | c.vsim help.findAction"));

        assertThat(tips.get(0).trial()).contains(new Tip.Trial(Optional.of("a.vsim"), Optional.empty()));
        assertThat(tips.get(1).trial()).contains(new Tip.Trial(Optional.empty(), Optional.of("edit.quickInsert")));
        assertThat(tips.get(2).trial())
                .contains(new Tip.Trial(Optional.of("c.vsim"), Optional.of("help.findAction")));
    }

    @Test
    void aTryOpensAtMostOneCircuitAndRunsAtMostOneAction() {
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | image | t | x | a.png | - | a.vsim b.vsim")))
                .hasMessageContaining("line 1");
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | image | t | x | a.png | - | edit.find edit.cut")))
                .hasMessageContaining("line 1");
    }

    @Test
    void aBrokenLineSaysWhichLineItIs() {
        assertThatThrownBy(() -> TipsFile.parse(List.of("# ok", "look-inside | circuit | only.three")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 2");
    }

    @Test
    void eachKindHasItsPictures() {
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | circuit | t | x | one.png | - | -")))
                .hasMessageContaining("line 1");
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | before-after | t | x | one.vsim | - | -")))
                .hasMessageContaining("line 1");
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | image | t | x | one.vsim | - | -")))
                .hasMessageContaining("line 1");
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | drawing | t | x | one.png | - | -")))
                .hasMessageContaining("line 1");
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | split | t | x | one.png | - | -")))
                .hasMessageContaining("line 1");
    }

    @Test
    void aSplitTipIsOneCircuitDrawnTwiceThatItsTryOpensAndSplits() {
        Tip tip = TipsFile.parse(List.of(
                "split-view | split | t | x | split-view.vsim | window.splitVertical | circuit window.splitVertical"))
                .get(0);

        assertThat(tip.kind()).isEqualTo(TipKind.SPLIT);
        assertThat(tip.trial())
                .contains(new Tip.Trial(Optional.of("split-view.vsim"), Optional.of("window.splitVertical")));
    }

    @Test
    void circuitInTheTryNeedsACircuitAmongThePictures() {
        assertThatThrownBy(() -> TipsFile.parse(List.of("a | image | t | x | one.png | - | circuit")))
                .hasMessageContaining("line 1");
    }

    @Test
    void tipIdsAreUnique() {
        assertThatThrownBy(() -> TipsFile.parse(List.of(
                "a | image | t | x | one.png | - | -",
                "a | image | t | x | two.png | - | -")))
                .hasMessageContaining("line 2");
    }


    @Test
    void theFixedKeysAreTheKeysOfGestures() {
        for (String name : List.of("alt", "shift", "space", "escape", "backspace", "enter")) {
            assertThat(NewsText.isKnown(name)).as(name).isTrue();
            assertThat(NewsText.strokeOf(name, new KeyBindings(AppPreferences.inMemory()))).as(name).isPresent();
        }
        assertThat(NewsText.isKnown("double-click")).isFalse();
    }

    @Test
    void everyTipHasItsPicturesItsTextsInBothBundlesKnownKeysAndActionsOfTheEditor() throws Exception {
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
            Map<String, Action> actions = ShortcutsExtension.actionsOf(view);
            List<Tip> tips = TipsFile.load();
            assertThat(tips).isNotEmpty();
            for (Tip tip : tips) {
                for (String picture : tip.pictures()) {
                    assertPictureLoads(picture);
                }
                for (Locale locale : List.of(Locale.forLanguageTag("es"), Locale.ENGLISH)) {
                    ResourceBundle bundle = Messages.bundle(locale);
                    assertThat(bundle.containsKey(tip.titleKey())).as(locale + " " + tip.titleKey()).isTrue();
                    assertThat(bundle.containsKey(tip.textKey())).as(locale + " " + tip.textKey()).isTrue();
                    NewsText.parse(bundle.getString(tip.textKey())).stream()
                            .filter(NewsText.Key.class::isInstance).map(NewsText.Key.class::cast)
                            .forEach(key -> assertKeyResolves(key.name(), actions, locale + " " + tip.textKey()));
                }
                tip.pictureKey().ifPresent(name -> assertKeyResolves(name, actions, tip.id() + " picture key"));
                tip.trial().flatMap(Tip.Trial::circuit).ifPresent(circuit -> assertThat(TipCircuits.builders())
                        .as(tip.id() + " opens " + circuit).containsKey(circuit));
                tip.trial().flatMap(Tip.Trial::actionId).ifPresent(actionId -> {
                    assertThat(ShortcutCatalog.find(actionId)).as(tip.id() + " " + actionId).isPresent();
                    assertThat(actions).as(tip.id() + " " + actionId).containsKey(actionId);
                });
            }
        } finally {
            view.dispose();
            openDocument.close();
            service.close();
        }
    }

    /** A fixed key, or a catalog id whose action the editor has (so its key or menu path can be shown). */
    private static void assertKeyResolves(String name, Map<String, Action> actions, String where) {
        assertThat(NewsText.isKnown(name)).as(where + " {" + name + "}").isTrue();
        if (ShortcutCatalog.find(name).isPresent()) {
            assertThat(actions).as(where + " {" + name + "} has an action").containsKey(name);
        }
    }

    private static void assertPictureLoads(String picture) throws Exception {
        if (TipKind.isCircuit(picture)) {
            // TipCircuitsTest checks each one is what its builder makes and shows only the problems it is about
            assertThat(TipCircuits.builders()).as(picture + " has a builder").containsKey(picture);
            assertThat(TipCircuit.load(picture).snapshot().failure()).as(picture).isEmpty();
            return;
        }
        try (InputStream stream = TipsFileTest.class.getResourceAsStream(TipsFile.FOLDER + picture)) {
            assertThat(stream).as(picture).isNotNull();
            assertThat(ImageIO.read(stream)).as(picture).isNotNull();
        }
    }
}
