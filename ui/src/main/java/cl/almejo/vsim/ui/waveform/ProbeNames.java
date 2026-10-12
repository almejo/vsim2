package cl.almejo.vsim.ui.waveform;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.Probe;
import cl.almejo.vsim.core.document.ProbeTarget;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.ProbeResolver;
import cl.almejo.vsim.ui.i18n.Messages;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

/**
 * The names of waveform channels (M6.1-E). A probe whose stored name is empty shows a default name computed live
 * from the circuit, so it follows label changes; only «Renombrar…» with a non-blank text stores a name.
 */
public final class ProbeNames {

    /** The languages an older VSim may have stored a default wire name in («cable w3», «wire w3»). */
    private static final List<Locale> STORED_WIRE_NAME_LOCALES = List.of(Messages.SPANISH, Locale.ENGLISH);

    private ProbeNames() {
    }

    /**
     * {@code U3.q} (label, else id, dot, pin name) for a pin; for a wire, the name of the pin its net is anchored to
     * now ({@link ProbeResolver#anchor}), else «cable w12».
     */
    public static String defaultName(ProbeTarget target, CircuitDocument document, Netlist netlist,
                                     ComponentTypeRegistry registry) {
        return switch (target) {
            case ProbeTarget.PinTarget pin -> pinName(pin, document, registry);
            case ProbeTarget.WireTarget wire -> ProbeResolver.anchor(wire.wireId(), document, netlist, registry)
                    .filter(ProbeTarget.PinTarget.class::isInstance)
                    .map(anchor -> pinName((ProbeTarget.PinTarget) anchor, document, registry))
                    .orElseGet(() -> wireName(wire));
        };
    }

    /** The stored name when it is not blank, else the live default name. */
    public static String displayName(Probe probe, CircuitDocument document, Netlist netlist,
                                     ComponentTypeRegistry registry) {
        return probe.name().isBlank() ? defaultName(probe.target(), document, netlist, registry) : probe.name();
    }

    /**
     * Turns the names an older VSim stored by default into live names: a stored name equal to the netlist-free
     * default (pin → {@code owner.pin}; wire → «cable wN» or «wire wN», in either language, since M6 stored it in the
     * UI language of the moment) becomes empty; any other name stays. Call it on a freshly loaded document, before
     * any view or history: it calls {@code setProbes} directly, and only when a name changed.
     */
    public static void forgetDefaultNames(CircuitDocument document, ComponentTypeRegistry registry) {
        List<Probe> probes = document.probes();
        List<Probe> cleared = probes.stream()
                .map(probe -> isStoredDefault(probe, document, registry) ? probe.withName("") : probe)
                .toList();
        if (!cleared.equals(probes)) {
            document.setProbes(cleared);
        }
    }

    private static boolean isStoredDefault(Probe probe, CircuitDocument document, ComponentTypeRegistry registry) {
        return switch (probe.target()) {
            case ProbeTarget.PinTarget pin -> probe.name().equals(pinName(pin, document, registry));
            case ProbeTarget.WireTarget wire -> STORED_WIRE_NAME_LOCALES.stream()
                    .anyMatch(locale -> probe.name().equals(wireName(wire, locale)));
        };
    }

    /** «cable w12» / «wire w12»: the {@code waveform.wireName} pattern of the bundle of {@code locale}. */
    private static String wireName(ProbeTarget.WireTarget wire, Locale locale) {
        return new MessageFormat(Messages.bundle(locale).getString("waveform.wireName"), locale)
                .format(new Object[]{wire.wireId().value()});
    }

    private static String pinName(ProbeTarget.PinTarget pin, CircuitDocument document,
                                  ComponentTypeRegistry registry) {
        return document.component(pin.componentId())
                .map(component -> ownerName(component) + "." + pinName(component, pin.pinIndex(), registry))
                .orElse(pin.componentId().value() + "." + pin.pinIndex());
    }

    private static String ownerName(Component component) {
        return component.label().isBlank() ? component.id().value() : component.label();
    }

    private static String pinName(Component component, int pinIndex, ComponentTypeRegistry registry) {
        return registry.findTypeOf(component)
                .filter(type -> pinIndex < type.pinCount())
                .map(type -> type.pin(pinIndex).name())
                .orElse(String.valueOf(pinIndex));
    }

    private static String wireName(ProbeTarget.WireTarget wire) {
        return Messages.format("waveform.wireName", wire.wireId().value());
    }
}
