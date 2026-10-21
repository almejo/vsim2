package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Problems panel for the AI (spec §10 «read Problems»): the problems of the latest published simulation, each
 * pointed at the circuit's parts and pins. Problems found inside a user component point at the instance that holds
 * them and keep their inner path.
 */
public final class ProblemsReader {

    private ProblemsReader() {
    }

    public static ProblemsView read(ReadSource source) {
        SimulationSnapshot snapshot = source.snapshot();
        List<ProblemView> problems = snapshot.problems().stream().map(problem -> view(source, problem)).toList();
        return new ProblemsView(source.id(), source.version(), Long.toString(snapshot.time()),
                snapshot.token() == source.netlistToken(), problems);
    }

    private static ProblemView view(ReadSource source, Problem problem) {
        References references = new References(source);
        return switch (problem) {
            case ShortCircuit shortCircuit -> {
                shortCircuit.drivers().forEach(driver -> references.pin(driver.componentId(), driver.pinIndex()));
                yield references.view("SHORT_CIRCUIT", false, problem.netId(),
                        shortCircuit.drivers().size() + " drivers impose different values on one net since time "
                                + shortCircuit.sinceTime() + " (" + driven(shortCircuit.drivers()) + ")");
            }
            case WidthMismatch mismatch -> {
                mismatch.pins().forEach(pin -> references.pin(pin.componentId(), pin.pinIndex()));
                yield references.view("WIDTH_MISMATCH", false, problem.netId(),
                        "Pins of different widths are joined in one net: " + widths(mismatch.pins()));
            }
            case DisplayBusOverride display -> {
                references.element(display.componentId());
                yield references.view("DISPLAY_BUS_OVERRIDE", true, problem.netId(),
                        "A 7-segment display has a driver on its bus input and on a one-bit input: the bus wins and"
                                + " the one-bit wires are ignored");
            }
            case TunnelProblem tunnel -> {
                tunnel.tunnels().forEach(references::element);
                yield references.view("TUNNEL_" + tunnel.kind().name(), tunnel.isWarning(), problem.netId(),
                        tunnelMessage(tunnel));
            }
        };
    }

    private static String driven(List<DriverRef> drivers) {
        return String.join(", ", drivers.stream()
                .map(driver -> driver.componentId() + " pin " + driver.pinIndex() + " drives " + driver.driven())
                .toList());
    }

    private static String widths(List<FlatPinRef> pins) {
        List<String> parts = new ArrayList<>();
        for (FlatPinRef pin : pins) {
            parts.add(pin.componentId() + " pin " + pin.pinIndex());
        }
        return String.join(", ", parts);
    }

    private static String tunnelMessage(TunnelProblem tunnel) {
        String name = "\"" + tunnel.name() + "\"";
        return switch (tunnel.kind()) {
            case DUPLICATE_ENTRANCE -> "Two or more tunnel entrances share the name " + name;
            case EXIT_WITHOUT_ENTRANCE -> "Tunnel exits named " + name + " have no entrance";
            case ENTRANCE_WITHOUT_EXITS -> "The tunnel entrance " + name + " has no exits";
            case EXIT_DRIVEN -> "A tunnel exit named " + name + " has a driver on its own wire: maybe it should be"
                    + " an entrance";
        };
    }

    /** The parts, pins and inner paths one problem names, in the order it names them, without repeats. */
    private static final class References {

        private final ReadSource source;
        private final Set<String> elementIds = new LinkedHashSet<>();
        private final Set<PinReference> pins = new LinkedHashSet<>();
        private final Set<String> insidePaths = new LinkedHashSet<>();

        References(ReadSource source) {
            this.source = source;
        }

        void element(String flatId) {
            String topLevel = InstancePath.topLevel(flatId);
            if (source.document().component(new ElementId(topLevel)).isPresent()) {
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
}
