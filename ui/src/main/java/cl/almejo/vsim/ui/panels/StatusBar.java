package cl.almejo.vsim.ui.panels;

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
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final JLabel zoom = new JLabel();
    private final JLabel selection = new JLabel();
    private final JLabel hint = new JLabel();
    private final JLabel notice = new JLabel();
    private final JButton noticeAction = new JButton();
    private Runnable noticeActionRun = () -> { };
    private final JLabel shortCircuits = new ErrorTextLabel();
    private final JLabel failure = new ErrorTextLabel();
    private boolean clickable;
    private boolean running;
    private boolean runStateClickable;
    private final Timer noticeTimer;
    /** What extensions show while something happens (the AI's task strip), after the problems. */
    private final List<JComponent> activities = new ArrayList<>();
    /** What extensions always show (the AI's state), in the stable zone at the right end. */
    private final List<JComponent> indicators = new ArrayList<>();

    public StatusBar() {
        this(StatusBar::swingNoticeTimer);
    }

    /** {@code noticeTimerFactory} builds the one-shot timer that hides a notice; tests pass a fake. */
    public StatusBar(Function<Runnable, Timer> noticeTimerFactory) {
        // Zones by priority (B01): when the bar is short the gesture help and the notice give way first (their whole
        // text stays in the tooltip), then an activity; the state, the problems and the indicators keep their width.
        super(new MigLayout("insets 2 8 2 8", "[][][][][shrinkprio " + YIELDING_PRIORITY + "][shrinkprio "
                + YIELDING_PRIORITY + "][]push[][]"));
        noticeTimer = noticeTimerFactory.apply(this::hideNotice);
        add(simulation);
        time.setToolTipText(Messages.text("status.timeTip"));
        add(time);
        add(zoom);
        add(selection);
        add(hint, YIELDING_CONSTRAINTS);
        add(notice, YIELDING_CONSTRAINTS);
        noticeAction.putClientProperty("JButton.buttonType", "borderless");
        noticeAction.addActionListener(event -> noticeActionRun.run());
        add(noticeAction, "hidemode 3");
        add(shortCircuits);
        add(failure);
        selection.setVisible(false);
        hint.setVisible(false);
        notice.setVisible(false);
        noticeAction.setVisible(false);
        shortCircuits.setVisible(false);
        failure.setVisible(false);
    }

    public void showSnapshot(SimulationSnapshot snapshot) {
        running = snapshot.running();
        simulation.setText(Messages.text(running ? "status.running" : "status.paused"));
        if (runStateClickable) {
            simulation.setToolTipText(runStateTip());
        }
        time.setText(Messages.format("status.time", snapshot.time()));
        failure.setText(snapshot.failure().map(reason -> Messages.format("status.failure", reason)).orElse(""));
        failure.setVisible(snapshot.failure().isPresent());
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
        return getComponentZOrder(failure) + 1;
    }

    JLabel shortCircuitsLabel() {
        return shortCircuits;
    }

    JLabel failureLabel() {
        return failure;
    }

    JLabel noticeLabel() {
        return notice;
    }

    /**
     * Clicking «zoom 125 %» runs {@code action} (the view goes back to 100 % around the middle of the canvas); the
     * label gets the hand cursor and the tooltip «Clic: volver a 100 %». Only the primary button counts.
     */
    public void onZoomClicked(Runnable action) {
        zoom.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        zoom.setToolTipText(Messages.text("status.zoomTip"));
        zoom.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    action.run();
                }
            }
        });
    }

    JLabel zoomLabel() {
        return zoom;
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

    public void showZoom(double zoomFactor) {
        zoom.setText(Messages.format("status.zoom", Math.round(zoomFactor * 100)));
    }

    public void showSelection(int count) {
        selection.setText(count == 0 ? "" : Messages.format("status.selected", count));
        selection.setVisible(count > 0);
    }

    /**
     * Shows {@code text} while the pointer is over something that explains itself (a tunnel: «CLK: 1 entrada, 3
     * salidas»); an empty text hides it.
     */
    public void showHint(String text) {
        hint.setText(text);
        hint.setVisible(!text.isEmpty());
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

    /** Visible texts joined with " · " (what the user reads, left to right). */
    public String text() {
        return List.of(simulation, time, zoom, selection, hint, notice, shortCircuits, failure).stream()
                .filter(JLabel::isVisible)
                .map(JLabel::getText)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" · "));
    }
}
