package cl.almejo.vsim.core.wiring;

/** Which tidy rules run: loose ends and floating networks, collinear merges, U detours (off by default). */
public record TidyOptions(boolean removeStubs, boolean mergeSegments, boolean flattenDetours) {

    public static final TidyOptions DEFAULTS = new TidyOptions(true, true, false);
}
