package cl.almejo.vsim.ui.preferences;

import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.TextChanges;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import cl.almejo.vsim.ui.theme.ThemeColors;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.icons.FlatSearchIcon;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FocusTraversalPolicy;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * The content of the preferences dialog: a search field above the list of groups on the left, the page of the
 * selected group on the right. A search narrows the list to the groups with matches (each with its count) and each
 * page to its matching rows; the selected group is stored as the last seen, so the dialog reopens on it.
 */
final class PreferencesPanel extends JPanel {

    /**
     * The width of the list column in MigLayout's unitless (logical) pixels, which scale with the interface font: the
     * 170 of the mockup plus the room the search placeholder and its icon need, enough for the longest title and a
     * three-digit count.
     */
    private static final int LIST_WIDTH = 195;
    private static final String NO_RESULTS_CARD = "\u0000none";
    private static final KeyStroke ESCAPE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    private static final String CLEAR_SEARCH = "clearSearch";
    private static final String FOCUS_SEARCH = "focusSearch";
    private static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    private static final String GO_TO_PAGE = "goToPage";

    private final AppPreferences preferences;
    private final List<PreferencesGroup> groups;
    private final BooleanSupplier keyboardCaptured;
    private final Runnable disposer;
    private final JTextField searchField = new JTextField();
    private final DefaultListModel<PreferencesGroup> listModel = new DefaultListModel<>();
    private final JList<PreferencesGroup> list = new JList<>(listModel);
    private final CardLayout cards = new CardLayout();
    private final JPanel cardPanel = new JPanel(cards);
    private final JLabel noResults = new JLabel("", SwingConstants.CENTER);
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private String selectedId;
    /** True while the panel itself moves the selection (a search), which is not a choice of the user. */
    private boolean adjusting;

    /**
     * @param requested        the group to open on; empty opens on the last one seen (the first when none is known)
     * @param keyboardCaptured whether a page is recording a key: Ctrl+F is then not taken from it
     * @param disposer         what releases the pages when the dialog closes
     */
    PreferencesPanel(AppPreferences preferences, List<PreferencesGroup> groups, Optional<String> requested,
                     BooleanSupplier keyboardCaptured, Runnable disposer) {
        super(new MigLayout("insets 0", "[" + LIST_WIDTH + "!][]0[grow, fill]", "[grow, fill]"));
        this.preferences = preferences;
        this.groups = List.copyOf(groups);
        this.keyboardCaptured = keyboardCaptured;
        this.disposer = disposer;

        configureSearchField();
        configureList();
        for (PreferencesGroup group : groups) {
            cardPanel.add(group.page(), group.id());
            counts.put(group.id(), 0);
        }
        cardPanel.add(noResults, NO_RESULTS_CARD);

        JPanel left = new JPanel(new MigLayout("insets 0 0 0 8, wrap 1", "[grow, fill]", "[][grow, fill]"));
        left.add(searchField);
        left.add(list);
        add(left, "grow");
        add(new JSeparator(SwingConstants.VERTICAL), "growy, gapx 0 8");
        add(cardPanel);
        installKeys();

        selectInitially(requested);
        applyQuery();
    }

    private void configureSearchField() {
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, Messages.text("preferences.search"));
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new FlatSearchIcon());
        searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        searchField.getAccessibleContext().setAccessibleName(Messages.text("preferences.search"));
        TextChanges.onChange(searchField, this::applyQuery);
    }

    private void configureList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new GroupRenderer());
        list.getAccessibleContext().setAccessibleName(Messages.text("preferences.title"));
        list.addListSelectionListener(event -> {
            PreferencesGroup chosen = list.getSelectedValue();
            if (!event.getValueIsAdjusting() && !adjusting && chosen != null) {
                choose(chosen.id());
            }
        });
    }

    /** Esc in the field clears it (and only then consumes the key); Enter goes to the page's first control (never to
     * the dialog's default button, which would close it); Ctrl+F (⌘F) anywhere goes to the field. */
    private void installKeys() {
        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(ESCAPE, CLEAR_SEARCH);
        searchField.getActionMap().put(CLEAR_SEARCH, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                searchField.setText("");
            }

            @Override
            public boolean isEnabled() {
                return !searchField.getText().isEmpty();
            }
        });
        searchField.getInputMap(JComponent.WHEN_FOCUSED).put(ENTER, GO_TO_PAGE);
        searchField.getActionMap().put(GO_TO_PAGE, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                firstControl().ifPresent(JComponent::requestFocusInWindow);
            }

            @Override
            public boolean isEnabled() {
                return firstControl().isPresent();
            }
        });
        KeyStroke find = KeyStroke.getKeyStroke(KeyEvent.VK_F, ShortcutCatalog.menuMask());
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(find, FOCUS_SEARCH);
        getActionMap().put(FOCUS_SEARCH, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                focusSearch();
            }

            @Override
            public boolean isEnabled() {
                return !keyboardCaptured.getAsBoolean();
            }
        });
    }

    /** The user chose {@code groupId}: it is shown, and stored as the last seen when the dialog closes. */
    private void choose(String groupId) {
        selectedId = groupId;
        cards.show(cardPanel, groupId);
    }

    private void selectInitially(Optional<String> requested) {
        Optional<String> asked = requested.filter(this::isKnown);
        selectedId = asked.or(() -> preferences.lastPreferencesGroup().filter(this::isKnown))
                .orElse(groups.get(0).id());
        asked.ifPresent(preferences::setLastPreferencesGroup);
    }

    private boolean isKnown(String groupId) {
        return groups.stream().anyMatch(group -> group.id().equals(groupId));
    }

    /** Applies the search to every page, then shows the groups that match (all when it is blank). */
    private void applyQuery() {
        String query = searchField.getText();
        boolean searching = !query.isBlank();
        for (PreferencesGroup group : groups) {
            counts.put(group.id(), group.filter().applyAsInt(query));
        }
        List<PreferencesGroup> visible = groups.stream()
                .filter(group -> !searching || counts.get(group.id()) > 0).toList();
        adjusting = true;
        try {
            listModel.clear();
            visible.forEach(listModel::addElement);
            if (!visible.isEmpty() && visible.stream().noneMatch(group -> group.id().equals(selectedId))) {
                selectedId = visible.get(0).id();
            }
            visible.stream().filter(group -> group.id().equals(selectedId)).findFirst()
                    .ifPresent(group -> list.setSelectedValue(group, false));
        } finally {
            adjusting = false;
        }
        if (visible.isEmpty()) {
            noResults.setText(Messages.format("preferences.search.none", query.strip()));
            cards.show(cardPanel, NO_RESULTS_CARD);
        } else {
            noResults.setText("");
            cards.show(cardPanel, selectedId);
        }
        list.repaint();
    }

    /** Puts the cursor in the search field with its text selected (Ctrl+F, and when the dialog opens). */
    void focusSearch() {
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    JTextField searchField() {
        return searchField;
    }

    List<PreferencesGroup> groups() {
        return groups;
    }

    List<String> visibleGroupIds() {
        return java.util.Collections.list(listModel.elements()).stream().map(PreferencesGroup::id).toList();
    }

    String selectedGroupId() {
        return selectedId;
    }

    /** Chooses {@code groupId} as the user does: it is shown and stored as the last seen. */
    void select(String groupId) {
        for (int index = 0; index < listModel.size(); index++) {
            if (listModel.get(index).id().equals(groupId)) {
                list.setSelectedIndex(index);
                choose(groupId);
                return;
            }
        }
    }

    /**
     * A page changed how many matches it has by itself (the user typed in its own search field): the list shows the
     * new count. The group stays listed, so the page the user is typing in does not vanish under the cursor.
     */
    void countChanged(String groupId, int count) {
        if (counts.containsKey(groupId)) {
            counts.put(groupId, count);
            list.repaint();
        }
    }

    /** The matches of {@code groupId} for the current search. */
    int countFor(String groupId) {
        return counts.getOrDefault(groupId, 0);
    }

    /** The first component of the page shown that the focus traversal policy gives the focus to; empty with no page. */
    Optional<JComponent> firstControl() {
        if (selectedId == null || listModel.isEmpty()) {
            return Optional.empty();
        }
        return groups.stream().filter(group -> group.id().equals(selectedId)).findFirst()
                .flatMap(group -> firstControlIn(group.page()));
    }

    /** The first component the focus traversal policy of the dialog would give the focus to inside {@code page}. */
    private static Optional<JComponent> firstControlIn(Container page) {
        Container cycleRoot = page.getFocusCycleRootAncestor(); // the dialog, once the panel is in one
        FocusTraversalPolicy policy = cycleRoot == null
                ? KeyboardFocusManager.getCurrentKeyboardFocusManager().getDefaultFocusTraversalPolicy()
                : cycleRoot.getFocusTraversalPolicy();
        Component first = policy.getFirstComponent(page);
        return first instanceof JComponent control ? Optional.of(control) : Optional.empty();
    }

    /** The cell the list paints for the row {@code index}, as it is now (for tests). */
    JComponent renderedRow(int index) {
        return (JComponent) list.getCellRenderer().getListCellRendererComponent(list, listModel.get(index), index,
                list.isSelectedIndex(index), false);
    }

    /** The text that replaces the page when nothing matches; empty otherwise. */
    String noResultsText() {
        return noResults.getText();
    }

    /** The dialog closed: stores the group it showed as the last seen (once, not on every click) and stops every
     * page following the preferences. */
    void dispose() {
        if (selectedId != null) {
            preferences.setLastPreferencesGroup(selectedId);
        }
        disposer.run();
    }

    /** A list row: the title, the count (muted, on the right) while searching, a line above the separated group. */
    private final class GroupRenderer implements ListCellRenderer<PreferencesGroup> {
        private final JPanel outer = new JPanel(new BorderLayout());
        private final JPanel inner = new JPanel(new BorderLayout(8, 0));
        private final JLabel title = new JLabel();
        private final JLabel count = new JLabel();

        GroupRenderer() {
            outer.setOpaque(false);
            inner.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            inner.add(title, BorderLayout.CENTER);
            inner.add(count, BorderLayout.EAST);
            outer.add(inner, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PreferencesGroup> owner,
                                                      PreferencesGroup group, int index, boolean selected,
                                                      boolean focused) {
            boolean searching = !searchField.getText().isBlank();
            title.setText(Messages.text(group.titleKey()));
            count.setText(searching ? String.valueOf(counts.getOrDefault(group.id(), 0)) : "");
            boolean active = owner.hasFocus();
            Color selectionBackground = active ? owner.getSelectionBackground()
                    : ThemeColors.ui("List.selectionInactiveBackground", owner.getSelectionBackground());
            Color selectionForeground = active ? owner.getSelectionForeground()
                    : ThemeColors.ui("List.selectionInactiveForeground", owner.getSelectionForeground());
            outer.getAccessibleContext().setAccessibleName(title.getText()
                    + (searching ? ", " + count.getText() : ""));
            inner.setOpaque(selected);
            inner.setBackground(selectionBackground);
            Color muted = ThemeColors.ui("Label.disabledForeground", owner.getForeground());
            title.setForeground(selected ? selectionForeground : owner.getForeground());
            count.setForeground(selected ? selectionForeground : muted);
            outer.setBorder(group.separatorBefore() && !searching ? BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(6, 0, 0, 0), BorderFactory.createMatteBorder(1, 0, 0, 0,
                            ThemeColors.ui("Component.borderColor", muted))) : BorderFactory.createEmptyBorder());
            return outer;
        }
    }
}
