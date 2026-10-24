package cl.almejo.vsim.ui.appearance;

/** Local tools of the appearance editor; shortcuts never enter the main circuit shortcut catalog. */
public enum ShapeTool {
    SELECT("V"), NODES("N"), PAN("H"), LINE("L"), RECTANGLE("R"), ELLIPSE("E"), TRIANGLE("G"), POLYGON("P"), TEXT("T"),
    ADD_NODE("A");

    private final String key;
    ShapeTool(String key) { this.key = key; }
    public String key() { return key; }
    public String messageKey() { return "shape.tool." + name().toLowerCase(java.util.Locale.ROOT); }
}
