package cl.almejo.vsim.ui.project;

import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.window.tabs.GroupColors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The «Proyecto» panel (spec §5.2): the circuit's document and every instance of a user component, nested by
 * containment, above the palette. A click chooses a row; a double click or Enter enters the instance (its component
 * opens in a tab, showing that instance's values); the context menu has «Entrar», «Ver la pieza», «Cambiar forma…» and
 * «Propiedades». The row of the context this view shows (a component tab's) is tinted with the group colour, read
 * when drawn. It follows the circuit and its tab's context; {@link #dispose()} stops it.
 */
public final class ProjectPanel extends JPanel {

    private static final String ENTER_KEY = "project.enter";
    private static final int INDENT = 14;
    private static final int HIGHLIGHT_ALPHA = 70;

    /** What the rows do; the editor view implements them, tests record them. */
    public interface Actions {
        /** Enters the instance of {@code row}, with its path as context. */
        void enter(ProjectRows.Row row);

        /** Shows the instance in the place that holds it, selected («Ver la pieza»: always offered). */
        void seePiece(ProjectRows.Row row);

        /** Opens «Cambiar forma…» for the instance (only when {@link #editsHere}). */
        void changeShape(ProjectRows.Row row);

        /** Shows the instance's properties (only when {@link #editsHere}). */
        void properties(ProjectRows.Row row);

        /** True when the instance is a part of the place this view shows: its shape and properties are edited here. */
        boolean editsHere(ProjectRows.Row row);
    }

    private final OpenDocument openDocument;
    private final Actions actions;
    private final DefaultListModel<ProjectRows.Row> model = new DefaultListModel<>();
    private final JList<ProjectRows.Row> list = new JList<>(model);
    private final Runnable documentListener = this::refresh;

    public ProjectPanel(OpenDocument openDocument, Actions actions) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.actions = actions;
        JLabel title = new JLabel(Messages.text("project.title"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(title, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_KEY);
        list.getActionMap().put(ENTER_KEY, cl.almejo.vsim.ui.actions.Actions.of("", this::enterChosen));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2 && event.getButton() == MouseEvent.BUTTON1) {
                    rowAt(event).ifPresent(row -> enter(row));
                }
            }

            @Override
            public void mousePressed(MouseEvent event) {
                maybeShowMenu(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                maybeShowMenu(event);
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        openDocument.addChangeListener(documentListener);
        refresh();
    }

    /** Stops following the document: the view closed. */
    public void dispose() {
        openDocument.removeChangeListener(documentListener);
    }

    JList<ProjectRows.Row> rowList() {
        return list;
    }

    List<ProjectRows.Row> rows() {
        List<ProjectRows.Row> rows = new ArrayList<>();
        for (int index = 0; index < model.size(); index++) {
            rows.add(model.get(index));
        }
        return rows;
    }

    /** The row of the context this view shows, if it has one. */
    Optional<ProjectRows.Row> highlightedRow() {
        Optional<String> context = openDocument.contextPath();
        if (context.isEmpty()) {
            return Optional.empty();
        }
        return rows().stream().filter(row -> row.instance().map(use -> use.path().equals(context.get()))
                .orElse(false)).findFirst();
    }

    /** The context menu of {@code row}: none for the document's row. */
    Optional<JPopupMenu> menuFor(ProjectRows.Row row) {
        if (row.instance().isEmpty()) {
            return Optional.empty();
        }
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(Messages.text("action.enter"), true, () -> actions.enter(row)));
        menu.add(item(Messages.text("project.seePiece"), true, () -> actions.seePiece(row)));
        boolean editsHere = actions.editsHere(row);
        menu.add(item(Messages.text("shape.change"), editsHere, () -> actions.changeShape(row)));
        menu.add(item(Messages.text("project.properties"), editsHere, () -> actions.properties(row)));
        return Optional.of(menu);
    }

    private static JMenuItem item(String text, boolean enabled, Runnable action) {
        JMenuItem item = new JMenuItem(cl.almejo.vsim.ui.actions.Actions.of(text, action));
        item.setEnabled(enabled);
        return item;
    }

    private void enter(ProjectRows.Row row) {
        if (row.instance().isPresent()) {
            actions.enter(row);
        }
    }

    private void enterChosen() {
        ProjectRows.Row chosen = list.getSelectedValue();
        if (chosen != null) {
            enter(chosen);
        }
    }

    private Optional<ProjectRows.Row> rowAt(MouseEvent event) {
        int index = list.locationToIndex(event.getPoint());
        Rectangle cell = index < 0 ? null : list.getCellBounds(index, index);
        return cell != null && cell.contains(event.getPoint()) ? Optional.of(model.get(index)) : Optional.empty();
    }

    private void maybeShowMenu(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        rowAt(event).ifPresent(row -> {
            list.setSelectedValue(row, false);
            menuFor(row).ifPresent(menu -> menu.show(list, event.getX(), event.getY()));
        });
    }

    /** Re-reads the rows from the circuit, keeping the chosen instance chosen. */
    private void refresh() {
        Optional<String> chosen = Optional.ofNullable(list.getSelectedValue())
                .flatMap(ProjectRows.Row::instance).map(use -> use.path());
        OpenDocument circuit = openDocument.parent().orElse(openDocument);
        List<ProjectRows.Row> fresh = ProjectRows.of(circuit.document(), circuit.displayName());
        model.clear();
        fresh.forEach(model::addElement);
        chosen.ifPresent(path -> {
            for (int index = 0; index < model.size(); index++) {
                if (model.get(index).instance().map(use -> use.path().equals(path)).orElse(false)) {
                    list.setSelectedIndex(index);
                }
            }
        });
        list.repaint();
    }

    /** Draws a row: indent by depth, the definition's name, the instance's name on the right, the context tinted. */
    private final class Renderer extends JPanel implements javax.swing.ListCellRenderer<ProjectRows.Row> {
        private final JLabel name = new JLabel();
        private final JLabel right = new JLabel();
        private boolean highlighted;

        Renderer() {
            super(new BorderLayout(8, 0));
            add(name, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ProjectRows.Row> owner, ProjectRows.Row row,
                                                      int index, boolean selected, boolean focused) {
            name.setText(row.text());
            right.setText(row.right());
            setBorder(BorderFactory.createEmptyBorder(2, 6 + row.depth() * INDENT, 2, 6));
            highlighted = row.instance().isPresent() && highlightedRow().filter(row::equals).isPresent();
            Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
            name.setForeground(foreground);
            name.setFont(owner.getFont().deriveFont(row.depth() == 0 ? Font.BOLD : Font.PLAIN));
            Color muted = UIManager.getColor("Label.disabledForeground");
            right.setForeground(selected || muted == null ? foreground : muted);
            right.setFont(owner.getFont());
            if (selected) {
                setBackground(owner.getSelectionBackground());
            } else if (highlighted) {
                Color group = GroupColors.colorOf(openDocument.parent().orElse(openDocument));
                setBackground(new Color(group.getRed(), group.getGreen(), group.getBlue(), HIGHLIGHT_ALPHA));
            } else {
                setBackground(owner.getBackground());
            }
            return this;
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            // a translucent tint over the list's own background: fill it first, then the tint
            if (highlighted) {
                graphics.setColor(UIManager.getColor("List.background"));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(graphics);
        }
    }
}
