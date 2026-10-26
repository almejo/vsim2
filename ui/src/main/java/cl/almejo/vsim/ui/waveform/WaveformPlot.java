package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.Keys;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The traces of the waveform panel. Click: select the row and put the cursor (snapped to an edge); hover: «t = … ·
 * Δt = …»; wheel: scroll time; Ctrl+wheel: zoom around the pointer; ← / → with focus: previous / next edge of the
 * selected row.
 */
final class WaveformPlot extends JComponent implements Scrollable {

    private static final int PREFERRED_WIDTH = 400;

    private final WaveformPanel panel;

    WaveformPlot(WaveformPanel panel) {
        this.panel = panel;
        setOpaque(true);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        ToolTipManager.sharedInstance().registerComponent(this);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(event)) {
                    panel.probeAt(event.getY()).ifPresent(panel::selectProbe);
                    panel.placeCursor(event.getX());
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                panel.plotResized(getWidth());
            }
        });
        addMouseWheelListener(event -> panel.wheel(event.getX(), event.getPreciseWheelRotation(),
                event.isControlDown() || event.isMetaDown()));
        bindKey(KeyEvent.VK_LEFT, "previousEdge", -1);
        bindKey(KeyEvent.VK_RIGHT, "nextEdge", 1);
    }

    private void bindKey(int keyCode, String name, int direction) {
        Keys.bind(this, WHEN_FOCUSED, KeyStroke.getKeyStroke(keyCode, 0), name,
                Actions.of(name, () -> panel.moveCursorToEdge(direction)));
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return panel.hoverText(event.getX());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        TimeAxis axis = panel.axis();
        axis.setWidth(getWidth());
        axis.follow(panel.displayed().time());
        // Painting on a copy: WaveformPainter leaves its font, color and stroke on the graphics it is given.
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            graphics2D.setColor(panel.scheme().diagramBackground());
            graphics2D.fillRect(0, 0, getWidth(), getHeight());
            WaveformPainter.paint(graphics2D, panel.displayed(), panel.channelIds(), axis, getWidth(),
                    panel.scheme());
        } finally {
            graphics2D.dispose();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PREFERRED_WIDTH, Math.max(1, panel.channelIds().size()) * WaveformPainter.ROW_HEIGHT);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRectangle, int orientation, int direction) {
        return WaveformPainter.ROW_HEIGHT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRectangle, int orientation, int direction) {
        return Math.max(WaveformPainter.ROW_HEIGHT, visibleRectangle.height);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }
}
