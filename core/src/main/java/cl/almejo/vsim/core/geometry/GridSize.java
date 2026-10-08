package cl.almejo.vsim.core.geometry;

public record GridSize(int width, int height) {
    public GridSize {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("size must be positive: " + width + "x" + height);
        }
    }
}
