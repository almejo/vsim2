package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.InstancePath;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The parts, pins and inner paths one problem names, in the order it names them, without repeats. */
final class ProblemReferences {

    private final ReadSource source;
    private final Set<String> elementIds = new LinkedHashSet<>();
    private final Set<PinReference> pins = new LinkedHashSet<>();
    private final Set<String> insidePaths = new LinkedHashSet<>();

    ProblemReferences(ReadSource source) {
        this.source = source;
    }

    void element(String flatId) {
        String topLevel = InstancePath.topLevel(flatId);
        if (source.document().element(new ElementId(topLevel)).isPresent()) {
            elementIds.add(topLevel);
        }
        if (InstancePath.isNested(flatId)) {
            insidePaths.add(flatId);
        }
    }

    void pin(String flatId, int pinIndex) {
        element(flatId);
        if (!InstancePath.isNested(flatId)) {
            Optional<PinReference> reference =
                    CircuitReader.reference(source, new PinRef(new ElementId(flatId), pinIndex));
            reference.ifPresent(pins::add);
        }
    }

    ProblemView view(String kind, boolean warning, String netId, String message) {
        return new ProblemView(kind, warning, netId, message, List.copyOf(elementIds), List.copyOf(pins),
                List.copyOf(insidePaths));
    }
}
