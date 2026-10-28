package cl.almejo.vsim.core.command;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.standard.MultiBitOrientation;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.geometry.Rotation;

import java.util.ArrayList;
import java.util.List;

import java.util.Map;

/**
 * Sets a component's parameters. A Switch or LED whose width crosses one bit moves its direction between the element's
 * rotation and its «side» ({@link MultiBitOrientation#withParameters}), so a multi-bit part never turns its body.
 */
public final class ChangeParametersCommand implements Command {

    private final ElementId id;
    private final ComponentType type;
    private final String typeId;
    private final Map<String, Object> parameters;
    private Map<String, Object> previousParameters;
    private GridPoint previousPosition;
    private Rotation previousRotation;
    private List<Probe> previousProbes;

    /** Normalizes {@code rawParameters} with {@code type}; invalid values throw IllegalArgumentException here. */
    public ChangeParametersCommand(ElementId id, ComponentType type, Map<String, Object> rawParameters) {
        this.id = id;
        this.type = type;
        this.typeId = type.id();
        this.parameters = type.resolveParameters(rawParameters);
    }

    @Override
    public void apply(CircuitDocument document) {
        Component component = document.component(id).orElseThrow();
        if (!component.typeId().equals(typeId)) {
            throw new IllegalArgumentException(id + " is a " + component.typeId() + ", not a " + typeId);
        }
        previousParameters = component.parameters();
        previousPosition = component.position();
        previousRotation = component.rotation();
        document.replace(MultiBitOrientation.withParameters(component, parameters, type));
        previousProbes = document.probes();
        List<Probe> retargeted = retargetedProbes(previousProbes, component.parameters(), parameters);
        if (!retargeted.equals(previousProbes)) {
            document.setProbes(retargeted);
        }
    }

    /**
     * Probes on pins of this component follow the pin with the same name when the parameters move the pins (a gate's
     * output moves when its inputs change); a probe whose pin no longer exists is dropped.
     */
    private List<Probe> retargetedProbes(List<Probe> probes, Map<String, Object> before, Map<String, Object> after) {
        if (probes.stream().noneMatch(probe -> probe.target() instanceof ProbeTarget.PinTarget pin
                && pin.componentId().equals(id))) {
            return probes;
        }
        List<PinSpec> oldPins = type.specializedFor(before).pins();
        List<PinSpec> newPins = type.specializedFor(after).pins();
        List<Probe> result = new ArrayList<>();
        for (Probe probe : probes) {
            if (!(probe.target() instanceof ProbeTarget.PinTarget pin) || !pin.componentId().equals(id)
                    || pin.pinIndex() >= oldPins.size()) {
                result.add(probe);
                continue;
            }
            String pinName = oldPins.get(pin.pinIndex()).name();
            for (int index = 0; index < newPins.size(); index++) {
                if (newPins.get(index).name().equals(pinName)) {
                    result.add(new Probe(probe.id(), new ProbeTarget.PinTarget(id, index), probe.name()));
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void revert(CircuitDocument document) {
        document.replace(document.component(id).orElseThrow().withParameters(previousParameters)
                .withPosition(previousPosition).withRotation(previousRotation));
        document.setProbes(previousProbes);
    }

    @Override
    public String description() {
        return "command.configure";
    }
}
