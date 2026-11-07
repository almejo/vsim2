package cl.almejo.vsim.ui.window;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * Split between the canvas and the {@link InspectorPanel}. The panel opens at its remembered width
 * ({@link InspectorState#width()}) measured from the right edge, and keeps that width when the window is resized (the
 * canvas takes the difference). Only dragging the divider with the mouse changes the remembered width (a width the
 * split had to cap so that the canvas keeps its minimum is not the user's); a double click on it closes the panel. No one-touch arrows: the panel opens and closes from its strip. Closed, the panel is hidden and the divider
 * too, so the canvas takes the whole split.
 */
final class InspectorSplit extends JSplitPane {

    /** Wide enough to see and grab; the look and feel default (5 px in FlatLaf) is easy to miss. */
    static final int DIVIDER_SIZE = 8;
    /**
     * In a window too narrow for both, the canvas keeps this much (before UI scaling) and the panel gets what is left:
     * the canvas is what the window is for.
     */
    static final int MINIMUM_CANVAS_WIDTH = 400;

    private final InspectorPanel inspector;
    /**
     * Closes the panel on a double click and tells when the user holds the divider. Added after the divider's own drag
     * handler, so on release that handler has already moved the divider to its final place.
     */
    private final MouseListener dividerMouse = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)) {
                dividerHeld = true;
            }
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            if (dividerHeld) {
                rememberTheDraggedWidth();
                dividerHeld = false;
            }
        }

        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                inspector.close();
            }
        }
    };
    /** Whether the panel showed the last time the split followed it. */
    private boolean shown;
    /** The width the divider was last placed for, or the one the user dragged it to since. */
    private int placedWidth;
    private boolean placeOnNextLayout;
    /** Whether the user holds the divider with the mouse: only then a new divider location is the user's width. */
    private boolean dividerHeld;
    /** Whether the split itself is moving the divider (for the panel's width or a resized window). */
    private boolean placing;
    /** The split's width at the last layout: a layout at another width is the window resizing, not a drag. */
    private int widthAtLastLayout = -1;

    InspectorSplit(Component canvas, InspectorPanel inspector) {
        super(HORIZONTAL_SPLIT, canvas, inspector);
        this.inspector = inspector;
        setContinuousLayout(true);
        setResizeWeight(1);
        setOneTouchExpandable(false);
        setDividerSize(0); // no divider while the panel is closed or has no section yet
        listenToTheDivider();
        addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, event -> {
            if (dividerHeld && !placing) {
                rememberTheDraggedWidth();
            }
        });
        inspector.onPresentationChange(this::followInspector);
        followInspector();
    }

    /** The look and feel builds a new divider on every theme change: the double click goes on that one too. */
    @Override
    public void updateUI() {
        super.updateUI();
        listenToTheDivider();
    }

    private void listenToTheDivider() {
        dividerHeld = false;
        if (dividerMouse != null && getUI() instanceof BasicSplitPaneUI basic && basic.getDivider() != null) {
            basic.getDivider().removeMouseListener(dividerMouse);
            basic.getDivider().addMouseListener(dividerMouse);
        }
    }

    /** Shows or hides the divider and places it for the panel's width when the panel opens or is given another. */
    private void followInspector() {
        boolean nowShown = inspector.isOpen();
        if (nowShown == shown && (!nowShown || inspector.width() == placedWidth)) {
            return;
        }
        shown = nowShown;
        if (nowShown) {
            setDividerSize(DIVIDER_SIZE);
            placedWidth = inspector.width();
            placeOnNextLayout = true;
            if (getWidth() > 0) {
                placeDivider();
            }
        } else {
            setDividerSize(0);
        }
        revalidate();
    }

    @Override
    public void doLayout() {
        if (shown && getWidth() != widthAtLastLayout) {
            placeOnNextLayout = true; // the window was resized: the panel keeps its width, the canvas takes the rest
        }
        widthAtLastLayout = getWidth();
        if (placeOnNextLayout && getWidth() > 0) {
            placeDivider();
        }
        super.doLayout();
    }

    private void placeDivider() {
        placeOnNextLayout = false;
        int available = getWidth() - getInsets().left - getInsets().right - getDividerSize();
        int canvasWidth = available - placedWidth;
        placing = true;
        try {
            setDividerLocation(getInsets().left + Math.max(Math.min(UIScale.scale(MINIMUM_CANVAS_WIDTH), available),
                    canvasWidth));
        } finally {
            placing = false;
        }
    }

    /** The user dragged the divider: the panel's width right of it is the one to remember. */
    private void rememberTheDraggedWidth() {
        int width = getWidth() - getInsets().right - getDividerLocation() - getDividerSize();
        if (!inspector.isOpen() || width <= 0 || width == placedWidth) {
            return;
        }
        placedWidth = width;
        inspector.rememberWidth(width);
    }
}
