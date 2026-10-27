package cl.almejo.vsim.core.memory;

/** What a memory file holds: an optional header and the (sparse) contents. */
public record MemoryFile(MemoryHeader header, MemoryContents contents) {
}
