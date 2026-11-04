package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.NetId;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks an exported circuit the way Logisim joins it ({@link LogisimNets}: wires at their ends, ends at their point):
 * every VSim pin that became a Logisim end must be in a Logisim net with exactly the ends of the pins its VSim net (as
 * drawn) joins. A Logisim net with ends of two VSim nets is a short; a VSim net whose ends fall in two Logisim nets is
 * open.
 */
final class ExportEquivalence {

    private ExportEquivalence() {
    }

    /** What differs, one line each; empty when Logisim joins exactly what VSim does. */
    static List<String> mismatches(ExportedCircuit exported, LogisimProject project) {
        Netlist netlist = new NetlistCompiler(exported.registry()).compileAsDrawn(exported.source());
        LogisimNetAnalysis analysis = LogisimNets.analyze(exported.circuit(), project);
        Map<String, Set<NetId>> vsimNetsOfLogisimNet = new HashMap<>();
        Map<NetId, Set<String>> logisimNetsOfVsimNet = new HashMap<>();
        int loneNet = 0;
        for (Map.Entry<PinRef, LogisimPoint> end : exported.ends().entrySet()) {
            Net vsimNet = netlist.netForPin(end.getKey()).orElseThrow();
            int logisimNet = analysis.netOf(end.getValue());
            String logisimKey = logisimNet < 0 ? "lone" + loneNet++ : String.valueOf(logisimNet);
            vsimNetsOfLogisimNet.computeIfAbsent(logisimKey, key -> new HashSet<>()).add(vsimNet.id());
            logisimNetsOfVsimNet.computeIfAbsent(vsimNet.id(), key -> new HashSet<>()).add(logisimKey);
        }
        List<String> mismatches = new ArrayList<>();
        vsimNetsOfLogisimNet.forEach((logisimNet, vsimNets) -> {
            if (vsimNets.size() > 1) {
                mismatches.add(exported.circuit().name() + ": short, Logisim net " + logisimNet + " joins " + vsimNets);
            }
        });
        logisimNetsOfVsimNet.forEach((vsimNet, logisimNets) -> {
            if (logisimNets.size() > 1) {
                mismatches.add(exported.circuit().name() + ": open, " + vsimNet + " falls in Logisim nets "
                        + logisimNets);
            }
        });
        return mismatches;
    }
}
