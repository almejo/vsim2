package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteFilter;
import cl.almejo.vsim.ui.panels.TextChanges;
import cl.almejo.vsim.ui.shortcuts.KeyBindings;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The command palette's content: two tabs, «Componentes» (quick insert) and «Acciones» (everything in the menus),
 * over one search field that keeps the focus and the text while Tab and Mayús+Tab switch tabs. With nothing typed,
 * «Componentes» offers the palette's «Favoritos» and «Recientes» and «Acciones» the ones used lately, each under a
 * heading the selection never stops on. When the tab finds nothing but the other one does, the footer says how many
 * the other finds. The key of «Buscar una acción…» goes to «Acciones», and there closes the palette. EDT only.
 */
final class QuickInsertPanel extends JPanel {

    /** What the palette does with a choice; the opener decides where and on which view. */
    interface Listener {
        /** Place {@code entry}, as the component palette would. */
        void place(PaletteEntry entry);

        /** Run {@code row}, which is enabled. */
        void run(ActionRow row);

        /** Close without doing anything. */
        void cancel();
    }

    /** A row of the list: a group heading, a component or an action. */
    sealed interface Row permits Heading, Choice, ActionChoice {
    }

    /** The title of a group («Favoritos», «Usadas hace poco»); never selected. */
    record Heading(String title) implements Row {
    }

    record Choice(PaletteEntry entry) implements Row {
    }

    record ActionChoice(ActionRow row) implements Row {
    }

    private static final Dimension POPUP_SIZE = new Dimension(460, 360);
    private final PaletteContent content;
    private final Listener listener;
    private final JTextField search = new JTextField();
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final JList<Row> list = new JList<>(model);
    private final JLabel sectionTitle = new JLabel();
    private final JLabel footer = new JLabel();
    private final Map<PaletteTab, JToggleButton> tabButtons = new EnumMap<>(PaletteTab.class);
    private PaletteTab tab;

    QuickInsertPanel(PaletteContent content, PaletteTab tab, Listener listener) {
        super(new BorderLayout(4, 4));
        this.content = content;
        this.listener = listener;
        this.tab = tab;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(POPUP_SIZE);
        setUpSearch();
        setUpList();
        add(header(), BorderLayout.NORTH);
        JPanel results = new JPanel(new BorderLayout(0, 4));
        sectionTitle.setLabelFor(list);
        results.add(sectionTitle, BorderLayout.NORTH);
        results.add(new JScrollPane(list), BorderLayout.CENTER);
        add(results, BorderLayout.CENTER);
        footer.putClientProperty("FlatLaf.styleClass", "small");
        add(footer, BorderLayout.SOUTH);
        showTab(tab);
    }

    private void setUpSearch() {
        search.putClientProperty("JTextField.showClearButton", true);
        FragmentRefusingTransferHandler.install(search);
        TextChanges.onChange(search, this::refresh);
        search.addActionListener(event -> acceptSelection());
        search.setFocusTraversalKeysEnabled(false); // Tab and Mayús+Tab switch tabs, the focus stays here
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next", () -> moveSelection(1));
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous", () -> moveSelection(-1));
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "nextTab", this::switchTab);
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK),
                "previousTab", this::switchTab);
        content.findActionKey().ifPresent(stroke ->
                Keys.bindWhenFocused(search, stroke, "findAction", this::findAction));
    }

    private void setUpList() {
        list.setSelectionModel(new HeadingSkippingSelectionModel());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false); // the search field keeps the keys: arrows, Enter, Tab and Esc work from there
        list.setCellRenderer(new PaletteRowRenderer(content.painters(), content.appearance()));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    int index = list.locationToIndex(event.getPoint());
                    if (index >= 0 && !(model.get(index) instanceof Heading)
                            && list.getCellBounds(index, index).contains(event.getPoint())) {
                        list.setSelectedIndex(index);
                        acceptSelection();
                    }
                }
            }
        });
    }

    private JPanel header() {
        JPanel tabs = new JPanel(new GridLayout(1, 2));
        ButtonGroup group = new ButtonGroup();
        addTabButton(tabs, group, PaletteTab.COMPONENTS,
                tabTitle(Messages.text("commandPalette.tab.components"), content.componentsKey()));
        addTabButton(tabs, group, PaletteTab.ACTIONS,
                tabTitle(Messages.text("commandPalette.tab.actions"), content.findActionKey()));
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.add(tabs, BorderLayout.NORTH);
        header.add(search, BorderLayout.CENTER);
        return header;
    }

    private void addTabButton(JPanel tabs, ButtonGroup group, PaletteTab target, String title) {
        JToggleButton button = new JToggleButton(title);
        button.putClientProperty("JButton.buttonType", "tab");
        button.setFocusable(false); // a click switches the tab, the typing goes on in the field
        button.addActionListener(event -> showTab(target));
        group.add(button);
        tabs.add(button);
        tabButtons.put(target, button);
    }

    /** «Acciones · Ctrl+K»: a tab's title with the key that opens it, when it has one. */
    private static String tabTitle(String title, Optional<KeyStroke> key) {
        return key.map(stroke -> Messages.format("commandPalette.tabWithKey", title, KeyBindings.describe(stroke)))
                .orElse(title);
    }

    void focusSearch() {
        search.requestFocusInWindow();
    }

    /** The search field, independent of the popup's visual layout. Package-private for keyboard tests. */
    JTextField searchField() {
        return search;
    }

    void setQuery(String query) {
        search.setText(query);
    }

    PaletteTab tab() {
        return tab;
    }

    /** The other tab, keeping what is typed. */
    void switchTab() {
        showTab(tab == PaletteTab.COMPONENTS ? PaletteTab.ACTIONS : PaletteTab.COMPONENTS);
    }

    /** The key of «Buscar una acción…» here: from «Componentes» to «Acciones», and from «Acciones» it closes. */
    void findAction() {
        if (tab == PaletteTab.ACTIONS) {
            cancel();
        } else {
            showTab(PaletteTab.ACTIONS);
        }
    }

    private void showTab(PaletteTab shown) {
        tab = shown;
        tabButtons.get(shown).setSelected(true);
        String placeholder = Messages.text(shown == PaletteTab.COMPONENTS ? "quickInsert.search"
                : "commandPalette.actions.search");
        search.putClientProperty("JTextField.placeholderText", placeholder);
        search.getAccessibleContext().setAccessibleName(placeholder);
        list.clearSelection();
        refresh();
    }

    /** The titles of the tabs, as shown. */
    List<String> tabTitles() {
        return List.of(tabButtons.get(PaletteTab.COMPONENTS).getText(), tabButtons.get(PaletteTab.ACTIONS).getText());
    }

    /** The group headings shown now, top to bottom (none while searching). */
    List<String> groupTitles() {
        return Collections.list(model.elements()).stream().filter(Heading.class::isInstance)
                .map(row -> ((Heading) row).title()).toList();
    }

    /** The components offered now, top to bottom, without the headings. */
    List<PaletteEntry> results() {
        return Collections.list(model.elements()).stream().filter(Choice.class::isInstance)
                .map(row -> ((Choice) row).entry()).toList();
    }

    /** The actions offered now, top to bottom, without the headings. */
    List<ActionRow> actionResults() {
        return Collections.list(model.elements()).stream().filter(ActionChoice.class::isInstance)
                .map(row -> ((ActionChoice) row).row()).toList();
    }

    /** The line under the list, as shown. */
    String footer() {
        return footer.getText();
    }

    /** Re-resolves ids on every refresh: a missing library entry stays saved but cannot be placed. */
    void refresh() {
        Optional<Row> selected = Optional.ofNullable(list.getSelectedValue());
        String query = search.getText();
        List<Row> rows = tab == PaletteTab.COMPONENTS ? componentRows(query) : actionRows(query);
        model.clear();
        rows.forEach(model::addElement);
        int selectedIndex = selected.map(row -> indexOfSame(rows, row)).filter(index -> index >= 0)
                .orElseGet(() -> firstChoice(rows));
        if (selectedIndex >= 0) {
            list.setSelectedIndex(selectedIndex);
            list.ensureIndexIsVisible(selectedIndex);
        }
        sectionTitle.setText(Messages.text("quickInsert.results"));
        sectionTitle.setVisible(tab == PaletteTab.COMPONENTS && !query.isBlank());
        footer.setText(footerText(query, rows.isEmpty()));
    }

    /** Where {@code wanted} is in {@code rows}, by component id or action path; -1 when it is gone. */
    private static int indexOfSame(List<Row> rows, Row wanted) {
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            boolean same = row instanceof Choice choice && wanted instanceof Choice wantedChoice
                    && choice.entry().id().equals(wantedChoice.entry().id())
                    || row instanceof ActionChoice action && wanted instanceof ActionChoice wantedAction
                    && action.row().key().equals(wantedAction.row().key());
            if (same) {
                return index;
            }
        }
        return -1;
    }

    private List<Row> componentRows(String query) {
        List<Row> rows = new ArrayList<>();
        if (query.isBlank()) {
            addGroup(rows, "quickInsert.favorites", content.palette().favoriteEntries().stream().map(Choice::new));
            addGroup(rows, "quickInsert.recent", content.palette().recentEntries().stream().map(Choice::new));
        } else {
            matchingComponents(query).forEach(entry -> rows.add(new Choice(entry)));
        }
        return rows;
    }

    private List<Row> actionRows(String query) {
        List<Row> rows = new ArrayList<>();
        if (query.isBlank()) {
            addGroup(rows, "commandPalette.actions.recent", content.recentActions().stream().map(ActionChoice::new));
        } else {
            ActionMatcher.filter(content.actions(), query).forEach(row -> rows.add(new ActionChoice(row)));
        }
        return rows;
    }

    private List<PaletteEntry> matchingComponents(String query) {
        return content.palette().entries().stream().filter(entry -> PaletteFilter.matches(entry, query)).toList();
    }

    private static void addGroup(List<Row> rows, String titleKey, Stream<? extends Row> group) {
        List<? extends Row> members = group.toList();
        if (!members.isEmpty()) {
            rows.add(new Heading(Messages.text(titleKey)));
            rows.addAll(members);
        }
    }

    /** The first row that can be chosen: an enabled action before a disabled one; -1 when there is none. */
    private static int firstChoice(List<Row> rows) {
        int first = -1;
        for (int index = 0; index < rows.size(); index++) {
            Row row = rows.get(index);
            if (row instanceof Choice || (row instanceof ActionChoice action && action.row().enabled())) {
                return index;
            }
            if (first < 0 && row instanceof ActionChoice) {
                first = index;
            }
        }
        return first;
    }

    /**
     * The keys while something is listed; otherwise why nothing is, or how many the other tab finds («3 componentes
     * con «and» · Tab»).
     */
    private String footerText(String query, boolean nothingListed) {
        boolean components = tab == PaletteTab.COMPONENTS;
        if (!nothingListed) {
            return Messages.text(components ? "commandPalette.keys.components" : "commandPalette.keys.actions");
        }
        if (query.isBlank()) {
            return Messages.text(components ? "quickInsert.empty" : "commandPalette.actions.empty");
        }
        String trimmed = query.strip();
        int other = components ? ActionMatcher.filter(content.actions(), query).size()
                : matchingComponents(query).size();
        if (other > 0) {
            return Messages.format(components ? "commandPalette.otherTab.actions" : "commandPalette.otherTab.components",
                    other, trimmed);
        }
        return components ? Messages.text("quickInsert.noResults")
                : Messages.format("commandPalette.actions.noResults", trimmed);
    }

    /** Moves to the next row in {@code direction}, round the end of the list, over the headings. */
    private void moveSelection(int direction) {
        int size = model.size();
        int index = list.getSelectedIndex();
        for (int step = 0; step < size; step++) {
            index = Math.floorMod(index + direction, size);
            if (!(model.get(index) instanceof Heading)) {
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    /** Chooses the selected row; a disabled action does nothing (its row says why). */
    void acceptSelection() {
        Row selected = list.getSelectedValue();
        if (selected instanceof Choice choice) {
            listener.place(choice.entry());
        } else if (selected instanceof ActionChoice action && action.row().enabled()) {
            listener.run(action.row());
        }
    }

    void cancel() {
        listener.cancel();
    }

    /**
     * Never selects a heading: the list's own arrow keys step over it in the direction they move, and a click on it
     * keeps the selection there was.
     */
    private final class HeadingSkippingSelectionModel extends DefaultListSelectionModel {
        @Override
        public void setSelectionInterval(int index0, int index1) {
            if (index0 >= 0 && index0 < model.size() && model.get(index0) instanceof Heading) {
                int lead = getLeadSelectionIndex();
                int past = index0 + (lead >= 0 && lead < index0 ? 1 : -1);
                if (lead >= 0 && Math.abs(lead - index0) == 1 && past >= 0 && past < model.size()
                        && !(model.get(past) instanceof Heading)) {
                    super.setSelectionInterval(past, past);
                }
                return;
            }
            super.setSelectionInterval(index0, index1);
        }
    }
}
