package cl.almejo.vsim.core.tools;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.component.standard.StandardComponentTypes;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.io.CircuitFormatException;
import cl.almejo.vsim.core.io.CircuitReader;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.CircuitFlattener;
import cl.almejo.vsim.core.simulation.SimulationEngine;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** M1 demo: loads a circuit, toggles its switches through every combination and prints the outputs. */
public final class SimulateCommandLine {

    private static final int MAXIMUM_SWITCHES_FOR_TABLE = 4;

    private record Probe(String name, PinRef pin) {
    }

    private SimulateCommandLine() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream error) {
        if (args.length < 1 || args.length > 2) {
            error.println("usage: SimulateCommandLine <circuit-file> [settle-time]");
            return 2;
        }
        long settleTime;
        try {
            settleTime = args.length == 2 ? Long.parseLong(args[1]) : 100;
        } catch (NumberFormatException exception) {
            error.println("usage: settle-time must be a number");
            return 2;
        }
        ComponentTypeRegistry registry = StandardComponentTypes.registry();
        CircuitDocument document;
        try {
            document = new CircuitReader(registry).read(Path.of(args[0]));
        } catch (CircuitFormatException exception) {
            error.println("error: " + exception.reason() + ": " + exception.detail());
            return 1;
        }
        // The instances of the file's templates resolve through the definitions it embeds (single thread here).
        registry.addResolver(SubcircuitTypes.resolverFor(document));

        NetlistCompiler compiler = new NetlistCompiler(registry);
        CircuitFlattener flattener = new CircuitFlattener(registry);
        SimulationEngine engine = new SimulationEngine();

        List<Component> switches = document.components().stream()
                .filter(component -> component.typeId().equals(StandardComponentTypes.SWITCH))
                .toList();
        Netlist netlist = compiler.compile(document);
        List<Probe> probes = terminalOutputs(document, registry, netlist);

        List<String> header = new ArrayList<>(switches.stream().map(SimulateCommandLine::name).toList());
        out.println(String.join(" ", header) + " | " + String.join(" ", probes.stream().map(Probe::name).toList()));

        boolean table = !switches.isEmpty() && switches.size() <= MAXIMUM_SWITCHES_FOR_TABLE;
        int rows = table ? 1 << switches.size() : 1;
        for (int row = 0; row < rows; row++) {
            List<String> inputs = new ArrayList<>();
            for (int index = 0; index < switches.size(); index++) {
                Component current = document.component(switches.get(index).id()).orElseThrow();
                if (table) {
                    int bit = row >> (switches.size() - 1 - index) & 1;
                    current = current.withParameters(Map.of("value", String.valueOf(bit)));
                    document.replace(current);
                }
                inputs.add(String.valueOf(current.parameters().get("value")));
            }
            engine.update(flattener.flatten(document, netlist));
            engine.advance(settleTime);
            List<String> outputs = probes.stream()
                    .map(probe -> engine.snapshot().netValue(netlist.netForPin(probe.pin()).orElseThrow().id().value()).toString())
                    .toList();
            out.println(String.join(" ", inputs) + " | " + String.join(" ", outputs));
        }
        return 0;
    }

    private static List<Probe> terminalOutputs(CircuitDocument document, ComponentTypeRegistry registry, Netlist netlist) {
        List<Probe> probes = new ArrayList<>();
        for (Component component : document.components()) {
            ComponentType type = registry.typeOf(component);
            List<Integer> terminalPins = new ArrayList<>();
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                if (!type.pin(pinIndex).isDriver() || component.typeId().equals(StandardComponentTypes.SWITCH)) {
                    continue;
                }
                Net net = netlist.netForPin(new PinRef(component.id(), pinIndex)).orElseThrow();
                boolean feedsSomething = net.pins().stream().anyMatch(pin -> !pin.componentId().equals(component.id())
                        && registry.typeOf(document.component(pin.componentId()).orElseThrow())
                        .pin(pin.pinIndex()).isInput());
                if (!feedsSomething) {
                    terminalPins.add(pinIndex);
                }
            }
            for (int pinIndex : terminalPins) {
                String name = name(component) + (terminalPins.size() > 1 ? "." + type.pin(pinIndex).name() : "");
                probes.add(new Probe(name, new PinRef(component.id(), pinIndex)));
            }
        }
        return probes;
    }

    private static String name(Component component) {
        return component.label().isBlank() ? component.typeId() + ":" + component.id().value() : component.label();
    }
}
