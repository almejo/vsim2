package cl.almejo.vsim.ui.whatsnew;

import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.ui.export.CircuitImage;
import cl.almejo.vsim.ui.paint.ColorScheme;
import cl.almejo.vsim.ui.paint.PainterRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A tip's small circuit, read from its resource and simulated once until it settles, so its picture shows the values
 * (a lit LED, the colour of a wire at 1) without a simulation thread of its own. The picture is drawn with the scheme
 * given at paint time ({@link CircuitImage}).
 */
final class TipCircuit {

    /**
     * How far in time a tip's circuit runs before it is drawn: enough for small circuits without a clock to settle,
     * and a bound for one with a clock (which never does).
     */
    private static final long SETTLE_TIME = 1_000;

    private final CircuitDocument document;
    private final ComponentTypeRegistry registry;
    private final Netlist netlist;
    private final SimulationSnapshot snapshot;

    private TipCircuit(CircuitDocument document, ComponentTypeRegistry registry, Netlist netlist,
                       SimulationSnapshot snapshot) {
        this.document = document;
        this.registry = registry;
        this.netlist = netlist;
        this.snapshot = snapshot;
    }

    /** The circuit {@code name} (a resource under the tips' folder), simulated until it settles. */
    static TipCircuit load(String name) throws CircuitFormatException {
        CircuitDocument document = read(name);
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        registry.addResolver(SubcircuitTypes.resolverFor(document));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        SimulationEngine engine = new SimulationEngine();
        engine.update(new CircuitFlattener(registry).flatten(document, netlist));
        for (OptionalLong next = engine.nextEventTime(); next.isPresent() && next.getAsLong() <= SETTLE_TIME;
             next = engine.nextEventTime()) {
            engine.runUntil(next.getAsLong());
        }
        return new TipCircuit(document, registry, netlist, engine.snapshot());
    }

    /** A fresh document read from the resource {@code name}: what «Probarlo» opens, never shared. */
    static CircuitDocument read(String name) throws CircuitFormatException {
        String resource = TipsFile.FOLDER + name;
        try (InputStream stream = TipCircuit.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, resource);
            }
            return new CircuitReader(StandardComponentTypes.registry())
                    .read(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CircuitFormatException(CircuitFormatException.Reason.READ_ERROR, resource, exception);
        }
    }

    /** What the settled simulation says (its problems are what the Problems list would show). */
    SimulationSnapshot snapshot() {
        return snapshot;
    }

    /** Whether the circuit has a frame: a picture about frames draws bigger ({@link TipPicture}). */
    boolean hasFrame() {
        return !document.frames().isEmpty();
    }

    /** The circuit drawn in {@code scheme}; empty for an empty circuit. */
    Optional<CircuitImage> image(PainterRegistry painters, ColorScheme scheme) {
        return CircuitImage.of(document, netlist, document.structureRevision(), snapshot, registry, painters, scheme,
                Optional.empty(), true); // a tip may be about a sticky note: it is part of the picture here
    }
}
