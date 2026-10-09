package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.stream.Collectors;

public final class StatusBar extends JPanel {

    private static final Color WARNING = new Color(0xE53935);

    private final JLabel simulation = new JLabel();
    private final JLabel time = new JLabel();
    private final JLabel zoom = new JLabel();
    private final JLabel selection = new JLabel();
    private final JLabel shortCircuits = new JLabel();
    private final JLabel failure = new JLabel();
    private boolean clickable;

    public StatusBar() {
        super(new MigLayout("insets 2 8 2 8", "[][][][]push[][]"));
        shortCircuits.setForeground(WARNING);
        failure.setForeground(WARNING);
        add(simulation);
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
        simulation.setText(Messages.text(snapshot.running() ? "status.running" : "status.paused"));
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
     * its tooltip only names short circuits when no width mismatch is counted.
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
            shortCircuits.setToolTipText(clickTooltip(widthMismatchCount));
        }
    }

    private static String clickTooltip(int widthMismatchCount) {
        return Messages.text(widthMismatchCount > 0 ? "tooltip.problems" : "tooltip.shortCircuits");
    }

    /** Clicking the "⚠ N cortocircuitos" warning runs {@code action} (jump to the next problem). */
    public void onShortCircuitsClicked(Runnable action) {
        shortCircuits.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clickable = true;
        shortCircuits.setToolTipText(clickTooltip(0));
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
