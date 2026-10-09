package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.component.ElementBounds;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.CircuitCanvas;
import cl.almejo.vsim.ui.i18n.Messages;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The "Componentes" inspector tab (spec §6.9): the document, its components (a list, or grouped by type) and, for
 * instances of user components, their parts. Choosing a row selects and centers its top-level component in the
 * canvas. Nested interface pins and LEDs show their value from the latest snapshot of the current netlist.
 */
public final class ComponentTreePanel extends JPanel {

    private final OpenDocument openDocument;
    private final CircuitCanvas canvas;
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree = new JTree(treeModel);
    private final JCheckBox groupByType = new JCheckBox(Messages.text("tree.groupByType"));
    private final Map<String, DefaultMutableTreeNode> nodesByPath = new HashMap<>();
    private final Runnable documentListener = this::rebuild;
    private final Consumer<SimulationSnapshot> snapshotListener = this::showSnapshot;
    private SimulationSnapshot snapshot = SimulationSnapshot.EMPTY;
    private Map<String, String> values = Map.of();
    private ComponentTreeNode root;
    private boolean rebuilding;

    public ComponentTreePanel(OpenDocument openDocument, CircuitCanvas canvas) {
        super(new BorderLayout());
        this.openDocument = openDocument;
        this.canvas = canvas;
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> selectionChanged());
        groupByType.addItemListener(event -> rebuild());
        add(groupByType, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        openDocument.addChangeListener(documentListener);
        canvas.addSnapshotListener(snapshotListener);
        rebuild();
    }

    /** Stops following the shared document and the canvas: the view closed. */
    public void dispose() {
        openDocument.removeChangeListener(documentListener);
        canvas.removeSnapshotListener(snapshotListener);
    }

    JTree tree() {
        return tree;
    }

    JCheckBox groupByTypeToggle() {
        return groupByType;
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
        Set<String> expanded = expandedPaths();
        Optional<String> chosen = chosenNode().map(ComponentTreeNode::path);
        root = ComponentTreeModel.build(openDocument.displayName(), openDocument.document(), openDocument.registry(),
                groupByType.isSelected());
        // the stored snapshot may belong to the revision before this edit: ignore it unless its token is current
        values = snapshot == SimulationSnapshot.EMPTY || snapshot.token() == openDocument.netlistToken()
                ? ComponentTreeModel.values(root, snapshot) : Map.of();
        nodesByPath.clear();
        rebuilding = true;
        try {
            DefaultMutableTreeNode rootNode = toSwing(root);
            treeModel.setRoot(rootNode);
            tree.expandPath(new TreePath(rootNode));
            for (String path : expanded) {
                treePath(path).ifPresent(tree::expandPath);
            }
            chosen.flatMap(this::treePath).ifPresent(tree::setSelectionPath);
        } finally {
            rebuilding = false;
        }
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

    /** A component row selects and centers its top-level component; the rebuild restoring a row does not. */
    private void selectionChanged() {
        if (rebuilding) {
            return;
        }
        chosenNode().filter(node -> node.kind() == ComponentTreeNode.Kind.COMPONENT).ifPresent(node -> {
            ElementId id = new ElementId(InstancePath.topLevel(node.path()));
            openDocument.document().component(id).ifPresent(component -> {
                canvas.selection().select(id);
                canvas.centerOn(ElementBounds.of(component, openDocument.registry()));
            });
        });
    }

    /** What a tree node holds: its row and the value it shows (null for none). */
    private record Row(ComponentTreeNode node, String value) {
        @Override
        public String toString() {
            return value == null ? node.text() : Messages.format("tree.withValue", node.text(), value);
        }
    }
}
