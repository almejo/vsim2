package cl.almejo.vsim.ui.appearance;

import java.util.Locale;

/** Local tools of the appearance editor; shortcuts never enter the main circuit shortcut catalog. */
public enum ShapeTool {
    SELECT("V"), NODES("N"), PAN("H"), LINE("L"), MULTILINE("K"), RECTANGLE("R"), ELLIPSE("E"), TRIANGLE("G"),
    POLYGON("P"), TEXT("T"), ADD_NODE("A"), REMOVE_NODE("Q");

    private final String key;

    ShapeTool(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public String messageKey() {
        return "shape.tool." + name().toLowerCase(Locale.ROOT);
    }
}
