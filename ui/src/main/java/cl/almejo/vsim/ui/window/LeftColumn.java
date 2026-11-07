package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The left column of an editor view (spec §5.3): the palette alone, or a panel above it (the circuit tree) as an
 * accordion. Each panel has a header («Circuito», «Paleta») whose click folds the panel to its header; the other panel
 * takes the space. Alt+1 / Alt+2 (catalog entries {@code view.circuitTree}, {@code view.palette}, rebindable, and Ver ▸
 * «Árbol del circuito» / «Paleta») work like the tool windows of an IDE: a folded panel opens and its tree gets the focus;
 * an open one gets the focus; an open one that has it folds and gives the focus back to the canvas. With both open they share a vertical split whose divider is remembered ({@code ui.circuitTree.split}). The
 * folded state belongs to the window ({@code WindowTabs} keeps the tabs of a window in step and saves it in the
 * session); here it is only shown and told to the fold listeners. EDT only.
 */
public final class LeftColumn extends JPanel {

    /** The two panels of the column. */
    public enum Section { CIRCUIT, PALETTE }

    private static final int DEFAULT_CIRCUIT_HEIGHT = 170;
    private static final String OPEN_ARROW = "▾ ";
    private static final String FOLDED_ARROW = "▸ ";

    private final AppPreferences preferences;
    private final KeyboardFocus focus;
    private final Runnable focusCanvas;
    private final Map<Section, Panel> panels = new EnumMap<>(Section.class);
    private final Map<Section, Action> toggles = new EnumMap<>(Section.class);
    private final Set<Section> folded = EnumSet.noneOf(Section.class);
    private final List<Runnable> foldListeners = new CopyOnWriteArrayList<>();
    private final JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
    /** Remembers the divider the user drags (not the one a hidden split gets while it is laid out). */
    private final PropertyChangeListener rememberDivider;

    /**
     * @param focus       where the focus is and how to ask for it (the view's)
     * @param focusCanvas gives the focus to the canvas, when a panel that had it folds
     */
    LeftColumn(JComponent palette, AppPreferences preferences, KeyboardFocus focus, Runnable focusCanvas) {
        super(new BorderLayout());
        Objects.requireNonNull(palette, "palette");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.focus = Objects.requireNonNull(focus, "focus");
        this.focusCanvas = Objects.requireNonNull(focusCanvas, "focusCanvas");
        this.rememberDivider = change -> {
            if (split.isShowing() || split.getHeight() > 0) {
                preferences.setCircuitTreeSplit(split.getDividerLocation());
            }
        };
        toggles.put(Section.CIRCUIT, toggle(Section.CIRCUIT, "action.circuitTree", KeyEvent.VK_1));
        toggles.put(Section.PALETTE, toggle(Section.PALETTE, "action.palette", KeyEvent.VK_2));
        split.setContinuousLayout(true);
        split.setResizeWeight(0);
        split.setBorder(BorderFactory.createEmptyBorder());
        panels.put(Section.PALETTE, new Panel(Messages.text("leftColumn.palette"), palette, toggles.get(
                Section.PALETTE), () -> flip(Section.PALETTE)));
        layOutPanels();
    }

    private Action toggle(Section section, String nameKey, int digit) {
        Action action = Actions.of(Messages.text(nameKey), KeyStroke.getKeyStroke(digit, InputEvent.ALT_DOWN_MASK),
                () -> showOrHide(section));
        action.putValue(Action.SELECTED_KEY, true);
        action.setEnabled(false);
        return action;
    }

    /**
     * Alt+1 / Alt+2: a folded panel opens with its tree focused; an open one without the focus gets it; an open one with
     * the focus inside folds and the canvas gets the focus. The Ver item stays checked while the panel is open (a click
     * on a checked item toggles it before this runs).
     */
    private void showOrHide(Section section) {
        if (isFolded(section)) {
            setFolded(section, false);
            focusInside(section);
        } else if (hasFocusInside(section)) {
            setFolded(section, true);
            focusCanvas.run();
        } else {
            focusInside(section);
        }
        toggles.get(section).putValue(Action.SELECTED_KEY, !isFolded(section));
    }

    private boolean hasFocusInside(Section section) {
        Panel panel = panels.get(section);
        return focus.owner().filter(owner -> SwingUtilities.isDescendingFrom(owner, panel)).isPresent();
    }

    /** Focuses what the panel's body names ({@link KeyboardFocus#TARGET_PROPERTY}: its tree), else the body. */
    private void focusInside(Section section) {
        JComponent body = panels.get(section).body;
        Object target = body.getClientProperty(KeyboardFocus.TARGET_PROPERTY);
        focus.request(target instanceof Component component ? component : body);
    }

    /**
     * Shows {@code component} above the palette under the header {@code title}; the returned runnable takes it away
     * again, leaving the palette alone (calling it twice does nothing).
     */
    public Runnable addAbovePalette(String title, JComponent component) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(component, "component");
        Panel circuit = new Panel(title, component, toggles.get(Section.CIRCUIT), () -> flip(Section.CIRCUIT));
        panels.put(Section.CIRCUIT, circuit);
        split.setDividerLocation(preferences.circuitTreeSplit().orElse(DEFAULT_CIRCUIT_HEIGHT));
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, rememberDivider);
        toggles.values().forEach(action -> action.setEnabled(true));
        layOutPanels();
        boolean[] removed = {false};
        return () -> {
            if (removed[0]) {
                return;
            }
            removed[0] = true;
            split.removePropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, rememberDivider);
            panels.remove(Section.CIRCUIT, circuit);
            circuit.dispose();
            toggles.values().forEach(action -> action.setEnabled(false));
            layOutPanels();
        };
    }

    /** The header text of {@code section}, when it shows one (headers show only with a panel above the palette). */
    public Optional<String> title(Section section) {
        if (!panels.containsKey(Section.CIRCUIT)) {
            return Optional.empty();
        }
        return Optional.ofNullable(panels.get(section)).map(panel -> panel.title);
    }

    /**
     * «Árbol del circuito» (Alt+1) or «Paleta» (Alt+2) in Ver: opens and focuses {@code section}, or folds it when it
     * has the focus ({@link #showOrHide}); selected while it is open; enabled only with a panel above the palette.
     */
    public Action toggleAction(Section section) {
        return toggles.get(section);
    }

    public boolean isFolded(Section section) {
        return folded.contains(section);
    }

    /** The folded panels, in declaration order. */
    public Set<Section> folded() {
        return Collections.unmodifiableSet(EnumSet.copyOf(folded));
    }

    private void flip(Section section) {
        setFolded(section, !isFolded(section));
    }

    /** Folds or unfolds {@code section}; the fold listeners hear a real change only. */
    public void setFolded(Section section, boolean fold) {
        if (isFolded(section) == fold) {
            return;
        }
        if (fold) {
            folded.add(section);
        } else {
            folded.remove(section);
        }
        toggles.get(section).putValue(Action.SELECTED_KEY, !fold);
        layOutPanels();
        foldListeners.forEach(Runnable::run);
    }

    /** Runs {@code listener} after every change of the folded panels; returns its removal. */
    public Runnable addFoldListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        foldListeners.add(listener);
        return () -> foldListeners.remove(listener);
    }

    /**
     * Places the panels: the palette alone without a panel above; else both in the split when open, a folded one as
     * its header at its end of the column (the other fills the rest), both headers on top when both are folded.
     */
    private void layOutPanels() {
        removeAll();
        // Never split.removeAll(): it also removes the divider, which then neither drags nor lays out the panels.
        split.setTopComponent(null);
        split.setBottomComponent(null);
        Panel circuit = panels.get(Section.CIRCUIT);
        Panel paletteSection = panels.get(Section.PALETTE);
        if (circuit == null) {
            paletteSection.showBody(true);
            paletteSection.header.setVisible(false);
            add(paletteSection, BorderLayout.CENTER);
        } else {
            boolean circuitOpen = !isFolded(Section.CIRCUIT);
            boolean paletteOpen = !isFolded(Section.PALETTE);
            circuit.showBody(circuitOpen);
            paletteSection.showBody(paletteOpen);
            paletteSection.header.setVisible(true);
            if (circuitOpen && paletteOpen) {
                split.setTopComponent(circuit);
                split.setBottomComponent(paletteSection);
                split.setDividerLocation(preferences.circuitTreeSplit().orElse(DEFAULT_CIRCUIT_HEIGHT));
                add(split, BorderLayout.CENTER);
            } else if (circuitOpen) {
                add(circuit, BorderLayout.CENTER);
                add(paletteSection, BorderLayout.SOUTH);
            } else if (paletteOpen) {
                add(circuit, BorderLayout.NORTH);
                add(paletteSection, BorderLayout.CENTER);
            } else {
                JPanel headers = new JPanel();
                headers.setLayout(new BoxLayout(headers, BoxLayout.Y_AXIS));
                headers.add(circuit);
                headers.add(paletteSection);
                add(headers, BorderLayout.NORTH);
            }
        }
        revalidate();
        repaint();
    }

    /** One panel of the accordion: its header (arrow, title, shortcut) over its body. */
    private static final class Panel extends JPanel {
        private final String title;
        private final JComponent body;
        private final Action toggle;
        private final Runnable headerClicked;
        private final JPanel header = new JPanel(new BorderLayout());
        private final JButton titleButton = new JButton();
        private final JLabel keyLabel = new JLabel();
        private final PropertyChangeListener toggleListener = change -> refreshHeader();

        /** @param headerClicked folds or unfolds the panel (a click on the header never moves the focus) */
        Panel(String title, JComponent body, Action toggle, Runnable headerClicked) {
            super(new BorderLayout());
            this.title = title;
            this.body = body;
            this.toggle = toggle;
            this.headerClicked = headerClicked;
            titleButton.putClientProperty("JButton.buttonType", "borderless");
            titleButton.setHorizontalAlignment(SwingConstants.LEADING);
            titleButton.setFocusable(true);
            titleButton.addActionListener(event -> headerClicked.run());
            keyLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8));
            header.add(titleButton, BorderLayout.CENTER);
            header.add(keyLabel, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);
            add(body, BorderLayout.CENTER);
            toggle.addPropertyChangeListener(toggleListener);
            applyTheme();
            refreshHeader();
        }

        void showBody(boolean shown) {
            body.setVisible(shown);
            refreshHeader();
        }

        /** Arrow and shortcut follow the fold and the user's key choice. */
        private void refreshHeader() {
            titleButton.setText((body.isVisible() ? OPEN_ARROW : FOLDED_ARROW) + title);
            Object stroke = toggle.getValue(Action.ACCELERATOR_KEY);
            keyLabel.setText(stroke instanceof KeyStroke keyStroke ? KeyBindings.describe(keyStroke) : "");
        }

        /** The header's colours and font come from the current theme: set again on every theme change. */
        @Override
        public void updateUI() {
            super.updateUI();
            if (titleButton == null) {
                return; // called by JPanel's constructor, before this class's fields exist
            }
            applyTheme();
        }

        private void applyTheme() {
            Font font = UIManager.getFont("Label.font");
            if (font != null) {
                titleButton.setFont(font.deriveFont(Font.BOLD));
            }
            Color muted = UIManager.getColor("Label.disabledForeground");
            keyLabel.setForeground(muted);
        }

        void dispose() {
            toggle.removePropertyChangeListener(toggleListener);
        }
    }
}
