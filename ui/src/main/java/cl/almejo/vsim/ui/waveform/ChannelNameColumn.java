package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.waveform.WaveformSnapshot;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterSupport;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;

/**
 * The row header of the waveform panel: each channel's name and its value at the cursor (or at the current time
 * without a cursor), «(sin conexión)» for a probe that does not resolve. Click selects a row, double click shows its
 * net in the circuit, right click opens the row menu.
 */
final class ChannelNameColumn extends JComponent {

    static final int COLUMN_WIDTH = 190;
    private static final int PADDING = 6;
    private static final int HIGHLIGHT_ALPHA = 90;

    private final WaveformPanel panel;

    ChannelNameColumn(WaveformPanel panel) {
        this.panel = panel;
        setOpaque(true);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                probeAt(event.getY()).ifPresent(panel::selectProbe);
                popupIfTrigger(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                popupIfTrigger(event);
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 2) {
                    probeAt(event.getY()).ifPresent(panel::showInCircuit);
                }
            }
        });
    }

    private void popupIfTrigger(MouseEvent event) {
        if (event.isPopupTrigger()) {
            probeAt(event.getY()).ifPresent(probeId -> panel.rowMenu(probeId).show(this, event.getX(), event.getY()));
        }
    }

    Optional<String> probeAt(int y) {
        return panel.probeAt(y);
    }

    /** What each row shows after its name. */
    List<String> values() {
        return panel.rows().stream().map(this::valueOf).toList();
    }

    private String valueOf(ChannelRows.ChannelRow row) {
        if (!row.connected()) {
            return Messages.text("waveform.disconnected");
        }
        WaveformSnapshot snapshot = panel.displayed();
        long time = panel.axis().cursor().orElse(snapshot.time());
        return ChannelRows.valueText(snapshot.trace(row.probeId()).flatMap(trace -> trace.valueAt(time)));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(COLUMN_WIDTH, Math.max(1, panel.channelIds().size()) * WaveformPainter.ROW_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            ColorScheme scheme = panel.scheme();
            graphics2D.setColor(scheme.diagramBackground());
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = graphics2D.getFontMetrics();
            List<ChannelRows.ChannelRow> rows = panel.rows();
            for (int index = 0; index < rows.size(); index++) {
                ChannelRows.ChannelRow row = rows.get(index);
                int top = index * WaveformPainter.ROW_HEIGHT;
                boolean selected = panel.selectedProbe().filter(row.probeId()::equals).isPresent();
                if (selected || panel.isBlinkLit(row.probeId())) {
                    graphics2D.setColor(PainterSupport.withAlpha(scheme.selection(), HIGHLIGHT_ALPHA));
                    graphics2D.fillRect(0, top, getWidth(), WaveformPainter.ROW_HEIGHT);
                }
                int baseline = top + (WaveformPainter.ROW_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
                String value = valueOf(row);
                int valueWidth = metrics.stringWidth(value);
                graphics2D.setColor(scheme.diagramTrace());
                graphics2D.drawString(value, getWidth() - PADDING - valueWidth, baseline);
                Graphics2D nameGraphics = (Graphics2D) graphics2D.create();
                nameGraphics.clipRect(0, top, Math.max(0, getWidth() - 2 * PADDING - valueWidth),
                        WaveformPainter.ROW_HEIGHT);
                nameGraphics.drawString(row.name(), PADDING, baseline);
                nameGraphics.dispose();
                graphics2D.setColor(scheme.diagramGrid());
                graphics2D.drawLine(0, top + WaveformPainter.ROW_HEIGHT - 1, getWidth(),
                        top + WaveformPainter.ROW_HEIGHT - 1);
            }
        } finally {
            graphics2D.dispose();
        }
    }
}
