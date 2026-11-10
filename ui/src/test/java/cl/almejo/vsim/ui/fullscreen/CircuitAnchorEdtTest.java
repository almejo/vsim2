package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.EditorFixture;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.ManualFrameTicker;
import cl.almejo.vsim.ui.canvas.ViewState;
import org.junit.jupiter.api.Test;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * The anchor does its work on the EDT and holds no lock of its own while it calls into Swing, whatever thread asks:
 * tests drive presentation mode off the EDT while the panes' resize events reach the anchor on the EDT, and the old
 * {@code synchronized} anchor deadlocked the suite against the AWT tree lock (2026-11-09,
 * {@code FullScreenWaveformsTest.close}: the test thread held the anchor and waited for the tree lock, the EDT held
 * the tree lock and waited for the anchor). No cleanup in an {@code @AfterEach}: after a deadlock it would hang the
 * run instead of letting the timeout report it.
 */
class CircuitAnchorEdtTest {

    private static final Duration DEADLOCK_TIMEOUT = Duration.ofSeconds(20);

    private final ManualFrameTicker clock = new ManualFrameTicker();
    private CircuitAnchor anchor;

    private static CircuitCanvas laidOut(EditorFixture fixture) {
        CircuitCanvas pane = fixture.view().canvas();
        pane.setSize(800, 600);
        pane.applyViewState(new ViewState(1, 0, 0));
        return pane;
    }

    private static JRootPane rootPaneAround(EditorFixture fixture) {
        JRootPane rootPane = new JRootPane();
        rootPane.setContentPane(fixture.view());
        return rootPane;
    }

    /** The anchor reads its clock in the middle of its work: where that runs is where the anchor's work runs. */
    @Test
    void everyCallOffTheEdtIsCarriedOutOnTheEdtWithoutHoldingTheAnchor() {
        EditorFixture fixture = EditorFixture.create();
        CircuitCanvas pane = laidOut(fixture);
        List<String> wrongPlaces = new CopyOnWriteArrayList<>();
        LongSupplier probingClock = () -> {
            if (!SwingUtilities.isEventDispatchThread()) {
                wrongPlaces.add("off the EDT: " + Thread.currentThread().getName());
            }
            if (Thread.holdsLock(anchor)) {
                wrongPlaces.add("holding the anchor's monitor");
            }
            return clock.now();
        };
        anchor = new CircuitAnchor(rootPaneAround(fixture), probingClock);

        assertTimeoutPreemptively(DEADLOCK_TIMEOUT, () -> {
            anchor.hold(List.of(pane));
            pane.setSize(1280, 820);
            anchor.paneChanged(pane);
            anchor.follow();
            anchor.release();
        });

        assertThat(wrongPlaces).isEmpty();
        fixture.close();
    }

    /**
     * The deadlock of 2026-11-09 made deterministic: while the anchor holds a pane for a test thread, a resize event
     * on the EDT takes the tree lock and reaches the anchor. The anchor must still finish.
     */
    @Test
    void holdingOffTheEdtWhileAResizeOnTheEdtHoldsTheTreeLockFinishes() {
        EditorFixture fixture = EditorFixture.create();
        CircuitCanvas pane = laidOut(fixture);
        AtomicBoolean rivalPosted = new AtomicBoolean();
        LongSupplier rivalClock = () -> {
            if (rivalPosted.compareAndSet(false, true)) {
                postResizeHoldingTheTreeLock(pane);
            }
            return clock.now();
        };
        anchor = new CircuitAnchor(rootPaneAround(fixture), rivalClock);

        assertTimeoutPreemptively(DEADLOCK_TIMEOUT, () -> {
            anchor.hold(List.of(pane));
            SwingUtilities.invokeAndWait(() -> { }); // the rival resize ran too
            anchor.release();
        });

        assertThat(rivalPosted).isTrue();
        fixture.close();
    }

    /**
     * Posts what the EDT does on an ancestor's resize ({@code Container.dispatchEventImpl} holds the tree lock while
     * it hands the hierarchy event to the pane's listener) and, when the anchor works off the EDT, waits until the EDT
     * holds the tree lock: the anchor's work then goes on to add its listeners, which takes the tree lock too.
     */
    private void postResizeHoldingTheTreeLock(CircuitCanvas pane) {
        CountDownLatch edtHoldsTheTreeLock = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            synchronized (pane.getTreeLock()) {
                edtHoldsTheTreeLock.countDown();
                anchor.paneChanged(pane);
            }
        });
        if (SwingUtilities.isEventDispatchThread()) {
            return; // the anchor works on the EDT: the posted resize runs after it, as in the editor
        }
        try {
            edtHoldsTheTreeLock.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
