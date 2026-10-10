package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The ruler above the traces: 1-2-5 ticks in simulation units (none before t = 0), «t = …» (the current time) at the right end and
 * «cursor t = …» next to the cursor. Dragging it pans the time axis; its tooltip says since when the history is
 * complete.
 */
final class TimeRuler extends JComponent {

    static final int RULER_HEIGHT = 22;
    private static final int TICK_LENGTH = 6;
    private static final int PADDING = 4;

    private final WaveformPanel panel;
    private int dragX;

    TimeRuler(WaveformPanel panel) {
        this.panel = panel;
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                dragX = event.getX();
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                panel.scrollBy(dragX - event.getX());
                dragX = event.getX();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
    }

    /** «cursor t = 1 234» while a cursor is set. */
    Optional<String> cursorLabel() {
        OptionalLong cursor = panel.axis().cursor();
        return cursor.isPresent()
                ? Optional.of(Messages.format("waveform.cursorTime", TimeAxis.format(cursor.getAsLong())))
                : Optional.empty();
    }

    /** The axis ticks the ruler labels: none before t = 0, where nothing is ever recorded. */
    List<Long> visibleTicks() {
        return panel.axis().ticks().stream().filter(tick -> tick >= 0).toList();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return panel.rulerToolTip();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(100, RULER_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            ColorScheme scheme = panel.scheme();
            TimeAxis axis = panel.axis();
            graphics2D.setColor(scheme.diagramBackground());
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            graphics2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = graphics2D.getFontMetrics();
            int baseline = metrics.getAscent() + 2;
            graphics2D.setColor(scheme.diagramTrace());
            for (long tick : visibleTicks()) {
                int x = (int) Math.floor(axis.xOf(tick));
                graphics2D.drawLine(x, getHeight() - TICK_LENGTH, x, getHeight());
                graphics2D.drawString(TimeAxis.format(tick), x + 3, baseline);
            }
            String now = "t = " + TimeAxis.format(panel.displayed().time());
            int nowWidth = metrics.stringWidth(now);
            graphics2D.setColor(scheme.diagramBackground());
            graphics2D.fillRect(getWidth() - nowWidth - 2 * PADDING, 0, nowWidth + 2 * PADDING, getHeight());
            graphics2D.setColor(scheme.diagramTrace());
            graphics2D.drawString(now, getWidth() - nowWidth - PADDING, baseline);
            Optional<String> label = cursorLabel();
            if (label.isPresent()) {
                int x = (int) Math.floor(axis.xOf(axis.cursor().getAsLong()));
                int labelWidth = metrics.stringWidth(label.get());
                graphics2D.setColor(scheme.selection());
                graphics2D.drawLine(x, 0, x, getHeight());
                graphics2D.fillRect(x + 1, 0, labelWidth + 2 * PADDING, metrics.getHeight() + 2);
                graphics2D.setColor(scheme.diagramBackground());
                graphics2D.drawString(label.get(), x + 1 + PADDING, baseline);
            }
        } finally {
            graphics2D.dispose();
        }
    }
}
