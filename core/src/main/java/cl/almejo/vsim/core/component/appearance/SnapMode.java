package cl.almejo.vsim.core.component.appearance;

/** Quantization for symbol figures; electrical pins always use whole grid units. */
public enum SnapMode {
    HALF_UNITS(4), FREE(1);

    private final int step;

    SnapMode(int step) {
        this.step = step;
    }

    public int snap(int coordinate) {
        return Math.round((float) coordinate / step) * step;
    }
}
