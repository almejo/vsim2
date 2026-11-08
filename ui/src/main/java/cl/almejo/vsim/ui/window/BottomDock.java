package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.Listeners;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Optional;

/**
 * Vertical split between the canvas (top) and a docked panel spanning its width (the waveform panel, M6-A). Hidden,
 * with no divider, until {@link #showDock()}; it opens {@link #DEFAULT_HEIGHT} px tall, or as tall as the user last
 * left it, and the canvas takes the rest when the window is resized.
 * <p>
 * Presentation mode can {@linkplain #lendContent() borrow the content} to show it behind the bottom screen edge: the
 * dock then stays open (Ver ▸ Formas de onda checked) but gives the canvas all its height until
 * {@link #returnContent()}.
 */
public final class BottomDock extends JSplitPane {

    public static final int DEFAULT_HEIGHT = 200;
    static final int DIVIDER_SIZE = InspectorSplit.DIVIDER_SIZE;

    private final JPanel holder = new JPanel(new BorderLayout());
    private final Listeners<Runnable> listeners = new Listeners<>();
    private boolean dockShowing;
    private boolean placeOnNextLayout;
    private int dockHeight = DEFAULT_HEIGHT;
    /** The content while it is shown elsewhere (presentation mode's bottom edge); null while it is in the dock. */
    private JComponent lent;

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
        returnContent();
        holder.removeAll();
        if (content != null) {
            holder.add(content, BorderLayout.CENTER);
        }
        holder.revalidate();
        holder.repaint();
    }

    /** The dock's content, also while it is lent. */
    public Optional<JComponent> content() {
        if (lent != null) {
            return Optional.of(lent);
        }
        return holder.getComponentCount() == 0 ? Optional.empty() : Optional.of((JComponent) holder.getComponent(0));
    }

    public void showDock() {
        if (dockShowing) {
            return;
        }
        dockShowing = true;
        if (lent == null) {
            openHolder();
        }
        revalidate();
        listeners.fire(Runnable::run);
    }

    public void hideDock() {
        if (!dockShowing) {
            return;
        }
        rememberHeight();
        dockShowing = false;
        closeHolder();
        revalidate();
        listeners.fire(Runnable::run);
    }

    /** Whether the docked panel is open (not {@code isShowing()}, which is AWT's "on screen"). */
    public boolean isDockShowing() {
        return dockShowing;
    }

    /**
     * How tall the docked panel is: its height on screen while it shows in the dock, otherwise the height it opens
     * with.
     */
    public int dockHeight() {
        rememberHeight();
        return dockHeight;
    }

    /** The docked panel opens (or, when open in the dock, is laid out again) {@code height} px tall. */
    public void setDockHeight(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("the dock's height must be positive: " + height);
        }
        dockHeight = height;
        if (dockShowing && lent == null) {
            placeOnNextLayout = true;
            revalidate();
        }
    }

    /**
     * Takes the content out of the dock to show it elsewhere (presentation mode puts it behind the bottom edge): the
     * dock stays open as far as Ver ▸ Formas de onda knows, but the canvas gets the whole height. Empty when there is
     * no content or it is already lent.
     */
    public Optional<JComponent> lendContent() {
        if (lent != null || holder.getComponentCount() == 0) {
            return Optional.empty();
        }
        rememberHeight();
        lent = (JComponent) holder.getComponent(0);
        holder.removeAll();
        closeHolder();
        revalidate();
        repaint();
        return Optional.of(lent);
    }

    /** Puts the lent content back in the dock; if the dock is open it opens as tall as {@link #dockHeight()}. */
    public void returnContent() {
        if (lent == null) {
            return;
        }
        JComponent content = lent;
        lent = null;
        if (content.getParent() != null) {
            content.getParent().remove(content);
        }
        holder.add(content, BorderLayout.CENTER);
        if (dockShowing) {
            // placed at the next layout, when the dock has its final size (leaving the mode shrinks it)
            holder.setVisible(true);
            setDividerSize(DIVIDER_SIZE);
            placeOnNextLayout = true;
        }
        revalidate();
        repaint();
    }

    /** Whether the content is shown elsewhere ({@link #lendContent()}). */
    public boolean isContentLent() {
        return lent != null;
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
            if (isHolderOpen() && placeOnNextLayout) {
                placeDivider();
            } else if (!isHolderOpen()) {
                int bottom = Math.max(0, getHeight() - getInsets().bottom);
                if (getDividerLocation() != bottom) {
                    setDividerLocation(bottom);
                }
            }
        }
        super.doLayout();
    }

    /** The panel shows in the dock: open and not lent. */
    private boolean isHolderOpen() {
        return dockShowing && lent == null;
    }

    private void openHolder() {
        holder.setVisible(true);
        setDividerSize(DIVIDER_SIZE);
        placeOnNextLayout = true;
        if (getHeight() > 0) {
            placeDivider();
        }
    }

    private void closeHolder() {
        holder.setVisible(false);
        setDividerSize(0);
    }

    /** Notes the panel's height on screen, the one it reopens with (only while it shows in the dock). */
    private void rememberHeight() {
        if (isHolderOpen() && holder.isVisible() && holder.getHeight() > 0 && !placeOnNextLayout) {
            dockHeight = holder.getHeight();
        }
    }

    private void placeDivider() {
        placeOnNextLayout = false;
        int topHeight = getHeight() - getInsets().bottom - getDividerSize() - dockHeight;
        setDividerLocation(Math.max(getHeight() / 3, topHeight));
    }
}
