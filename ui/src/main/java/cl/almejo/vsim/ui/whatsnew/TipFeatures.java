package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.MenuId;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.List;

/**
 * Ayuda ▸ «Consejo del día» of one editor view, and the card that comes by itself when this view is the first one
 * shown in a run whose start is the tip's ({@link StartupNotices}), once the window is free of presentation mode and
 * of the AI's work ({@link TourGate}). The menu item always opens it, with the first tip not seen yet.
 */
final class TipFeatures {

    private final EditorView view;
    private final TipOfTheDayExtension.Seams seams;
    private final StartupNotices notices;
    private final List<Tip> tips;
    private final JMenuItem item;
    private final Runnable cancelReadiness;
    private TourGate gate;
    private TipShowing showing;
    private boolean disposed;

    TipFeatures(EditorView view, TipOfTheDayExtension.Seams seams, StartupNotices notices, List<Tip> tips) {
        this.view = view;
        this.seams = seams;
        this.notices = notices;
        this.tips = tips;
        this.item = new JMenuItem(Actions.of(Messages.text("action.tipOfTheDay"), this::open));
        item.putClientProperty(Actions.SEARCH_TERMS, Messages.text("tip.searchTerms"));
        item.setEnabled(!tips.isEmpty());
        JMenu help = view.menu(MenuId.HELP);
        help.add(item, itemIndex(help));
        this.cancelReadiness = seams.readiness().whenReady(view, this::windowReady);
    }

    /** Right after «Qué hay de nuevo» when it is there, first otherwise. */
    private static int itemIndex(JMenu help) {
        String whatsNew = Messages.text("action.whatsNew");
        for (int index = 0; index < help.getMenuComponentCount(); index++) {
            if (help.getMenuComponent(index) instanceof JMenuItem candidate && whatsNew.equals(candidate.getText())) {
                return index + 1;
            }
        }
        return 0;
    }

    /** This view's window shows: the first one of the run asks whether the start is the tip's. */
    private void windowReady() {
        if (disposed || notices.claimForWindow(view).filter(StartupNotices.Notice.TIP_OF_THE_DAY::equals).isEmpty()) {
            return;
        }
        gate = new TourGate(new ViewConditions(view, seams.ai()), this::openByItself);
        gate.windowReady();
    }

    private void openByItself() {
        if (open()) {
            notices.shown(StartupNotices.Notice.TIP_OF_THE_DAY);
        }
    }

    /** Opens the card with the first tip not seen yet; false when nothing was opened. */
    private boolean open() {
        if (disposed || tips.isEmpty() || (showing != null && showing.isOpen())) {
            return false;
        }
        TipOrder order = new TipOrder(tips.stream().map(Tip::id).toList(), view.services().preferences());
        showing = new TipShowing(view, tips, order, seams);
        return showing.open(order.chooseFirst());
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
        if (showing != null) {
            showing.close();
        }
        view.menu(MenuId.HELP).remove(item);
    }
}
