package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.automation.PinReference;
import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.memory.LinkedFileChecks;
import cl.almejo.vsim.core.simulation.InstancePath;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
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
        List<Problem> found = new ArrayList<>(snapshot.problems());
        found.addAll(LinkedFileChecks.missing(source.document(), source.circuitFile()));
        List<ProblemView> problems = found.stream().map(problem -> view(source, problem)).toList();
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
            case LinkedFileProblem linkedFile -> {
                references.element(linkedFile.elementId());
                yield references.view("LINKED_FILE_" + linkedFile.kind().name(), true, problem.netId(),
                        linkedFileMessage(source, linkedFile));
            }
            case ConnectivityProblem connectivity -> {
                if (connectivity.isPin()) {
                    references.pin(connectivity.elementId(), connectivity.pinIndex());
                } else {
                    references.element(connectivity.elementId());
                }
                if (connectivity.cause() == ConnectivityProblem.Cause.UNCONNECTED_INTERFACE) {
                    yield references.view("INTERFACE_UNCONNECTED_INSIDE", true, problem.netId(),
                            "Terminal " + connectivity.pinIndex() + " of " + connectivity.elementId()
                                    + " is not connected to anything inside the component; the fault is inside,"
                                    + " not in the wire outside");
                }
                yield references.view(connectivity.isPin() ? "DISCONNECTED_PIN" : "LOOSE_WIRE_END", true,
                        problem.netId(), connectivity.isPin()
                                ? "Disconnected pin " + connectivity.pinIndex() + " of " + connectivity.elementId()
                                : "Loose wire end of " + connectivity.elementId() + " at " + connectivity.position());
            }
        };
    }

    private static String linkedFileMessage(ReadSource source, LinkedFileProblem problem) {
        String name = source.document().component(new ElementId(problem.elementId()))
                .map(component -> component.label().isBlank() ? component.typeId() : component.label())
                .orElse(problem.elementId());
        return switch (problem.kind()) {
            case MISSING -> "The memory " + name + " is linked to the file " + problem.link()
                    + ", which is not there: it keeps the contents saved in the circuit";
            case UNREADABLE -> "The memory " + name + " is linked to the file " + problem.link()
                    + ", which cannot be read (" + problem.detail() + "): it keeps the contents saved in the circuit";
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
}
