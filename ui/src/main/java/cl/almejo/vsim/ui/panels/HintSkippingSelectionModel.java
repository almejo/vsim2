package cl.almejo.vsim.ui.panels;

import cl.almejo.vsim.ui.panels.PalettePanel.TopSection;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Optional;

/**
 * Never selects the empty hint of «Favoritos»: the arrow keys step over it (from the row above or below it), and
 * a click on it keeps the selection there was, so it neither traps the keyboard nor clears what is being placed.
 */
final class HintSkippingSelectionModel extends DefaultTreeSelectionModel {
    private final JTree tree;

    HintSkippingSelectionModel(JTree tree) {
        this.tree = tree;
    }

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
