package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.theme.ReadableColors;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The traces of the waveform panel. Click: select the row and put the cursor (snapped to an edge); hover: «t = … ·
 * Δt = …», after the whole name of the state under the pointer in a machine's state row; wheel: scroll time;
 * Ctrl+wheel: zoom around the pointer; ← / → with focus: previous / next edge of the selected row.
 */
final class WaveformPlot extends JComponent implements Scrollable {

    private static final int PREFERRED_WIDTH = 400;

    private final WaveformPanel panel;
    private boolean focused;

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
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                focused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                focused = false;
                repaint();
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
        return panel.hoverText(event.getX(), event.getY());
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
                    panel.scheme(), panel.formats(), panel.valueNames(), panel.valueColors());
            if (panel.channelIds().isEmpty()) {
                paintEmptyHint(graphics2D);
            }
            if (focused) {
                paintFocusRing(graphics2D);
            }
        } finally {
            graphics2D.dispose();
        }
    }

    /** «Right-click a wire or pin and choose “Show in waveforms”», with the real text of the context menu item. */
    String emptyHint() {
        return Messages.format("waveform.empty", Messages.text("waveform.menu.show"));
    }

    /** The hint's colour: readable over the scheme's chronogram background, not over the theme's. */
    static Color hintColor(ColorScheme scheme) {
        Color background = scheme.diagramBackground();
        Color trace = scheme.diagramTrace();
        if (ReadableColors.contrast(trace, background) >= ReadableColors.MINIMUM_TEXT_CONTRAST) {
            return trace;
        }
        return ReadableColors.contrast(Color.BLACK, background) >= ReadableColors.contrast(Color.WHITE, background)
                ? Color.BLACK : Color.WHITE;
    }

    private void paintEmptyHint(Graphics2D graphics) {
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            graphics.setFont(font);
        }
        FontMetrics metrics = graphics.getFontMetrics();
        String hint = emptyHint();
        graphics.setColor(hintColor(panel.scheme()));
        int x = Math.max(WaveformPainter.scaled(6), (getWidth() - metrics.stringWidth(hint)) / 2);
        int y = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
        graphics.drawString(hint, x, y);
    }

    private void paintFocusRing(Graphics2D graphics) {
        Color color = UIManager.getColor("Component.focusColor");
        if (color == null) {
            return;
        }
        int thickness = Math.max(2, WaveformPainter.scaled(2));
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(thickness));
        graphics.drawRect(thickness / 2, thickness / 2, getWidth() - thickness, getHeight() - thickness);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PREFERRED_WIDTH, Math.max(1, panel.channelIds().size()) * WaveformPainter.rowHeight());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRectangle, int orientation, int direction) {
        return WaveformPainter.rowHeight();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRectangle, int orientation, int direction) {
        return Math.max(WaveformPainter.rowHeight(), visibleRectangle.height);
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
