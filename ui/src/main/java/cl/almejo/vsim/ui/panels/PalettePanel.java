package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Component palette: search box plus a tree of categories. Choosing an entry starts placing it. */
public final class PalettePanel extends JPanel {

    private static final List<ComponentCategory> ORDER = List.of(ComponentCategory.BASIC,
            ComponentCategory.INPUT_OUTPUT, ComponentCategory.DEBUG, ComponentCategory.INTERFACE,
            ComponentCategory.CUSTOM);
    private static final int ICON_SIZE = 24;

    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final ColorScheme scheme;
    private final Consumer<ComponentType> onChoose;
    private final JTextField search = new JTextField();
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final Map<String, Icon> icons = new HashMap<>();
    private boolean updating;

    public PalettePanel(ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme,
                        Consumer<ComponentType> onChoose) {
        super(new MigLayout("insets 4, fill, wrap 1", "[grow, fill]", "[][grow, fill]"));
        this.registry = registry;
        this.painters = painters;
        this.scheme = scheme;
        this.onChoose = onChoose;
        FragmentRefusingTransferHandler.install(search);
        search.putClientProperty("JTextField.placeholderText", Messages.text("palette.search"));
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                rebuild();
            }
        });
        swallowUndoKeystrokes();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(ICON_SIZE + 4);
        tree.setCellRenderer(new Renderer());
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> {
            if (!updating) {
                selectedType().ifPresent(onChoose);
            }
        });
        add(search);
        if (!GraphicsEnvironment.isHeadless()) {
            tree.setDragEnabled(true); // Swing refuses to enable dragging without a display
        }
        tree.setTransferHandler(new TransferHandler() {
            @Override
            public int getSourceActions(JComponent source) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent source) {
                return selectedType().map(type -> new StringSelection(PaletteTransfer.encode(type.id()))).orElse(null);
            }
        });
        add(new JScrollPane(tree));
        rebuild();
    }

    /** Package-private for tests. */
    JTextField searchField() {
        return search;
    }

    /** Keeps Ctrl+Z / Ctrl+Shift+Z in the search box from reaching the circuit's undo menu accelerators. */
    private void swallowUndoKeystrokes() {
        int mask = GraphicsEnvironment.isHeadless() ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        String actionKey = "palette.ignoreUndo";
        search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask), actionKey);
        search.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask | InputEvent.SHIFT_DOWN_MASK), actionKey);
        search.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                // intentionally empty: consuming the keystroke is the point
            }
        });
    }

    public void setFilter(String text) {
        search.setText(text);
    }

    public List<String> visibleTypeIds() {
        List<String> ids = new ArrayList<>();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (Object category : Collections.list(root.children())) {
            for (Object leaf : Collections.list(((DefaultMutableTreeNode) category).children())) {
                ids.add(((ComponentType) ((DefaultMutableTreeNode) leaf).getUserObject()).id());
            }
        }
        return ids;
    }

    public void choose(String typeId) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        for (Object category : Collections.list(root.children())) {
            for (Object leaf : Collections.list(((DefaultMutableTreeNode) category).children())) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) leaf;
                if (((ComponentType) node.getUserObject()).id().equals(typeId)) {
                    tree.setSelectionPath(new TreePath(node.getPath()));
                    return;
                }
            }
        }
    }

    /** Clears the highlighted entry without calling back (the canvas left placement mode). */
    public void clearSelection() {
        updating = true;
        try {
            tree.clearSelection();
        } finally {
            updating = false;
        }
    }

    private void rebuild() {
        String query = search.getText();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (ComponentCategory category : ORDER) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(category);
            registry.all().stream()
                    .filter(type -> type.category() == category)
                    .filter(type -> PaletteFilter.matches(type, query))
                    .forEach(type -> categoryNode.add(new DefaultMutableTreeNode(type, false)));
            if (categoryNode.getChildCount() > 0) {
                root.add(categoryNode);
            }
        }
        updating = true;
        try {
            tree.setModel(new DefaultTreeModel(root));
            for (int row = 0; row < tree.getRowCount(); row++) {
                tree.expandRow(row);
            }
        } finally {
            updating = false;
        }
    }

    private Optional<ComponentType> selectedType() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return Optional.empty();
        }
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof ComponentType type ? Optional.of(type) : Optional.empty();
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(owner, value, selected, expanded, leaf, row, focused);
            Object item = ((DefaultMutableTreeNode) value).getUserObject();
            if (item instanceof ComponentType type) {
                setText(Messages.componentName(type.id()));
                setIcon(icons.computeIfAbsent(type.id(),
                        id -> new ComponentIcon(type, painters.painterFor(id), scheme, ICON_SIZE)));
            } else if (item instanceof ComponentCategory category) {
                setText(Messages.categoryName(category));
                setIcon(null);
            }
            return this;
        }
    }
}
