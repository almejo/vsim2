package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.Optional;

/**
 * Ayuda ▸ «Qué hay de nuevo» of one editor view, and the bubbles that come by themselves when this view is the first
 * one shown in a run whose version has news the user has not seen. The menu item always replays them. A tour ended by
 * the user stores the version; one taken away because the window closed does not, so it comes again next time.
 */
final class WhatsNewFeatures {

    private final EditorView view;
    private final WhatsNewExtension.Seams seams;
    private final StartupNotices notices;
    private final JMenuItem item;
    private final Runnable cancelReadiness;
    private TourGate gate;
    private Runnable closeTour;
    private boolean disposed;

    WhatsNewFeatures(EditorView view, WhatsNewExtension.Seams seams, StartupNotices notices) {
        this.view = view;
        this.seams = seams;
        this.notices = notices;
        Action replay = Actions.of(Messages.text("action.whatsNew"), this::replay);
        replay.setEnabled(news().isPresent());
        this.item = new JMenuItem(replay);
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text("whatsNew.searchTerms"));
        JMenu help = view.menu(MenuId.HELP);
        help.add(item, itemIndex(help));
        this.cancelReadiness = seams.readiness().whenReady(view, this::windowReady);
    }

    /** Right after «Atajos de teclado» when the sheet put it there, first otherwise. */
    private static int itemIndex(JMenu help) {
        String sheet = Messages.text("action.shortcutSheet");
        for (int index = 0; index < help.getMenuComponentCount(); index++) {
            if (help.getMenuComponent(index) instanceof JMenuItem candidate && sheet.equals(candidate.getText())) {
                return index + 1;
            }
        }
        return 0;
    }

    private Optional<NewsFile> news() {
        return seams.news().apply(seams.runningVersion());
    }

    /** This view's window shows: the first one of the run asks whether the bubbles are due. */
    private void windowReady() {
        if (disposed || notices.claimForWindow().isEmpty()) {
            return;
        }
        Optional<NewsFile> news = news();
        if (news.isEmpty()) {
            return;
        }
        gate = new TourGate(new ViewConditions(), () -> open(news.get()));
        gate.windowReady();
    }

    private void replay() {
        news().ifPresent(this::open);
    }

    /** Opens the bubbles from the first one, taking away any tour already showing. */
    private void open(NewsFile news) {
        if (disposed) {
            return;
        }
        takeAwayTheTour();
        closeTour = seams.tours().open(view, news, this::tourEnded);
    }

    private void tourEnded() {
        closeTour = null;
        notices.shown(StartupNotices.Notice.NEWS_BUBBLES);
    }

    private void takeAwayTheTour() {
        if (closeTour != null) {
            Runnable closing = closeTour;
            closeTour = null;
            closing.run();
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        cancelReadiness.run();
        if (gate != null) {
            gate.dispose();
        }
        takeAwayTheTour();
        view.menu(MenuId.HELP).remove(item);
    }

    /** Presentation mode of this view and the AI's work, as the gate reads them. */
    private final class ViewConditions implements TourGate.Conditions {

        @Override
        public boolean presenting() {
            return view.fullScreenStage().isPresent();
        }

        @Override
        public boolean aiWorking() {
            return seams.ai().working();
        }

        @Override
        public Runnable addChangeListener(Runnable listener) {
            Runnable removeLayoutListener = view.addFullScreenLayoutListener(listener);
            Runnable removeAiListener = seams.ai().addListener(listener);
            return () -> {
                removeAiListener.run();
                removeLayoutListener.run();
            };
        }
    }
}
