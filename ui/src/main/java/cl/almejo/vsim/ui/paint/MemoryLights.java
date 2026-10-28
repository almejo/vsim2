package cl.almejo.vsim.ui.paint;

import cl.almejo.vsim.core.component.VisibleState;

/**
 * Which of a memory's two lights are on and the words they point at: {@code reading} lights RD and outlines the word
 * at {@code readAddress} in blue, {@code writing} lights WR and outlines the word at {@code writeAddress} in orange
 * (an address is {@code -1} when there is none). Both can be on at once.
 */
public record MemoryLights(boolean reading, long readAddress, boolean writing, long writeAddress) {

    /** Both lights off. */
    public static final MemoryLights IDLE = new MemoryLights(false, -1, false, -1);

    /**
     * What a state shows by itself, without a view that remembers the frames before (an exported image, a test): the
     * light of its last access, on that access's address.
     */
    public static MemoryLights of(VisibleState state) {
        if (!(state instanceof VisibleState.MemoryState memory)) {
            return IDLE;
        }
        return switch (memory.lastAccess()) {
            case READ -> new MemoryLights(true, memory.lastAddress(), false, -1);
            case WRITE -> new MemoryLights(false, -1, true, memory.lastAddress());
            case NONE -> IDLE;
        };
    }
}
