package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ComponentPreset;
import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.library.InstancePaths;
import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.core.search.SearchProblem;
import cl.almejo.vsim.core.search.SearchQuery;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.ui.actions.Actions;
import cl.almejo.vsim.ui.actions.Keys;
import cl.almejo.vsim.ui.actions.KeyboardFocus;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.app.Delay;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;
import cl.almejo.vsim.ui.search.FindFeatures;
import cl.almejo.vsim.ui.search.SearchField;
import cl.almejo.vsim.ui.window.EditorView;
import cl.almejo.vsim.ui.window.SeePiece;
import cl.almejo.vsim.ui.window.WindowCommands;
import cl.almejo.vsim.ui.window.tabs.GroupColors;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The circuit tree of the left column (spec §5.3): the circuit (its name, «N piezas») and every piece of it with the
 * palette's icon, instances of user components holding their bodies at any depth, each named by
 * {@link cl.almejo.vsim.ui.window.InstanceNames} (the id only in the tooltip). It shows the whole circuit in every tab
 * of it, and follows the tab's place: the row of that place (the circuit, or the instance a component tab shows) is
 * expanded and marked with the group colour.
 *
 * <p>A click on a piece goes to it — its place's tab comes to the front (the component tab opens with the right
 * instance when needed), the piece is selected and the view travels there: the same {@link WindowCommands#goToPart}
 * as F8 and «Ver la pieza». A double click on a user-component instance enters it; its single click waits the
 * double-click time first, so a double click never goes to another tab on the way. The context menu has «Entrar»,
 * «Ver la pieza», «Cambiar forma…» and «Propiedades». Choosing a single piece on the canvas chooses its row (without
 * travelling).
 *
 * <p>The keyboard (Alt+1 focuses the tree): the arrows move, Enter does what a click does, Up on the first row goes to
 * the search box and Down there comes back, Esc gives the focus to the canvas.
 *
 * <p>The search field on top looks through the whole circuit, inside components too, with the find bar's rules and
 * its «Aa» / «.*» options; matches are listed flat with their route in grey («en Ram › Muldiv») and the matched text in
 * bold. Enter goes to the first, Down chooses it in the tree; Esc clears it. Ctrl+F inside the tree focuses it ({@link
 * FindFeatures#FILTER_FOCUS_PROPERTY}). No values are shown. {@link #dispose()} stops every listener.
 */
public final class CircuitTree extends JPanel {

    private static final int ICON_SIZE = 16;
    private static final int MARK_ALPHA = 70;
    /** When the desktop does not say its double-click time (headless, some window managers). */
    private static final int DEFAULT_DOUBLE_CLICK_MILLISECONDS = 400;
    private static final String ENTER_KEY = "circuitTree.enter";
    private static final String CLEAR_SEARCH_KEY = "circuitTree.clearSearch";
    private static final String FIRST_RESULT_KEY = "circuitTree.firstResult";
    private static final String UP_KEY = "circuitTree.up";
    private static final String TO_THE_ROWS_KEY = "circuitTree.toTheRows";
    private static final String TO_THE_CANVAS_KEY = "circuitTree.toTheCanvas";
    private static final String ROUTE_SEPARATOR = " › ";

    private final EditorView view;
    private final OpenDocument place;
    private final Delay delay;
    private final PainterRegistry painters;
    private final Appearance appearance;
    private final AppPreferences preferences;
    /** Component icons by type and parameters, drawn with the current scheme; cleared when the scheme changes. */
    private final Map<IconKey, Icon> icons = new HashMap<>();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(treeModel);
    private final Renderer renderer = new Renderer();
    private final SearchField searchField = new SearchField(Messages.text("tree.search"));
    private final JLabel countLabel = new JLabel();
    private final Map<String, DefaultMutableTreeNode> nodesByPath = new HashMap<>();
    private final Runnable documentListener = this::refresh;
    private final Runnable appearanceListener = () -> {
        icons.clear();
        tree.repaint();
    };
    private final Runnable preferenceListener = this::preferencesChanged;
    private final Runnable selectionListener = this::followSelection;
    private final Runnable tabShownRemover;
    /** The place row last followed: a new one (another context) is chosen once, then the user decides. */
    private Optional<String> followedPlace = Optional.empty();
    private CircuitTreeNode root;
    private List<CircuitTreeModel.Match> matches = List.of();
    private boolean matchCase;
    private boolean regex;
    private boolean searching;
    /** The rows expanded when searching started, brought back when the search is cleared. */
    private Set<String> expandedBeforeSearch = Set.of();
    /** Cancels the click on a user component that waits to see whether a double click follows. */
    private Runnable pendingClick = () -> {
    };

    /** The tree of {@code view}'s circuit, in its left column. */
    public CircuitTree(EditorView view) {
        this(view, Delay.swing());
    }

    /** {@code delay} times the wait of a single click on a user component (a manual one in tests). */
    CircuitTree(EditorView view, Delay delay) {
        super(new BorderLayout());
        this.view = view;
        this.place = view.openDocument();
        this.delay = delay;
        this.painters = view.services().painters();
        this.appearance = view.services().appearance();
        this.preferences = view.services().preferences();
        this.matchCase = preferences.findMatchCase();
        this.regex = preferences.findRegex();
        tree.setCellRenderer(renderer);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(0); // a double click enters a component; the arrows fold
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addMouseListener(new Mouse());
        Keys.bind(tree, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ENTER_KEY,
                this::pressEnter);
        Keys.bind(tree, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), UP_KEY, this::pressUp);
        Keys.bind(tree, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), TO_THE_CANVAS_KEY,
                view::escapeToCanvas);
        putClientProperty(KeyboardFocus.TARGET_PROPERTY, tree);
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.onTextChange(this::searchChanged);
        Keys.bind(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                CLEAR_SEARCH_KEY, () -> !searchField.getText().isEmpty(), () -> searchField.setText(""));
        Keys.bind(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
                FIRST_RESULT_KEY, () -> !matches.isEmpty(), () -> goTo(matches.getFirst().node().path()));
        Keys.bind(searchField, JComponent.WHEN_FOCUSED, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
                TO_THE_ROWS_KEY, this::downFromTheSearch);
        JPanel top = new JPanel(new MigLayout("insets 4 4 2 4, gap 2, wrap 1, fillx", "[grow, fill]"));
        top.add(searchField);
        top.add(countLabel, "hidemode 3");
        countLabel.setVisible(false);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        putClientProperty(FindFeatures.FILTER_FOCUS_PROPERTY, (Runnable) this::focusSearch);
        place.addChangeListener(documentListener);
        appearance.addListener(appearanceListener);
        preferences.addListener(preferenceListener);
        view.selection().addListener(selectionListener);
        tabShownRemover = view.addTabShownListener(this::followPlace);
        refresh();
    }

    /** Stops following the document, the appearance, the find options and the selection: the view closed. */
    public void dispose() {
        pendingClick.run();
        place.removeChangeListener(documentListener);
        appearance.removeListener(appearanceListener);
        preferences.removeListener(preferenceListener);
        view.selection().removeListener(selectionListener);
        tabShownRemover.run();
    }

    private OpenDocument circuit() {
        return place.parent().orElse(place);
    }

    private WindowCommands window() {
        return view.windowCommands();
    }

    /** The row of the place this tab shows: the circuit, or the instance of a component tab; none «Sin valores». */
    private Optional<String> placePath() {
        return place.isBody() ? place.contextPath() : Optional.of(CircuitTreeNode.DOCUMENT_PATH);
    }

    // --- building the rows ---

    /** Reads the circuit again (an edit, another context), keeping the expanded rows and the chosen one. */
    private void refresh() {
        Set<String> expanded = searching ? expandedBeforeSearch : expandedPaths();
        Optional<String> chosen = chosenRow();
        OpenDocument circuit = circuit();
        root = CircuitTreeModel.build(circuit.displayName(), circuit.document(), circuit.registry());
        if (searching) {
            showMatches();
        } else {
            showTree(expanded);
        }
        chosen.flatMap(this::treePath).ifPresent(tree::setSelectionPath);
        Optional<String> placeRow = placeRow();
        if (!placeRow.equals(followedPlace)) {
            followedPlace = placeRow;
            followPlace();
        }
    }

    /**
     * The row this tab follows: the circuit, the instance whose values a component tab shows, or with «Sin valores»
     * the component's first use.
     */
    private Optional<String> placeRow() {
        if (!place.isBody()) {
            return Optional.of(CircuitTreeNode.DOCUMENT_PATH);
        }
        return place.contextPath().or(() -> place.definitionId().flatMap(definition ->
                place.instancePaths(definition).stream().findFirst().map(InstancePaths.InstanceUse::path)));
    }

    /**
     * Chooses the row of this tab's place, its route expanded and the row scrolled into view: when the tab comes to the
     * front and when «Valores de» changes. Never travels nor takes the focus (the rows act only on a click or a key).
     */
    private void followPlace() {
        if (searching) {
            return;
        }
        placeRow().ifPresent(path -> {
            expandTo(path);
            choose(path);
        });
    }

    private void showTree(Set<String> expanded) {
        nodesByPath.clear();
        DefaultMutableTreeNode rootNode = toSwing(root);
        treeModel.setRoot(rootNode);
        tree.expandPath(new TreePath(rootNode));
        expanded.forEach(path -> treePath(path).ifPresent(tree::expandPath));
    }

    private DefaultMutableTreeNode toSwing(CircuitTreeNode node) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(new Row(node, List.of()));
        nodesByPath.put(node.path(), swingNode);
        node.children().forEach(child -> swingNode.add(toSwing(child)));
        return swingNode;
    }

    /** Expands {@code path}'s row and every row above it. */
    private void expandTo(String path) {
        if (CircuitTreeNode.DOCUMENT_PATH.equals(path)) {
            return; // always expanded
        }
        String prefix = "";
        for (String segment : InstancePath.segments(path)) {
            prefix = prefix.isEmpty() ? segment : InstancePath.child(prefix, segment);
            treePath(prefix).ifPresent(tree::expandPath);
        }
    }

    private Set<String> expandedPaths() {
        Set<String> expanded = new HashSet<>();
        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(treeModel.getRoot()));
        if (paths != null) {
            while (paths.hasMoreElements()) {
                if (paths.nextElement().getLastPathComponent() instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof Row row) {
                    expanded.add(row.node().path());
                }
            }
        }
        return expanded;
    }

    private Optional<TreePath> treePath(String path) {
        return Optional.ofNullable(nodesByPath.get(path)).map(node -> new TreePath(node.getPath()));
    }

    private Optional<CircuitTreeNode> nodeAt(String path) {
        return Optional.ofNullable(nodesByPath.get(path)).map(node -> ((Row) node.getUserObject()).node());
    }

    // --- the search ---

    private void focusSearch() {
        view.keyboardFocus().request(searchField);
        searchField.selectAll();
    }

    private void searchChanged() {
        boolean nowSearching = !searchField.getText().isEmpty();
        if (nowSearching && !searching) {
            expandedBeforeSearch = expandedPaths();
        }
        boolean cleared = searching && !nowSearching;
        searching = nowSearching;
        countLabel.setVisible(searching);
        if (cleared) {
            matches = List.of();
            searchField.showError(false);
            searchField.setToolTipText(null);
            showTree(expandedBeforeSearch);
            expandedBeforeSearch = Set.of();
        } else if (searching) {
            showMatches();
        }
    }

    /** «Aa» or «.*» changed (in a find bar or Preferencias): search again with them. */
    private void preferencesChanged() {
        if (preferences.findMatchCase() == matchCase && preferences.findRegex() == regex) {
            return;
        }
        matchCase = preferences.findMatchCase();
        regex = preferences.findRegex();
        if (searching) {
            showMatches();
        }
    }

    /** The matches, flat under the circuit's row; the field red and a word on why when there are none. */
    private void showMatches() {
        CompiledQuery query = new SearchQuery(searchField.getText(), matchCase, regex).compile();
        List<CircuitTreeModel.Match> found = CircuitTreeModel.search(root, query);
        Optional<SearchProblem> problem = query.problem(); // after matching: a regex may give up while matching
        matches = problem.isPresent() ? List.of() : found;
        searchField.showError(matches.isEmpty());
        searchField.setToolTipText(problem.map(cause -> Messages.format(cause.messageKey(),
                cause.arguments().toArray())).orElse(null));
        countLabel.setText(switch (matches.size()) {
            case 0 -> Messages.text("tree.noResults");
            case 1 -> Messages.text("tree.resultOne");
            default -> Messages.format("tree.results", matches.size());
        });
        nodesByPath.clear();
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new Row(root, List.of()));
        nodesByPath.put(root.path(), rootNode);
        for (CircuitTreeModel.Match match : matches) {
            DefaultMutableTreeNode result = new DefaultMutableTreeNode(new Row(match.node(), match.route()));
            nodesByPath.put(match.node().path(), result);
            rootNode.add(result);
        }
        treeModel.setRoot(rootNode);
        tree.expandPath(new TreePath(rootNode));
    }

    // --- what the rows do ---

    /**
     * A click on the row {@code path}: once goes to the piece (the circuit's row brings its main tab to the front),
     * twice enters a user component. A user component's single click waits for the double-click time.
     */
    void rowClicked(String path, int clickCount) {
        Optional<CircuitTreeNode> node = nodeAt(path);
        if (node.isEmpty()) {
            return;
        }
        if (clickCount >= 2) {
            pendingClick.run();
            pendingClick = () -> {
            };
            if (node.get().isUserComponent()) {
                enter(node.get());
            }
            return;
        }
        if (node.get().isUserComponent()) {
            pendingClick.run();
            pendingClick = delay.after(doubleClickMilliseconds(), () -> goTo(path));
        } else {
            goTo(path);
        }
    }

    /**
     * Enter on the chosen row: what a click does, at once — goes to the piece, a user component too (a double click or
     * «Entrar» enters it; the user's decision, round C).
     */
    void pressEnter() {
        chosenRow().ifPresent(this::goTo);
    }

    /** Up moves to the row above; on the first row (or with none chosen) the search box above gets the focus. */
    private void pressUp() {
        int row = tree.getLeadSelectionRow();
        if (row <= 0) {
            focusSearch();
            return;
        }
        tree.setSelectionRow(row - 1);
        tree.scrollRowToVisible(row - 1);
    }

    /** Down in the search box: the tree gets the focus on its first row, the first result while searching. */
    private void downFromTheSearch() {
        int row = searching && !matches.isEmpty() ? 1 : 0;
        tree.setSelectionRow(row);
        tree.scrollRowToVisible(row);
        view.keyboardFocus().request(tree);
    }

    private void enter(CircuitTreeNode node) {
        node.definitionId().ifPresent(definitionId ->
                window().enter(circuit(), definitionId, Optional.of(node.path())));
    }

    /** Goes to the piece at {@code path} where it is; the circuit's row shows the main tab. */
    private void goTo(String path) {
        if (CircuitTreeNode.DOCUMENT_PATH.equals(path)) {
            window().showPlace(circuit(), Optional.empty(), Optional.empty());
            return;
        }
        if (!SeePiece.show(window(), circuit(), path)) {
            SeePiece.targetOf(path, circuit().document()).filter(this::isHere)
                    .ifPresent(target -> SeePiece.selectAndTravel(view, target.part()));
        }
    }

    /**
     * Brings the place of the piece at {@code path} to the front, selects the piece there and runs {@code action} on
     * that tab's view with the piece's id; without a window, only for a piece of this tab's place.
     */
    private void inItsPlace(String path, Consumer<PlacedPiece> action) {
        SeePiece.targetOf(path, circuit().document()).ifPresent(target -> {
            Optional<EditorView> shown = window().showPlace(circuit(), target.definitionId(), target.contextPath())
                    .or(() -> isHere(target) ? Optional.of(view) : Optional.empty());
            shown.ifPresent(where -> {
                SeePiece.selectAndTravel(where, target.part());
                action.accept(new PlacedPiece(where, target.part()));
            });
        });
    }

    /** A piece shown in the tab of its place. */
    private record PlacedPiece(EditorView view, ElementId part) {
    }

    /** Whether {@code target}'s place is the one this tab shows. */
    private boolean isHere(SeePiece.Target target) {
        return place.isBody()
                ? target.definitionId().equals(place.definitionId()) && target.contextPath().equals(place.contextPath())
                : target.definitionId().isEmpty();
    }

    /** The context menu of the row {@code path}; none for the circuit's row. */
    Optional<JPopupMenu> contextMenu(String path) {
        Optional<CircuitTreeNode> found = nodeAt(path).filter(node -> !node.isDocument());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        CircuitTreeNode node = found.get();
        JPopupMenu menu = new JPopupMenu();
        if (node.isUserComponent()) {
            menu.add(new JMenuItem(Actions.of(Messages.text("action.enter"), () -> enter(node))));
        }
        menu.add(new JMenuItem(Actions.of(Messages.text("tree.seePiece"), () -> goTo(path))));
        if (node.isUserComponent()) {
            menu.add(new JMenuItem(Actions.of(Messages.text("shape.change"), () -> inItsPlace(path,
                    piece -> piece.view().changeShapeOf(piece.part())))));
        }
        menu.add(new JMenuItem(Actions.of(Messages.text("tree.properties"), () -> inItsPlace(path,
                piece -> piece.view().inspector().showTab(Messages.text("inspector.properties"))))));
        return Optional.of(menu);
    }

    /** One piece chosen on the canvas: its row is chosen too, never travelling. */
    private void followSelection() {
        Set<ElementId> ids = view.selection().ids();
        if (ids.size() != 1 || searching) {
            return;
        }
        String id = ids.iterator().next().value();
        Optional<String> path = place.isBody() ? place.contextPath().map(context -> InstancePath.child(context, id))
                : Optional.of(id);
        path.ifPresent(found -> {
            int separator = found.lastIndexOf(InstancePath.SEPARATOR);
            if (separator > 0) {
                expandTo(found.substring(0, separator));
            }
            choose(found);
        });
    }

    /** Chooses the row {@code path} (scrolled into view), doing nothing else. */
    void choose(String path) {
        treePath(path).ifPresent(treePath -> {
            tree.setSelectionPath(treePath);
            tree.scrollPathToVisible(treePath);
        });
    }

    private static int doubleClickMilliseconds() {
        Object interval = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        return interval instanceof Integer milliseconds ? milliseconds : DEFAULT_DOUBLE_CLICK_MILLISECONDS;
    }

    // --- what the tests read ---

    /** The row's text: the name, and the type after a label («IR · Registro»). */
    Optional<String> rowText(String path) {
        return nodeAt(path).map(CircuitTree::text);
    }

    private static String text(CircuitTreeNode node) {
        return node.kind().map(kind -> Messages.format("tree.component.labeled", node.name(), kind))
                .orElse(node.name());
    }

    /** What the row of {@code path} shows in bold for the search now; empty when none is (for tests). */
    Optional<String> boldTextOf(String path) {
        return Optional.ofNullable(nodesByPath.get(path)).map(renderer::boldText).filter(text -> !text.isEmpty());
    }

    /** The grey text at the right of the row: «N piezas» on the circuit's row. */
    Optional<String> rightText(String path) {
        return nodeAt(path).filter(CircuitTreeNode::isDocument).map(node -> node.children().size() == 1
                ? Messages.text("tree.piecesOne") : Messages.format("tree.pieces", node.children().size()));
    }

    List<String> childPaths(String path) {
        return Optional.ofNullable(nodesByPath.get(path)).map(node -> {
            List<String> paths = new ArrayList<>();
            for (int index = 0; index < node.getChildCount(); index++) {
                paths.add(((Row) ((DefaultMutableTreeNode) node.getChildAt(index)).getUserObject()).node().path());
            }
            return paths;
        }).orElse(List.of());
    }

    Optional<Icon> iconOf(String path) {
        return nodeAt(path).map(this::icon);
    }

    /** The row's tooltip: what a click does, and the piece's id. */
    Optional<String> toolTipOf(String path) {
        return nodeAt(path).filter(node -> !node.isDocument()).map(node -> Messages.format(
                node.isUserComponent() ? "tree.tooltip.component" : "tree.tooltip.piece", node.path()));
    }

    SearchField searchField() {
        return searchField;
    }

    JTree tree() {
        return tree;
    }

    List<String> resultPaths() {
        return matches.stream().map(match -> match.node().path()).toList();
    }

    /** The grey route of a search result: «en Ram › Muldiv»; empty for a piece of the circuit or outside a search. */
    Optional<String> routeOf(String path) {
        return Optional.ofNullable(nodesByPath.get(path)).map(node -> (Row) node.getUserObject())
                .flatMap(Row::routeText);
    }

    String countText() {
        return countLabel.getText();
    }

    Optional<String> markedRow() {
        return placePath().filter(nodesByPath::containsKey);
    }

    boolean isExpanded(String path) {
        return treePath(path).map(tree::isExpanded).orElse(false);
    }

    Optional<String> chosenRow() {
        TreePath path = tree.getSelectionPath();
        return path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof Row row ? Optional.of(row.node().path()) : Optional.empty();
    }

    // --- drawing ---

    /**
     * The palette's icon of a piece (the type with its preset's parameters: a NAND shows its bubble); none for the
     * circuit's row.
     */
    private Icon icon(CircuitTreeNode node) {
        Optional<ComponentType> type = node.type();
        if (type.isEmpty()) {
            return null;
        }
        Map<String, Object> parameters = StandardComponentTypes.preset(node.paletteId())
                .map(ComponentPreset::parameters).orElseGet(Map::of);
        return icons.computeIfAbsent(new IconKey(type.get(), parameters), key -> new ComponentIcon(key.type(),
                painters.painterFor(key.type().id()), appearance.scheme(), ICON_SIZE, key.parameters()));
    }

    /** What an icon is drawn from: a NAND row and an AND row share the type but not the icon. */
    private record IconKey(ComponentType type, Map<String, Object> parameters) {
    }

    /** What a tree node holds: its row and, for a search result, the names of the pieces it is inside. */
    private record Row(CircuitTreeNode node, List<String> route) {
        Optional<String> routeText() {
            return route.isEmpty() ? Optional.empty()
                    : Optional.of(Messages.format("tree.route", String.join(ROUTE_SEPARATOR, route)));
        }

        @Override
        public String toString() {
            return text(node);
        }
    }

    private final class Mouse extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getButton() != MouseEvent.BUTTON1) {
                return;
            }
            rowAt(event).ifPresent(path -> rowClicked(path, event.getClickCount()));
        }

        @Override
        public void mousePressed(MouseEvent event) {
            showMenu(event);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            showMenu(event);
        }

        private Optional<String> rowAt(MouseEvent event) {
            TreePath path = tree.getPathForLocation(event.getX(), event.getY());
            return path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof Row row ? Optional.of(row.node().path()) : Optional.empty();
        }

        private void showMenu(MouseEvent event) {
            if (!event.isPopupTrigger()) {
                return;
            }
            rowAt(event).ifPresent(path -> contextMenu(path).ifPresent(menu -> {
                choose(path);
                menu.show(tree, event.getX(), event.getY());
            }));
        }
    }

    /**
     * Draws a row: icon and name (the searched text in bold), the type after a label, a search result's route and the
     * circuit's «N piezas» in grey; the row of this tab's place tinted with the group colour. Colours are read here,
     * when drawn.
     */
    private final class Renderer extends JPanel implements TreeCellRenderer {
        private final JLabel before = new JLabel();
        private final JLabel matched = new JLabel();
        private final JLabel after = new JLabel();
        private final JLabel muted = new JLabel();
        private Color mark;

        Renderer() {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            add(before);
            add(matched);
            add(after);
            add(muted);
            muted.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        }

        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int rowIndex, boolean focused) {
            if (!(value instanceof DefaultMutableTreeNode swingNode && swingNode.getUserObject() instanceof Row row)) {
                return this; // the empty root the tree has before the first rows
            }
            CircuitTreeNode node = row.node();
            Font font = owner.getFont();
            Color foreground = selected ? UIManager.getColor("Tree.selectionForeground") : owner.getForeground();
            Color grey = selected ? foreground : UIManager.getColor("Label.disabledForeground");
            showName(node, searching ? searchField.getText() : "", font, foreground);
            before.setIcon(icon(node));
            List<String> greyParts = new ArrayList<>();
            node.kind().ifPresent(kind -> greyParts.add("· " + kind));
            row.routeText().ifPresent(greyParts::add);
            rightText(node).ifPresent(greyParts::add);
            muted.setText(String.join(" ", greyParts));
            muted.setFont(font);
            muted.setForeground(grey);
            boolean marked = placePath().filter(node.path()::equals).isPresent();
            Color group = GroupColors.colorOf(circuit());
            mark = marked ? new Color(group.getRed(), group.getGreen(), group.getBlue(), MARK_ALPHA) : null;
            setOpaque(selected);
            setBackground(selected ? UIManager.getColor("Tree.selectionBackground") : owner.getBackground());
            setToolTipText(toolTipOf(node.path()).orElse(null));
            return this;
        }

        private Optional<String> rightText(CircuitTreeNode node) {
            return node.isDocument() ? CircuitTree.this.rightText(node.path()) : Optional.empty();
        }

        /**
         * The name, the first plain match of {@code query} in bold, placed in the name as the search finds it (so a
         * match that only exists ignoring accents or case is bold too). Nothing is bold with «.*»: the row is a result
         * all the same.
         */
        private void showName(CircuitTreeNode node, String query, Font font, Color foreground) {
            String name = node.name();
            Optional<CompiledQuery.Range> range = query.isEmpty() || regex ? Optional.empty()
                    : new SearchQuery(query, matchCase, false).compile().firstPlainMatch(name);
            int start = range.map(CompiledQuery.Range::start).orElse(-1);
            int end = range.map(CompiledQuery.Range::end).orElse(0);
            before.setText(start < 0 ? name : name.substring(0, start));
            matched.setText(start < 0 ? "" : name.substring(start, end));
            after.setText(start < 0 ? "" : name.substring(end));
            Font plain = font.deriveFont(node.isDocument() ? Font.BOLD : Font.PLAIN);
            before.setFont(plain);
            after.setFont(plain);
            matched.setFont(font.deriveFont(Font.BOLD));
            before.setForeground(foreground);
            matched.setForeground(foreground);
            after.setForeground(foreground);
        }

        /** What the row of {@code node} shows in bold for the search now (for tests). */
        String boldText(DefaultMutableTreeNode swingNode) {
            getTreeCellRendererComponent(tree, swingNode, false, false, true, 0, false);
            return matched.getText();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (mark != null) {
                graphics.setColor(mark);
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }
}
