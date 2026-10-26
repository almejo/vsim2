package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteFilter;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.TextChanges;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Search-first popup content. An empty query offers the palette's «Favoritos» and then its «Recientes», each under a
 * heading row the selection never stops on; typing searches the whole palette. EDT only.
 */
final class QuickInsertPanel extends JPanel {

    /** A row of the list: a group heading or a component to choose. */
    sealed interface Row permits Heading, Choice {
    }

    /** The title of a group («Favoritos», «Recientes»); never selected. */
    record Heading(String title) implements Row {
    }

    record Choice(PaletteEntry entry) implements Row {
    }

    private static final int ICON_SIZE = 24;
    private static final Dimension POPUP_SIZE = new Dimension(360, 300);
    private final PalettePanel palette;
    private final Consumer<PaletteEntry> choose;
    private final Runnable cancel;
    private final JTextField search = new JTextField();
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final JList<Row> list = new JList<>(model);
    private final JLabel sectionTitle = new JLabel();
    private final JLabel hint = new JLabel();

    QuickInsertPanel(PalettePanel palette, PainterRegistry painters, Appearance appearance,
                     Consumer<PaletteEntry> choose, Runnable cancel) {
        super(new BorderLayout(4, 4));
        this.palette = palette;
        this.choose = choose;
        this.cancel = cancel;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(POPUP_SIZE);
        search.putClientProperty("JTextField.placeholderText", Messages.text("quickInsert.search"));
        search.getAccessibleContext().setAccessibleName(Messages.text("quickInsert.search"));
        search.putClientProperty("JTextField.showClearButton", true);
        FragmentRefusingTransferHandler.install(search);
        TextChanges.onChange(search, this::refresh);
        search.addActionListener(event -> acceptSelection());
        list.setSelectionModel(new HeadingSkippingSelectionModel());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(owner, value, index, selected, focused);
                if (value instanceof Choice choice) {
                    PaletteEntry entry = choice.entry();
                    setText(Messages.componentName(entry.type()) + " · " + entry.category());
                    setIcon(new ComponentIcon(entry.type(), painters.painterFor(entry.type().id()),
                            appearance.scheme(), ICON_SIZE));
                    putClientProperty("FlatLaf.style", null);
                } else if (value instanceof Heading heading) {
                    setText(heading.title());
                    setIcon(null);
                    putClientProperty("FlatLaf.style", "font: bold");
                }
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event)) {
                    int index = list.locationToIndex(event.getPoint());
                    if (index >= 0 && model.get(index) instanceof Choice
                            && list.getCellBounds(index, index).contains(event.getPoint())) {
                        list.setSelectedIndex(index);
                        acceptSelection();
                    }
                }
            }
        });
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "next", () -> moveSelection(1));
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "previous", () -> moveSelection(-1));
        Keys.bindWhenFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        Keys.bindWhenFocused(list, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel", this::cancel);
        Keys.bindWhenFocused(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose", this::acceptSelection);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel(Messages.text("quickInsert.title"));
        title.putClientProperty("FlatLaf.style", "font: bold");
        title.setLabelFor(search);
        header.add(title, BorderLayout.NORTH);
        header.add(search, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        JPanel results = new JPanel(new BorderLayout(0, 4));
        sectionTitle.setLabelFor(list);
        results.add(sectionTitle, BorderLayout.NORTH);
        results.add(new JScrollPane(list), BorderLayout.CENTER);
        add(results, BorderLayout.CENTER);
        add(hint, BorderLayout.SOUTH);
        refresh();
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

    /** Re-resolves ids on every refresh: a missing library entry stays saved but cannot be placed. */
    void refresh() {
        String selectedId = selectedEntry().map(entry -> entry.type().id()).orElse("");
        String query = search.getText();
        List<Row> rows = new ArrayList<>();
        if (query.isBlank()) {
            addGroup(rows, "quickInsert.favorites", palette.favoriteEntries());
            addGroup(rows, "quickInsert.recent", palette.recentEntries());
        } else {
            palette.entries().stream().filter(entry -> PaletteFilter.matches(entry.type(), query))
                    .forEach(entry -> rows.add(new Choice(entry)));
        }
        model.clear();
        rows.forEach(model::addElement);
        int selectedIndex = -1;
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index) instanceof Choice choice
                    && (selectedIndex < 0 || choice.entry().type().id().equals(selectedId))) {
                selectedIndex = index;
                if (choice.entry().type().id().equals(selectedId)) {
                    break;
                }
            }
        }
        if (selectedIndex >= 0) {
            list.setSelectedIndex(selectedIndex);
        }
        sectionTitle.setText(Messages.text("quickInsert.results"));
        sectionTitle.setVisible(!query.isBlank());
        hint.setText(Messages.text(query.isBlank() ? "quickInsert.empty" : "quickInsert.noResults"));
        hint.setVisible(rows.isEmpty());
    }

    private static void addGroup(List<Row> rows, String titleKey, List<PaletteEntry> entries) {
        if (!entries.isEmpty()) {
            rows.add(new Heading(Messages.text(titleKey)));
            entries.forEach(entry -> rows.add(new Choice(entry)));
        }
    }

    private Optional<PaletteEntry> selectedEntry() {
        return list.getSelectedValue() instanceof Choice choice ? Optional.of(choice.entry()) : Optional.empty();
    }

    /** Moves to the next component in {@code direction}, round the end of the list, over the headings. */
    private void moveSelection(int direction) {
        int size = model.size();
        int index = list.getSelectedIndex();
        for (int step = 0; step < size; step++) {
            index = Math.floorMod(index + direction, size);
            if (model.get(index) instanceof Choice) {
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    void acceptSelection() {
        selectedEntry().ifPresent(choose);
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
                        && model.get(past) instanceof Choice) {
                    super.setSelectionInterval(past, past);
                }
                return;
            }
            super.setSelectionInterval(index0, index1);
        }
    }

    void cancel() {
        cancel.run();
    }
}
