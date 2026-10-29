package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sets the «Valor» of a Switch the user clicked while simulating (decided 2026-10-28, so Propiedades, the file and the
 * simulation agree). The simulation already got the click as an input event; the edit only records it, as a change of
 * live values only ({@link CircuitDocument#changeLiveValues}): the netlist stays and the engine hands the running switch
 * its new value without building it again. The AI's tests (vsim_set_inputs) stay simulation only and never use it.
 *
 * <p>A run of {@linkplain #click clicks} on the same switch, one right after the other, merges into one undo step, as a
 * run of arrow-key moves of the same selection does; any other command, an undo, a save or a click on another switch
 * ends it. A run that comes back to the value it started from leaves no step.
 */
public final class SetSwitchValueCommand implements Command {

    /** The undo menu says «Deshacer clic en interruptor». */
    public static final String CLICK_DESCRIPTION = "command.switchClick";

    /** The new text by switch; already canonical ({@code SwitchValues#textFor}). */
    private final Map<ElementId, String> values;
    /** For a merged run: the texts before its first click, to tell a run that came back; empty otherwise. */
    private final Map<ElementId, Object> runStart;
    private Map<ElementId, Map<String, Object>> previousParameters = Map.of();

    private SetSwitchValueCommand(Map<ElementId, String> values, Map<ElementId, Object> runStart) {
        this.values = Map.copyOf(values);
        this.runStart = runStart;
    }

    /** A click that left switch {@code id} at {@code valueText}; merges with the next click on the same switch. */
    public static SetSwitchValueCommand click(ElementId id, String valueText) {
        return new SetSwitchValueCommand(Map.of(id, valueText), Map.of());
    }

    @Override
    public void apply(CircuitDocument document) {
        Map<ElementId, Map<String, Object>> previous = new LinkedHashMap<>();
        document.changeLiveValues(() -> values.forEach((id, text) -> {
            Component component = document.component(id).orElseThrow();
            if (!component.typeId().equals(StandardComponentTypes.SWITCH)) {
                throw new IllegalArgumentException(id + " is a " + component.typeId() + ", not a switch");
            }
            previous.put(id, component.parameters());
            Map<String, Object> parameters = new HashMap<>(component.parameters());
            parameters.put(StandardComponentTypes.VALUE, text);
            document.replace(component.withParameters(Map.copyOf(parameters)));
        }));
        previousParameters = previous;
    }

    @Override
    public void revert(CircuitDocument document) {
        document.changeLiveValues(() -> previousParameters.forEach((id, parameters) -> document.replace(
                document.component(id).orElseThrow().withParameters(parameters))));
    }

    /** The click run of this click and {@code next}, when {@code next} is a click on the same switch right after it. */
    @Override
    public Optional<Command> mergedWith(Command next) {
        if (!(next instanceof SetSwitchValueCommand following) || !following.values.keySet().equals(values.keySet())) {
            return Optional.empty();
        }
        Map<ElementId, Object> start = runStart.isEmpty() ? valuesBefore() : runStart;
        return Optional.of(new SetSwitchValueCommand(following.values, start));
    }

    /** The «Valor» texts this command found when it was applied. */
    private Map<ElementId, Object> valuesBefore() {
        Map<ElementId, Object> before = new HashMap<>();
        previousParameters.forEach((id, parameters) -> before.put(id, parameters.get(StandardComponentTypes.VALUE)));
        return before;
    }

    @Override
    public boolean changesNothing() {
        return !runStart.isEmpty() && runStart.equals(values);
    }

    @Override
    public String description() {
        return CLICK_DESCRIPTION;
    }
}
