package cl.almejo.vsim.core.io.logisim;

import cl.almejo.vsim.core.component.ComponentType;
import cl.almejo.vsim.core.component.ComponentTypeRegistry;
import cl.almejo.vsim.core.document.CircuitDocument;
import cl.almejo.vsim.core.document.Component;
import cl.almejo.vsim.core.library.ComponentDefinition;
import cl.almejo.vsim.core.library.SubcircuitTypes;
import cl.almejo.vsim.core.netlist.Net;
import cl.almejo.vsim.core.netlist.Netlist;
import cl.almejo.vsim.core.netlist.NetlistCompiler;
import cl.almejo.vsim.core.netlist.PinRef;
import cl.almejo.vsim.core.tunnel.Tunnels;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a trip through Logisim must keep, measured on VSim documents: the parts (by type) and the nets, each net as the
 * sorted pins it joins ({@code and.in0}, {@code subcircuit:Inversor.a}); tunnels join by name and are not themselves
 * counted, since Logisim's tunnels have no direction (the import chooses it again).
 */
final class RoundTrip {

    private RoundTrip() {
    }

    /** {@code document} exported, written, read back and imported again. */
    static CircuitDocument throughLogisim(CircuitDocument document, ComponentTypeRegistry registry) throws Exception {
        String text = LogisimWriter.toText(LogisimExporter.exportDocument(document, registry).project());
        LogisimProject project = new LogisimReader().read(new ByteArrayInputStream(
                text.getBytes(StandardCharsets.UTF_8)));
        return LogisimImporter.importProject(project, registry).document();
    }

    /** The parts by type and the nets of {@code document} and of every definition it uses, by circuit name. */
    static Map<String, List<String>> describe(CircuitDocument document, ComponentTypeRegistry baseRegistry) {
        Map<String, List<String>> circuits = new LinkedHashMap<>();
        circuits.put("(main)", describeCircuit(document, baseRegistry.extendedWith(
                SubcircuitTypes.resolverFor(document))));
        for (ComponentDefinition definition : document.usedDefinitions()) {
            CircuitDocument body = definition.bodyDocument(document);
            circuits.put(definition.name(), describeCircuit(body, baseRegistry.extendedWith(
                    SubcircuitTypes.resolverFor(body))));
        }
        return circuits;
    }

    private static List<String> describeCircuit(CircuitDocument document, ComponentTypeRegistry registry) {
        Map<String, Integer> parts = new TreeMap<>();
        for (Component component : document.components()) {
            if (!Tunnels.isTunnel(component.typeId())) {
                parts.merge(typeName(component, registry), 1, Integer::sum);
            }
        }
        List<String> description = new ArrayList<>();
        parts.forEach((type, count) -> description.add("part " + type + " x" + count));
        Netlist netlist = new NetlistCompiler(registry).compile(document);
        Map<PinRef, Component> owners = new HashMap<>();
        document.components().forEach(component -> {
            for (int pin = 0; pin < registry.typeOf(component).forComponent(component).pinCount(); pin++) {
                owners.put(new PinRef(component.id(), pin), component);
            }
        });
        List<String> nets = new ArrayList<>();
        for (Net net : netlist.nets()) {
            List<String> pins = new ArrayList<>();
            for (PinRef pin : net.pins()) {
                Component owner = owners.get(pin);
                if (owner != null && !Tunnels.isTunnel(owner.typeId())) {
                    ComponentType type = registry.typeOf(owner).forComponent(owner);
                    pins.add(typeName(owner, registry) + "." + type.pin(pin.pinIndex()).name());
                }
            }
            if (pins.size() > 1) {
                nets.add("net " + pins.stream().sorted().toList());
            }
        }
        nets.sort(String::compareTo);
        description.addAll(nets);
        return description;
    }

    private static String typeName(Component component, ComponentTypeRegistry registry) {
        return SubcircuitTypes.definitionOf(registry.typeOf(component))
                .map(definition -> "subcircuit:" + definition.name())
                .orElse(component.typeId());
    }
}
