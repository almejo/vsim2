package cl.almejo.vsim.core.io.logisim;

/**
 * A point of a Logisim file, in Logisim's pixels (its grid is 10 px): a component's location, a wire end, a pin's
 * place in an appearance.
 */
public record LogisimPoint(int x, int y) {

    /**
     * The point Logisim writes as {@code (x,y)} (the parentheses are optional, spaces are allowed).
     *
     * @throws IllegalArgumentException when the text is not two integers
     */
    public static LogisimPoint parse(String text) {
        String inside = text.strip();
        if (inside.startsWith("(") && inside.endsWith(")")) {
            inside = inside.substring(1, inside.length() - 1);
        }
        String[] parts = inside.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("not a point: " + text);
        }
        try {
            return new LogisimPoint(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("not a point: " + text, exception);
        }
    }
}
