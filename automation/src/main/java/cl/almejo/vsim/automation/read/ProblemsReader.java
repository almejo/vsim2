package cl.almejo.vsim.automation.read;

import cl.almejo.vsim.core.document.ElementId;
import cl.almejo.vsim.core.io.LibraryProblem;
import cl.almejo.vsim.core.simulation.ConnectivityProblem;
import cl.almejo.vsim.core.simulation.DisplayBusOverride;
import cl.almejo.vsim.core.simulation.DriverRef;
import cl.almejo.vsim.core.simulation.FlatPinRef;
import cl.almejo.vsim.core.simulation.LinkedFileProblem;
import cl.almejo.vsim.core.simulation.ListedProblems;
import cl.almejo.vsim.core.simulation.Problem;
import cl.almejo.vsim.core.simulation.ShortCircuit;
import cl.almejo.vsim.core.simulation.SimulationSnapshot;
import cl.almejo.vsim.core.simulation.TunnelProblem;
import cl.almejo.vsim.core.simulation.WidthMismatch;

import java.util.ArrayList;
import java.util.List;

/**
 * The Problems panel for the AI (spec §10 «read Problems»): every row the Problemas list shows for the circuit, made
 * from the same sources ({@link ListedProblems}; fix round D: a problem is never hidden, not from the AI either), then
 * the component library's load problems; each pointed at the circuit's parts and pins. Problems found inside a user
 * component point at the instance that holds them and keep their inner path.
 */
public final class ProblemsReader {

    private ProblemsReader() {
    }

    public static ProblemsView read(ReadSource source) {
        SimulationSnapshot snapshot = source.snapshot();
        List<ProblemView> problems = new ArrayList<>();
        ListedProblems.of(source.document(), source.registry(), source.problemSources())
                .forEach(problem -> problems.add(view(source, problem)));
        source.libraryProblems().forEach(problem -> problems.add(LibraryProblemViews.view(problem,
                source.library())));
        return new ProblemsView(source.id(), source.version(), Long.toString(snapshot.time()),
                snapshot.token() == source.netlistToken(), problems);
    }

    /** The kind the AI reads for a row of the circuit's Problemas list ({@code SHORT_CIRCUIT}, …). */
    public static String kindOf(Problem problem) {
        return switch (problem) {
            case ShortCircuit shortCircuit -> "SHORT_CIRCUIT";
            case WidthMismatch mismatch -> "WIDTH_MISMATCH";
            case DisplayBusOverride display -> "DISPLAY_BUS_OVERRIDE";
            case TunnelProblem tunnel -> "TUNNEL_" + tunnel.kind().name();
            case LinkedFileProblem linkedFile -> "LINKED_FILE_" + linkedFile.kind().name();
            case ConnectivityProblem connectivity -> ConnectivityProblemViews.kindOf(connectivity.cause());
        };
    }

    /** The kind the AI reads for a load problem of the component library ({@code LIBRARY_INVALID}, …). */
    public static String kindOf(LibraryProblem problem) {
        return LibraryProblemViews.kindOf(problem);
    }

    private static ProblemView view(ReadSource source, Problem problem) {
        ProblemReferences references = new ProblemReferences(source);
        String kind = kindOf(problem);
        return switch (problem) {
            case ShortCircuit shortCircuit -> {
                shortCircuit.drivers().forEach(driver -> references.pin(driver.componentId(), driver.pinIndex()));
                yield references.view(kind, false, problem.netId(),
                        shortCircuit.drivers().size() + " drivers impose different values on one net since time "
                                + shortCircuit.sinceTime() + " (" + driven(shortCircuit.drivers()) + ")");
            }
            case WidthMismatch mismatch -> {
                mismatch.pins().forEach(pin -> references.pin(pin.componentId(), pin.pinIndex()));
                yield references.view(kind, false, problem.netId(),
                        "Pins of different widths are joined in one net: " + widths(mismatch.pins()));
            }
            case DisplayBusOverride display -> {
                references.element(display.componentId());
                yield references.view(kind, true, problem.netId(),
                        "A 7-segment display has a driver on its bus input and on a one-bit input: the bus wins and"
                                + " the one-bit wires are ignored");
            }
            case TunnelProblem tunnel -> {
                tunnel.tunnels().forEach(references::element);
                yield references.view(kind, tunnel.isWarning(), problem.netId(), tunnelMessage(tunnel));
            }
            case LinkedFileProblem linkedFile -> {
                references.element(linkedFile.elementId());
                yield references.view(kind, true, problem.netId(), linkedFileMessage(source, linkedFile));
            }
            case ConnectivityProblem connectivity -> ConnectivityProblemViews.view(source, connectivity);
        };
    }

    private static String linkedFileMessage(ReadSource source, LinkedFileProblem problem) {
        String name = source.document().component(new ElementId(problem.elementId()))
                .map(ConnectivityProblemViews::nameOf).orElse(problem.elementId());
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
            case LONE_TUNNEL -> "The tunnel " + name + " is without direction and no other tunnel has its name: it"
                    + " joins nothing";
        };
    }
}
