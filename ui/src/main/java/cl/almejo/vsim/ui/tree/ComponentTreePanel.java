package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.document.DocumentElement;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.canvas.DrawnBounds;
import cl.almejo.vsim.ui.frames.FrameIcons;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.icons.Icons;
import cl.almejo.vsim.ui.paint.Appearance;
import cl.almejo.vsim.ui.paint.ComponentIcon;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import cl.almejo.vsim.core.search.CompiledQuery;
import cl.almejo.vsim.core.search.SearchProblem;
import cl.almejo.vsim.core.search.SearchQuery;
import cl.almejo.vsim.ui.app.AppPreferences;
import cl.almejo.vsim.ui.search.FindFeatures;
import cl.almejo.vsim.ui.search.SearchField;
import net.miginfocom.swing.MigLayout;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The "Componentes" inspector tab (spec §6.9): the document, its frames and components (nested by the innermost
 * enclosing frame; a list, or grouped by type) and, for instances of user components, their parts, each row with its
 * palette icon. Choosing a row selects and centers its frame or top-level component in the canvas. Nested interface pins and LEDs show their value from the latest snapshot of the current netlist.
 *
 * <p>A filter field on top keeps the rows whose label, type name or frame title match, plus their ancestors, all
 * expanded; it matches with the find bar's {@link CompiledQuery} and its «Aa» / «.*» preferences, so both always
 * agree. Clearing it brings back the rows that were expanded before filtering. Ctrl+F inside the tab focuses it
 * ({@link FindFeatures#FILTER_FOCUS_PROPERTY}); Esc in it clears it.
 */
public final class ComponentTreePanel extends JPanel {

    private static final int ICON_SIZE = 16;

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final PainterRegistry painters;
    private final Appearance appearance;
    /** Component icons by type id, drawn with the current scheme; cleared when the scheme changes. */
    private final Map<String, Icon> icons = new HashMap<>();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(treeModel);
    private final JCheckBox groupByType = new JCheckBox(Messages.text("tree.groupByType"));
    private final SearchField filterField = new SearchField(Messages.text("tree.filter"));
    private final AppPreferences preferences;
    private final Runnable preferenceListener = this::preferencesChanged;
    private boolean matchCase;
    private boolean regex;
    /** The rows expanded when filtering started, brought back when the filter is cleared. */
    private Set<String> expandedBeforeFiltering = Set.of();
    private boolean filtering;
    private final Map<String, DefaultMutableTreeNode> nodesByPath = new HashMap<>();
    private final Runnable documentListener = this::rebuild;
    private final Runnable appearanceListener = () -> {
        icons.clear();
        tree.repaint();
    };
    private final Consumer<SimulationSnapshot> snapshotListener = this::showSnapshot;
    private SimulationSnapshot snapshot = SimulationSnapshot.EMPTY;
    private Map<String, String> values = Map.of();
    private ComponentTreeNode root;
    private boolean rebuilding;

    public ComponentTreePanel(OpenDocument openDocument, CircuitCanvas canvas, PainterRegistry painters,
                              Appearance appearance, AppPreferences preferences) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.canvas = canvas;
        this.painters = painters;
        this.appearance = appearance;
        this.preferences = preferences;
        this.matchCase = preferences.findMatchCase();
        this.regex = preferences.findRegex();
        tree.setCellRenderer(new Renderer());
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> selectionChanged());
        groupByType.addItemListener(event -> rebuild());
        filterField.putClientProperty("JTextField.showClearButton", true);
        filterField.onTextChange(this::filterChanged);
        bindEscapeToClearTheFilter();
        JPanel top = new JPanel(new MigLayout("insets 4 4 0 4, gap 2, wrap 1, fillx", "[grow, fill]"));
        top.add(filterField);
        top.add(groupByType);
        add(top, BorderLayout.NORTH);
        putClientProperty(FindFeatures.FILTER_FOCUS_PROPERTY, (Runnable) this::focusFilter);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        openDocument.addChangeListener(documentListener);
        canvas.addSnapshotListener(snapshotListener);
        appearance.addListener(appearanceListener);
        preferences.addListener(preferenceListener);
        rebuild();
    }

    /** Stops following the shared document, the canvas and the appearance: the view closed. */
    public void dispose() {
        openDocument.removeChangeListener(documentListener);
        canvas.removeSnapshotListener(snapshotListener);
        appearance.removeListener(appearanceListener);
        preferences.removeListener(preferenceListener);
    }

    JTree tree() {
        return tree;
    }

    JCheckBox groupByTypeToggle() {
        return groupByType;
    }

    SearchField filterField() {
        return filterField;
    }

    /** Ctrl+F with the focus in this tab: the filter, its whole text selected. */
    private void focusFilter() {
        filterField.requestFocusInWindow();
        filterField.selectAll();
    }

    /** The tree path of the row whose {@link ComponentTreeNode#path()} is {@code nodePath}, if it is shown. */
    Optional<TreePath> treePath(String nodePath) {
        DefaultMutableTreeNode node = nodesByPath.get(nodePath);
        return node == null ? Optional.empty() : Optional.of(new TreePath(node.getPath()));
    }

    /** The text the row {@code nodePath} shows, value included. */
    Optional<String> rowText(String nodePath) {
        return Optional.ofNullable(nodesByPath.get(nodePath)).map(DefaultMutableTreeNode::toString);
    }

    /** Rebuilds every row from the document, keeping expanded rows expanded and the chosen row chosen. */
    void rebuild() {
        Set<String> expanded = filtering ? Set.of() : expandedPaths();
        rebuild(expanded);
    }

    /** Rebuilds every row; unfiltered, {@code expanded} are the rows to expand, filtered every row kept is. */
    private void rebuild(Set<String> expanded) {
        Optional<String> chosen = chosenNode().map(ComponentTreeNode::path);
        root = ComponentTreeModel.build(openDocument.displayName(), openDocument.document(), openDocument.registry(),
                groupByType.isSelected());
        ComponentTreeNode shown = filtering ? filtered(root) : root;
        // the stored snapshot may belong to the revision before this edit: ignore it unless its token is current
        values = snapshot == SimulationSnapshot.EMPTY || snapshot.token() == openDocument.netlistToken()
                ? ComponentTreeModel.values(root, snapshot) : Map.of();
        nodesByPath.clear();
        rebuilding = true;
        try {
            DefaultMutableTreeNode rootNode = toSwing(shown);
            treeModel.setRoot(rootNode);
            tree.expandPath(new TreePath(rootNode));
            if (filtering) {
                expandAll(rootNode);
            }
            for (String path : expanded) {
                treePath(path).ifPresent(tree::expandPath);
            }
            chosen.flatMap(this::treePath).ifPresent(tree::setSelectionPath);
        } finally {
            rebuilding = false;
        }
    }

    /** The rows the filter keeps under the document row, and the field red when it keeps none or cannot match. */
    private ComponentTreeNode filtered(ComponentTreeNode all) {
        CompiledQuery query = new SearchQuery(filterField.getText(), matchCase, regex).compile();
        List<ComponentTreeNode> kept = new ArrayList<>();
        for (ComponentTreeNode child : all.children()) {
            ComponentTreeModel.filter(child, query, openDocument.registry()).ifPresent(kept::add);
        }
        Optional<SearchProblem> problem = query.problem(); // after matching: a regex may give up while matching
        if (problem.isPresent()) {
            kept.clear(); // gave up part way: what it kept so far is not the answer
        }
        filterField.showError(problem.isPresent() || kept.isEmpty());
        filterField.setToolTipText(problem.map(found -> Messages.format(found.messageKey(),
                found.arguments().toArray())).orElse(kept.isEmpty() ? Messages.text("find.noResults") : null));
        return all.withChildren(kept);
    }

    private void expandAll(DefaultMutableTreeNode node) {
        if (node.getChildCount() == 0) {
            return;
        }
        tree.expandPath(new TreePath(node.getPath()));
        for (int index = 0; index < node.getChildCount(); index++) {
            expandAll((DefaultMutableTreeNode) node.getChildAt(index));
        }
    }

    private void filterChanged() {
        boolean nowFiltering = !filterField.getText().isEmpty();
        if (nowFiltering && !filtering) {
            expandedBeforeFiltering = expandedPaths();
        }
        boolean cleared = filtering && !nowFiltering;
        filtering = nowFiltering;
        if (cleared) {
            filterField.showError(false);
            filterField.setToolTipText(null);
            rebuild(expandedBeforeFiltering);
            expandedBeforeFiltering = Set.of();
        } else {
            rebuild();
        }
    }

    /** «Aa» or «.*» changed (in a find bar or Preferencias): filter again with them. */
    private void preferencesChanged() {
        if (preferences.findMatchCase() == matchCase && preferences.findRegex() == regex) {
            return;
        }
        matchCase = preferences.findMatchCase();
        regex = preferences.findRegex();
        if (filtering) {
            rebuild();
        }
    }

    /** Esc in a filter with text clears it; an empty filter lets Esc go on (to the canvas tool). */
    private void bindEscapeToClearTheFilter() {
        String actionKey = "tree.clearFilter";
        filterField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), actionKey);
        filterField.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return !filterField.getText().isEmpty();
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                filterField.setText("");
            }
        });
    }

    /** Takes the values of a new snapshot; repaints only the rows whose value changed. */
    void showSnapshot(SimulationSnapshot latest) {
        if (latest != SimulationSnapshot.EMPTY && latest.token() != openDocument.netlistToken()) {
            return; // compiled from an older revision: its ids may not match these rows
        }
        snapshot = latest;
        Map<String, String> next = ComponentTreeModel.values(root, latest);
        if (next.equals(values)) {
            return;
        }
        Set<String> changed = new HashSet<>(values.keySet());
        changed.addAll(next.keySet());
        Map<String, String> previous = values;
        changed.removeIf(path -> Objects.equals(previous.get(path), next.get(path)));
        values = next;
        for (String path : changed) {
            DefaultMutableTreeNode node = nodesByPath.get(path);
            if (node != null && node.getUserObject() instanceof Row row) {
                node.setUserObject(new Row(row.node(), next.get(path)));
                treeModel.nodeChanged(node);
            }
        }
    }

    private DefaultMutableTreeNode toSwing(ComponentTreeNode node) {
        DefaultMutableTreeNode swingNode = new DefaultMutableTreeNode(new Row(node, values.get(node.path())));
        nodesByPath.put(node.path(), swingNode);
        for (ComponentTreeNode child : node.children()) {
            swingNode.add(toSwing(child));
        }
        return swingNode;
    }

    private Set<String> expandedPaths() {
        Set<String> expanded = new HashSet<>();
        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(treeModel.getRoot()));
        if (paths != null) {
            while (paths.hasMoreElements()) {
                Object last = paths.nextElement().getLastPathComponent();
                if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Row row) {
                    expanded.add(row.node().path());
                }
            }
        }
        return expanded;
    }

    private Optional<ComponentTreeNode> chosenNode() {
        TreePath path = tree.getSelectionPath();
        if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof Row row) {
            return Optional.of(row.node());
        }
        return Optional.empty();
    }

    /**
     * A component row selects and centers its top-level component, a frame row its frame; the rebuild restoring a row
     * does not.
     */
    private void selectionChanged() {
        if (rebuilding) {
            return;
        }
        chosenNode().flatMap(this::elementOf).ifPresent(element -> {
            canvas.selection().select(element.id());
            canvas.travelTo(DrawnBounds.of(element, openDocument.registry())); // like the find bar
        });
    }

    private Optional<DocumentElement> elementOf(ComponentTreeNode node) {
        return switch (node.kind()) {
            case COMPONENT -> openDocument.document().component(new ElementId(InstancePath.topLevel(node.path())))
                    .map(DocumentElement.class::cast);
            case FRAME -> openDocument.document().frames().stream()
                    .filter(frame -> ComponentTreeNode.framePath(frame.id()).equals(node.path()))
                    .findFirst().map(DocumentElement.class::cast);
            case DOCUMENT, GROUP -> Optional.empty();
        };
    }

    /** The icon of a row: the palette icon of its type, the frame tool icon for frames, none for the document. */
    Icon icon(ComponentTreeNode node) {
        String typeId = node.typeId(); // the palette icon: the type with its default parameters, as in PalettePanel
        return switch (node.kind()) {
            case COMPONENT, GROUP -> openDocument.registry().find(typeId)
                    .map(type -> icons.computeIfAbsent(type.id(), id -> new ComponentIcon(type,
                            painters.painterFor(id), appearance.scheme(), ICON_SIZE)))
                    .orElse(null);
            case FRAME -> Icons.get(FrameIcons.FRAME_TOOL, ICON_SIZE);
            case DOCUMENT -> null;
        };
    }

    /** Draws each row with its icon. */
    private final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree owner, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(owner, value, selected, expanded, leaf, row, focused);
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Row treeRow) {
                setIcon(icon(treeRow.node()));
            }
            return this;
        }
    }

    /** What a tree node holds: its row and the value it shows (null for none). */
    private record Row(ComponentTreeNode node, String value) {
        @Override
        public String toString() {
            return value == null ? node.text() : Messages.format("tree.withValue", node.text(), value);
        }
    }
}
