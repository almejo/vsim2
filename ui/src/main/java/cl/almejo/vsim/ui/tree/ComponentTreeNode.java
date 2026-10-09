package cl.almejo.vsim.ui.tree;

import java.util.List;
import java.util.Objects;

/**
 * One row of the component tree. {@code path} identifies the row: {@link #DOCUMENT_PATH} for the document,
 * {@link #groupPath} for a group, the hierarchical simulation id ({@code c5}, {@code c5/c2}) for a component.
 * {@code showsValue} marks nested interface pins and LEDs, whose current value is shown after the text.
 */
public record ComponentTreeNode(Kind kind, String path, String text, boolean showsValue,
                                List<ComponentTreeNode> children) {

    public static final String DOCUMENT_PATH = "/";

    public enum Kind { DOCUMENT, GROUP, COMPONENT }

    public ComponentTreeNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(text, "text");
        children = List.copyOf(children);
    }

    /** Path of the group of {@code typeId}: element ids never contain "/", so it cannot clash with a component. */
    public static String groupPath(String typeId) {
        return DOCUMENT_PATH + typeId;
    }
}
