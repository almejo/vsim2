package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.clipboard.FragmentTransferable;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
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
import javax.swing.tree.TreeModel;
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Component palette: search box plus a tree of categories, then "Mis componentes" (the user's library) and
 * "Componentes de este circuito". Choosing an entry starts placing it.
 */
public final class PalettePanel extends JPanel {

    /** A user-defined component shown in "Mis componentes" ({@code inLibrary}) or "Componentes de este circuito". */
    public record UserComponent(DefinitionBundle bundle, boolean inLibrary, ComponentType type) {
        public static UserComponent of(DefinitionBundle bundle, boolean inLibrary) {
            return new UserComponent(bundle, inLibrary, SubcircuitTypes.typeOf(bundle.root()));
        }
    }

    /** The two sections after the categories (overview M4-A: Mis componentes, then Componentes de este circuito). */
    private enum UserSection {
        LIBRARY, DOCUMENT;

        String title() {
            return this == LIBRARY ? Messages.text("palette.section.library")
                    : Messages.text("palette.section.document");
        }
    }

    private static final List<ComponentCategory> ORDER = List.of(ComponentCategory.BASIC,
            ComponentCategory.INPUT_OUTPUT, ComponentCategory.DEBUG, ComponentCategory.INTERFACE);
    private static final int ICON_SIZE = 24;

    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final Runnable appearanceListener;
    private final Consumer<ComponentType> onChoose;
    private final JTextField search = new JTextField();
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final Map<String, Icon> icons = new HashMap<>();
    private final PaletteTransferHandler transferHandler = new PaletteTransferHandler();
    private List<DefinitionBundle> libraryBundles = List.of();
    private List<DefinitionBundle> documentBundles = List.of();
    private List<UserComponent> libraryComponents = List.of();
    private List<UserComponent> documentComponents = List.of();
    private Consumer<UserComponent> userChooser = component -> {
    };
    private boolean updating;

    public PalettePanel(ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme,
                        Consumer<ComponentType> onChoose) {
        this(registry, painters, new Appearance(scheme), onChoose);
    }

    public PalettePanel(ComponentTypeRegistry registry, PainterRegistry painters, Appearance appearance,
                        Consumer<ComponentType> onChoose) {
        super(new MigLayout("insets 4, fill, wrap 1", "[grow, fill]", "[][grow, fill]"));
        this.registry = registry;
        this.painters = painters;
        this.appearance = appearance;
        this.onChoose = onChoose;
        this.appearanceListener = () -> {
            icons.clear();
            tree.repaint();
        };
        appearance.addListener(appearanceListener);
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
                selectedUserComponent().ifPresent(component -> userChooser.accept(component));
            }
        });
        add(search);
        if (!GraphicsEnvironment.isHeadless()) {
            tree.setDragEnabled(true); // Swing refuses to enable dragging without a display
        }
        tree.setTransferHandler(transferHandler);
        add(new JScrollPane(tree));
        rebuild();
    }

    /** Stops following the shared appearance: the window showing this palette closed. */
    public void dispose() {
        appearance.removeListener(appearanceListener);
    }

    /** The icon drawn for {@code type} with the current scheme. Package-private for tests. */
    Icon icon(ComponentType type) {
        return icons.computeIfAbsent(type.id(),
                id -> new ComponentIcon(type, painters.painterFor(id), appearance.scheme(), ICON_SIZE));
    }

    /** Package-private for tests. */
    JTextField searchField() {
        return search;
    }

    /** The tree model now shown: the same instance means nothing was rebuilt. Package-private for tests. */
    TreeModel treeModel() {
        return tree.getModel();
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

    /**
     * Fills "Mis componentes" ({@code library}) and "Componentes de este circuito" ({@code ownedByDocument}). Equal
     * lists change nothing (the tree keeps what the user folded and the highlight); otherwise only the icons of the
     * definitions that changed are redrawn. EDT.
     */
    public void setUserComponents(List<DefinitionBundle> library, List<DefinitionBundle> ownedByDocument) {
        if (library.equals(libraryBundles) && ownedByDocument.equals(documentBundles)) {
            return;
        }
        Map<String, ComponentDefinition> previous = rootsById(libraryBundles, documentBundles);
        Map<String, ComponentDefinition> next = rootsById(library, ownedByDocument);
        previous.forEach((id, definition) -> {
            if (!Objects.equals(definition, next.get(id))) {
                icons.remove(SubcircuitTypes.typeIdOf(id)); // a renamed or reshaped box redraws
            }
        });
        libraryBundles = List.copyOf(library);
        documentBundles = List.copyOf(ownedByDocument);
        libraryComponents = libraryBundles.stream().map(bundle -> UserComponent.of(bundle, true)).toList();
        documentComponents = documentBundles.stream().map(bundle -> UserComponent.of(bundle, false)).toList();
        rebuild();
    }

    /** Called when the user picks a user component (the standard types keep going to {@code onChoose}). */
    public void setUserComponentChooser(Consumer<UserComponent> chooser) {
        userChooser = Objects.requireNonNull(chooser);
    }

    /** Titles of the visible sections, top to bottom. */
    public List<String> visibleSections() {
        List<String> titles = new ArrayList<>();
        for (DefaultMutableTreeNode section : sections()) {
            Object item = section.getUserObject();
            titles.add(item instanceof UserSection userSection ? userSection.title()
                    : Messages.categoryName((ComponentCategory) item));
        }
        return titles;
    }

    /** What a drag from the selected entry carries. Package-private for tests. */
    Transferable dragTransferable() {
        return transferHandler.createTransferable(tree);
    }

    public List<String> visibleTypeIds() {
        List<String> ids = new ArrayList<>();
        for (DefaultMutableTreeNode section : sections()) {
            for (Object leaf : Collections.list(section.children())) {
                ids.add(typeIdOf(((DefaultMutableTreeNode) leaf).getUserObject()));
            }
        }
        return ids;
    }

    public void choose(String typeId) {
        pathOf(typeId).ifPresent(tree::setSelectionPath);
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
        addUserSection(root, UserSection.LIBRARY, libraryComponents, query);
        addUserSection(root, UserSection.DOCUMENT, documentComponents, query);
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

    private void addUserSection(DefaultMutableTreeNode root, UserSection section, List<UserComponent> components,
                                String query) {
        DefaultMutableTreeNode sectionNode = new DefaultMutableTreeNode(section);
        components.stream()
                .filter(component -> PaletteFilter.matches(component.type(), query))
                .forEach(component -> sectionNode.add(new DefaultMutableTreeNode(component, false)));
        if (sectionNode.getChildCount() > 0) {
            root.add(sectionNode);
        }
    }

    private Optional<UserComponent> selectedUserComponent() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return Optional.empty();
        }
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof UserComponent component ? Optional.of(component) : Optional.empty();
    }

    private List<DefaultMutableTreeNode> sections() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        return Collections.list(root.children()).stream().map(DefaultMutableTreeNode.class::cast).toList();
    }

    private Optional<TreePath> pathOf(String typeId) {
        for (DefaultMutableTreeNode section : sections()) {
            for (Object leaf : Collections.list(section.children())) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) leaf;
                if (typeIdOf(node.getUserObject()).equals(typeId)) {
                    return Optional.of(new TreePath(node.getPath()));
                }
            }
        }
        return Optional.empty();
    }

    private static String typeIdOf(Object item) {
        return item instanceof UserComponent component ? component.type().id() : ((ComponentType) item).id();
    }

    private static Map<String, ComponentDefinition> rootsById(List<DefinitionBundle> library,
                                                              List<DefinitionBundle> ownedByDocument) {
        Map<String, ComponentDefinition> roots = new HashMap<>();
        for (List<DefinitionBundle> bundles : List.of(library, ownedByDocument)) {
            bundles.forEach(bundle -> roots.put(bundle.root().id(), bundle.root()));
        }
        return roots;
    }

    /** One instance at the origin plus every definition it needs: what the canvas floats on a drop. */
    private String fragmentJson(UserComponent component) {
        cl.almejo.vsim.core.document.Component instance = component.type().newComponent(new ElementId("c1"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        return new FragmentFormat(registry).write(new CircuitFragment(List.of(instance), List.of(), List.of(),
                component.bundle().all()));
    }

    /** Drags entries to a canvas: a standard type as text, a user component as a fragment with its definitions. */
    private final class PaletteTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent source) {
            return COPY;
        }

        @Override
        public Transferable createTransferable(JComponent source) {
            Optional<UserComponent> user = selectedUserComponent();
            if (user.isPresent()) {
                return new FragmentTransferable(fragmentJson(user.get()));
            }
            return selectedType().map(type -> new StringSelection(PaletteTransfer.encode(type.id()))).orElse(null);
        }
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(owner, value, selected, expanded, leaf, row, focused);
            Object item = ((DefaultMutableTreeNode) value).getUserObject();
            if (item instanceof ComponentType type) {
                setText(Messages.componentName(type));
                setIcon(icon(type));
            } else if (item instanceof UserComponent component) {
                setText(Messages.componentName(component.type()));
                setIcon(icon(component.type()));
            } else if (item instanceof UserSection section) {
                setText(section.title());
                setIcon(null);
            } else if (item instanceof ComponentCategory category) {
                setText(Messages.categoryName(category));
                setIcon(null);
            }
            return this;
        }
    }
}
