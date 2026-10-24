package cl.almejo.vsim.ui.quickinsert;

import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.panels.PaletteEntry;
import cl.almejo.vsim.ui.panels.PaletteFilter;
import cl.almejo.vsim.ui.panels.PalettePanel;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Search-first popup content; an empty query offers the palette's available favourites in order. EDT only. */
final class QuickInsertPanel extends JPanel {

    private static final int ICON_SIZE = 24;
    private static final Dimension POPUP_SIZE = new Dimension(360, 300);
    private final PalettePanel palette;
    private final Consumer<PaletteEntry> choose;
    private final Runnable cancel;
    private final JTextField search = new JTextField();
    private final DefaultListModel<PaletteEntry> model = new DefaultListModel<>();
    private final JList<PaletteEntry> list = new JList<>(model);
    private final JLabel sectionTitle = new JLabel();
    private final JLabel hint = new JLabel();

    QuickInsertPanel(PalettePanel palette, PainterRegistry painters, Appearance appearance,
                     Consumer<PaletteEntry> choose, Runnable cancel) {
        super(new BorderLayout(4, 4));
        this.palette = palette;
        this.choose = choose;
        this.cancel = cancel;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(POPUP_SIZE);
        search.putClientProperty("JTextField.placeholderText", Messages.text("quickInsert.search"));
        search.getAccessibleContext().setAccessibleName(Messages.text("quickInsert.search"));
        search.putClientProperty("JTextField.showClearButton", true);
        FragmentRefusingTransferHandler.install(search);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh(); }
            @Override public void removeUpdate(DocumentEvent event) { refresh(); }
            @Override public void changedUpdate(DocumentEvent event) { refresh(); }
        });
        search.addActionListener(event -> acceptSelection());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                                                          boolean selected, boolean focused) {
                super.getListCellRendererComponent(owner, value, index, selected, focused);
                if (value instanceof PaletteEntry entry) {
                    setText(Messages.componentName(entry.type()) + " · " + entry.category());
                    setIcon(new ComponentIcon(entry.type(), painters.painterFor(entry.type().id()),
                            appearance.scheme(), ICON_SIZE));
                }
                return this;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(event)) {
                    int index = list.locationToIndex(event.getPoint());
                    if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) {
                        list.setSelectedIndex(index);
                        acceptSelection();
                    }
                }
            }
        });
        bind(search, KeyEvent.VK_DOWN, "next", () -> moveSelection(1));
        bind(search, KeyEvent.VK_UP, "previous", () -> moveSelection(-1));
        bind(search, KeyEvent.VK_ESCAPE, "cancel", this::cancel);
        bind(list, KeyEvent.VK_ESCAPE, "cancel", this::cancel);
        bind(list, KeyEvent.VK_ENTER, "choose", this::acceptSelection);
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

    private static void bind(JComponent component, int key, String name, Runnable action) {
        component.getInputMap().put(KeyStroke.getKeyStroke(key, 0), name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
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

    List<PaletteEntry> results() {
        return java.util.Collections.list(model.elements());
    }

    /** Re-resolves ids on every refresh: a missing library entry stays saved but cannot be placed. */
    void refresh() {
        String selectedId = list.getSelectedValue() == null ? "" : list.getSelectedValue().type().id();
        String query = search.getText();
        List<PaletteEntry> results = new ArrayList<>();
        if (query.isBlank()) {
            results.addAll(palette.favoriteEntries());
        } else {
            palette.entries().stream().filter(entry -> PaletteFilter.matches(entry.type(), query))
                    .forEach(results::add);
        }
        model.clear();
        results.forEach(model::addElement);
        if (!results.isEmpty()) {
            int selectedIndex = 0;
            for (int index = 0; index < results.size(); index++) {
                if (results.get(index).type().id().equals(selectedId)) {
                    selectedIndex = index;
                    break;
                }
            }
            list.setSelectedIndex(selectedIndex);
        }
        sectionTitle.setText(Messages.text(query.isBlank() ? "quickInsert.favorites" : "quickInsert.results"));
        hint.setText(Messages.text(query.isBlank() ? "quickInsert.empty" : "quickInsert.noResults"));
        hint.setVisible(results.isEmpty());
    }

    private void moveSelection(int direction) {
        if (!model.isEmpty()) {
            int next = Math.floorMod(list.getSelectedIndex() + direction, model.size());
            list.setSelectedIndex(next);
            list.ensureIndexIsVisible(next);
        }
    }

    void acceptSelection() {
        PaletteEntry entry = list.getSelectedValue();
        if (entry != null) {
            choose.accept(entry);
        }
    }

    void cancel() {
        cancel.run();
    }
}
