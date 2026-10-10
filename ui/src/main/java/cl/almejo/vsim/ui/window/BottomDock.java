package cl.almejo.vsim.ui.window;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Vertical split between the canvas (top) and a docked panel spanning its width (the waveform panel, M6-A). Hidden,
 * with no divider, until {@link #showDock()}; it opens {@link #DEFAULT_HEIGHT} px tall, or as tall as the user last
 * left it, and the canvas takes the rest when the window is resized.
 */
public final class BottomDock extends JSplitPane {

    public static final int DEFAULT_HEIGHT = 200;
    static final int DIVIDER_SIZE = InspectorSplit.DIVIDER_SIZE;

    private final JPanel holder = new JPanel(new BorderLayout());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean dockShowing;
    private boolean placeOnNextLayout;
    private int dockHeight = DEFAULT_HEIGHT;

    public BottomDock(Component top) {
        super(VERTICAL_SPLIT, top, null);
        holder.setMinimumSize(new Dimension(0, 0));
        holder.setVisible(false);
        setBottomComponent(holder);
        setContinuousLayout(true);
        setResizeWeight(1);
        setDividerSize(0);
    }

    /** What the dock shows when open; null empties it. */
    public void setContent(JComponent content) {
        holder.removeAll();
        if (content != null) {
            holder.add(content, BorderLayout.CENTER);
        }
        holder.revalidate();
        holder.repaint();
    }

    public Optional<JComponent> content() {
        return holder.getComponentCount() == 0 ? Optional.empty() : Optional.of((JComponent) holder.getComponent(0));
    }

    public void showDock() {
        if (dockShowing) {
            return;
        }
        dockShowing = true;
        holder.setVisible(true);
        setDividerSize(DIVIDER_SIZE);
        placeOnNextLayout = true;
        if (getHeight() > 0) {
            placeDivider();
        }
        revalidate();
        listeners.forEach(Runnable::run);
    }

    public void hideDock() {
        if (!dockShowing) {
            return;
        }
        if (holder.getHeight() > 0) {
            dockHeight = holder.getHeight();
        }
        dockShowing = false;
        holder.setVisible(false);
        setDividerSize(0);
        revalidate();
        listeners.forEach(Runnable::run);
    }

    /** Whether the docked panel is open (not {@code isShowing()}, which is AWT's "on screen"). */
    public boolean isDockShowing() {
        return dockShowing;
    }

    /** Runs {@code listener} after every {@link #showDock()} or {@link #hideDock()} that changed something. */
    public void addDockListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeDockListener(Runnable listener) {
        listeners.remove(listener);
    }

    @Override
    public void doLayout() {
        if (getHeight() > 0) {
            if (dockShowing && placeOnNextLayout) {
                placeDivider();
            } else if (!dockShowing) {
                int bottom = Math.max(0, getHeight() - getInsets().bottom);
                if (getDividerLocation() != bottom) {
                    setDividerLocation(bottom);
                }
            }
        }
        super.doLayout();
    }

    private void placeDivider() {
        placeOnNextLayout = false;
        int topHeight = getHeight() - getInsets().bottom - getDividerSize() - dockHeight;
        setDividerLocation(Math.max(getHeight() / 3, topHeight));
    }
}
