package cl.almejo.vsim.automation.simulate;

import cl.almejo.vsim.automation.DocumentId;

/**
 * The answer to typing a text into a Teclado: {@code accepted} of its {@code total} characters entered the queue (a
 * full queue takes fewer, in order), which then holds {@code queueSize} bytes.
 */
public record TypedText(DocumentId document, String component, int accepted, int total, int queueSize) {
}
