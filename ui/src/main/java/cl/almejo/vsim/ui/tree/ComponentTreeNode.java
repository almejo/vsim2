package cl.almejo.vsim.ui.tree;

import cl.almejo.vsim.core.document.ElementId;

import java.util.List;
import java.util.Objects;

/**
 * One row of the component tree. {@code path} identifies the row: {@link #DOCUMENT_PATH} for the document,
 * {@link #framePath} for a frame, {@link #groupPath} for a group, the hierarchical simulation id ({@code c5},
 * {@code c5/c2}) for a component. {@code paletteId} names a component or group row and gives its icon: the type id, or
 * a preset's ({@code nand} for an AND with «Negar salida», so NANDs group apart from ANDs); empty
 * for the document and frames. {@code showsValue} marks nested interface pins and LEDs, whose current value is shown
 * after the text. {@code matchTexts} are what the tree's filter matches, the same texts the find bar searches: a
 * component's label and type name, a frame's title; none for the document and groups.
 */
public record ComponentTreeNode(Kind kind, String path, String text, String paletteId, boolean showsValue,
                                List<ComponentTreeNode> children, List<String> matchTexts) {

    public static final String DOCUMENT_PATH = "/";
    private static final String FRAME_PREFIX = "frame:";

    public enum Kind { DOCUMENT, FRAME, GROUP, COMPONENT }

    public ComponentTreeNode {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(paletteId, "paletteId");
        children = List.copyOf(children);
        matchTexts = List.copyOf(matchTexts);
    }

    /** This row with {@code newChildren} instead of its children. */
    public ComponentTreeNode withChildren(List<ComponentTreeNode> newChildren) {
        return new ComponentTreeNode(kind, path, text, paletteId, showsValue, newChildren, matchTexts);
    }

    /** Path of the top-level group of {@code paletteId}: element ids never contain "/", so it cannot clash. */
    public static String groupPath(String paletteId) {
        return DOCUMENT_PATH + paletteId;
    }

    /** Path of the group of {@code paletteId} inside the row {@code parentPath} (a frame). */
    public static String groupPath(String parentPath, String paletteId) {
        return DOCUMENT_PATH.equals(parentPath) ? groupPath(paletteId) : parentPath + "/" + paletteId;
    }

    /** Path of the frame {@code id}: stable while the frame exists, whatever it holds. */
    public static String framePath(ElementId id) {
        return FRAME_PREFIX + id.value();
    }
}
