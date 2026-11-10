package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.ui.window.EditorView;

import javax.swing.Timer;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * A view's window is ready for the bubbles once it shows and has had {@value #SETTLE_MILLISECONDS} ms to settle (the
 * rest of the start: the other windows of the session, the library, the first paint), so they never come in the middle
 * of the start. Fires once; a view hidden again before then (another tab came to the front) waits for its next showing.
 */
final class SettledWindow implements WhatsNewExtension.WindowReadiness {

    static final int SETTLE_MILLISECONDS = 1_500;

    @Override
    public Runnable whenReady(EditorView view, Runnable ready) {
        Timer timer = new Timer(SETTLE_MILLISECONDS, null);
        timer.setRepeats(false);
        HierarchyListener showing = new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent event) {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) {
                    return;
                }
                if (view.isShowing()) {
                    timer.restart();
                } else {
                    timer.stop();
                }
            }
        };
        timer.addActionListener(event -> {
            if (view.isShowing()) {
                view.removeHierarchyListener(showing);
                ready.run();
            }
        });
        view.addHierarchyListener(showing);
        if (view.isShowing()) {
            timer.start();
        }
        return () -> {
            timer.stop();
            view.removeHierarchyListener(showing);
        };
    }
}
