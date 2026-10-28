package cl.almejo.vsim.core.simulation;

import java.util.Objects;

/**
 * What the «Mapa de memoria» wants to see of the memory simulated as {@code componentId} (flat id): {@code cells}
 * consecutive cells of {@code wordsPerCell} words each, from {@code firstAddress} (spec §6). The simulation thread
 * answers with a {@link MemoryMapFrame} (at most every 16 ms while running).
 */
public record MemoryMapRequest(String componentId, long firstAddress, long wordsPerCell, int cells) {

    /** The most cells one request may ask for (a 256 x 256 grid). */
    public static final int MAXIMUM_CELLS = 1 << 16;

    public MemoryMapRequest {
        Objects.requireNonNull(componentId, "componentId");
        if (firstAddress < 0 || wordsPerCell < 1 || cells < 0 || cells > MAXIMUM_CELLS) {
            throw new IllegalArgumentException("a memory map of " + cells + " cells of " + wordsPerCell
                    + " words from " + firstAddress);
        }
    }

    /** The first address of {@code cell}. */
    public long addressOf(int cell) {
        return firstAddress + cell * wordsPerCell;
    }
}
