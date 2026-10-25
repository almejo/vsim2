package cl.almejo.vsim.ui.appearance;

import java.util.List;

/**
 * The toolbar icons of «Cambiar forma…», by file name under {@code icons/} (drawn from the approved mockup's glyphs
 * in the main toolbar's style). Seleccionar, Mover vista, the magnet, undo/redo and zoom reuse the main icons
 * ({@code select}, {@code pan}, {@code snap}, {@code undo}, {@code redo}, {@code zoom-in}, {@code zoom-out}).
 */
public final class ShapeEditorIcons {

    public static final String NODES = "shape-nodes";
    public static final String LINE = "shape-line";
    public static final String RECTANGLE = "shape-rectangle";
    public static final String ELLIPSE = "shape-ellipse";
    public static final String TRIANGLE = "shape-triangle";
    public static final String MULTILINE = "shape-multiline";
    public static final String POLYGON = "shape-polygon";
    public static final String TEXT = "shape-text";
    public static final String NODE_ADD = "shape-node-add";
    public static final String NODE_REMOVE = "shape-node-remove";
    public static final String CURVE = "shape-curve";
    public static final String CLOSE_PATH = "shape-close-path";
    public static final String FILL = "shape-fill";
    public static final String TEMPLATES = "shape-templates";
    public static final String FRONT = "shape-front";
    public static final String BACK = "shape-back";

    private ShapeEditorIcons() {
    }

    /** Every shape editor icon, once. */
    public static List<String> names() {
        return List.of(NODES, LINE, MULTILINE, RECTANGLE, ELLIPSE, TRIANGLE, POLYGON, TEXT, NODE_ADD, NODE_REMOVE,
                CURVE, CLOSE_PATH, FILL, TEMPLATES, FRONT, BACK);
    }
}
