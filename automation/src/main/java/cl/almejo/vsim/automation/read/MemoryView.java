package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.DocumentId;
import cl.almejo.vsim.automation.DocumentVersion;

import java.util.List;
import java.util.Objects;

/**
 * A window of a RAM or ROM's words: {@code words} are upper-case hex of the memory's width starting at
 * {@code firstAddress}; {@code source} is {@code initial} (what the circuit holds) or {@code current} (the running
 * simulation). {@code totalWords} and {@code nonZeroWords} describe the whole memory, {@code linked} says its initial
 * contents come from a file the AI may not change.
 */
public record MemoryView(DocumentId document, DocumentVersion version, String component, String source,
                         long firstAddress, List<String> words, int width, long totalWords, boolean linked,
                         long nonZeroWords) {

    public MemoryView {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(source, "source");
        words = List.copyOf(words);
    }
}
