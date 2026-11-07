package cl.almejo.vsim.ui.canvas;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.ConstantValue;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The «Mostrar como» of the part that drives a net, as a {@link Probe} format: what a new waveform channel starts
 * with and what a bus tooltip adds for fixed point (a net alone has no fraction bits, its part does). Only parts that
 * have the {@link ConstantValue#FORMAT} parameter count (Constante, pins, registers, counters...).
 */
public final class PartFormats {

    private PartFormats() {
    }

    /**
     * The format of the net's driver part with a format parameter (the first by document order); with no such driver,
     * the format all the other parts agree on; empty when no part has one, they disagree, or the format is «auto» or
     * a float at a width with no IEEE 754 format (the caller's default then applies).
     */
    public static Optional<String> of(Net net, CircuitDocument document, ComponentTypeRegistry registry) {
        Comparator<PinRef> order = Comparator.comparingLong((PinRef pin) -> document.orderOf(pin.componentId()))
                .thenComparingInt(PinRef::pinIndex);
        List<PinRef> pins = net.pins().stream().filter(pin -> document.component(pin.componentId()).isPresent())
                .sorted(order).toList();
        Set<Optional<String>> others = new LinkedHashSet<>();
        for (PinRef pin : pins) {
            Component component = document.component(pin.componentId()).orElseThrow();
            Optional<ComponentType> type = registry.findTypeOf(component);
            if (type.isEmpty() || pin.pinIndex() >= type.get().pinCount()
                    || type.get().parameters().stream().noneMatch(spec -> ConstantValue.FORMAT.equals(spec.name()))) {
                continue;
            }
            Optional<String> format = formatOf(type.get(), component, net.width());
            if (type.get().pin(pin.pinIndex()).isDriver()) {
                return format;
            }
            others.add(format);
        }
        return others.size() == 1 ? others.iterator().next() : Optional.empty();
    }

    private static Optional<String> formatOf(ComponentType type, Component component, int width) {
        var resolved = type.resolveParameters(component.parameters());
        String format = String.valueOf(resolved.get(ConstantValue.FORMAT));
        return switch (format) {
            case ConstantValue.BINARY, ConstantValue.HEX, ConstantValue.OCTAL, ConstantValue.DECIMAL,
                 ConstantValue.SIGNED, ConstantValue.ASCII -> Optional.of(format);
            case ConstantValue.FLOAT -> width == 16 || width == 32 || width == 64 ? Optional.of(format)
                    : Optional.empty();
            case ConstantValue.FIXED -> Optional.of(Probe.fixedFormat((Integer) resolved.get(ConstantValue.FRACTION)));
            default -> Optional.empty();
        };
    }
}
