package cl.almejo.vsim.core.machine;

/** A point of a state diagram, in diagram units (free, not the circuit grid). */
public record DiagramPoint(double x, double y) {

    public static final DiagramPoint ORIGIN = new DiagramPoint(0, 0);

    public DiagramPoint {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("a diagram point needs finite coordinates: " + x + "," + y);
        }
    }

    public DiagramPoint translated(double deltaX, double deltaY) {
        return new DiagramPoint(x + deltaX, y + deltaY);
    }
}
