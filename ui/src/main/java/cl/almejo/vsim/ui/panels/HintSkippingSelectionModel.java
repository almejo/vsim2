package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.panels.PalettePanel.TopSection;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Never selects the empty hint of «Favoritos» nor a disabled entry (one that cannot be placed here, inside a component
 * tab): the arrow keys step over such a row (from the row above or below it), and a click on it keeps the selection
 * there was, so it neither traps the keyboard nor clears what is being placed.
 */
final class HintSkippingSelectionModel extends DefaultTreeSelectionModel {
    private final JTree tree;
    private final Predicate<Object> disabled;

    HintSkippingSelectionModel(JTree tree, Predicate<Object> disabled) {
        this.tree = tree;
        this.disabled = disabled;
    }

    @Override
    public void setSelectionPaths(TreePath[] paths) {
        if (paths != null && paths.length == 1 && isSkipped(paths[0])) {
            pastTheHint(paths[0]).ifPresent(path -> super.setSelectionPaths(new TreePath[] {path}));
            return;
        }
        super.setSelectionPaths(paths == null ? null
                : Arrays.stream(paths).filter(path -> !isSkipped(path)).toArray(TreePath[]::new));
    }

    /** The row beyond the skipped one in the direction the lead moves; none when the lead is not next to it. */
    private Optional<TreePath> pastTheHint(TreePath hint) {
        int hintRow = tree.getRowForPath(hint);
        int leadRow = getLeadSelectionRow();
        int step = leadRow == hintRow - 1 ? 1 : leadRow == hintRow + 1 ? -1 : 0;
        return step == 0 ? Optional.empty() : Optional.ofNullable(tree.getPathForRow(hintRow + step))
                .filter(path -> !isSkipped(path));
    }

    private boolean isSkipped(TreePath path) {
        Object item = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        return item == TopSection.EMPTY || disabled.test(item);
    }
}
