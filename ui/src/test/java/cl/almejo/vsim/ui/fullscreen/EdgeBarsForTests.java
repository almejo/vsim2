package cl.almejo.vsim.ui.fullscreen;

import cl.almejo.vsim.ui.canvas.ManualFrameTicker;

import javax.swing.JRootPane;

/** Edge bars over a headless root pane, for tests outside this package that lay a view out for presentation mode. */
public final class EdgeBarsForTests {

    private EdgeBarsForTests() {
    }

    /** Bars over a {@code width} × {@code height} root pane, without animation. */
    public static EdgeBars over(int width, int height) {
        JRootPane area = new JRootPane();
        area.setSize(width, height);
        area.doLayout();
        area.getLayeredPane().setSize(width, height);
        ManualFrameTicker ticker = new ManualFrameTicker();
        return new EdgeBars(area, ticker, ticker::now, () -> false);
    }
}
