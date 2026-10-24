package cl.almejo.vsim.core.component.appearance;

/** A local symbol coordinate in eighths of one circuit grid unit. */
public record SymbolPoint(int x, int y) {
    public static final int EIGHTHS = 8;
}
