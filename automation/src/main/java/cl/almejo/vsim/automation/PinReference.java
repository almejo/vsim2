package cl.almejo.vsim.automation;

/**
 * A pin named the way the AI names it (spec §10): the component's element id and the pin's name, plus {@code index}
 * when several pins share that name (0 for the first).
 */
public record PinReference(String elementId, String pinName, int index) {
}
