package cl.almejo.vsim.core.truthtable;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.simulation.FlatCircuit;
import cl.almejo.vsim.core.simulation.FlatComponent;
import cl.almejo.vsim.core.simulation.FlatNet;
import cl.almejo.vsim.core.simulation.FlatPinRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds feedback: a cycle in the graph whose nodes are nets and whose edges go from a net a component reads to a net
 * the same component drives (through a different pin). Passive components (LEDs, output pins, interface probes) add
 * no edge. Iterative depth-first search, so deep circuits cannot overflow the stack.
 */
public final class CombinationalLoops {

    private CombinationalLoops() {
    }

    public static boolean exists(FlatCircuit circuit) {
        Map<String, Set<String>> successors = successors(circuit);
        Set<String> finished = new HashSet<>();
        Set<String> onPath = new HashSet<>();
        for (String start : successors.keySet()) {
            if (finished.contains(start)) {
                continue;
            }
            Deque<Map.Entry<String, Iterator<String>>> stack = new ArrayDeque<>();
            stack.push(Map.entry(start, successors.get(start).iterator()));
            onPath.add(start);
            while (!stack.isEmpty()) {
                Map.Entry<String, Iterator<String>> top = stack.peek();
                if (!top.getValue().hasNext()) {
                    stack.pop();
                    onPath.remove(top.getKey());
                    finished.add(top.getKey());
                    continue;
                }
                String next = top.getValue().next();
                if (onPath.contains(next)) {
                    return true;
                }
                if (!finished.contains(next)) {
                    onPath.add(next);
                    stack.push(Map.entry(next, successors.getOrDefault(next, Set.of()).iterator()));
                }
            }
        }
        return false;
    }

    private static Map<String, Set<String>> successors(FlatCircuit circuit) {
        Map<FlatPinRef, String> netOfPin = new HashMap<>();
        for (FlatNet net : circuit.nets()) {
            net.pins().forEach(pin -> netOfPin.put(pin, net.id()));
        }
        Map<String, Set<String>> successors = new LinkedHashMap<>();
        for (FlatComponent component : circuit.components()) {
            ComponentType type = component.type();
            List<Integer> inputs = new ArrayList<>();
            List<Integer> drivers = new ArrayList<>();
            for (int pinIndex = 0; pinIndex < type.pinCount(); pinIndex++) {
                if (type.pin(pinIndex).isInput()) {
                    inputs.add(pinIndex);
                }
                if (type.pin(pinIndex).isDriver()) {
                    drivers.add(pinIndex);
                }
            }
            for (int input : inputs) {
                String from = netOfPin.get(new FlatPinRef(component.id(), input));
                if (from == null) {
                    continue;
                }
                for (int driver : drivers) {
                    String to = netOfPin.get(new FlatPinRef(component.id(), driver));
                    if (driver != input && to != null) {
                        successors.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
                    }
                }
            }
        }
        return successors;
    }
}
