package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;

/**
 * A piece kept at its old geometry because the proposed shape could change its electrical connections.
 * {@code instanceName} is the piece's label (its id when unlabelled), {@code pinName} the pin that would touch and
 * {@code touchedNetName} what it would touch (a pin, a probe or, failing that, the point); none is ever blank.
 */
public record ForeignContact(ElementId instance, String instanceName, String pinName, String touchedNetName) {
    public ForeignContact {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(instanceName, "instanceName");
        Objects.requireNonNull(pinName, "pinName");
        Objects.requireNonNull(touchedNetName, "touchedNetName");
    }
}
