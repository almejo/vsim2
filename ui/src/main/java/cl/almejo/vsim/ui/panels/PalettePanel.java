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
import cl.almejo.vsim.ui.clipboard.CircuitFileDrop;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.panels.TextChanges;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Component palette: search box plus a tree of «Favoritos» and «Recientes» (when their owners turn them on), the
 * categories, then "Mis componentes" (the user's library) and "Componentes de este circuito". Choosing an entry
 * starts placing it.
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
            ComponentCategory.INPUT_OUTPUT, ComponentCategory.BLOCKS, ComponentCategory.DEBUG,
            ComponentCategory.INTERFACE);
    private static final int ICON_SIZE = 24;
    /** Width of a help tooltip before it wraps, in pixels. */
    private static final int HELP_WIDTH = 260;

    /** The sections above the categories, «Favoritos» and «Recientes», and the empty hint row of «Favoritos». */
    private enum TopSection { FAVORITES, EMPTY, RECENT }

    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final Runnable appearanceListener;
    private final Consumer<ComponentType> onChoose;
    private final JTextField search = new JTextField();
    private final JTree tree = new FavoritesTree();
    private final Map<String, Icon> icons = new HashMap<>();
    private final PaletteTransferHandler transferHandler = new PaletteTransferHandler();
    private List<DefinitionBundle> libraryBundles = List.of();
    private List<DefinitionBundle> documentBundles = List.of();
    private List<UserComponent> libraryComponents = List.of();
    private List<UserComponent> documentComponents = List.of();
    private Consumer<UserComponent> userChooser = component -> {
    };
    private Function<Optional<UserComponent>, Optional<JPopupMenu>> userMenuProvider = component -> Optional.empty();
    private final List<BiConsumer<Optional<UserComponent>, JPopupMenu>> userMenuContributors = new ArrayList<>();
    private Consumer<List<Path>> fileDropHandler;
    private Runnable focusAfterMouseChoice = () -> {
    };
    private boolean updating;
    private Optional<Consumer<String>> favoriteToggle = Optional.empty();
    private List<String> favoriteIds = List.of();
    private List<String> recentIds = List.of();
    private final List<Runnable> entriesListeners = new CopyOnWriteArrayList<>();

    /**
     * Turns on «Favoritos» and the star: a click on the star or the context-menu item hands the type id to
     * {@code toggle}, whose owner saves the change and answers with {@link #setFavorites}.
     */
    public void setFavoriteToggle(Consumer<String> toggle) {
        favoriteToggle = Optional.of(Objects.requireNonNull(toggle));
        rebuild();
    }

    /**
     * The favourites' type ids in marking order; ids of components not available now are kept but not shown. An
     * equal list changes nothing.
     */
    public void setFavorites(List<String> typeIds) {
        List<String> next = List.copyOf(typeIds);
        if (favoriteIds.equals(next)) {
            return;
        }
        Set<String> changed = symmetricDifference(favoriteIds, next);
        favoriteIds = next;
        refreshSection(TopSection.FAVORITES, favoritesSection(search.getText()), 0);
        refreshRecents(); // a new favourite leaves «Recientes», an unmarked one may come back
        ((FavoritesTree) tree).repaintRowsOf(changed);
        fireEntriesChanged();
    }

    private static Set<String> symmetricDifference(List<String> first, List<String> second) {
        Set<String> difference = new HashSet<>(first);
        difference.addAll(second);
        Set<String> common = new HashSet<>(first);
        common.retainAll(second);
        difference.removeAll(common);
        return difference;
    }

    /**
     * Puts {@code fresh}'s children in the section {@code key} of the tree shown, without rebuilding the rest: the
     * other sections keep what the user folded, and a highlight inside the section stays on its entry (or, when it
     * left the section, moves to the first row that shows it: «Favoritos» when it was just marked, otherwise its
     * category) without calling the chooser again. An empty
     * {@code fresh} removes the section; a missing one is inserted at {@code index}, expanded. EDT.
     */
    private void refreshSection(Object key, DefaultMutableTreeNode fresh, int index) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        Optional<DefaultMutableTreeNode> existing = sections().stream()
                .filter(section -> section.getUserObject() == key).findFirst();
        TreePath selected = tree.getSelectionPath();
        Optional<Object> selectedInside = selected != null && existing.isPresent() && selected.getPathCount() == 3
                && selected.getPathComponent(1) == existing.get()
                ? Optional.of(((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject())
                : Optional.empty();
        updating = true;
        try {
            if (fresh.getChildCount() == 0) {
                existing.ifPresent(model::removeNodeFromParent);
            } else if (existing.isPresent()) {
                DefaultMutableTreeNode section = existing.get();
                boolean expanded = tree.isExpanded(new TreePath(section.getPath()));
                section.removeAllChildren();
                while (fresh.getChildCount() > 0) {
                    section.add((DefaultMutableTreeNode) fresh.getFirstChild());
                }
                model.nodeStructureChanged(section);
                if (expanded) {
                    tree.expandPath(new TreePath(section.getPath()));
                }
            } else {
                model.insertNodeInto(fresh, root, Math.min(index, root.getChildCount()));
                tree.expandPath(new TreePath(fresh.getPath()));
            }
            selectedInside.flatMap(item -> pathOfItem(key, item)).ifPresent(tree::setSelectionPath);
        } finally {
            updating = false;
        }
    }

    /** The visible row of {@code item}: in the section {@code preferred} when there, else the first one. */
    private Optional<TreePath> pathOfItem(Object preferred, Object item) {
        Optional<TreePath> first = Optional.empty();
        for (DefaultMutableTreeNode section : sections()) {
            for (Object leaf : Collections.list(section.children())) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) leaf;
                if (node.getUserObject().equals(item)) {
                    if (section.getUserObject() == preferred) {
                        return Optional.of(new TreePath(node.getPath()));
                    }
                    first = first.or(() -> Optional.of(new TreePath(node.getPath())));
                }
            }
        }
        return first;
    }

    /**
     * The components used recently, most recent first (spec 2026-10-21 §9.2): «Recientes», right below «Favoritos»,
     * hidden while none is available. An equal list changes nothing. EDT.
     */
    public void setRecents(List<String> typeIds) {
        List<String> next = List.copyOf(typeIds);
        if (recentIds.equals(next)) {
            return;
        }
        recentIds = next;
        refreshRecents();
        fireEntriesChanged();
    }

    /** The recent components available now and not favourites (those are already in view), most recent first. */
    public List<PaletteEntry> recentEntries() {
        Map<String, PaletteEntry> available = availableById();
        return recentIds.stream().filter(typeId -> !isShownAsFavorite(typeId)).map(available::get)
                .filter(Objects::nonNull).toList();
    }

    private boolean isShownAsFavorite(String typeId) {
        return favoriteToggle.isPresent() && favoriteIds.contains(typeId);
    }

    private Map<String, PaletteEntry> availableById() {
        Map<String, PaletteEntry> available = new HashMap<>();
        entries().forEach(entry -> available.put(entry.type().id(), entry));
        return available;
    }

    private void refreshRecents() {
        boolean favoritesShown = sections().stream()
                .anyMatch(section -> section.getUserObject() == TopSection.FAVORITES);
        refreshSection(TopSection.RECENT, recentSection(search.getText()), favoritesShown ? 1 : 0);
    }

    /** The favourites available now, in marking order (empty while favourites are off). */
    public List<PaletteEntry> favoriteEntries() {
        if (favoriteToggle.isEmpty()) {
            return List.of();
        }
        Map<String, PaletteEntry> available = availableById();
        return favoriteIds.stream().map(available::get).filter(Objects::nonNull).toList();
    }

    /**
     * Runs {@code listener} after the entries, the favourites or the recent components change (EDT). Returns its
     * removal.
     */
    public Runnable addEntriesListener(Runnable listener) {
        entriesListeners.add(Objects.requireNonNull(listener));
        return () -> entriesListeners.remove(listener);
    }

    private void fireEntriesChanged() {
        entriesListeners.forEach(Runnable::run);
    }

    /**
     * Available standard and user entries, independent of the palette's current search filter. An id is offered once;
     * the library's and the circuit's own sections never share one (the circuit's list leaves out the library's ids),
     * so no version of a user component hides another.
     */
    public List<PaletteEntry> entries() {
        Map<String, PaletteEntry> entries = new LinkedHashMap<>();
        for (ComponentCategory category : ORDER) {
            registry.all().stream().filter(type -> type.category() == category)
                    .forEach(type -> entries.put(type.id(), new PaletteEntry(type, Optional.empty())));
        }
        for (UserComponent component : libraryComponents) {
            entries.put(component.type().id(), new PaletteEntry(component.type(), Optional.of(component)));
        }
        for (UserComponent component : documentComponents) {
            entries.put(component.type().id(), new PaletteEntry(component.type(), Optional.of(component)));
        }
        return List.copyOf(entries.values());
    }

    /** Starts the same placement as choosing the entry in the palette, including library-version warnings. */
    public void chooseEntry(PaletteEntry entry) {
        if (entry.userComponent().isPresent()) {
            userChooser.accept(entry.userComponent().get());
        } else {
            onChoose.accept(entry.type());
        }
    }

    private void toggleFavorite(String typeId) {
        favoriteToggle.ifPresent(toggle -> toggle.accept(typeId));
    }

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
        TextChanges.onChange(search, this::rebuild);
        swallowUndoKeystrokes();
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(ICON_SIZE + 4);
        tree.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setSelectionModel(new HintSkippingSelectionModel());
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
        tree.setDropMode(DropMode.ON); // a file dragged over the tree must not change the selection (it starts placing)
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showPopup(event);
                handOverFocusAfterChoice(event);
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        // The star column sits at the visible right edge: a blitted horizontal scroll would drag old stars along.
        scroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        add(scroll);
        rebuild();
    }

    /** Stops following the shared appearance: the window showing this palette closed. */
    public void dispose() {
        appearance.removeListener(appearanceListener);
        ToolTipManager.sharedInstance().unregisterComponent(tree);
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

    /**
     * What gives the focus away (to the canvas) when the mouse is released on the chosen entry. With dragging enabled
     * the tree chooses on the press but takes the focus back on the release, after the choice asked for the canvas:
     * this runs once that release is over, so R, Delete and Escape reach the canvas while placing. Choosing with the
     * keyboard is left to {@code onChoose}.
     */
    public void setFocusAfterMouseChoice(Runnable focusTarget) {
        focusAfterMouseChoice = Objects.requireNonNull(focusTarget);
    }

    private void handOverFocusAfterChoice(MouseEvent event) {
        TreePath selected = tree.getSelectionPath();
        if (event.isPopupTrigger() || !SwingUtilities.isLeftMouseButton(event) || selected == null
                || !selected.equals(tree.getPathForLocation(event.getX(), event.getY()))) {
            return;
        }
        if (selectedType().isPresent() || selectedUserComponent().isPresent()) {
            SwingUtilities.invokeLater(focusAfterMouseChoice);
        }
    }

    /** What a row's star column shows. */
    enum StarState { NONE, OUTLINE, FILLED }

    /** The star {@code row} shows now. Package-private for tests. */
    StarState starState(int row) {
        FavoritesTree favoritesTree = (FavoritesTree) tree;
        return favoritesTree.starState(row, favoritesTree.hoverPath());
    }

    /** The row under the pointer, whose hover star shows. Package-private for tests. */
    Optional<TreePath> hoveredPath() {
        return ((FavoritesTree) tree).hoverPath();
    }

    /** Package-private for tests. */
    JTree tree() {
        return tree;
    }

    /** The tree model now shown: the same instance means nothing was rebuilt. Package-private for tests. */
    TreeModel treeModel() {
        return tree.getModel();
    }

    /** Keeps Ctrl+Z / Ctrl+Shift+Z in the search box from reaching the circuit's undo menu accelerators. */
    private void swallowUndoKeystrokes() {
        int mask = ShortcutCatalog.menuMask();
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
        fireEntriesChanged();
    }

    /** Called when the user picks a user component (the standard types keep going to {@code onChoose}). */
    public void setUserComponentChooser(Consumer<UserComponent> chooser) {
        userChooser = Objects.requireNonNull(chooser);
    }

    /** Builds the right-click menu for the user component under the mouse (empty: elsewhere in the palette). */
    public void setUserMenuProvider(Function<Optional<UserComponent>, Optional<JPopupMenu>> provider) {
        userMenuProvider = Objects.requireNonNull(provider);
    }

    /** Adds view-owned feature entries after the library entries; the returned action removes the contribution. */
    public Runnable addUserMenuContributor(BiConsumer<Optional<UserComponent>, JPopupMenu> contributor) {
        userMenuContributors.add(Objects.requireNonNull(contributor));
        return () -> userMenuContributors.remove(contributor);
    }

    /**
     * Receives the circuit and component files dropped on the palette, as the canvas and the window do; without a
     * handler drops are refused.
     */
    public void setFileDropHandler(Consumer<List<Path>> handler) {
        fileDropHandler = handler;
    }

    /** Titles of the visible sections, top to bottom. */
    public List<String> visibleSections() {
        List<String> titles = new ArrayList<>();
        for (DefaultMutableTreeNode section : sections()) {
            Object item = section.getUserObject();
            titles.add(item == TopSection.FAVORITES ? Messages.text("palette.favorites")
                    : item == TopSection.RECENT ? Messages.text("palette.recent")
                    : item instanceof UserSection userSection ? userSection.title()
                    : Messages.categoryName((ComponentCategory) item));
        }
        return titles;
    }

    /** What a drag from the selected entry carries. Package-private for tests. */
    Transferable dragTransferable() {
        return transferHandler.createTransferable(tree);
    }

    /** The drop actions a drag from the palette offers. Package-private for tests. */
    int dragSourceActions() {
        return transferHandler.getSourceActions(tree);
    }

    /** The context menu for the entry at {@code path} (null: no entry). Package-private for tests. */
    Optional<JPopupMenu> popupFor(TreePath path) {
        Optional<UserComponent> component = path == null ? Optional.empty() : userComponentOf(path);
        Optional<JPopupMenu> provided = userMenuProvider.apply(component);
        provided.ifPresent(popup -> userMenuContributors.forEach(contributor -> contributor.accept(component, popup)));
        Object item = path == null ? null : ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (favoriteToggle.isEmpty() || !(item instanceof ComponentType || item instanceof UserComponent)) {
            return provided;
        }
        String typeId = typeIdOf(item);
        JPopupMenu menu = provided.orElseGet(JPopupMenu::new);
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        JMenuItem favorite = new JMenuItem(Messages.text(favoriteIds.contains(typeId)
                ? "palette.favorite.remove" : "palette.favorite.add"));
        favorite.addActionListener(event -> toggleFavorite(typeId));
        menu.add(favorite);
        return Optional.of(menu);
    }

    /** What a paste or drop of {@code transferable} on the tree does. Package-private for tests. */
    boolean importFiles(Transferable transferable) {
        return transferHandler.importData(new TransferHandler.TransferSupport(tree, transferable));
    }

    public List<String> visibleTypeIds() {
        List<String> ids = new ArrayList<>();
        for (DefaultMutableTreeNode section : sections()) {
            for (Object leaf : Collections.list(section.children())) {
                Object item = ((DefaultMutableTreeNode) leaf).getUserObject();
                if (item instanceof ComponentType || item instanceof UserComponent) {
                    ids.add(typeIdOf(item));
                }
            }
        }
        return ids;
    }

    public void choose(String typeId) {
        TreePath path = pathOf(typeId);
        if (path != null) {
            tree.setSelectionPath(path);
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

    /** True while an entry is highlighted. Package-private for tests. */
    boolean hasSelection() {
        return tree.getSelectionPath() != null;
    }

    private void rebuild() {
        String query = search.getText();
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        addSection(root, favoritesSection(query));
        addSection(root, recentSection(query));
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

    /** «Favoritos» filtered by {@code query}: no rows while favourites are off, the hint when empty and unfiltered. */
    private DefaultMutableTreeNode favoritesSection(String query) {
        DefaultMutableTreeNode section = new DefaultMutableTreeNode(TopSection.FAVORITES);
        if (favoriteToggle.isEmpty()) {
            return section;
        }
        favoriteEntries().stream().filter(entry -> PaletteFilter.matches(entry.type(), query))
                .forEach(entry -> section.add(new DefaultMutableTreeNode(entry.treeItem(), false)));
        if (section.getChildCount() == 0 && query.isBlank()) {
            section.add(new DefaultMutableTreeNode(TopSection.EMPTY, false));
        }
        return section;
    }

    /** «Recientes» filtered by {@code query}; no hint row: an empty section is not shown. */
    private DefaultMutableTreeNode recentSection(String query) {
        DefaultMutableTreeNode section = new DefaultMutableTreeNode(TopSection.RECENT);
        recentEntries().stream().filter(entry -> PaletteFilter.matches(entry.type(), query))
                .forEach(entry -> section.add(new DefaultMutableTreeNode(entry.treeItem(), false)));
        return section;
    }

    private static void addSection(DefaultMutableTreeNode root, DefaultMutableTreeNode section) {
        if (section.getChildCount() > 0) {
            root.add(section);
        }
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
        return path == null ? Optional.empty() : userComponentOf(path);
    }

    private static Optional<UserComponent> userComponentOf(TreePath path) {
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof UserComponent component ? Optional.of(component) : Optional.empty();
    }

    private List<DefaultMutableTreeNode> sections() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        return Collections.list(root.children()).stream().map(DefaultMutableTreeNode.class::cast).toList();
    }

    /** The tree path of the visible entry with {@code typeId}, or null. Package-private for tests. */
    TreePath pathOf(String typeId) {
        for (DefaultMutableTreeNode section : sections()) {
            for (Object leaf : Collections.list(section.children())) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) leaf;
                if ((node.getUserObject() instanceof ComponentType || node.getUserObject() instanceof UserComponent)
                        && typeIdOf(node.getUserObject()).equals(typeId)) {
                    return new TreePath(node.getPath());
                }
            }
        }
        return null;
    }

    /** The palette tooltip of {@code type}: its help wrapped to {@link #HELP_WIDTH}, or null (no tooltip). */
    private static String help(ComponentType type) {
        return Messages.componentHelp(type)
                .map(text -> "<html><body style='width:" + HELP_WIDTH + "px'>" + text + "</body></html>")
                .orElse(null);
    }

    /** The tooltip the palette shows over the entry of {@code typeId}, if any. Package-private for tests. */
    Optional<String> toolTipFor(String typeId) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent();
            Object item = node.getUserObject();
            if ((item instanceof ComponentType || item instanceof UserComponent) && typeId.equals(typeIdOf(item))) {
                Component rendered = tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, false,
                        true, row, false);
                return Optional.ofNullable(((JComponent) rendered).getToolTipText());
            }
        }
        return Optional.empty();
    }

    /** What a screen reader announces for the entry of {@code typeId}, if shown. Package-private for tests. */
    Optional<String> accessibleNameOf(String typeId) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent();
            Object item = node.getUserObject();
            if ((item instanceof ComponentType || item instanceof UserComponent) && typeId.equals(typeIdOf(item))) {
                Component rendered = tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, false,
                        true, row, false);
                return Optional.ofNullable(rendered.getAccessibleContext().getAccessibleName());
            }
        }
        return Optional.empty();
    }

    private static String typeIdOf(Object item) {
        return item instanceof UserComponent component ? component.type().id() : ((ComponentType) item).id();
    }

    private void showPopup(MouseEvent event) {
        if (event.isPopupTrigger()) {
            popupFor(((FavoritesTree) tree).pathAtRow(event.getX(), event.getY()))
                    .ifPresent(menu -> menu.show(tree, event.getX(), event.getY()));
        }
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

    /**
     * Drags entries to a canvas (a standard type as text, a user component as a fragment with its definitions) and
     * accepts dropped circuit and component files.
     */
    private final class PaletteTransferHandler extends TransferHandler {
        @Override
        public int getSourceActions(JComponent source) {
            return COPY;
        }

        @Override
        public Transferable createTransferable(JComponent source) {
            Optional<UserComponent> user = selectedUserComponent();
            if (user.isPresent()) {
                return new PaletteDragTransferable(fragmentJson(user.get()), user.get().type().id());
            }
            return selectedType().map(type -> new StringSelection(PaletteTransfer.encode(type.id()))).orElse(null);
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return fileDropHandler != null && CircuitFileDrop.canImport(support);
        }

        @Override
        public boolean importData(TransferSupport support) {
            return fileDropHandler != null && CircuitFileDrop.importData(support, fileDropHandler);
        }
    }

    /**
     * Never selects the empty hint of «Favoritos»: the arrow keys step over it (from the row above or below it), and
     * a click on it keeps the selection there was, so it neither traps the keyboard nor clears what is being placed.
     */
    private final class HintSkippingSelectionModel extends DefaultTreeSelectionModel {
        @Override
        public void setSelectionPaths(TreePath[] paths) {
            if (paths != null && paths.length == 1 && isHint(paths[0])) {
                pastTheHint(paths[0]).ifPresent(path -> super.setSelectionPaths(new TreePath[] {path}));
                return;
            }
            super.setSelectionPaths(paths == null ? null
                    : Arrays.stream(paths).filter(path -> !isHint(path)).toArray(TreePath[]::new));
        }

        /** The row beyond the hint in the direction the lead moves; none when the lead is not next to it. */
        private Optional<TreePath> pastTheHint(TreePath hint) {
            int hintRow = tree.getRowForPath(hint);
            int leadRow = getLeadSelectionRow();
            int step = leadRow == hintRow - 1 ? 1 : leadRow == hintRow + 1 ? -1 : 0;
            return step == 0 ? Optional.empty() : Optional.ofNullable(tree.getPathForRow(hintRow + step));
        }

        private static boolean isHint(TreePath path) {
            return ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject() == TopSection.EMPTY;
        }
    }

    /** Intercepts the star before BasicTreeUI selects a row or starts a drag. */
    private final class FavoritesTree extends JTree {
        private static final int STAR_COLUMN_WIDTH = 24;
        private Optional<Point> hoverPoint = Optional.empty();
        private boolean starGesture;

        FavoritesTree() {
            super(new DefaultMutableTreeNode());
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    hoverAt(Optional.of(event.getPoint()));
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    hoverAt(Optional.empty());
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent event) {
                    hoverAt(Optional.empty());
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK),
                    "palette.contextMenu");
            getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "palette.contextMenu");
            getActionMap().put("palette.contextMenu", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    TreePath path = getSelectionPath();
                    if (path != null && isShowing()) {
                        Rectangle bounds = getPathBounds(path);
                        popupFor(path).ifPresent(menu -> menu.show(FavoritesTree.this, bounds.x,
                                bounds.y + bounds.height));
                    }
                }
            });
        }

        /**
         * The row under the pointer, worked out from where the pointer is rather than kept as a path: a section that
         * changes under it (a click on the star) leaves the star of the row now under the pointer showing.
         */
        Optional<TreePath> hoverPath() {
            return hoverPoint.map(point -> pathAtRow(point.x, point.y));
        }

        private void hoverAt(Optional<Point> point) {
            int previous = hoverPath().map(this::getRowForPath).orElse(-1);
            hoverPoint = point;
            int next = hoverPath().map(this::getRowForPath).orElse(-1);
            if (previous != next) {
                repaintRow(previous);
                repaintRow(next);
            }
        }

        /**
         * The star {@code row} shows: filled for a favourite, in its own category as well as in «Favoritos» (no hover
         * needed); an outline under the pointer while no popup is open; none on headers and hints.
         */
        StarState starState(int row, Optional<TreePath> hovered) {
            TreePath path = getPathForRow(row);
            Optional<String> typeId = favoriteType(path);
            if (typeId.isEmpty()) {
                return StarState.NONE;
            }
            if (favoriteIds.contains(typeId.get())) {
                return StarState.FILLED;
            }
            return hovered.filter(path::equals).isPresent()
                    && MenuSelectionManager.defaultManager().getSelectedPath().length == 0
                    ? StarState.OUTLINE : StarState.NONE;
        }

        /** Repaints the rows of {@code typeIds} (their stars filled or emptied). */
        void repaintRowsOf(Set<String> typeIds) {
            for (int row = 0; row < getRowCount(); row++) {
                if (favoriteType(getPathForRow(row)).filter(typeIds::contains).isPresent()) {
                    repaintRow(row);
                }
            }
        }

        private void repaintRow(int row) {
            Rectangle bounds = row < 0 ? null : getRowBounds(row);
            if (bounds != null) {
                repaint(0, bounds.y, getWidth(), bounds.height);
            }
        }

        private TreePath pathAtRow(int x, int y) {
            TreePath path = getClosestPathForLocation(x, y);
            Rectangle bounds = path == null ? null : getPathBounds(path);
            return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? path : null;
        }

        private Rectangle starBounds(TreePath path) {
            Rectangle row = getPathBounds(path);
            Rectangle visible = getVisibleRect();
            return new Rectangle(visible.x + visible.width - STAR_COLUMN_WIDTH, row.y,
                    STAR_COLUMN_WIDTH, row.height);
        }

        private Optional<String> favoriteType(TreePath path) {
            if (path == null || favoriteToggle.isEmpty()) {
                return Optional.empty();
            }
            Object item = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            return item instanceof ComponentType || item instanceof UserComponent
                    ? Optional.of(typeIdOf(item)) : Optional.empty();
        }

        @Override
        protected void processMouseEvent(MouseEvent event) {
            if (event.getID() == MouseEvent.MOUSE_PRESSED) {
                starGesture = false;
                TreePath path = pathAtRow(event.getX(), event.getY());
                Optional<String> typeId = favoriteType(path);
                if (SwingUtilities.isLeftMouseButton(event) && typeId.isPresent()
                        && starBounds(path).contains(event.getPoint())) {
                    starGesture = true;
                    toggleFavorite(typeId.get());
                    event.consume();
                    return;
                }
            } else if (starGesture && (event.getID() == MouseEvent.MOUSE_RELEASED
                    || event.getID() == MouseEvent.MOUSE_CLICKED)) {
                event.consume();
                return;
            }
            super.processMouseEvent(event);
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent event) {
            if (starGesture && event.getID() == MouseEvent.MOUSE_DRAGGED) {
                event.consume();
                return;
            }
            super.processMouseMotionEvent(event);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            TreePath path = pathAtRow(event.getX(), event.getY());
            Optional<String> typeId = favoriteType(path);
            if (typeId.isPresent() && starBounds(path).contains(event.getPoint())) {
                return Messages.text(favoriteIds.contains(typeId.get())
                        ? "palette.favorite.remove" : "palette.favorite.add");
            }
            return super.getToolTipText(event);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Rectangle clip = graphics.getClipBounds() != null ? graphics.getClipBounds() : getVisibleRect();
            int firstRow = Math.max(0, getClosestRowForLocation(clip.x, clip.y));
            int lastRow = getClosestRowForLocation(clip.x, clip.y + clip.height);
            Optional<TreePath> hovered = hoverPath();
            for (int row = firstRow; row <= lastRow && row < getRowCount(); row++) {
                StarState state = starState(row, hovered);
                Rectangle bounds = state == StarState.NONE ? null : starBounds(getPathForRow(row));
                if (bounds == null || !bounds.intersects(getVisibleRect())) {
                    continue;
                }
                boolean selected = isRowSelected(row);
                FavoriteStar star = new FavoriteStar(state == StarState.FILLED, selected);
                if (!selected) {
                    // Hides the tail of a long name; a selected row keeps its highlight whole.
                    graphics.setColor(getBackground());
                    graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }
                star.paintIcon(this, graphics, bounds.x + (bounds.width - star.getIconWidth()) / 2,
                        bounds.y + (bounds.height - star.getIconHeight()) / 2);
            }
        }
    }

    /**
     * The name a screen reader announces for a component row: a favourite says so («LED, favorito»), since the star
     * is painted and has no accessible object of its own.
     */
    private String accessibleName(Object item) {
        String name = Messages.componentName(item instanceof UserComponent component ? component.type()
                : (ComponentType) item);
        return favoriteToggle.isPresent() && favoriteIds.contains(typeIdOf(item))
                ? Messages.format("palette.favorite.accessibleName", name) : name;
    }

    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(owner, value, selected, expanded, leaf, row, focused);
            Object item = ((DefaultMutableTreeNode) value).getUserObject();
            setEnabled(owner.isEnabled() && item != TopSection.EMPTY);
            getAccessibleContext().setAccessibleName(item instanceof ComponentType || item instanceof UserComponent
                    ? accessibleName(item) : null);
            if (item instanceof ComponentType type) {
                setText(Messages.componentName(type));
                setIcon(icon(type));
                setToolTipText(help(type));
            } else if (item instanceof UserComponent component) {
                setText(Messages.componentName(component.type()));
                setIcon(icon(component.type()));
                setToolTipText(null);
            } else if (item instanceof TopSection section) {
                setText(Messages.text(switch (section) {
                    case FAVORITES -> "palette.favorites";
                    case EMPTY -> "palette.favorites.empty";
                    case RECENT -> "palette.recent";
                }));
                setIcon(switch (section) {
                    case FAVORITES -> new FavoriteStar(true, selected);
                    case EMPTY -> null;
                    case RECENT -> new RecentIcon(selected);
                });
                setToolTipText(null);
            } else if (item instanceof UserSection section) {
                setText(section.title());
                setIcon(null);
                setToolTipText(null);
            } else if (item instanceof ComponentCategory category) {
                setText(Messages.categoryName(category));
                setIcon(null);
                setToolTipText(null);
            }
            return this;
        }
    }
}
