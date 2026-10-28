package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.core.component.ComponentCategory;
import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;
import cl.almejo.vsim.core.io.CircuitFragment;
import cl.almejo.vsim.core.io.FragmentFormat;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.DefinitionBundle;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.ui.app.Listeners;
import cl.almejo.vsim.ui.clipboard.FragmentRefusingTransferHandler;
import cl.almejo.vsim.ui.i18n.Descriptions;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.shortcuts.ShortcutCatalog;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
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
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    enum UserSection {
        LIBRARY, DOCUMENT;

        String title() {
            return this == LIBRARY ? Messages.text("palette.section.library")
                    : Messages.text("palette.section.document");
        }
    }

    /**
     * The entry each preset follows in its category, so the gates read AND, OR, XOR, NAND, NOR, XNOR, Buffer, NOT
     * (spec 2026-10-28 §3): the negated gates after the three plain ones, NOT after the Buffer, and the controlled
     * inverter after the tri-state buffer.
     */
    private static final Map<String, String> PRESET_PLACES = Map.of(
            StandardComponentTypes.NAND_PRESET, StandardComponentTypes.XOR,
            StandardComponentTypes.NOR_PRESET, StandardComponentTypes.NAND_PRESET,
            StandardComponentTypes.XNOR_PRESET, StandardComponentTypes.NOR_PRESET,
            StandardComponentTypes.NOT_PRESET, StandardComponentTypes.BUFFER,
            StandardComponentTypes.INVERTER_CONTROLLED_PRESET, StandardComponentTypes.TRI_STATE);
    private static final List<ComponentCategory> ORDER = List.of(ComponentCategory.GATES,
            ComponentCategory.WIRING, ComponentCategory.ARITHMETIC, ComponentCategory.PLEXERS,
            ComponentCategory.SEQUENTIAL, ComponentCategory.MEMORY, ComponentCategory.INPUT_OUTPUT,
            ComponentCategory.DEBUG);
    static final int ICON_SIZE = 24;
    /** Width of a help tooltip before it wraps, in pixels. */
    private static final int HELP_WIDTH = 260;

    /** The sections above the categories, «Favoritos» and «Recientes», and the empty hint row of «Favoritos». */
    enum TopSection { FAVORITES, EMPTY, RECENT }

    private final ComponentTypeRegistry registry;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final Runnable appearanceListener;
    private final Consumer<PaletteEntry> onChoose;
    private final JTextField search = new JTextField();
    private final JTree tree = new FavoritesTree(this);
    private final Map<String, Icon> icons = new HashMap<>();
    private final PaletteTransferHandler transferHandler = new PaletteTransferHandler(this);
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
    private final Listeners<Runnable> entriesListeners = new Listeners<>();

    /** The ids (type or preset) shown as favourites now; read live by the tree, the renderer and the popup. */
    List<String> favoriteIds() {
        return favoriteIds;
    }

    /** The favourite toggle, present once an owner turns favourites on. */
    Optional<Consumer<String>> favoriteToggle() {
        return favoriteToggle;
    }

    /** Where dropped circuit files go; null while none is set. */
    Consumer<List<Path>> fileDropHandler() {
        return fileDropHandler;
    }

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
        entries().forEach(entry -> available.put(entry.id(), entry));
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
        return entriesListeners.add(Objects.requireNonNull(listener));
    }

    private void fireEntriesChanged() {
        entriesListeners.fire(Runnable::run);
    }

    /**
     * Available standard and user entries, independent of the palette's current search filter. An id is offered once;
     * the library's and the circuit's own sections never share one (the circuit's list leaves out the library's ids),
     * so no version of a user component hides another.
     */
    public List<PaletteEntry> entries() {
        Map<String, PaletteEntry> entries = new LinkedHashMap<>();
        for (ComponentCategory category : ORDER) {
            standardEntries(category).forEach(entry -> entries.put(entry.id(), entry));
        }
        for (UserComponent component : libraryComponents) {
            entries.put(component.type().id(), new PaletteEntry(component.type(), Optional.of(component)));
        }
        for (UserComponent component : documentComponents) {
            entries.put(component.type().id(), new PaletteEntry(component.type(), Optional.of(component)));
        }
        return List.copyOf(entries.values());
    }

    /**
     * The types of {@code category} in the registry's order, with each preset of one of them (NAND, NOR, XNOR, NOT)
     * where {@link #PRESET_PLACES} puts it: AND, OR, XOR, NAND, NOR, XNOR, Buffer, NOT.
     */
    private List<PaletteEntry> standardEntries(ComponentCategory category) {
        List<PaletteEntry> entries = new ArrayList<>();
        registry.all().stream().filter(type -> type.category() == category)
                .forEach(type -> entries.add(PaletteEntry.of(type)));
        for (ComponentPreset preset : StandardComponentTypes.presets()) {
            PaletteEntry.of(preset, registry).filter(entry -> entry.type().category() == category)
                    .ifPresent(entry -> entries.add(placeOf(entries, preset), entry));
        }
        return entries;
    }

    /** Where {@code preset} goes in {@code entries}: right after the entry {@link #PRESET_PLACES} names, or last. */
    private static int placeOf(List<PaletteEntry> entries, ComponentPreset preset) {
        String after = PRESET_PLACES.getOrDefault(preset.id(), "");
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).id().equals(after)) {
                return index + 1;
            }
        }
        return entries.size();
    }

    /** Starts the same placement as choosing the entry in the palette, including library-version warnings. */
    public void chooseEntry(PaletteEntry entry) {
        if (entry.userComponent().isPresent()) {
            userChooser.accept(entry.userComponent().get());
        } else {
            onChoose.accept(entry);
        }
    }

    void toggleFavorite(String typeId) {
        favoriteToggle.ifPresent(toggle -> toggle.accept(typeId));
    }

    /**
     * {@code onChoose} receives the standard entries chosen (a type, or a preset with its parameters); user components
     * go to the {@link #setUserComponentChooser chooser}.
     */
    public PalettePanel(ComponentTypeRegistry registry, PainterRegistry painters, ColorScheme scheme,
                        Consumer<PaletteEntry> onChoose) {
        this(registry, painters, new Appearance(scheme), onChoose);
    }

    public PalettePanel(ComponentTypeRegistry registry, PainterRegistry painters, Appearance appearance,
                        Consumer<PaletteEntry> onChoose) {
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
        tree.setCellRenderer(new PaletteRowRenderer(this));
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setSelectionModel(new HintSkippingSelectionModel(tree));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> {
            if (!updating) {
                selectedEntry().ifPresent(onChoose);
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
        return icon(PaletteEntry.of(type));
    }

    /** The icon of {@code entry}: its type drawn with the entry's parameters (a NAND's bubble), current scheme. */
    Icon icon(PaletteEntry entry) {
        return icons.computeIfAbsent(entry.id(), id -> new ComponentIcon(entry.type(),
                painters.painterFor(entry.type().id()), appearance.scheme(), ICON_SIZE, entry.parameters()));
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
        if (selectedEntry().isPresent() || selectedUserComponent().isPresent()) {
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
        if (favoriteToggle.isEmpty() || !isEntryItem(item)) {
            return provided;
        }
        String typeId = idOf(item);
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
                if (isEntryItem(item)) {
                    ids.add(idOf(item));
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
            standardEntries(category).stream()
                    .filter(entry -> PaletteFilter.matches(entry, query))
                    .forEach(entry -> categoryNode.add(new DefaultMutableTreeNode(entry.treeItem(), false)));
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

    /** The standard entry (a type or a preset) highlighted now; empty for a user component or none. */
    Optional<PaletteEntry> selectedEntry() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return Optional.empty();
        }
        Object value = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return value instanceof PaletteEntry entry ? Optional.of(entry) : Optional.empty();
    }

    /** «Favoritos» filtered by {@code query}: no rows while favourites are off, the hint when empty and unfiltered. */
    private DefaultMutableTreeNode favoritesSection(String query) {
        DefaultMutableTreeNode section = new DefaultMutableTreeNode(TopSection.FAVORITES);
        if (favoriteToggle.isEmpty()) {
            return section;
        }
        favoriteEntries().stream().filter(entry -> PaletteFilter.matches(entry, query))
                .forEach(entry -> section.add(new DefaultMutableTreeNode(entry.treeItem(), false)));
        if (section.getChildCount() == 0 && query.isBlank()) {
            section.add(new DefaultMutableTreeNode(TopSection.EMPTY, false));
        }
        return section;
    }

    /** «Recientes» filtered by {@code query}; no hint row: an empty section is not shown. */
    private DefaultMutableTreeNode recentSection(String query) {
        DefaultMutableTreeNode section = new DefaultMutableTreeNode(TopSection.RECENT);
        recentEntries().stream().filter(entry -> PaletteFilter.matches(entry, query))
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

    Optional<UserComponent> selectedUserComponent() {
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
                if (isEntryItem(node.getUserObject()) && idOf(node.getUserObject()).equals(typeId)) {
                    return new TreePath(node.getPath());
                }
            }
        }
        return null;
    }

    /**
     * The palette tooltip of {@code entry}: the first paragraph of its «Descripción» ({@link Descriptions#summary}),
     * wrapped to {@link #HELP_WIDTH}, or null (no tooltip).
     */
    static String help(PaletteEntry entry) {
        return Descriptions.summary(entry.type(), entry.parameters())
                .map(text -> "<html><body style='width:" + HELP_WIDTH + "px'>" + escapeHtml(text) + "</body></html>")
                .orElse(null);
    }

    /** {@code text} safe inside a tooltip's HTML: «A<B» and «<<» are text, not tags. */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** The tooltip the palette shows over the entry of {@code typeId}, if any. Package-private for tests. */
    Optional<String> toolTipFor(String typeId) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent();
            Object item = node.getUserObject();
            if (isEntryItem(item) && typeId.equals(idOf(item))) {
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
            if (isEntryItem(item) && typeId.equals(idOf(item))) {
                Component rendered = tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, false,
                        true, row, false);
                return Optional.ofNullable(rendered.getAccessibleContext().getAccessibleName());
            }
        }
        return Optional.empty();
    }

    /** True for a row that is a component to place (a type, a preset or a user component), not a heading. */
    static boolean isEntryItem(Object item) {
        return item instanceof PaletteEntry || item instanceof UserComponent;
    }

    /** The id an entry row is known by: the preset's ({@code nand}) or the type's. */
    static String idOf(Object item) {
        return item instanceof UserComponent component ? component.type().id() : ((PaletteEntry) item).id();
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
    String fragmentJson(UserComponent component) {
        cl.almejo.vsim.core.document.Component instance = component.type().newComponent(new ElementId("c1"),
                GridPoint.ORIGIN, Rotation.R0, Map.of(), "");
        return new FragmentFormat(registry).write(new CircuitFragment(List.of(instance), List.of(), List.of(),
                component.bundle().all()));
    }

    /**
     * The name a screen reader announces for a component row: a favourite says so («LED, favorito»), since the star
     * is painted and has no accessible object of its own.
     */
    String accessibleName(Object item) {
        String name = item instanceof UserComponent component ? Messages.componentName(component.type())
                : ((PaletteEntry) item).name();
        return favoriteToggle.isPresent() && favoriteIds.contains(idOf(item))
                ? Messages.format("palette.favorite.accessibleName", name) : name;
    }
}
