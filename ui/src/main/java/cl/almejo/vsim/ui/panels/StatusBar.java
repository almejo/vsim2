package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

public final class StatusBar extends JPanel {

    private static final String CLICK_TOOLTIP = "tooltip.shortCircuits";
    private static final Color WARNING = new Color(0xE53935);

    private final JLabel simulation = new JLabel();
    private final JLabel time = new JLabel();
    private final JLabel zoom = new JLabel();
    private final JLabel selection = new JLabel();
    private final JLabel shortCircuits = new JLabel();
    private final JLabel failure = new JLabel();
    private boolean clickable;
    private boolean running;
    private boolean runStateClickable;

    public StatusBar() {
        super(new MigLayout("insets 2 8 2 8", "[][][][]push[][]"));
        shortCircuits.setForeground(WARNING);
        failure.setForeground(WARNING);
        add(simulation);
        time.setToolTipText(Messages.text("status.timeTip"));
        add(time);
        add(zoom);
        add(selection);
        add(shortCircuits);
        add(failure);
        selection.setVisible(false);
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
        shortCircuits.setText(text);
        shortCircuits.setVisible(!text.isEmpty());
        if (clickable) {
            shortCircuits.setToolTipText(Messages.text(CLICK_TOOLTIP));
        }
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

    JLabel shortCircuitsLabel() {
        return shortCircuits;
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

    /** Visible texts joined with " · " (what the user reads, left to right). */
    public String text() {
        return List.of(simulation, time, zoom, selection, shortCircuits, failure).stream()
                .filter(JLabel::isVisible)
                .map(JLabel::getText)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" · "));
    }
}
