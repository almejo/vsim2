package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.simulation.PauseNotice;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.theme.ErrorTextLabel;
import net.miginfocom.swing.MigLayout;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StatusBar extends JPanel {

    private static final String CLICK_TOOLTIP = "tooltip.shortCircuits";
    /** Above MigLayout's default 100: these columns shrink before the others. */
    private static final int YIELDING_PRIORITY = 200;
    /** Text that may be cut short (the full text is in its tooltip). */
    private static final String YIELDING_CONSTRAINTS = "wmin 0";
    private static final String ACTIVITY_CONSTRAINTS = "hidemode 3, wmin 0";
    private static final String INDICATOR_CONSTRAINTS = "";

    private final JLabel simulation = new JLabel();
    private final JLabel time = new JLabel();
    private final JLabel achievedSpeed = new JLabel();
    private final JLabel selection = new JLabel();
    private final JLabel hint = new JLabel();
    private final JLabel notice = new JLabel();
    private final JButton noticeAction = new JButton();
    /** What the keys do during the gesture under way; after the notice, before the problems. */
    private final JLabel gesture = new JLabel();
    private Runnable noticeActionRun = () -> { };
    private final JLabel shortCircuits = new ErrorTextLabel();
    private final JLabel failure = new ErrorTextLabel();
    /** «Simulación detenida en t = …: …»: why a part paused the simulation, while it stays paused. */
    private final JLabel pauseNotice = new JLabel();
    /** What stays true of the place shown while it is shown («Sin valores» in a component tab). */
    private final JLabel placeHint = new JLabel();
    private boolean clickable;
    private boolean running;
    /** The time of the last snapshot shown. */
    private long snapshotTime;
    /** The {@link PauseNotice#sequence()} of the last pause the simulation ran on from; it is not said again. */
    private long pauseNoticeOver;
    private boolean runStateClickable;
    private final Timer noticeTimer;
    /** What extensions show while something happens (the AI's task strip), after the problems. */
    private final List<JComponent> activities = new ArrayList<>();
    /** What extensions always show (the AI's state), in the stable zone at the right end. */
    private final List<JComponent> indicators = new ArrayList<>();
    /** The zoom control, at the very end (after the indicators); null until the window gives it. */
    private ZoomControl zoomControl;

    public StatusBar() {
        this(StatusBar::swingNoticeTimer);
    }

    /** {@code noticeTimerFactory} builds the one-shot timer that hides a notice; tests pass a fake. */
    public StatusBar(Function<Runnable, Timer> noticeTimerFactory) {
        // Columns, one per component before the push: state, time, speed, selection, hint (yields), notice (yields),
        // the notice's button (hidemode 2: size 0 but its cell stays), the gesture phrase (yields).
        // Zones by priority (B01): when the bar is short the gesture help and the notice give way first (their whole
        // text stays in the tooltip), then an activity and the zoom slider; the state, the problems, the indicators
        // and the zoom percentage keep their width.
        super(new MigLayout("insets 2 8 2 8", "[][][][][shrinkprio " + YIELDING_PRIORITY + "][shrinkprio "
                + YIELDING_PRIORITY + "][][shrinkprio " + YIELDING_PRIORITY + "]push[][]"));
        noticeTimer = noticeTimerFactory.apply(this::hideNotice);
        add(simulation);
        time.setToolTipText(Messages.text("status.timeTip"));
        add(time);
        achievedSpeed.setToolTipText(Messages.text("status.speedTip"));
        add(achievedSpeed);
        add(selection);
        add(hint, YIELDING_CONSTRAINTS);
        add(notice, YIELDING_CONSTRAINTS);
        noticeAction.putClientProperty("JButton.buttonType", "borderless");
        noticeAction.addActionListener(event -> noticeActionRun.run());
        // hidemode 2 (size 0, cell kept), not 3: a hidden button must not shift the columns the priorities name
        add(noticeAction, "hidemode 2");
        add(gesture, YIELDING_CONSTRAINTS);
        add(shortCircuits);
        add(failure);
        add(pauseNotice);
        add(placeHint, YIELDING_CONSTRAINTS);
        achievedSpeed.setVisible(false);
        selection.setVisible(false);
        hint.setVisible(false);
        notice.setVisible(false);
        noticeAction.setVisible(false);
        gesture.setVisible(false);
        shortCircuits.setVisible(false);
        failure.setVisible(false);
        pauseNotice.setVisible(false);
        placeHint.setVisible(false);
    }

    public void showSnapshot(SimulationSnapshot snapshot) {
        running = snapshot.running();
        snapshotTime = snapshot.time();
        simulation.setText(Messages.text(running ? "status.running" : "status.paused"));
        if (runStateClickable) {
            simulation.setToolTipText(runStateTip());
        }
        time.setText(Messages.format("status.time", snapshot.time()));
        if (!running) {
            achievedSpeed.setVisible(false);
        }
        failure.setText(snapshot.failure().map(reason -> Messages.format("status.failure", reason)).orElse(""));
        failure.setVisible(snapshot.failure().isPresent());
    }

    /**
     * Says why a part paused the simulation ({@code latest}, read after the snapshot shown last): while it stays
     * paused, «Simulación detenida en t = 120: «consola» recibió «=120»». Once a snapshot shows the simulation past that
     * time, running or stepped, the pause is over and is not said again, not even when the user pauses later; a newer
     * pause is.
     */
    public void showPauseNotice(Optional<PauseNotice> latest) {
        if (latest.isEmpty()) {
            pauseNotice.setVisible(false);
            return;
        }
        PauseNotice notice = latest.get();
        if (snapshotTime > notice.time()) {
            pauseNoticeOver = Math.max(pauseNoticeOver, notice.sequence());
        }
        boolean shown = !running && notice.sequence() > pauseNoticeOver;
        if (shown) {
            String reason = Messages.format(notice.messageKey(), notice.arguments().toArray());
            pauseNotice.setText(Messages.format("status.pausedByPart", notice.time(), reason));
            pauseNotice.setToolTipText(pauseNotice.getText());
        }
        pauseNotice.setVisible(shown);
    }

    /** The speed the running simulation achieves, in units per real millisecond; 0, or a pause, hides it. */
    public void showSpeed(double unitsPerMillisecond) {
        boolean shown = running && unitsPerMillisecond > 0;
        if (shown) {
            Number rounded;
            if (unitsPerMillisecond >= 10) {
                rounded = Math.round(unitsPerMillisecond);
            } else {
                rounded = Math.round(unitsPerMillisecond * 10) / 10.0;
            }
            achievedSpeed.setText(Messages.format("status.speed", rounded));
        }
        achievedSpeed.setVisible(shown);
    }

    public void showShortCircuits(int count) {
        showProblems(count, 0);
    }

    /**
     * Shows "⚠ N cortocircuitos", "⚠ N anchos incompatibles" or both, hidden when there is neither. Fed by the Problems
     * controller, which ignores snapshots of an older netlist, so the counts match the Problems tab. Once clickable,
     * its tooltip is always the generic "next problem" one.
     */
    public void showProblems(int shortCircuitCount, int widthMismatchCount) {
        showProblems(shortCircuitCount, widthMismatchCount, 0);
    }

    /**
     * Like {@link #showProblems(int, int)}, then «N errores de túneles» (duplicate entradas, salidas without entrada)
     * after them, or on its own with the ⚠ when there is nothing else.
     */
    public void showProblems(int shortCircuitCount, int widthMismatchCount, int tunnelErrorCount) {
        String text = problemsText(shortCircuitCount, widthMismatchCount);
        if (tunnelErrorCount > 0) {
            text = text.isEmpty() ? Messages.format("status.tunnelErrors", tunnelErrorCount)
                    : text + " · " + Messages.format("status.moreTunnelErrors", tunnelErrorCount);
        }
        shortCircuits.setText(text);
        shortCircuits.setVisible(!text.isEmpty());
        if (clickable) {
            shortCircuits.setToolTipText(Messages.text(CLICK_TOOLTIP));
        }
    }

    private static String problemsText(int shortCircuitCount, int widthMismatchCount) {
        String text;
        if (shortCircuitCount > 0 && widthMismatchCount > 0) {
            text = Messages.format("status.shortCircuitsAndWidthMismatches", shortCircuitCount, widthMismatchCount);
        } else if (shortCircuitCount > 0) {
            text = Messages.format("status.shortCircuits", shortCircuitCount);
        } else if (widthMismatchCount > 0) {
            text = Messages.format("status.widthMismatches", widthMismatchCount);
        } else {
            text = "";
        }
        return text;
    }

    /** Clicking the "⚠ N cortocircuitos" warning runs {@code action} (jump to the next problem). */
    public void onShortCircuitsClicked(Runnable action) {
        shortCircuits.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clickable = true;
        shortCircuits.setToolTipText(Messages.text(CLICK_TOOLTIP));
        shortCircuits.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                action.run();
            }
        });
    }

    /**
     * Adds {@code activity} (a strip that comes and goes, like the AI's task) after the problems and before the
     * indicators. Returns what takes it back.
     */
    public Runnable addActivity(JComponent activity) {
        add(activity, ACTIVITY_CONSTRAINTS, indexAfterFailure() + activities.size());
        activities.add(activity);
        revalidate();
        return () -> {
            activities.remove(activity);
            remove(activity);
            revalidate();
            repaint();
        };
    }

    /**
     * Adds {@code indicator} (always shown, like the AI's state) to the stable zone at the right end, after the
     * activities: it keeps its whole width however long a notice is. Returns what takes it back.
     */
    public Runnable addIndicator(JComponent indicator) {
        add(indicator, INDICATOR_CONSTRAINTS, indexAfterFailure() + activities.size() + indicators.size());
        indicators.add(indicator);
        revalidate();
        return () -> {
            indicators.remove(indicator);
            remove(indicator);
            revalidate();
            repaint();
        };
    }

    private int indexAfterFailure() {
        return getComponentZOrder(pauseNotice) + 1;
    }

    JLabel shortCircuitsLabel() {
        return shortCircuits;
    }

    JLabel failureLabel() {
        return failure;
    }

    JLabel pauseNoticeLabel() {
        return pauseNotice;
    }

    JLabel noticeLabel() {
        return notice;
    }

    /**
     * Puts the window's zoom control at the very end of the bar, after the indicators (which an extension adds later
     * still go before it). Given once.
     */
    public void setZoomControl(ZoomControl control) {
        if (zoomControl != null) {
            throw new IllegalStateException("the status bar already has its zoom control");
        }
        zoomControl = Objects.requireNonNull(control, "control");
        add(control, "gapleft 8");
        revalidate();
    }

    /**
     * Clicking «▶ corriendo» runs {@code whenRunning} (pause) and «⏸ en pausa» runs {@code whenPaused} (start), as
     * the last shown snapshot says (M6.1-E); hand cursor and the tooltip «Clic: pausar» / «Clic: iniciar». Only the
     * primary button counts.
     */
    public void onRunStateClicked(Runnable whenRunning, Runnable whenPaused) {
        runStateClickable = true;
        simulation.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        simulation.setToolTipText(runStateTip());
        simulation.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    (running ? whenRunning : whenPaused).run();
                }
            }
        });
    }

    private String runStateTip() {
        return Messages.text(running ? "status.runningTip" : "status.pausedTip");
    }

    JLabel runStateLabel() {
        return simulation;
    }

    JLabel timeLabel() {
        return time;
    }

    /** Shows the active pane's zoom in the zoom control (nothing before the window gives one). */
    public void showZoom(double zoomFactor) {
        if (zoomControl != null) {
            zoomControl.showZoom(zoomFactor);
        }
    }

    public void showSelection(int count) {
        selection.setText(count == 0 ? "" : Messages.format("status.selected", count));
        selection.setVisible(count > 0);
    }

    /**
     * Shows {@code phrase}, what the keys and the next click do while a gesture is under way («Moviendo: suelta para
     * dejarlas · Mayús: …»); an empty phrase hides it. It has its own label, so a hover hint never takes its place.
     */
    public void showGesture(String phrase) {
        gesture.setText(phrase);
        gesture.setToolTipText(phrase.isEmpty() ? null : phrase);
        gesture.setVisible(!phrase.isEmpty());
    }

    JLabel gestureLabel() {
        return gesture;
    }

    /**
     * Shows {@code text} while the pointer is over something that explains itself (a tunnel: «CLK: 1 entrada, 3
     * salidas»); an empty text hides it.
     */
    public void showHint(String text) {
        hint.setText(text);
        hint.setVisible(!text.isEmpty());
    }

    /**
     * Shows {@code text} for as long as it holds of the place the window shows (a component tab with «Sin valores»
     * says where to choose an instance); an empty text hides it. Unlike a notice it does not go by itself.
     */
    public void showPlaceHint(String text) {
        placeHint.setText(text);
        placeHint.setToolTipText(text.isEmpty() ? null : text);
        placeHint.setVisible(!text.isEmpty());
    }

    /** How long a notice stays, in milliseconds. */
    public static final int NOTICE_MILLISECONDS = 4000;
    /** How long a notice with a button stays, so there is time to reach the button. */
    public static final int ACTION_NOTICE_MILLISECONDS = 10_000;

    private static Timer swingNoticeTimer(Runnable hide) {
        Timer timer = new Timer(NOTICE_MILLISECONDS, event -> hide.run());
        timer.setRepeats(false);
        return timer;
    }

    /** Shows {@code text} for four seconds; a new notice replaces the one on screen and restarts the time. */
    public void showNotice(String text) {
        showNotice(text, NOTICE_MILLISECONDS);
        noticeAction.setVisible(false);
        noticeActionRun = () -> { };
    }

    /**
     * Shows {@code text} with a button {@code actionText} after it that runs {@code action} (once clicked the
     * notice goes), for {@value #ACTION_NOTICE_MILLISECONDS} ms; a new notice replaces both.
     */
    public void showNotice(String text, String actionText, Runnable action) {
        showNotice(text, actionText, null, action);
    }

    /** {@link #showNotice(String, String, Runnable)} with {@code actionIcon} next to the button's text (or none). */
    public void showNotice(String text, String actionText, Icon actionIcon, Runnable action) {
        showNotice(text, ACTION_NOTICE_MILLISECONDS);
        noticeAction.setText(actionText);
        noticeAction.setIcon(actionIcon);
        noticeAction.setVisible(true);
        noticeActionRun = () -> {
            hideNotice();
            action.run();
        };
        revalidate();
    }

    private void showNotice(String text, int milliseconds) {
        notice.setText(text);
        notice.setToolTipText(text); // whole, when a short bar cuts it
        notice.setVisible(true);
        noticeTimer.setInitialDelay(milliseconds);
        noticeTimer.restart();
    }

    private void hideNotice() {
        notice.setText("");
        notice.setToolTipText(null);
        notice.setVisible(false);
        noticeAction.setVisible(false);
        noticeActionRun = () -> { };
    }

    /** The button of a notice shown with one (hidden otherwise). */
    public JButton noticeAction() {
        return noticeAction;
    }

    /** Stops the notice timer; the bar goes away with its window. */
    public void dispose() {
        noticeTimer.stop();
    }

    /** Visible texts joined with " · " (what the user reads, left to right), the zoom percentage last («125 %»). */
    public String text() {
        Stream<String> labels = Stream.of(simulation, time, achievedSpeed, selection, hint, notice, gesture,
                        shortCircuits, failure, pauseNotice, placeHint)
                .filter(JLabel::isVisible)
                .map(JLabel::getText);
        Stream<String> zoom = zoomControl != null && zoomControl.isVisible() ? Stream.of(zoomControl.text())
                : Stream.empty();
        return Stream.concat(labels, zoom)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" · "));
    }
}
