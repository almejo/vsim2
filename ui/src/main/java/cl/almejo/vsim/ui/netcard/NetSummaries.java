package cl.almejo.vsim.ui.netcard;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.PinSpec;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.geometry.GridPoint;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.WidthMismatch;
import cl.almejo.vsim.core.tunnel.Tunnels;
import cl.almejo.vsim.core.value.Value;
import cl.almejo.vsim.ui.app.OpenDocument;
import cl.almejo.vsim.ui.canvas.TopLevelNets;
import cl.almejo.vsim.ui.i18n.Messages;
import cl.almejo.vsim.ui.i18n.PinNames;
import cl.almejo.vsim.ui.problems.ProblemText;
import cl.almejo.vsim.ui.window.InstanceNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Builds the {@link NetSummary} of the net of a wire, on the EDT, from the document's netlist (which already joins
 * same-name tunnels into one net), its registry and a snapshot. The snapshot is trusted only when its token is the
 * netlist's ({@code snapshot.token() == openDocument.netlistToken()}, as the tooltips do): else the value is «sin
 * simular», never a stale one.
 * <p>
 * Drivers and readers come from the pins' directions: an output drives, an input reads, a bidirectional pin reads and
 * counts as a driver only when nothing else could drive the net or the short circuit names it. Tunnel pins are not
 * ends (their names go to {@code tunnelNames}); a component's inner parts are never entered.
 */
public final class NetSummaries {

    private NetSummaries() {
    }

    /** The summary of the net of {@code wire} with the latest snapshot; empty when the wire or its net is gone. */
    public static Optional<NetSummary> forWire(OpenDocument document, ElementId wire) {
        return forWire(document, wire, document.latestSnapshot());
    }

    /** As {@link #forWire(OpenDocument, ElementId)} with {@code snapshot}; merged nets found by flattening. */
    static Optional<NetSummary> forWire(OpenDocument document, ElementId wire, SimulationSnapshot snapshot) {
        return forWire(document, wire, snapshot,
                () -> TopLevelNets.of(document.document(), document.netlist(), document.registry()));
    }

    /**
     * As {@link #forWire(OpenDocument, ElementId)} with {@code snapshot}; {@code topLevelNets} (asked only when the
     * snapshot has problems) gives the id the engine reports the problems of a net joined through a template under:
     * the canvas keeps it cached ({@code canvas.highlights().topLevelNets}).
     */
    static Optional<NetSummary> forWire(OpenDocument document, ElementId wire, SimulationSnapshot snapshot,
                                        Supplier<TopLevelNets> topLevelNets) {
        return forWire(document, wire, snapshot, topLevelNets,
                () -> InstanceNames.names(document.document().components(), document.document()));
    }

    /**
     * As {@link #forWire(OpenDocument, ElementId, SimulationSnapshot, Supplier)}; {@code names} (asked only when an
     * unlabeled part is on the net) gives every part's name in the circuit («Interruptor 2»), which the caller may keep
     * for the document's revision.
     */
    static Optional<NetSummary> forWire(OpenDocument document, ElementId wire, SimulationSnapshot snapshot,
                                        Supplier<TopLevelNets> topLevelNets,
                                        Supplier<Map<ElementId, String>> names) {
        Objects.requireNonNull(snapshot, "snapshot");
        return document.netlist().netForWire(wire)
                .map(net -> new Builder(document, net, snapshot, topLevelNets, names).summary());
    }

    /** The summary of one net, built once. */
    private static final class Builder {

        private final CircuitDocument document;
        private final ComponentTypeRegistry registry;
        private final Net net;
        private final SimulationSnapshot snapshot;
        private final boolean current;
        private final Supplier<TopLevelNets> topLevelNets;
        private final Supplier<Map<ElementId, String>> names;

        Builder(OpenDocument openDocument, Net net, SimulationSnapshot snapshot, Supplier<TopLevelNets> topLevelNets,
                Supplier<Map<ElementId, String>> names) {
            this.document = openDocument.document();
            this.registry = openDocument.registry();
            this.net = net;
            this.snapshot = snapshot;
            this.current = snapshot.token() == openDocument.netlistToken()
                    && !snapshot.equals(SimulationSnapshot.EMPTY);
            this.topLevelNets = topLevelNets;
            this.names = names;
        }

        NetSummary summary() {
            List<PinRef> pins = pinsInDocumentOrder();
            List<String> tunnelNames = new ArrayList<>();
            List<NetEnd> outputs = new ArrayList<>();
            List<NetEnd> bidirectionals = new ArrayList<>();
            List<NetEnd> readers = new ArrayList<>();
            for (PinRef pin : pins) {
                Optional<Component> component = document.component(pin.componentId());
                Optional<ComponentType> type = component.flatMap(registry::findTypeOf);
                if (component.isEmpty() || type.isEmpty() || pin.pinIndex() >= type.get().pinCount()) {
                    continue;
                }
                if (Tunnels.isTunnel(component.get().typeId())) {
                    tunnelNames.add(Tunnels.nameOf(component.get()));
                    continue;
                }
                PinSpec spec = type.get().pin(pin.pinIndex());
                NetEnd end = end(component.get(), type.get(), pin.pinIndex());
                switch (spec.direction()) {
                    case OUTPUT -> outputs.add(end);
                    case INPUT -> readers.add(end);
                    case BIDIRECTIONAL -> {
                        bidirectionals.add(end);
                        readers.add(end);
                    }
                }
            }
            Optional<ShortCircuit> shortCircuit = shortCircuit();
            List<NetEnd> drivers = drivers(outputs, bidirectionals, shortCircuit);
            NetValue value = value();
            return new NetSummary(net.id(), net.width(), net.wires(), net.points(), drivers, readers,
                    new ArrayList<>(new TreeSet<>(tunnelNames)), value, problem(shortCircuit, drivers, value));
        }

        /** The pins of the net in the order their parts were added, the pins of one part by index. */
        private List<PinRef> pinsInDocumentOrder() {
            return net.pins().stream()
                    .filter(pin -> document.component(pin.componentId()).isPresent())
                    .sorted(Comparator.comparingLong((PinRef pin) -> document.orderOf(pin.componentId()))
                            .thenComparingInt(PinRef::pinIndex))
                    .toList();
        }

        private NetEnd end(Component component, ComponentType type, int pinIndex) {
            return new NetEnd(component.id(), pinIndex, partText(component),
                    PinNames.display(type.pin(pinIndex).name()), Optional.empty(),
                    type.pinLocation(component, pinIndex));
        }

        /** «AND U1» for a labeled part (its type and label), else its name in the circuit («AND», «Interruptor 2»). */
        private String partText(Component component) {
            return component.label().isBlank()
                    ? names.get().getOrDefault(component.id(), InstanceNames.typeName(component, document))
                    : Messages.format("netCard.partLabeled", InstanceNames.typeName(component, document),
                    component.label());
        }

        /**
         * The outputs; the bidirectional pins too when nothing else could drive the net; any pin the short circuit
         * names; each with the value it imposes when the short circuit gives one.
         */
        private List<NetEnd> drivers(List<NetEnd> outputs, List<NetEnd> bidirectionals,
                                     Optional<ShortCircuit> shortCircuit) {
            List<NetEnd> drivers = new ArrayList<>(outputs);
            for (NetEnd bidirectional : bidirectionals) {
                if (outputs.isEmpty() || drivenBy(shortCircuit, bidirectional).isPresent()) {
                    drivers.add(bidirectional);
                }
            }
            return drivers.stream()
                    .map(driver -> drivenBy(shortCircuit, driver).map(found -> driver.withValue(NetCardText.value(found)))
                            .orElse(driver))
                    .toList();
        }

        private static Optional<Value> drivenBy(Optional<ShortCircuit> shortCircuit, NetEnd end) {
            return shortCircuit.stream()
                    .flatMap(found -> found.drivers().stream())
                    .filter(driver -> driver.componentId().equals(end.componentId().value())
                            && driver.pinIndex() == end.pinIndex())
                    .map(DriverRef::driven)
                    .findFirst();
        }

        private Optional<ShortCircuit> shortCircuit() {
            if (!current || snapshot.problems().isEmpty()) {
                return Optional.empty();
            }
            String reportedId = topLevelNets.get().rootOf(net.id().value());
            return snapshot.shortCircuits().stream()
                    .filter(found -> found.netId().equals(reportedId))
                    .findFirst();
        }

        private NetValue value() {
            Value value = current ? snapshot.netValues().get(net.id().value()) : null;
            return value == null ? new NetValue.NotSimulating() : new NetValue.Simulated(value);
        }

        /** A short circuit first, then a width mismatch, then nothing that could drive it, then a Z it is left in. */
        private Optional<NetProblem> problem(Optional<ShortCircuit> shortCircuit, List<NetEnd> drivers,
                                             NetValue value) {
            if (shortCircuit.isPresent()) {
                return Optional.of(new NetProblem.ShortCircuit(shortCircuit.get().drivers().stream()
                        .map(this::driverEnd)
                        .toList()));
            }
            Optional<WidthMismatch> mismatch = widthMismatch();
            if (mismatch.isPresent()) {
                return Optional.of(new NetProblem.WidthMismatch(
                        ProblemText.describe(mismatch.get(), document, registry)));
            }
            if (drivers.isEmpty()) {
                return Optional.of(new NetProblem.Undriven());
            }
            if (value instanceof NetValue.Simulated simulated && simulated.value().isFullyHighImpedance()
                    && !simulated.value().conflict()) {
                return Optional.of(new NetProblem.Floating());
            }
            return Optional.empty();
        }

        private Optional<WidthMismatch> widthMismatch() {
            if (current && !snapshot.problems().isEmpty()) {
                String reportedId = topLevelNets.get().rootOf(net.id().value());
                Optional<WidthMismatch> reported = snapshot.widthMismatches().stream()
                        .filter(found -> found.netId().equals(reportedId))
                        .findFirst();
                if (reported.isPresent()) {
                    return reported;
                }
            }
            return net.widthMismatch() ? Optional.of(ProblemText.ownPinsMismatch(net, document, registry))
                    : Optional.empty();
        }

        /**
         * One driver of the short circuit with its value: a part of this circuit by its pin; one inside a component's
         * body by its route, pointing at the top-level component that holds it.
         */
        private NetEnd driverEnd(DriverRef driver) {
            String text = NetCardText.value(driver.driven());
            if (InstancePath.isNested(driver.componentId())) {
                ElementId holder = new ElementId(InstancePath.topLevel(driver.componentId()));
                String route = InstanceNames.path(driver.componentId(), document).orElse(driver.componentId());
                return new NetEnd(holder, driver.pinIndex(), route, "", Optional.of(text), positionOf(holder));
            }
            ElementId id = new ElementId(driver.componentId());
            Optional<Component> component = document.component(id);
            Optional<ComponentType> type = component.flatMap(registry::findTypeOf);
            if (component.isEmpty() || type.isEmpty() || driver.pinIndex() >= type.get().pinCount()) {
                return new NetEnd(id, driver.pinIndex(), driver.componentId(), "", Optional.of(text),
                        positionOf(id));
            }
            return end(component.get(), type.get(), driver.pinIndex()).withValue(text);
        }

        private GridPoint positionOf(ElementId id) {
            return document.component(id).map(Component::position)
                    .orElse(new GridPoint(0, 0));
        }
    }
}
