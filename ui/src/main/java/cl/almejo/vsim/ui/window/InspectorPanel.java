package cl.almejo.vsim.ui.window;

import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.theme.ThemeColors;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The right panel of an editor view (option A of the 2026-11-06 mockup): a header (the section's icon, its title and a
 * close button) over one section at a time, and an {@link InspectorStrip} of section icons that the view puts on its
 * right edge, outside the split, so it stays when the panel is closed. Sections are known by id ({@link #PROPERTIES},
 * {@link #PROBLEMS}, {@link #NOTES}, {@link #MEMORY_MAP}); the strip shows them in the order they were added.
 * <p>
 * A click on another icon (or {@link #toggleTab} for another section) switches; on the open section's icon it closes
 * the panel and gives the focus back to the canvas. Closing keeps the section and the width ({@link InspectorState}),
 * so reopening looks as it did. Badges on the icons (a count) stay visible while the panel is closed. EDT only.
 */
public final class InspectorPanel extends JPanel {

    /** The width a new window's panel opens at, before UI scaling. */
    public static final int PREFERRED_WIDTH = 280;
    /** Narrower than this the divider does not go: closing is the strip's job, not a drag to nothing. */
    static final int MINIMUM_WIDTH = 160;

    public static final String PROPERTIES = "properties";
    public static final String PROBLEMS = "problems";
    public static final String NOTES = "notes";
    public static final String MEMORY_MAP = "memoryMap";

    public static final String PROPERTIES_ICON = "inspector-properties";
    public static final String PROBLEMS_ICON = "inspector-problems";
    public static final String NOTES_ICON = "sticky-note-tool";
    public static final String MEMORY_MAP_ICON = "inspector-memory-map";
    static final String CLOSE_ICON = "panel-close";
    static final String OPEN_ICON = "panel-open";

    /** Every icon the panel and its strip use, by file name under {@code icons/}. */
    public static List<String> iconNames() {
        return List.of(PROPERTIES_ICON, PROBLEMS_ICON, NOTES_ICON, MEMORY_MAP_ICON, CLOSE_ICON, OPEN_ICON);
    }

    /** How a badge reads: errors red, warnings amber, a plain count (the notes) grey. */
    public enum Badge { ERROR, WARNING, QUIET }

    /** A badge as it shows on a section's icon. */
    public record ShownBadge(int count, Badge kind) {
        public ShownBadge {
            Objects.requireNonNull(kind, "kind");
            if (count <= 0) {
                throw new IllegalArgumentException("a shown badge counts at least one: " + count);
            }
        }
    }

    private record Section(String id, String title, String iconName, JComponent content) { }

    private final KeyboardFocus focus;
    private final Runnable focusCanvas;
    private final Map<String, Section> sections = new LinkedHashMap<>();
    private final Map<String, ShownBadge> badges = new HashMap<>();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final Header header = new Header();
    private final InspectorStrip strip;
    private final Listeners<Runnable> stateListeners = new Listeners<>();
    /** The split, told whenever the panel shows, hides or changes width. */
    private final Listeners<Runnable> presentationListeners = new Listeners<>();
    private InspectorState state = InspectorState.initial();

    /**
     * @param focus       where the focus is and how to ask for it (the view's)
     * @param focusCanvas gives the focus to the canvas, when the panel closes
     */
    public InspectorPanel(KeyboardFocus focus, Runnable focusCanvas) {
        super(new BorderLayout());
        this.focus = Objects.requireNonNull(focus, "focus");
        this.focusCanvas = Objects.requireNonNull(focusCanvas, "focusCanvas");
        this.strip = new InspectorStrip(this::toggleTab, this::openOrClose);
        add(header, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
        refresh();
    }

    /** Adds the section {@code id}, shown as {@code title} with the icon {@code iconName} on the strip and header. */
    public void addTab(String id, String title, String iconName, JComponent content) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(iconName, "iconName");
        Objects.requireNonNull(content, "content");
        if (sections.containsKey(id)) {
            throw new IllegalArgumentException("duplicate inspector section " + id);
        }
        sections.put(id, new Section(id, title, iconName, content));
        cards.add(content, id);
        strip.addSection(id, title, iconName);
        refresh();
    }

    /** Shows {@code count} on the icon of section {@code id} as {@code kind}; 0 hides the badge. */
    public void setBadge(String id, int count, Badge kind) {
        section(id);
        Objects.requireNonNull(kind, "kind");
        if (count < 0) {
            throw new IllegalArgumentException("a badge cannot count below zero: " + count);
        }
        if (count == 0) {
            badges.remove(id);
        } else {
            badges.put(id, new ShownBadge(count, kind));
        }
        strip.setBadge(id, Optional.ofNullable(badges.get(id)));
    }

    /** The badge on the icon of section {@code id}; empty when it shows none. */
    public Optional<ShownBadge> badge(String id) {
        section(id);
        return Optional.ofNullable(badges.get(id));
    }

    /** The title of section {@code id} («Propiedades»); empty when there is no such section. */
    public Optional<String> title(String id) {
        return Optional.ofNullable(sections.get(id)).map(Section::title);
    }

    /**
     * Lets the strip's tooltip of section {@code id} name the key of {@code action} («Propiedades · Alt+3»), and
     * follow it when the user binds another key.
     */
    public void showShortcut(String id, Action action) {
        section(id);
        strip.showShortcut(id, Objects.requireNonNull(action, "action"));
    }

    /** Opens the panel on section {@code id} and gives that section the focus. */
    public void showTab(String id) {
        section(id);
        setState(state.withOpen(true).withSelectedTab(id));
        focusSection(id);
    }

    /**
     * What a section's key or icon does: a closed panel opens on that section (focused); the open section closes the
     * panel and the canvas gets the focus; another section is switched to.
     */
    public void toggleTab(String id) {
        section(id);
        if (isOpen() && selectedTab().filter(id::equals).isPresent()) {
            close();
        } else {
            showTab(id);
        }
    }

    /** Closes the panel (the strip stays) and gives the focus back to the canvas. */
    public void close() {
        setState(state.withOpen(false));
        focusCanvas.run();
    }

    /** Whether the panel shows: open, and with at least one section. */
    public boolean isOpen() {
        return state.open() && !sections.isEmpty();
    }

    /** The id of the section the panel shows (or would show, when closed); empty while it has none. */
    public Optional<String> selectedTab() {
        if (sections.containsKey(state.selectedTab())) {
            return Optional.of(state.selectedTab());
        }
        return sections.keySet().stream().findFirst();
    }

    /** Open or closed, the section and the width; a section restored before it was added is kept as asked. */
    public InspectorState state() {
        return state;
    }

    /** Takes {@code restored} as it is (a session, another tab of the window); moves no focus. */
    public void restore(InspectorState restored) {
        setState(Objects.requireNonNull(restored, "restored"));
    }

    /**
     * {@code listener} hears every change of {@link #state()}: opened, closed, another section, a new width. Running
     * what this returns stops it.
     */
    public Runnable onStateChange(Runnable listener) {
        stateListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> stateListeners.remove(listener);
    }

    /** The strip of section icons, for the view to put on its right edge. */
    public JComponent strip() {
        return strip;
    }

    public int tabCount() {
        return sections.size();
    }

    /** The width the open panel is given, in pixels. */
    int width() {
        return state.width();
    }

    /** The user dragged the divider: the panel is {@code newWidth} wide from now on, also after closing. */
    void rememberWidth(int newWidth) {
        if (newWidth > 0) {
            setState(state.withWidth(newWidth));
        }
    }

    /** {@code listener} hears whenever the panel shows, hides or is given another width (the split places itself). */
    void onPresentationChange(Runnable listener) {
        presentationListeners.add(listener);
    }

    String headerTitle() {
        return header.title.getText();
    }

    void clickHeaderClose() {
        header.close.doClick();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(UIScale.scale(MINIMUM_WIDTH), 0);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(state.width(), super.getPreferredSize().height);
    }

    private void openOrClose() {
        if (isOpen()) {
            close();
        } else {
            setState(state.withOpen(true));
            selectedTab().ifPresent(this::focusSection);
        }
    }

    private Section section(String id) {
        Section section = sections.get(Objects.requireNonNull(id, "id"));
        if (section == null) {
            throw new IllegalArgumentException("no inspector section " + id);
        }
        return section;
    }

    private void setState(InspectorState newState) {
        if (newState.equals(state)) {
            return;
        }
        state = newState;
        refresh();
        stateListeners.fire(Runnable::run);
    }

    /** Shows what the state says: the section's card and header, the strip's highlight, the panel itself. */
    private void refresh() {
        Optional<Section> shown = selectedTab().map(sections::get);
        shown.ifPresent(section -> {
            cardLayout.show(cards, section.id());
            header.show(section);
        });
        setVisible(isOpen());
        strip.showState(isOpen() ? shown.map(Section::id) : Optional.empty(), isOpen());
        revalidate();
        repaint();
        presentationListeners.fire(Runnable::run);
    }

    /** Focuses what the section names ({@link KeyboardFocus#TARGET_PROPERTY}), else its first control. */
    private void focusSection(String id) {
        JComponent content = section(id).content();
        Object target = content.getClientProperty(KeyboardFocus.TARGET_PROPERTY);
        if (target instanceof Component component) {
            focus.request(component);
            return;
        }
        focus.request(firstControl(content).orElse(content));
    }

    /** The first visible, enabled control inside {@code component}, depth first (a text field, a list, a button…). */
    private static Optional<Component> firstControl(Component component) {
        if (!component.isVisible()) {
            return Optional.empty();
        }
        if (isControl(component)) {
            return Optional.of(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                Optional<Component> found = firstControl(child);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isControl(Component component) {
        boolean control = component instanceof JTextComponent || component instanceof AbstractButton
                || component instanceof JList || component instanceof JTree || component instanceof JTable
                || component instanceof JComboBox;
        return control && component.isFocusable() && component.isEnabled();
    }

    /** The section's icon and title, and the button that closes the panel; a rule under it in the theme's colour. */
    private final class Header extends JPanel {

        private static final int ICON_SIZE = 16;
        private static final int VERTICAL_PADDING = 6;
        private static final int LEFT_PADDING = 10;
        private static final int RIGHT_PADDING = 4;
        private static final int ICON_GAP = 8;

        private final JLabel title = new JLabel();
        private final JButton close = new JButton();

        Header() {
            super(new BorderLayout());
            title.setIconTextGap(UIScale.scale(ICON_GAP));
            close.putClientProperty("JButton.buttonType", "toolBarButton");
            close.setIcon(Icons.get(CLOSE_ICON, ICON_SIZE));
            close.setFocusable(false);
            close.setToolTipText(Messages.text("inspector.close"));
            close.getAccessibleContext().setAccessibleName(Messages.text("inspector.close"));
            close.addActionListener(event -> InspectorPanel.this.close());
            add(title, BorderLayout.CENTER);
            add(close, BorderLayout.EAST);
            applyFont();
        }

        void show(Section section) {
            title.setText(section.title());
            title.setIcon(Icons.get(section.iconName(), ICON_SIZE));
        }

        @Override
        public Insets getInsets() {
            return new Insets(UIScale.scale(VERTICAL_PADDING), UIScale.scale(LEFT_PADDING),
                    UIScale.scale(VERTICAL_PADDING) + 1, UIScale.scale(RIGHT_PADDING));
        }

        /** The title's bold font follows the theme and the font size the user chose. */
        @Override
        public void updateUI() {
            super.updateUI();
            if (title != null) {
                applyFont();
            }
        }

        private void applyFont() {
            Font font = UIManager.getFont("Label.font");
            if (font != null) {
                title.setFont(font.deriveFont(Font.BOLD));
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            ThemeColors.paintRule(graphics, this, 0, getHeight() - 1, getWidth(), 1);
        }
    }
}
