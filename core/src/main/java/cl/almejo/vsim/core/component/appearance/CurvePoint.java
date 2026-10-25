package cl.almejo.vsim.core.component.appearance;

/** An exact point on a segment, in eighths of a grid unit; unlike {@link SymbolPoint} it is not rounded. */
public record CurvePoint(double x, double y) {
}
