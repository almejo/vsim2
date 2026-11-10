package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.library.ComponentLibrary;
import cl.almejo.vsim.ui.UiTestSupport;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.app.RecordingKeyDispatchers;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutEchoExtension;
import cl.almejo.vsim.ui.shortcuts.ShortcutsExtension;
import cl.almejo.vsim.ui.shortcuts.sheet.ShortcutSheetExtension;
import cl.almejo.vsim.ui.window.EditorServices;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Ayuda ▸ «Qué hay de nuevo» and the bubbles that come by themselves the first time a version opens. */
class WhatsNewExtensionTest {

    private final AppPreferences preferences = AppPreferences.inMemory();
    private final Map<EditorView, Runnable> readyHooks = new HashMap<>();
    private final List<String> opened = new ArrayList<>();
    private final List<Runnable> endings = new ArrayList<>();
    private final List<Runnable> aiListeners = new ArrayList<>();
    private int closedTours;
    private boolean aiWorking;
    private final List<EditorView> views = new ArrayList<>();
    private final List<OpenDocument> documents = new ArrayList<>();
    private WhatsNewExtension extension;

    private final WhatsNewExtension.AiActivity ai = new WhatsNewExtension.AiActivity() {
        @Override
        public boolean working() {
            return aiWorking;
        }

        @Override
        public Runnable addListener(Runnable listener) {
            aiListeners.add(listener);
            return () -> aiListeners.remove(listener);
        }
    };

    @BeforeEach
    void anExtensionOverFakeTours() {
        Messages.initialize(Messages.SPANISH);
        extension = new WhatsNewExtension(new WhatsNewExtension.Seams("0.8",
                version -> Optional.of(new NewsFile(version, List.of())), StartupNotices.NewsRule.ONCE_PER_VERSION,
                (view, ready) -> {
                    readyHooks.put(view, ready);
                    return () -> readyHooks.remove(view);
                }, ai, (view, news, ended) -> {
                    opened.add(news.version());
                    endings.add(ended);
                    return () -> closedTours++;
                }));
    }

    @AfterEach
    void close() {
        views.forEach(EditorView::dispose);
        documents.forEach(OpenDocument::close);
    }

    private EditorView newView() {
        OpenDocument openDocument = new OpenDocument(new CircuitDocument(), StandardComponentTypes.registry(), null,
                false);
        RecordingKeyDispatchers keyHooks = new RecordingKeyDispatchers();
        ShortcutSheetExtension sheet = new ShortcutSheetExtension(new ShortcutSheetExtension.Seams(keyHooks,
                ManualFrameTicker::new, () -> 0L, candidate -> false, Runnable::run));
        ShortcutEchoExtension echo = new ShortcutEchoExtension(new ShortcutEchoExtension.Seams(
                ManualFrameTicker::new, () -> 0L, candidate -> false, keyHooks, () -> null, () -> null, false));
        EditorServices services = new EditorServices(PainterRegistry.standard(), new Appearance(ColorScheme.LIGHT),
                preferences, new ComponentLibrary(), List.of(sheet, extension, echo, new ShortcutsExtension()));
        EditorView view = new EditorView(openDocument, services, UiTestSupport.NO_WINDOW);
        views.add(view);
        documents.add(openDocument);
        return view;
    }

    private static JMenuItem whatsNewItem(EditorView view) {
        JMenu help = view.menu(MenuId.HELP);
        for (int index = 0; index < help.getItemCount(); index++) {
            JMenuItem item = help.getItem(index);
            if (item != null && Messages.text("action.whatsNew").equals(item.getText())) {
                return item;
            }
        }
        throw new AssertionError("no «Qué hay de nuevo» in Ayuda: " + UiTestSupport.texts(help));
    }

    @Test
    void ayudaListsWhatsNewRightAfterTheKeyboardShortcuts() {
        EditorView view = newView();

        List<String> help = UiTestSupport.texts(view.menu(MenuId.HELP));
        int sheet = help.indexOf(Messages.text("action.shortcutSheet"));
        assertThat(sheet).isNotNegative();
        assertThat(help.get(sheet + 1)).isEqualTo(Messages.text("action.whatsNew"));
        assertThat(whatsNewItem(view).getClientProperty(Actions.SEARCH_TERMS))
                .isEqualTo(Messages.text("whatsNew.searchTerms"));
    }

    @Test
    void theMenuItemReplaysThisVersionsBubblesEvenAfterTheyWereSeen() {
        preferences.setNewsShownVersion("0.8");
        EditorView view = newView();

        whatsNewItem(view).doClick(0);

        assertThat(opened).containsExactly("0.8");
    }

    @Test
    void theFirstWindowReadyGetsTheBubblesAndEndingThemStoresTheVersion() {
        preferences.setNewsShownVersion("0.7");
        EditorView first = newView();
        EditorView second = newView();

        readyHooks.get(first).run();
        readyHooks.get(second).run();

        assertThat(opened).containsExactly("0.8");
        assertThat(preferences.newsShownVersion()).contains("0.7");
        endings.get(0).run();
        assertThat(preferences.newsShownVersion()).contains("0.8");
    }

    @Test
    void aFirstInstallShowsThem() {
        EditorView view = newView();

        readyHooks.get(view).run();

        assertThat(opened).containsExactly("0.8");
    }

    @Test
    void theSameVersionOpensNothingByItself() {
        preferences.setNewsShownVersion("0.8");
        EditorView view = newView();

        readyHooks.get(view).run();

        assertThat(opened).isEmpty();
    }

    @Test
    void theAiWorkingHoldsThemUntilItStops() {
        aiWorking = true;
        EditorView view = newView();

        readyHooks.get(view).run();
        assertThat(opened).isEmpty();
        aiWorking = false;
        List.copyOf(aiListeners).forEach(Runnable::run);

        assertThat(opened).containsExactly("0.8");
        assertThat(aiListeners).isEmpty();
    }

    @Test
    void closingTheWindowTakesBackTheItemTheHooksAndTheTourWithoutStoringTheVersion() {
        preferences.setNewsShownVersion("0.7");
        EditorView view = newView();
        readyHooks.get(view).run();

        view.dispose();
        views.remove(view);
        documents.forEach(OpenDocument::close);
        documents.clear();

        assertThat(UiTestSupport.texts(view.menu(MenuId.HELP))).doesNotContain(Messages.text("action.whatsNew"));
        assertThat(readyHooks).doesNotContainKey(view);
        assertThat(closedTours).isEqualTo(1);
        assertThat(preferences.newsShownVersion()).contains("0.7");
    }
}
