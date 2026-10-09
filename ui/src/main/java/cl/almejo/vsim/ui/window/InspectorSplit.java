package cl.almejo.vsim.ui.window;

import javax.swing.JSplitPane;
import java.awt.Component;

/**
 * Split between the canvas and the {@link InspectorPanel}. The inspector opens at
 * {@link InspectorPanel#PREFERRED_WIDTH} measured from the right edge, the first time the split has a width,
 * and keeps that width when the window is resized (the canvas takes the difference).
 */
final class InspectorSplit extends JSplitPane {

    /** Wide enough to see and grab; the look and feel default (5 px in FlatLaf) is easy to miss. */
    static final int DIVIDER_SIZE = 8;

    private boolean openOnNextLayout;

    InspectorSplit(Component canvas, InspectorPanel inspector) {
        super(HORIZONTAL_SPLIT, canvas, inspector);
        setContinuousLayout(true);
        setResizeWeight(1);
        setOneTouchExpandable(true);
        setDividerSize(0); // no divider while the inspector is hidden; InspectorPanel opens it with its first tab
    }

    /** Shows the divider and gives the inspector its preferred width at the next layout (or now, if laid out). */
    void openInspector() {
        setDividerSize(DIVIDER_SIZE);
        openOnNextLayout = true;
        if (getWidth() > 0) {
            placeDivider();
        }
        revalidate();
    }

    @Override
    public void doLayout() {
        if (openOnNextLayout && getWidth() > 0) {
            placeDivider();
        }
        super.doLayout();
    }

    private void placeDivider() {
        openOnNextLayout = false;
        int canvasWidth = getWidth() - getInsets().right - getDividerSize() - InspectorPanel.PREFERRED_WIDTH;
        setDividerLocation(Math.max(getWidth() / 3, canvasWidth));
    }
}
