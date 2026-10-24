package cl.almejo.vsim.core.wiring;

import cl.almejo.vsim.core.document.ElementId;

import java.util.Objects;

/** A piece kept at its old geometry because the proposed shape could change its electrical connections. */
public record ForeignContact(ElementId instance, String pinName, String touchedNetName) {
    public ForeignContact {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(pinName, "pinName");
        Objects.requireNonNull(touchedNetName, "touchedNetName");
    }
}
