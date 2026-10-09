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
    private final JLabel shortCircuits = new JLabel();
    private final JLabel failure = new JLabel();

    public StatusBar() {
        super(new MigLayout("insets 2 8 2 8", "[][][]push[][]"));
        shortCircuits.setForeground(WARNING);
        failure.setForeground(WARNING);
        add(simulation);
        add(time);
        add(zoom);
        add(shortCircuits);
        add(failure);
        shortCircuits.setVisible(false);
        failure.setVisible(false);
    }

    public void showSnapshot(SimulationSnapshot snapshot) {
        simulation.setText(Messages.text(snapshot.running() ? "status.running" : "status.paused"));
        time.setText(Messages.format("status.time", snapshot.time()));
        int shorts = snapshot.shortCircuits().size();
        shortCircuits.setText(shorts == 0 ? "" : Messages.format("status.shortCircuits", shorts));
        shortCircuits.setVisible(shorts > 0);
        failure.setText(snapshot.failure().map(reason -> Messages.format("status.failure", reason)).orElse(""));
        failure.setVisible(snapshot.failure().isPresent());
    }

    /** Clicking the "⚠ N cortocircuitos" warning runs {@code action} (jump to the next problem). */
    public void onShortCircuitsClicked(Runnable action) {
        shortCircuits.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        shortCircuits.setToolTipText(Messages.text("tooltip.shortCircuits"));
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

    /** Visible texts joined with " · " (what the user reads, left to right). */
    public String text() {
        return List.of(simulation, time, zoom, shortCircuits, failure).stream()
                .filter(JLabel::isVisible)
                .map(JLabel::getText)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" · "));
    }
}
