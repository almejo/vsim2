package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.app.Listeners;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Objects;
import java.util.Optional;

/**
 * Vertical split between the canvas (top) and a docked panel spanning its width (the waveform panel, M6-A). Hidden,
 * with no divider, until {@link #showDock()}; it opens {@link #DEFAULT_HEIGHT} px tall, or as tall as the user last
 * left it, and the canvas takes the rest when the window is resized.
 * <p>
 * Presentation mode can {@linkplain #lendContent() borrow the content} to show it behind the bottom screen edge: the
 * dock then stays open (Ver ▸ Formas de onda checked) but gives the canvas all its height until
 * {@link #returnContent()}.
 * <p>
 * Another component can {@linkplain #contentInstead(JComponent) show instead of the content} for a while (a machine's
 * test table instead of its wave strip): the content is set aside and comes back when it goes.
 */
public final class BottomDock extends JSplitPane {

    public static final int DEFAULT_HEIGHT = 200;
    static final int DIVIDER_SIZE = InspectorSplit.DIVIDER_SIZE;

    private final JPanel holder = new JPanel(new BorderLayout());
    private final Listeners<Runnable> listeners = new Listeners<>();
    private final Listeners<Runnable> contentListeners = new Listeners<>();
    /** What {@link #setContent} gave; null for none. */
    private JComponent ownContent;
    /** What shows instead of {@link #ownContent} ({@link #contentInstead}); null while the content itself shows. */
    private JComponent instead;
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

    /**
     * What the dock shows when open; null empties it. While another component shows {@linkplain #contentInstead
     * instead}, the new content waits until it goes.
     */
    public void setContent(JComponent content) {
        returnContent();
        ownContent = content;
        fillHolder();
    }

    /**
     * Shows {@code replacement} where the content shows (in the dock, or wherever it is lent) until the returned
     * runnable runs; the content is set aside meanwhile and {@link #content()} answers {@code replacement}. The dock
     * stays open or closed as it was, and keeps its height. Installing another one replaces this one; then this one's
     * runnable does nothing. Running the runnable shows the content again; running it twice does nothing more.
     * <p>
     * While the content is {@linkplain #lendContent() lent}, the borrower holds the old component: the dock fires
     * its {@linkplain #addContentListener content listeners}, and a borrower that wants to show what the dock shows
     * now {@linkplain #returnContent() returns} it (which takes the old one out of the borrower's hands) and lends
     * again. EDT only.
     */
    public Runnable contentInstead(JComponent replacement) {
        Objects.requireNonNull(replacement, "replacement");
        showInstead(replacement);
        return () -> {
            if (instead == replacement) {
                showInstead(null);
            }
        };
    }

    /**
     * Runs {@code listener} after what the dock shows changed through {@link #contentInstead} (installed or
     * removed); returns its removal. Presentation mode follows it to show the new component behind the bottom edge.
     */
    public Runnable addContentListener(Runnable listener) {
        return contentListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** What the dock shows: the content, or what shows instead of it; also while it is lent. */
    public Optional<JComponent> content() {
        return Optional.ofNullable(shown());
    }

    private JComponent shown() {
        return instead != null ? instead : ownContent;
    }

    private void showInstead(JComponent replacement) {
        instead = replacement;
        if (lent == null) {
            fillHolder();
        }
        contentListeners.fire(Runnable::run);
    }

    /** Puts what the dock shows in its holder (while nothing is lent). */
    private void fillHolder() {
        holder.removeAll();
        JComponent shown = shown();
        if (shown != null) {
            if (shown.getParent() != null) {
                shown.getParent().remove(shown);
            }
            holder.add(shown, BorderLayout.CENTER);
        }
        holder.revalidate();
        holder.repaint();
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
     * Takes what the dock shows (the content, or what shows {@linkplain #contentInstead instead}) out of the dock to
     * show it elsewhere (presentation mode puts it behind the bottom edge): the dock stays open as far as Ver ▸ Formas
     * de onda knows, but the canvas gets the whole height. Empty when there is no content or it is already lent.
     */
    public Optional<JComponent> lendContent() {
        if (lent != null || shown() == null) {
            return Optional.empty();
        }
        rememberHeight();
        lent = shown();
        holder.removeAll();
        closeHolder();
        revalidate();
        repaint();
        return Optional.of(lent);
    }

    /**
     * Puts the lent content back in the dock; if the dock is open it opens as tall as {@link #dockHeight()}. When
     * what the dock shows changed meanwhile ({@link #contentInstead}), the lent component leaves the borrower and the
     * one shown now goes in the dock.
     */
    public void returnContent() {
        if (lent == null) {
            return;
        }
        JComponent content = lent;
        lent = null;
        if (content.getParent() != null) {
            content.getParent().remove(content);
        }
        fillHolder();
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
