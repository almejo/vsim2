package cl.almejo.vsim.ui.toolbar;

import cl.almejo.vsim.ui.app.AppPreferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A toolbar button with a narrow ▾ beside it that picks the <em>mode</em> the button works in (for example the
 * screenshot button: «Ventana de VSim» or «Circuito visible»). The main part shows the current mode's icon and
 * description (tooltip); the ▾ opens a popup with a caption and one radio item per mode. The chosen mode is
 * remembered in {@link AppPreferences} under the button's preference id, so the next start finds it again; a stored
 * id that no longer exists falls back to the first mode.
 *
 * <p>Three variants share everything but the main part:
 * <ul>
 *   <li>{@link #forAction}: a plain button; pressing it runs the callback with the current mode. Choosing a mode in
 *   the ▾ only selects it.</li>
 *   <li>{@link #forTool}: the main part is a {@link JToggleButton} ({@link #mainButton()}) that can join the
 *   toolbar's tool {@link ButtonGroup}, so its selection follows the active tool. Pressing it runs the callback
 *   (typically activating the tool) and so does choosing a mode in the ▾, which therefore also activates the
 *   tool.</li>
 *   <li>{@link #forToggle}: the main part is a {@link JToggleButton} on an {@code Action} (its
 *   {@code SELECTED_KEY} is the state, its icon and tooltip are used as they are); choosing a mode in the ▾ only
 *   selects it and never presses.</li>
 * </ul>
 *
 * <p>Keyboard: the ▾ is focusable; Space and Enter (the button's own action) and Alt+Down open the popup. Colours of
 * the ▾ and the popup are read from the look and feel when painted or opened, so a theme change needs nothing here;
 * the mode icons are the caller's and must follow the theme themselves (the {@code Icons} ones do).
 */
public final class SplitButton extends JComponent {

    private static final int ARROW_WIDTH = 14;
    private static final int ARROW_HALF_WIDTH = 3;
    private static final int ARROW_HEIGHT = 3;
    private static final String TOOLBAR_BUTTON_TYPE = "toolBarButton";
    private static final String OPEN_MODES = "openModes";

    private final String preferenceId;
    private final String menuTitle;
    private final List<SplitButtonMode> modes;
    private final AppPreferences preferences;
    private final Consumer<SplitButtonMode> pressed;
    private final boolean toolVariant;
    /** For {@link #forToggle}: the main part is the action's own, so the mode never changes its icon or tooltip. */
    private final boolean toggleVariant;
    private final AbstractButton mainButton;
    private final JButton arrowButton = new JButton(new ArrowIcon());
    private final List<Consumer<SplitButtonMode>> modeListeners = new CopyOnWriteArrayList<>();
    private SplitButtonMode mode;

    private SplitButton(String preferenceId, String menuTitle, List<SplitButtonMode> modes,
                        AppPreferences preferences, Consumer<SplitButtonMode> pressed, boolean toolVariant,
                        Action toggle, String defaultModeId) {
        this.preferenceId = Objects.requireNonNull(preferenceId, "preferenceId");
        this.menuTitle = menuTitle;
        this.modes = List.copyOf(modes);
        if (this.modes.isEmpty()) {
            throw new IllegalArgumentException("A split button needs at least one mode");
        }
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.pressed = Objects.requireNonNull(pressed, "pressed");
        this.toolVariant = toolVariant;
        this.toggleVariant = toggle != null;
        mainButton = toggleVariant ? new JToggleButton(toggle) : toolVariant ? new JToggleButton() : new JButton();
        SplitButtonMode defaultMode = modeWithId(defaultModeId).orElse(this.modes.get(0));
        mode = preferences.splitButtonMode(preferenceId).flatMap(this::modeWithId).orElse(defaultMode);
        build();
        showMode();
    }

    /**
     * A plain button: a press runs {@code pressed} with the current mode.
     *
     * @param preferenceId key the chosen mode is remembered under ({@code splitButton.<preferenceId>})
     * @param menuTitle    the popup's caption; {@code null} for none
     * @param modes        at least one, the first is the default
     * @param pressed      what a press of the main button does
     */
    public static SplitButton forAction(String preferenceId, String menuTitle, List<SplitButtonMode> modes,
                                        AppPreferences preferences, Consumer<SplitButtonMode> pressed) {
        return new SplitButton(preferenceId, menuTitle, modes, preferences, pressed, false, null, null);
    }

    /**
     * A tool button: {@link #mainButton()} is a {@link JToggleButton} to add to the tool group. A press runs
     * {@code pressed} (typically activating the tool); choosing a mode in the ▾ also runs it. Arguments as in
     * {@link #forAction}.
     */
    public static SplitButton forTool(String preferenceId, String menuTitle, List<SplitButtonMode> modes,
                                      AppPreferences preferences, Consumer<SplitButtonMode> pressed) {
        return new SplitButton(preferenceId, menuTitle, modes, preferences, pressed, true, null, null);
    }

    /**
     * A toggle button: {@link #mainButton()} is a {@link JToggleButton} on {@code toggle}, whose
     * {@code SELECTED_KEY} is the state and whose icon and tooltip are the action's own. Choosing a mode in the ▾
     * only selects it: it never presses the button. The first mode is the default.
     */
    public static SplitButton forToggle(String preferenceId, String menuTitle, List<SplitButtonMode> modes,
                                        AppPreferences preferences, Action toggle) {
        return forToggle(preferenceId, menuTitle, modes, preferences, toggle, null);
    }

    /** Like {@link #forToggle(String, String, List, AppPreferences, Action)}, with {@code defaultModeId} the default. */
    public static SplitButton forToggle(String preferenceId, String menuTitle, List<SplitButtonMode> modes,
                                        AppPreferences preferences, Action toggle, String defaultModeId) {
        Objects.requireNonNull(toggle, "toggle");
        return new SplitButton(preferenceId, menuTitle, modes, preferences, ignored -> {
        }, false, toggle, defaultModeId);
    }

    /** The current mode. */
    public SplitButtonMode mode() {
        return mode;
    }

    /**
     * Makes {@code id} the current mode: remembers it, shows its icon and tooltip and tells the mode listeners. It
     * does not run the press callback.
     *
     * @throws IllegalArgumentException when no mode has that id
     */
    public void setMode(String id) {
        SplitButtonMode chosen = modeWithId(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown mode of " + preferenceId + ": " + id));
        mode = chosen;
        preferences.setSplitButtonMode(preferenceId, chosen.id());
        showMode();
        for (Consumer<SplitButtonMode> listener : modeListeners) {
            listener.accept(chosen);
        }
    }

    /** The main part: a {@link JButton}, or a {@link JToggleButton} for {@link #forTool}. */
    public AbstractButton mainButton() {
        return mainButton;
    }

    /** Calls {@code listener} with the new mode each time {@link #setMode} changes it; the result removes it. */
    public Runnable addModeListener(Consumer<SplitButtonMode> listener) {
        Objects.requireNonNull(listener, "listener");
        modeListeners.add(listener);
        return () -> modeListeners.remove(listener);
    }

    /** The ▾ button (focusable; its press opens the popup). */
    JButton arrowButton() {
        return arrowButton;
    }

    /** A fresh popup for the current state: the caption (when there is a title) and a radio item per mode. */
    public JPopupMenu popupMenu() {
        JPopupMenu menu = new JPopupMenu();
        if (menuTitle != null) {
            JMenuItem caption = new JMenuItem(menuTitle);
            caption.setEnabled(false);
            menu.add(caption);
        }
        ButtonGroup group = new ButtonGroup();
        for (SplitButtonMode candidate : modes) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(candidate.text(), candidate.icon(),
                    candidate == mode);
            item.addActionListener(event -> chooseFromMenu(candidate));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mainButton.setEnabled(enabled);
        arrowButton.setEnabled(enabled);
    }

    private java.util.Optional<SplitButtonMode> modeWithId(String id) {
        if (id == null) {
            return java.util.Optional.empty();
        }
        return modes.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
    }

    private void chooseFromMenu(SplitButtonMode chosen) {
        setMode(chosen.id());
        if (toolVariant) {
            pressed.accept(chosen);
        }
    }

    private void showMode() {
        if (toggleVariant) {
            return;
        }
        mainButton.setIcon(mode.icon());
        mainButton.setToolTipText(mode.description());
        mainButton.getAccessibleContext().setAccessibleName(mode.description());
    }

    private void openPopup() {
        popupMenu().show(this, 0, getHeight());
    }

    /** Never wider or taller than it wants: a toolbar's box layout would stretch a component with no maximum. */
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    private void build() {
        setLayout(new BorderLayout());
        if (!toggleVariant) {
            mainButton.addActionListener(event -> pressed.accept(mode));
        }
        mainButton.putClientProperty("JButton.buttonType", TOOLBAR_BUTTON_TYPE);
        arrowButton.putClientProperty("JButton.buttonType", TOOLBAR_BUTTON_TYPE);
        arrowButton.setFocusable(true);
        arrowButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
        arrowButton.setPreferredSize(new Dimension(ARROW_WIDTH, mainButton.getPreferredSize().height));
        arrowButton.addActionListener(event -> openPopup());
        arrowButton.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, KeyEvent.ALT_DOWN_MASK), OPEN_MODES);
        arrowButton.getActionMap().put(OPEN_MODES, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openPopup();
            }
        });
        add(mainButton, BorderLayout.CENTER);
        add(arrowButton, BorderLayout.EAST);
    }

    /** A small ▾ drawn in the button's foreground when painted, so it follows the theme. */
    private static final class ArrowIcon implements Icon {

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D painter = (Graphics2D) graphics.create();
            try {
                painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = component.isEnabled() ? component.getForeground()
                        : javax.swing.UIManager.getColor("Button.disabledText");
                painter.setColor(color == null ? component.getForeground() : color);
                int centre = x + getIconWidth() / 2;
                int middle = y + getIconHeight() / 2;
                painter.fillPolygon(new int[]{centre - ARROW_HALF_WIDTH, centre + ARROW_HALF_WIDTH, centre},
                        new int[]{middle - ARROW_HEIGHT / 2, middle - ARROW_HEIGHT / 2, middle + ARROW_HEIGHT / 2 + 1},
                        3);
            } finally {
                painter.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return ARROW_HALF_WIDTH * 2 + 2;
        }

        @Override
        public int getIconHeight() {
            return ARROW_HEIGHT + 3;
        }
    }
}
